package xyz.nextalone.nagram.ui.fluid;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.text.StaticLayout;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.ChatActivityInterface;
import org.telegram.ui.Components.ChatAvatarContainer;
import org.telegram.ui.TopicsFragment;

import java.lang.ref.WeakReference;

import xyz.nextalone.nagram.ui.UIStyleEngine;

/** Geometry only: the navigation stack owns progress, gestures, springs and fragment callbacks. */
public final class FluidChatTransition {
    public interface Renderer { boolean draw(); }

    private final WeakReference<BaseFragment> list;
    private final WeakReference<BaseFragment> target;
    private final WeakReference<View> sourceView;
    private final long dialogId;
    private final long topicId;
    private final boolean isTopic;
    private final boolean isChatOrTopic;
    private final RectF origin = new RectF(), originAvatar = new RectF();
    private final RectF surface = new RectF(), avatarBounds = new RectF();
    private final RectF targetAvatar = new RectF(), targetTitle = new RectF();
    private final Path clip = new Path();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] hostLocation = new int[2], location = new int[2];
    private final Rect visible = new Rect();
    private StaticLayout sourceTitle;
    private float titleX, titleY;
    private View hiddenAvatar, hiddenTitle;
    private float avatarAlpha, titleAlpha;
    private boolean unanchoredReturn;

    private FluidChatTransition(BaseFragment list, BaseFragment target, View sourceView, long dialogId, long topicId, boolean isTopic, boolean isChatOrTopic) {
        this.list = new WeakReference<>(list);
        this.target = new WeakReference<>(target);
        this.sourceView = sourceView != null ? new WeakReference<>(sourceView) : null;
        this.dialogId = dialogId;
        this.topicId = topicId;
        this.isTopic = isTopic;
        this.isChatOrTopic = isChatOrTopic;
    }

    public static FluidChatTransition capture(ViewGroup host, BaseFragment list, BaseFragment target, View touched) {
        if (!UIStyleEngine.isFluid() || list == null || target == null || list.getCurrentAccount() != target.getCurrentAccount()) {
            return null;
        }
        long dialogId = 0;
        long topicId = 0;
        boolean isTopic = false;
        boolean isChatOrTopic = false;
        if (target instanceof ChatActivity) {
            ChatActivity chat = (ChatActivity) target;
            dialogId = chat.getDialogId();
            topicId = chat.getTopicId();
            isTopic = chat.isTopic || topicId != 0;
            isChatOrTopic = true;
        } else if (target instanceof TopicsFragment) {
            TopicsFragment topics = (TopicsFragment) target;
            dialogId = topics.getDialogId();
            topicId = 0;
            isTopic = false;
            isChatOrTopic = true;
        }

        if (isChatOrTopic) {
            View row = touched;
            while (row != null && !(row instanceof DialogCell)) {
                row = row.getParent() instanceof View ? (View) row.getParent() : null;
            }
            if (!(row instanceof DialogCell) && list.getFragmentView() != null) {
                row = findRow(list.getFragmentView(), dialogId, topicId, isTopic, 0);
            }
            if (row instanceof DialogCell && row.getWidth() > 0 && row.getHeight() > 0) {
                DialogCell cell = (DialogCell) row;
                if (cell.getDialogId() == dialogId && (!isTopic || (cell.forumTopic != null && cell.forumTopic.id == topicId))) {
                    FluidChatTransition result = new FluidChatTransition(list, target, cell, dialogId, topicId, isTopic, true);
                    boolean captured = result.captureRow(host, cell, 0f, 0f);
                    if (captured) return result;
                }
            }
        }

        // Generic page morph transition (Profile, Settings, EditChat, Sub-pages, etc.)
        if (touched != null && touched.isAttachedToWindow() && touched.getWidth() > 0 && touched.getHeight() > 0) {
            FluidChatTransition result = new FluidChatTransition(list, target, touched, dialogId, topicId, isTopic, false);
            if (result.captureGeneric(host, touched, 0f, 0f)) {
                return result;
            }
        }
        return null;
    }

    public boolean matches(BaseFragment previous, BaseFragment current) {
        return previous != null && previous == list.get() && current != null && current == target.get();
    }

    public View chatView() {
        BaseFragment owner = target.get();
        return owner == null ? null : owner.getFragmentView();
    }

    /** Reacquire by dialog identity, never by a RecyclerView position or a recycled row reference. */
    public void refreshOrigin(ViewGroup host, View listContainer, boolean returning) {
        BaseFragment owner = list.get();
        if (owner == null || owner.getFragmentView() == null || !owner.getFragmentView().isAttachedToWindow()) return;
        if (isChatOrTopic) {
            DialogCell row = findRow(owner.getFragmentView(), 0);
            boolean found = row != null && captureRow(host, row, listContainer.getTranslationX(), listContainer.getTranslationY());
            if (returning && !found && owner.getFragmentView().isLaidOut()) unanchoredReturn = true;
        } else {
            View src = sourceView != null ? sourceView.get() : null;
            boolean found = src != null && src.isAttachedToWindow() && src.isShown() && captureGeneric(host, src, listContainer.getTranslationX(), listContainer.getTranslationY());
            if (returning && !found && owner.getFragmentView().isLaidOut()) unanchoredReturn = true;
        }
    }

    private boolean captureGeneric(ViewGroup host, View view, float dx, float dy) {
        if (!view.getGlobalVisibleRect(visible) || visible.height() < 4) return false;
        host.getLocationOnScreen(hostLocation);
        view.getLocationOnScreen(location);
        float x = location[0] - hostLocation[0] - dx, y = location[1] - hostLocation[1] - dy;
        origin.set(x, y, x + view.getWidth(), y + view.getHeight());
        return true;
    }

    private DialogCell findRow(View view, int depth) {
        return findRow(view, dialogId, topicId, isTopic, depth);
    }

    private static DialogCell findRow(View view, long dialogId, long topicId, boolean isTopic, int depth) {
        if (depth > 18 || view == null || view.getVisibility() != View.VISIBLE) return null;
        if (view instanceof DialogCell) {
            DialogCell cell = (DialogCell) view;
            if (cell.getDialogId() == dialogId) {
                if (isTopic) {
                    if (cell.forumTopic != null && cell.forumTopic.id == topicId) {
                        return cell;
                    }
                } else {
                    return cell;
                }
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                DialogCell found = findRow(group.getChildAt(i), dialogId, topicId, isTopic, depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    private boolean captureRow(ViewGroup host, DialogCell row, float dx, float dy) {
        if (!row.getGlobalVisibleRect(visible) || visible.height() < row.getHeight() / 2) return false;
        host.getLocationOnScreen(hostLocation);
        row.getLocationOnScreen(location);
        float x = location[0] - hostLocation[0] - dx, y = location[1] - hostLocation[1] - dy;
        origin.set(x, y, x + row.getWidth(), y + row.getHeight());

        boolean isTopicCell = isTopic || row instanceof TopicsFragment.TopicDialogCell || (row.forumTopic != null && !row.drawAvatar);
        if (isTopicCell && !row.drawAvatar) {
            int padding = AndroidUtilities.dp(10);
            int paddingTop = AndroidUtilities.dp(10);
            int size = AndroidUtilities.dp(28);
            float iconLeft = LocaleController.isRTL ? (x + row.getWidth() - padding - size) : (x + padding);
            float iconTop = y + paddingTop;
            originAvatar.set(iconLeft, iconTop, iconLeft + size, iconTop + size);

            sourceTitle = row.nameLayout;
            titleX = x + row.nameLeft + row.nameLayoutTranslateX;
            titleY = y + AndroidUtilities.dp(row.useForceThreeLines || SharedConfig.useThreeLinesLayout ? 10 : 14) - AndroidUtilities.dp(4);
        } else {
            originAvatar.set(x + row.avatarImage.getImageX(), y + row.avatarImage.getImageY(),
                    x + row.avatarImage.getImageX() + row.avatarImage.getImageWidth(),
                    y + row.avatarImage.getImageY() + row.avatarImage.getImageHeight());
            sourceTitle = row.nameLayout;
            titleX = x + row.nameLeft + row.nameLayoutTranslateX;
            titleY = y + AndroidUtilities.dp(row.useForceThreeLines || SharedConfig.useThreeLinesLayout ? 10 : 14);
        }
        return true;
    }

    public boolean draw(Canvas canvas, ViewGroup host, View child, float progress, Renderer renderer) {
        float p = Math.max(0f, Math.min(1f, progress));
        BaseFragment owner = target.get();
        if (owner == null) return renderer.draw();
        ChatAvatarContainer header = null;
        if (owner instanceof ChatActivityInterface) {
            header = ((ChatActivityInterface) owner).getAvatarContainer();
        }
        View avatar = header == null ? null : header.getAvatarImageView();
        View title = header == null ? null : header.getTitleTextView();
        boolean sharedHeader = avatar != null && title != null && avatar.getWidth() > 0 && title.getWidth() > 0;
        if (sharedHeader) {
            bounds(host, child, avatar, targetAvatar);
            bounds(host, child, title, targetTitle);
            hideHeader(avatar, title);
        }
        float left = host.getPaddingLeft(), top = child.getTop();
        float right = host.getWidth() - host.getPaddingRight(), bottom = child.getBottom();
        float inset = AndroidUtilities.dp(24);
        surface.set(mix(unanchoredReturn ? left + inset : origin.left, left, p),
                mix(unanchoredReturn ? top + inset : origin.top, top, p),
                mix(unanchoredReturn ? right - inset : origin.right, right, p),
                mix(unanchoredReturn ? bottom - inset : origin.bottom, bottom, p));
        float radius = UIStyleEngine.getCardCornerRadius() * (1f - p);
        clip.rewind();
        clip.addRoundRect(surface, radius, radius, Path.Direction.CW);
        int save = canvas.save();
        canvas.clipPath(clip);
        int bgColor = Theme.getColor(Theme.key_windowBackgroundWhite, owner.getResourceProvider());
        if (owner.getFragmentView() != null && owner.getFragmentView().getBackground() instanceof ColorDrawable) {
            bgColor = ((ColorDrawable) owner.getFragmentView().getBackground()).getColor();
        }
        paint.setColor(bgColor);
        canvas.drawRect(surface, paint);
        int content = -1;
        if (p < 0.999f) {
            content = canvas.saveLayerAlpha(surface, (int) (255f * p));
        }
        canvas.translate(-child.getTranslationX(), -child.getTranslationY());
        boolean result = renderer.draw();
        if (content != -1) {
            canvas.restoreToCount(content);
        }
        if (sharedHeader) {
            avatarBounds.set(mix(unanchoredReturn ? targetAvatar.left : originAvatar.left, targetAvatar.left, p),
                    mix(unanchoredReturn ? targetAvatar.top : originAvatar.top, targetAvatar.top, p),
                    mix(unanchoredReturn ? targetAvatar.right : originAvatar.right, targetAvatar.right, p),
                    mix(unanchoredReturn ? targetAvatar.bottom : originAvatar.bottom, targetAvatar.bottom, p));
            int av = canvas.save();
            canvas.translate(avatarBounds.left, avatarBounds.top);
            canvas.scale(avatarBounds.width() / avatar.getWidth(), avatarBounds.height() / avatar.getHeight());
            avatar.setAlpha(1f);
            avatar.draw(canvas);
            avatar.setAlpha(0f);
            canvas.restoreToCount(av);
            float tx = mix(unanchoredReturn ? targetTitle.left : titleX, targetTitle.left, p);
            float ty = mix(unanchoredReturn ? targetTitle.top : titleY, targetTitle.top, p);
            if (!unanchoredReturn && sourceTitle != null && p < 1f) {
                int text = canvas.saveLayerAlpha(tx, ty, tx + sourceTitle.getWidth(), ty + sourceTitle.getHeight(), (int) (255f * (1f - p)));
                canvas.translate(tx, ty);
                sourceTitle.draw(canvas);
                canvas.restoreToCount(text);
            }
            int text = canvas.saveLayerAlpha(tx, ty, tx + title.getWidth(), ty + title.getHeight(), (int) (255f * (unanchoredReturn ? 1f : p)));
            canvas.translate(tx, ty);
            title.setAlpha(1f);
            title.draw(canvas);
            title.setAlpha(0f);
            canvas.restoreToCount(text);
        }
        canvas.restoreToCount(save);
        return result;
    }

    private void bounds(ViewGroup host, View container, View view, RectF out) {
        host.getLocationOnScreen(hostLocation);
        view.getLocationOnScreen(location);
        float x = location[0] - hostLocation[0] - container.getTranslationX();
        float y = location[1] - hostLocation[1] - container.getTranslationY();
        out.set(x, y, x + view.getWidth(), y + view.getHeight());
    }

    private void hideHeader(View avatar, View title) {
        if (hiddenAvatar != avatar || hiddenTitle != title) {
            boolean fallback = unanchoredReturn;
            restore();
            unanchoredReturn = fallback;
            hiddenAvatar = avatar; hiddenTitle = title;
            avatarAlpha = avatar.getAlpha(); titleAlpha = title.getAlpha();
        }
        avatar.setAlpha(0f); title.setAlpha(0f);
    }

    public void restore() {
        if (hiddenAvatar != null) hiddenAvatar.setAlpha(avatarAlpha);
        if (hiddenTitle != null) hiddenTitle.setAlpha(titleAlpha);
        hiddenAvatar = hiddenTitle = null;
        unanchoredReturn = false;
    }

    private static float mix(float a, float b, float p) { return a + (b - a) * p; }
}

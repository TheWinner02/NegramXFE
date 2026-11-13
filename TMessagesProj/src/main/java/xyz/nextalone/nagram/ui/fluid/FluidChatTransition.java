package xyz.nextalone.nagram.ui.fluid;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.StaticLayout;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.ChatAvatarContainer;

import java.lang.ref.WeakReference;

import xyz.nextalone.nagram.ui.UIStyleEngine;

/** Geometry only: the navigation stack owns progress, gestures, springs and fragment callbacks. */
public final class FluidChatTransition {
    public interface Renderer { boolean draw(); }

    private final WeakReference<BaseFragment> list;
    private final WeakReference<ChatActivity> chat;
    private final long dialogId;
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

    private FluidChatTransition(BaseFragment list, ChatActivity chat) {
        this.list = new WeakReference<>(list);
        this.chat = new WeakReference<>(chat);
        dialogId = chat.getDialogId();
    }

    public static FluidChatTransition capture(ViewGroup host, BaseFragment list, ChatActivity chat, View touched) {
        if (!UIStyleEngine.isFluid() || list == null || list.getCurrentAccount() != chat.getCurrentAccount()) return null;
        View row = touched;
        while (row != null && !(row instanceof DialogCell)) {
            row = row.getParent() instanceof View ? (View) row.getParent() : null;
        }
        if (!(row instanceof DialogCell) || ((DialogCell) row).getDialogId() != chat.getDialogId()
                || row.getWidth() <= 0 || row.getHeight() <= 0) return null;
        FluidChatTransition result = new FluidChatTransition(list, chat);
        return result.captureRow(host, (DialogCell) row, 0f, 0f) ? result : null;
    }

    public boolean matches(BaseFragment previous, BaseFragment current) {
        return previous != null && previous == list.get() && current != null && current == chat.get();
    }

    public View chatView() {
        ChatActivity owner = chat.get();
        return owner == null ? null : owner.getFragmentView();
    }

    /** Reacquire by dialog identity, never by a RecyclerView position or a recycled row reference. */
    public void refreshOrigin(ViewGroup host, View listContainer, boolean returning) {
        BaseFragment owner = list.get();
        if (owner == null || owner.getFragmentView() == null || !owner.getFragmentView().isAttachedToWindow()) return;
        DialogCell row = findRow(owner.getFragmentView(), 0);
        boolean found = row != null && captureRow(host, row, listContainer.getTranslationX(), listContainer.getTranslationY());
        if (returning && !found && owner.getFragmentView().isLaidOut()) unanchoredReturn = true;
    }

    private DialogCell findRow(View view, int depth) {
        if (depth > 18 || view.getVisibility() != View.VISIBLE) return null;
        if (view instanceof DialogCell) return ((DialogCell) view).getDialogId() == dialogId ? (DialogCell) view : null;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                DialogCell found = findRow(group.getChildAt(i), depth + 1);
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
        originAvatar.set(x + row.avatarImage.getImageX(), y + row.avatarImage.getImageY(),
                x + row.avatarImage.getImageX() + row.avatarImage.getImageWidth(),
                y + row.avatarImage.getImageY() + row.avatarImage.getImageHeight());
        sourceTitle = row.nameLayout;
        titleX = x + row.nameLeft + row.nameLayoutTranslateX;
        titleY = y + AndroidUtilities.dp(row.useForceThreeLines || SharedConfig.useThreeLinesLayout ? 10 : 14);
        return true;
    }

    public boolean draw(Canvas canvas, ViewGroup host, View child, float progress, Renderer renderer) {
        float p = Math.max(0f, Math.min(1f, progress));
        ChatActivity owner = chat.get();
        if (owner == null) return renderer.draw();
        ChatAvatarContainer header = owner.getAvatarContainer();
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
        if (unanchoredReturn) canvas.saveLayerAlpha(surface, (int) (255f * p));
        paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, owner.getResourceProvider()));
        canvas.drawRect(surface, paint);
        int content = canvas.saveLayerAlpha(surface, (int) (255f * p));
        canvas.translate(-child.getTranslationX(), -child.getTranslationY());
        boolean result = renderer.draw();
        canvas.restoreToCount(content);
        if (sharedHeader) {
            avatarBounds.set(mix(unanchoredReturn ? targetAvatar.left : originAvatar.left, targetAvatar.left, p),
                    mix(unanchoredReturn ? targetAvatar.top : originAvatar.top, targetAvatar.top, p),
                    mix(unanchoredReturn ? targetAvatar.right : originAvatar.right, targetAvatar.right, p),
                    mix(unanchoredReturn ? targetAvatar.bottom : originAvatar.bottom, targetAvatar.bottom, p));
            int av = canvas.save();
            canvas.translate(avatarBounds.left, avatarBounds.top);
            canvas.scale(avatarBounds.width() / avatar.getWidth(), avatarBounds.height() / avatar.getHeight());
            avatar.draw(canvas);
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
            title.draw(canvas);
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

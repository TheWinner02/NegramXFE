package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DialogRadioCell;
import org.telegram.ui.Cells.RadioButtonCell;
import org.telegram.ui.Cells.RadioCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextCheckCell2;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextRadioCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.FiltersSetupActivity;

import java.util.ArrayList;
import java.util.WeakHashMap;
import java.util.Objects;

public class SectionsScrollView extends ScrollView {

    private Theme.ResourcesProvider resourcesProvider;
    private LinearLayout contentView;

    private float sectionRadius = dp(16);
    private float[] sectionRadiusTop, sectionRadiusBottom;

    private static boolean isM3Expressive() {
        return xyz.nextalone.nagram.ui.UIStyleEngine.isMaterial3Expressive();
    }

    public static boolean isSectionView(View view) {
        if (isM3Expressive()) {
            return !Objects.equals(view.getTag(), RecyclerListView.TAG_NOT_SECTION) && (
                view instanceof TextCell ||
                view instanceof TextSettingsCell ||
                view instanceof TextCheckCell ||
                view instanceof TextCheckCell2 ||
                view instanceof RadioButtonCell ||
                view instanceof RadioCell ||
                view instanceof TextRadioCell ||
                view instanceof DialogRadioCell ||
                view instanceof org.telegram.ui.ActionBar.AlertDialog.AlertDialogCell ||
                view instanceof org.telegram.ui.Cells.AdminedChannelCell ||
                view instanceof LinkActionView ||
                view instanceof org.telegram.ui.PeerColorActivity.ChangeNameColorCell ||
                view instanceof org.telegram.ui.community.cells.CommunityLinkView2
            );
        }
        return !Objects.equals(view.getTag(), RecyclerListView.TAG_NOT_SECTION) && !(
            view instanceof TextInfoPrivacyCell ||
            view instanceof ShadowSectionCell ||
            view instanceof FiltersSetupActivity.HintInnerCell
        );
    }

    public SectionsScrollView(Context context, LinearLayout content, Theme.ResourcesProvider resourcesProvider) {
        this(context, content, resourcesProvider, true);
    }
    public SectionsScrollView(
        Context context,
        LinearLayout content,
        Theme.ResourcesProvider resourcesProvider,
        boolean enableTopPadding
    ) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        this.contentView = content;
        setWillNotDraw(false);

        int horizontalPadding = 12;
        int topPadding = enableTopPadding ? 12 : (isM3Expressive() ? 8 : 4);
        int bottomPadding = isM3Expressive() ? 16 : 12;
        contentView.setPadding(dp(horizontalPadding), dp(topPadding), dp(horizontalPadding), dp(bottomPadding));

        if (isM3Expressive() || xyz.nextalone.nagram.ui.UIStyleEngine.isIosLiquidGlass()) {
            sectionRadius = xyz.nextalone.nagram.ui.UIStyleEngine.getCardCornerRadius();
        }
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        this.sectionRadiusTop = new float[] {
            sectionRadius, sectionRadius,
            sectionRadius, sectionRadius,
            0, 0,
            0, 0
        };
        this.sectionRadiusBottom = new float[] {
            0, 0,
            0, 0,
            sectionRadius, sectionRadius,
            sectionRadius, sectionRadius
        };
    }

    private ArrayList<Runnable> onScroll = new ArrayList<>();
    private View pressedSectionView;
    private int touchSlop;
    private float pressDownX;
    private float pressDownY;
    private boolean pressCancelledByScroll;

    public void onScroll(Runnable listener) {
        onScroll.add(listener);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (isM3Expressive()) {
            updatePressedSectionView(ev);
        }
        boolean result = super.dispatchTouchEvent(ev);
        if (isM3Expressive() && (ev.getActionMasked() == MotionEvent.ACTION_UP || ev.getActionMasked() == MotionEvent.ACTION_CANCEL)) {
            setPressedSectionView(null);
        }
        return result;
    }

    private void updatePressedSectionView(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressDownX = ev.getX();
                pressDownY = ev.getY();
                pressCancelledByScroll = false;
                children.clear();
                gatherChildren(contentView, 0, 0);
                setPressedSectionView(findSectionChildUnder(ev.getX(), ev.getY()));
                break;
            case MotionEvent.ACTION_MOVE:
                if (pressCancelledByScroll) {
                    return;
                }
                float dx = ev.getX() - pressDownX;
                float dy = ev.getY() - pressDownY;
                if (dx * dx + dy * dy > touchSlop * touchSlop) {
                    pressCancelledByScroll = true;
                    setPressedSectionView(null);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                pressCancelledByScroll = false;
                setPressedSectionView(null);
                break;
        }
    }

    private View findSectionChildUnder(float x, float y) {
        float contentX = contentView.getX();
        float contentY = contentView.getY() - getScrollY();
        for (int i = children.size() - 1; i >= 0; i--) {
            View child = children.get(i);
            if (!isSectionView(child)) {
                continue;
            }
            float left = contentX + getChildX(child);
            float top = contentY + getChildY(child);
            float right = left + child.getWidth();
            float bottom = top + child.getHeight();
            if (x >= left && x <= right && y >= top && y <= bottom) {
                return child;
            }
        }
        return null;
    }

    private void setPressedSectionView(View view) {
        if (pressedSectionView == view) {
            return;
        }
        if (pressedSectionView != null) {
            getPressMorph(pressedSectionView).setPressed(false);
        }
        pressedSectionView = view;
        if (pressedSectionView != null) {
            getPressMorph(pressedSectionView).setPressed(true);
        }
        invalidate();
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);

        for (Runnable listener : onScroll)
            listener.run();

        invalidate();
        contentView.invalidate();
    }

    private ArrayList<View> children = new ArrayList<>();
    private void gatherChildren(ViewGroup layout, float x, float y) {
        for (int i = 0; i < layout.getChildCount(); ++i) {
            final View child = layout.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE) continue;
            boolean canFlatten = !Objects.equals(child.getTag(), RecyclerListView.TAG_NOT_SECTION) &&
                child instanceof LinearLayout &&
                ((LinearLayout) child).getOrientation() == LinearLayout.VERTICAL &&
                x + child.getX() <= contentView.getPaddingLeft() &&
                x + child.getX() + child.getWidth() >= contentView.getWidth() - contentView.getPaddingRight();
            if (isM3Expressive()) {
                canFlatten = canFlatten && child instanceof SectionsLinearLayout;
            }
            if (canFlatten) {
                gatherChildren((LinearLayout) child, x + child.getX(), y + child.getY());
            } else {
                children.add(child);
            }
        }
    }
    private float getChildX(View child) {
        if (child == null || child == contentView || !(child.getParent() instanceof View)) return 0;
        return getChildX((View) child.getParent()) + child.getX();
    }
    private float getChildY(View child) {
        if (child == null || child == contentView || !(child.getParent() instanceof View)) return 0;
        return getChildY((View) child.getParent()) + child.getY();
    }

    private void drawSectionsBackgrounds(Canvas canvas) {
        children.clear();
        gatherChildren(contentView, 0, 0);

        if (isM3Expressive()) {
            drawM3ExpressiveSectionBackgrounds(canvas);
            return;
        }

        View start = null, prev = null;
        for (View child : children) {
            if (!isSectionView(child)) {
                drawSectionBackground(canvas, start, prev);
                start = prev = null;
                continue;
            }
            if (start != null && Math.abs(prev.getAlpha() - child.getAlpha()) > 0.1f) {
                drawSectionBackground(canvas, start, prev);
                start = null;
            }
            if (start == null) {
                start = child;
            }
            prev = child;
        }
        drawSectionBackground(canvas, start, prev);
    }

    private boolean hasM3ExpressiveSectionItem(int index) {
        return index >= 0 && index < children.size() && isSectionView(children.get(index));
    }

    private void drawM3ExpressiveSectionBackgrounds(Canvas canvas) {
        for (int i = 0; i < children.size(); i++) {
            View child = children.get(i);
            if (!isSectionView(child)) {
                continue;
            }
            boolean hasAbove = hasM3ExpressiveSectionItem(i - 1);
            boolean hasBelow = hasM3ExpressiveSectionItem(i + 1);
            drawM3ExpressiveSectionItemBackground(canvas, child, hasAbove, hasBelow);
        }
    }

    private void drawM3ExpressiveSectionItemBackground(Canvas canvas, View child, boolean hasAbove, boolean hasBelow) {
        M3PressMorphHelper pressMorph = getPressMorph(child);
        pressMorph.setPressed(child == pressedSectionView || child.isPressed());
        float pressProgress = pressMorph.getProgress();
        float innerRadius = dp(4);
        float pressedRadius = getM3PressedRadius(child);
        float topRadius = AndroidUtilities.lerp(hasAbove ? innerRadius : sectionRadius, pressedRadius, pressProgress);
        float bottomRadius = AndroidUtilities.lerp(hasBelow ? innerRadius : sectionRadius, pressedRadius, pressProgress);
        float gap = dp(1);

        AndroidUtilities.rectTmp.set(
            contentView.getX() + getChildX(child),
            Math.max(getScrollY() - sectionRadius, contentView.getY() + getChildY(child) + (hasAbove ? gap : 0)),
            contentView.getX() + getChildX(child) + child.getWidth(),
            Math.min(getHeight() + sectionRadius + getScrollY(), contentView.getY() + getChildY(child) + child.getHeight() - (hasBelow ? gap : 0))
        );
        if (AndroidUtilities.rectTmp.bottom < AndroidUtilities.rectTmp.top) return;
        RecyclerListView.drawBackgroundRect(canvas, AndroidUtilities.rectTmp, topRadius, bottomRadius, child.getAlpha(), resourcesProvider);
    }

    private void drawSectionBackground(
        Canvas canvas,
        View from, View to
    ) {
        if (from == null || to == null) return;

        float fromTopMargin = 0, toBottomMargin = 0;
        ViewGroup.LayoutParams fromLp = from.getLayoutParams();
        ViewGroup.LayoutParams toLp = to.getLayoutParams();
        if (from.getParent() != contentView && fromLp instanceof MarginLayoutParams) {
            fromTopMargin = ((MarginLayoutParams) fromLp).topMargin;
        }
        if (to.getParent() != contentView && toLp instanceof MarginLayoutParams) {
            toBottomMargin = ((MarginLayoutParams) toLp).bottomMargin;
        }

        View fromContainer = (from.getParent() instanceof View && from.getParent() != contentView) ? (View) from.getParent() : from;
        float left = contentView.getX() + getChildX(fromContainer);
        float right = left + fromContainer.getWidth();

        AndroidUtilities.rectTmp.set(
            left,
            Math.max(getScrollY() - dp(16), contentView.getY() + getChildY(from) - fromTopMargin),
            right,
            Math.min(getHeight() + dp(16) + getScrollY(), contentView.getY() + getChildY(to) + to.getHeight() + toBottomMargin)
        );
        if (AndroidUtilities.rectTmp.bottom < AndroidUtilities.rectTmp.top) return;
        RecyclerListView.drawBackgroundRect(canvas, AndroidUtilities.rectTmp, dp(16), dp(16), from.getAlpha(), resourcesProvider);
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        drawSectionsBackgrounds(canvas);
        super.dispatchDraw(canvas);
    }

    @Override
    protected boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime) {
        return super.drawChild(canvas, child, drawingTime);
    }

    private final Path clipPath = new Path();
    private final WeakHashMap<View, M3PressMorphHelper> pressMorphHelpers = new WeakHashMap<>();

    private M3PressMorphHelper getPressMorph(View view) {
        M3PressMorphHelper helper = pressMorphHelpers.get(view);
        if (helper == null) {
            helper = new M3PressMorphHelper(this);
            pressMorphHelpers.put(view, helper);
        }
        return helper;
    }

    private void clipChild(Canvas canvas, View child, ViewGroup drawingParent) {
        if (child == null || !isSectionView(child))
            return;

        boolean prev, next;
        int position = children.indexOf(child);
        if (position < 0) {
            return;
        }
        final View prevChild = position - 1 < 0 ? null : children.get(position - 1);
        final View nextChild = position + 1 >= children.size() ? null : children.get(position + 1);
        prev = prevChild != null && isSectionView(prevChild);
        next = nextChild != null && isSectionView(nextChild);

        float left = getChildXRelativeTo(child, drawingParent);
        float top = getChildYRelativeTo(child, drawingParent);
        AndroidUtilities.rectTmp.set(left, top, left + child.getWidth(), top + child.getHeight());
        if (prev && next) {
            if (prev && next && !isM3Expressive()) return;
        }
        float topRadius = sectionRadius;
        float bottomRadius = sectionRadius;
        if (isM3Expressive()) {
            M3PressMorphHelper pressMorph = getPressMorph(child);
            pressMorph.setPressed(child == pressedSectionView || child.isPressed());
            float progress = pressMorph.getProgress();
            float pressedRadius = getM3PressedRadius(child);
            topRadius = AndroidUtilities.lerp(prev ? dp(4) : sectionRadius, pressedRadius, progress);
            bottomRadius = AndroidUtilities.lerp(next ? dp(4) : sectionRadius, pressedRadius, progress);
        }
        if (!prev && !next) {
            clipPath.rewind();
            clipPath.addRoundRect(AndroidUtilities.rectTmp, topRadius, topRadius, Path.Direction.CW);
            canvas.clipPath(clipPath);
        } else if (!prev) {
            clipPath.rewind();
            float[] radii = isM3Expressive() ? new float[] { topRadius, topRadius, topRadius, topRadius, 0, 0, 0, 0 } : sectionRadiusTop;
            clipPath.addRoundRect(AndroidUtilities.rectTmp, radii, Path.Direction.CW);
            canvas.clipPath(clipPath);
        } else if (!next) {
            clipPath.rewind();
            float[] radii = isM3Expressive() ? new float[] { 0, 0, 0, 0, bottomRadius, bottomRadius, bottomRadius, bottomRadius } : sectionRadiusBottom;
            clipPath.addRoundRect(AndroidUtilities.rectTmp, radii, Path.Direction.CW);
            canvas.clipPath(clipPath);
        } else if (isM3Expressive()) {
            clipPath.rewind();
            clipPath.addRoundRect(AndroidUtilities.rectTmp, new float[] { topRadius, topRadius, topRadius, topRadius, bottomRadius, bottomRadius, bottomRadius, bottomRadius }, Path.Direction.CW);
            canvas.clipPath(clipPath);
        }
    }

    private float getChildXRelativeTo(View child, ViewGroup parent) {
        float x = child.getX();
        ViewParent currentParent = child.getParent();
        while (currentParent instanceof View && currentParent != parent) {
            View parentView = (View) currentParent;
            x += parentView.getX();
            currentParent = parentView.getParent();
        }
        return x;
    }

    private float getChildYRelativeTo(View child, ViewGroup parent) {
        float y = child.getY();
        ViewParent currentParent = child.getParent();
        while (currentParent instanceof View && currentParent != parent) {
            View parentView = (View) currentParent;
            y += parentView.getY();
            currentParent = parentView.getParent();
        }
        return y;
    }

    private float getM3PressedRadius(View child) {
        if (child instanceof org.telegram.ui.ActionBar.AlertDialog.AlertDialogCell) {
            return Math.max(sectionRadius, child.getHeight() / 2f);
        }
        return Math.max(sectionRadius, child.getHeight() / 2f);
    }

    public static class SectionsLinearLayout extends LinearLayout {
        public SectionsLinearLayout(Context context) {
            super(context);
            setWillNotDraw(false);
        }

        @Override
        public void setBackgroundColor(int color) {
            SectionsScrollView scrollView = findSectionsScrollView();
            if ((isM3Expressive() || xyz.nextalone.nagram.ui.UIStyleEngine.isIosLiquidGlass()) && scrollView != null) {
                super.setBackgroundColor(Color.TRANSPARENT);
                scrollView.invalidate();
                return;
            }
            super.setBackgroundColor(color);
        }

        @Override
        public void setBackground(Drawable background) {
            SectionsScrollView scrollView = findSectionsScrollView();
            if ((isM3Expressive() || xyz.nextalone.nagram.ui.UIStyleEngine.isIosLiquidGlass()) && scrollView != null && background != null) {
                super.setBackground(null);
                scrollView.invalidate();
                return;
            }
            super.setBackground(background);
        }

        @Override
        protected boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime) {
            SectionsScrollView scrollView = findSectionsScrollView();
            if (scrollView != null && isM3Expressive() && isSectionView(child)) {
                M3PressMorphHelper pressMorph = scrollView.getPressMorph(child);
                pressMorph.setPressed(child == scrollView.pressedSectionView || child.isPressed());
                if (pressMorph.getProgress() > 0f) {
                    scrollView.invalidate();
                }
            }
            if (scrollView != null && (isM3Expressive() || xyz.nextalone.nagram.ui.UIStyleEngine.isIosLiquidGlass() || getParent() instanceof SectionsScrollView)) {
                canvas.save();
                scrollView.clipChild(canvas, child, this);
                boolean r = super.drawChild(canvas, child, drawingTime);
                canvas.restore();
                return r;
            }
            return super.drawChild(canvas, child, drawingTime);
        }

        private SectionsScrollView findSectionsScrollView() {
            ViewParent parent = getParent();
            while (parent != null) {
                if (parent instanceof SectionsScrollView) {
                    return (SectionsScrollView) parent;
                }
                parent = parent.getParent();
            }
            return null;
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, r, b);
            SectionsScrollView scrollView = findSectionsScrollView();
            if (scrollView != null) {
                scrollView.invalidate();
            }
        }
    }
}

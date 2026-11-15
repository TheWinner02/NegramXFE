package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
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
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.FileLog;
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
import org.telegram.ui.Components.blur3.capture.IBlur3Capture;
import org.telegram.ui.FiltersSetupActivity;

import java.util.ArrayList;
import java.util.WeakHashMap;
import java.util.Objects;

public class SectionsScrollView extends ScrollView implements IBlur3Capture {

    private static final float OPAQUE_BACKGROUND_OVERLAP = 1.0f;
    private static final int SECTION_VALUES_COUNT = 5;
    private static final long OVERSCROLL_CAPTURE_TIMEOUT_MS = 500L;

    private Theme.ResourcesProvider resourcesProvider;
    private LinearLayout contentView;

    private final float sectionRadius = dp(16);
    private final Paint backgroundPaint = new Paint();
    private final Paint sectionPaint = new Paint();
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Rect cornerSrc = new Rect();
    private final RectF cornerDst = new RectF();
    private final RectF captureRect = new RectF();
    private final Matrix inverseMatrix = new Matrix();
    private Bitmap cornerBitmap;
    private int bitmapSize;
    private int bitmapBackgroundColor;
    private boolean drawBackground;
    private float[] sections = new float[SECTION_VALUES_COUNT * 16];
    private int sectionsCount;
    private float[] opaqueIntervals = new float[32];
    private int opaqueIntervalsCount;
    private boolean overscrolling;
    private ArrayList<OnOverscrollChangedListener> overscrollListeners;
    private final Runnable finishOverscroll = () -> setOverscrolling(false);

    public interface OnOverscrollChangedListener {
        void onOverscrollChanged(boolean active);
    }

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

    public boolean isDrawBackground() {
        return drawBackground;
    }

    public void setDrawBackground(boolean drawBackground) {
        if (this.drawBackground == drawBackground) return;
        this.drawBackground = drawBackground;
        invalidate();
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

    public boolean hasActiveOverscroll() {
        return overscrolling;
    }

    public void addOnOverscrollChangedListener(OnOverscrollChangedListener listener) {
        if (listener == null) return;
        if (overscrollListeners == null) {
            overscrollListeners = new ArrayList<>();
        }
        if (!overscrollListeners.contains(listener)) {
            overscrollListeners.add(listener);
        }
    }

    public void removeOnOverscrollChangedListener(OnOverscrollChangedListener listener) {
        if (overscrollListeners != null) {
            overscrollListeners.remove(listener);
        }
    }

    private void setOverscrolling(boolean overscrolling) {
        if (this.overscrolling == overscrolling) return;
        this.overscrolling = overscrolling;
        invalidate();
        if (overscrollListeners == null) return;
        for (int i = overscrollListeners.size() - 1; i >= 0; i--) {
            overscrollListeners.get(i).onOverscrollChanged(overscrolling);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(finishOverscroll);
        setOverscrolling(false);
        super.onDetachedFromWindow();
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

    @Override
    protected boolean overScrollBy(
            int deltaX, int deltaY,
            int scrollX, int scrollY,
            int scrollRangeX, int scrollRangeY,
            int maxOverScrollX, int maxOverScrollY,
            boolean isTouchEvent
    ) {
        final long targetScrollY = (long) scrollY + deltaY;
        if (getOverScrollMode() != OVER_SCROLL_NEVER &&
                (targetScrollY < 0L || targetScrollY > scrollRangeY)) {
            setOverscrolling(true);
            removeCallbacks(finishOverscroll);
            postDelayed(finishOverscroll, OVERSCROLL_CAPTURE_TIMEOUT_MS);
        }
        return super.overScrollBy(
                deltaX, deltaY,
                scrollX, scrollY,
                scrollRangeX, scrollRangeY,
                maxOverScrollX, maxOverScrollY,
                isTouchEvent
        );
    }

    private final ArrayList<View> children = new ArrayList<>();
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
        if (child == null || child == contentView) return 0.0f;
        if (!(child.getParent() instanceof View)) return child.getX();
        return getChildX((View) child.getParent()) + child.getX();
    }
    private float getChildY(View child) {
        if (child == null || child == contentView) return 0.0f;
        if (!(child.getParent() instanceof View)) return child.getY();
        return getChildY((View) child.getParent()) + child.getY();
    }

    private void collectSections() {
        sectionsCount = 0;
        children.clear();
        gatherChildren(contentView, 0, 0);



        View start = null, prev = null;
        for (View child : children) {
            if (!isSectionView(child)) {
                addSection(start, prev);
                start = prev = null;
                continue;
            }
            if (start != null && Math.abs(prev.getAlpha() - child.getAlpha()) > 0.1f) {
                addSection(start, prev);
                start = null;
            }
            if (start == null) {
                start = child;
            }
            prev = child;
        }
        addSection(start, prev);
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

    private void addSection(View from, View to) {
        if (from == null || to == null) return;

        float fromTopMargin = 0.0f;
        float toBottomMargin = 0.0f;
        final ViewGroup.LayoutParams fromLp = from.getLayoutParams();
        final ViewGroup.LayoutParams toLp = to.getLayoutParams();
        if (from.getParent() != contentView && fromLp instanceof MarginLayoutParams) {
            fromTopMargin = ((MarginLayoutParams) fromLp).topMargin;
        }
        if (to.getParent() != contentView && toLp instanceof MarginLayoutParams) {
            toBottomMargin = ((MarginLayoutParams) toLp).bottomMargin;

        }
        toBottomMargin += getTrailingPaddingBottom(to);

        final float left = contentView.getX() + getChildX(from);
        final float top = contentView.getY() + getChildY(from) - fromTopMargin;
        final float right = left + from.getWidth();
        final float bottom = contentView.getY() + getChildY(to) + to.getHeight() + toBottomMargin;
        if (right <= left || bottom <= top) return;

        ensureSectionsCapacity();
        sections[sectionsCount++] = left;
        sections[sectionsCount++] = top;
        sections[sectionsCount++] = right;
        sections[sectionsCount++] = bottom;
        sections[sectionsCount++] = from.getAlpha();
    }

    // bottom paddings of the gathered parents that end with this child (e.g. under LinkActionView buttons)
    private float getTrailingPaddingBottom(View child) {
        float padding = 0.0f;
        while (child.getParent() instanceof ViewGroup && child.getParent() != contentView) {
            final ViewGroup parent = (ViewGroup) child.getParent();
            for (int i = parent.getChildCount() - 1; i >= 0; --i) {
                final View last = parent.getChildAt(i);
                if (last.getVisibility() == View.GONE) continue;
                if (last != child) return padding;
                break;
            }
            padding += parent.getPaddingBottom();
            child = parent;
        }
        return padding;
    }

    private void ensureSectionsCapacity() {
        if (sectionsCount + SECTION_VALUES_COUNT <= sections.length) return;
        final float[] newSections = new float[sections.length * 2];
        System.arraycopy(sections, 0, newSections, 0, sectionsCount);
        sections = newSections;
    }

    private void updateColors() {
        final int backgroundColor = ColorUtils.setAlphaComponent(
                Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider),
                0xFF
        );
        backgroundPaint.setColor(backgroundColor);
        if (bitmapBackgroundColor != backgroundColor) {
            bitmapBackgroundColor = backgroundColor;
            cornerBitmap = null;
        }
    }

    private int getSectionColor(float alpha) {
        final int sectionColor = Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider);
        final int sectionAlpha = Math.round(Color.alpha(sectionColor) * Math.max(0.0f, Math.min(1.0f, alpha)));
        if (!drawBackground) {
            return ColorUtils.setAlphaComponent(sectionColor, sectionAlpha);
        }
        return ColorUtils.compositeColors(
                ColorUtils.setAlphaComponent(sectionColor, sectionAlpha),
                backgroundPaint.getColor()
        );
    }

    private void drawSectionsBackgrounds(Canvas canvas) {

        captureRect.set(
                getScrollX(), getScrollY(),
                getScrollX() + getWidth(), getScrollY() + getHeight()
        );
        for (int i = 0; i < sectionsCount; i += SECTION_VALUES_COUNT) {
            if (!captureRect.intersects(
                    sections[i], sections[i + 1], sections[i + 2], sections[i + 3])) continue;
            sectionPaint.setColor(getSectionColor(sections[i + 4]));
            canvas.drawRect(sections[i], sections[i + 1], sections[i + 2], sections[i + 3], sectionPaint);
        }
    }

    private void drawOptimizedBackground(Canvas canvas) {
        final float viewLeft = getScrollX();
        final float viewTop = getScrollY();
        final float viewRight = viewLeft + getWidth();
        final float viewBottom = viewTop + getHeight();
        final float sectionLeft = Math.max(viewLeft, Math.min(viewRight, contentView.getX() + contentView.getPaddingLeft()));
        final float sectionRight = Math.max(sectionLeft, Math.min(viewRight, contentView.getX() + contentView.getWidth() - contentView.getPaddingRight()));

        collectOpaqueIntervals(viewTop, viewBottom);
        if (opaqueIntervalsCount == 0 || sectionLeft >= sectionRight) {
            canvas.drawRect(viewLeft, viewTop, viewRight, viewBottom, backgroundPaint);
        } else {
            final float firstTop = opaqueIntervals[0];
            final float lastBottom = opaqueIntervals[opaqueIntervalsCount - 1];
            if (firstTop > viewTop) {
                canvas.drawRect(viewLeft, viewTop, viewRight, Math.min(viewBottom, firstTop + OPAQUE_BACKGROUND_OVERLAP), backgroundPaint);
            }
            if (lastBottom < viewBottom) {
                canvas.drawRect(viewLeft, Math.max(viewTop, lastBottom - OPAQUE_BACKGROUND_OVERLAP), viewRight, viewBottom, backgroundPaint);
            }

            final float sidesTop = Math.max(viewTop, firstTop);
            final float sidesBottom = Math.min(viewBottom, lastBottom);
            if (sidesBottom > sidesTop) {
                canvas.drawRect(viewLeft, sidesTop, Math.min(viewRight, sectionLeft + OPAQUE_BACKGROUND_OVERLAP), sidesBottom, backgroundPaint);
                canvas.drawRect(Math.max(viewLeft, sectionRight - OPAQUE_BACKGROUND_OVERLAP), sidesTop, viewRight, sidesBottom, backgroundPaint);
            }

            for (int i = 2; i < opaqueIntervalsCount; i += 2) {
                final float from = opaqueIntervals[i - 1];
                final float to = opaqueIntervals[i];
                if (to > from) {
                    canvas.drawRect(
                            sectionLeft,
                            Math.max(viewTop, from - OPAQUE_BACKGROUND_OVERLAP),
                            sectionRight,
                            Math.min(viewBottom, to + OPAQUE_BACKGROUND_OVERLAP),
                            backgroundPaint
                    );
                }
            }
        }

        drawSectionsBackgrounds(canvas);
    }

    private void collectOpaqueIntervals(float viewTop, float viewBottom) {
        opaqueIntervalsCount = 0;
        for (int i = 0; i < sectionsCount; i += SECTION_VALUES_COUNT) {
            final float from = Math.max(viewTop, sections[i + 1]);
            final float to = Math.min(viewBottom, sections[i + 3]);
            if (to <= from) continue;
            ensureOpaqueIntervalsCapacity();
            opaqueIntervals[opaqueIntervalsCount++] = from;
            opaqueIntervals[opaqueIntervalsCount++] = to;
        }
        sortOpaqueIntervals();
        mergeOpaqueIntervals();
    }

    private void ensureOpaqueIntervalsCapacity() {
        if (opaqueIntervalsCount + 2 <= opaqueIntervals.length) return;
        final float[] newIntervals = new float[opaqueIntervals.length * 2];
        System.arraycopy(opaqueIntervals, 0, newIntervals, 0, opaqueIntervalsCount);
        opaqueIntervals = newIntervals;
    }

    private void sortOpaqueIntervals() {
        for (int i = 2; i < opaqueIntervalsCount; i += 2) {
            final float from = opaqueIntervals[i];
            final float to = opaqueIntervals[i + 1];
            int j = i - 2;
            while (j >= 0 && opaqueIntervals[j] > from) {
                opaqueIntervals[j + 2] = opaqueIntervals[j];
                opaqueIntervals[j + 3] = opaqueIntervals[j + 1];
                j -= 2;
            }
            opaqueIntervals[j + 2] = from;
            opaqueIntervals[j + 3] = to;
        }
    }

    private void mergeOpaqueIntervals() {
        int mergedCount = 0;
        for (int i = 0; i < opaqueIntervalsCount; i += 2) {
            final float from = opaqueIntervals[i];
            final float to = opaqueIntervals[i + 1];
            if (mergedCount > 0 && from <= opaqueIntervals[mergedCount - 1] + OPAQUE_BACKGROUND_OVERLAP) {
                opaqueIntervals[mergedCount - 1] = Math.max(opaqueIntervals[mergedCount - 1], to);
            } else {
                opaqueIntervals[mergedCount++] = from;
                opaqueIntervals[mergedCount++] = to;
            }
        }
        opaqueIntervalsCount = mergedCount;
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        updateColors();
        collectSections();
        if (drawBackground) {
            drawOptimizedBackground(canvas);
        } else {
            drawSectionsBackgrounds(canvas);
        }
        super.dispatchDraw(canvas);
        if (!isM3Expressive()) {
            drawSectionCorners(canvas);
        }
    }

    @Override
    public void capture(Canvas canvas, RectF position) {
        final long drawingTime = SystemClock.uptimeMillis();
        if (hasActiveOverscroll() && getOverScrollMode() != OVER_SCROLL_NEVER) {
            canvas.save();
            try {
                canvas.clipRect(position);
                final Matrix matrix = getMatrix();
                if (matrix.invert(inverseMatrix)) {
                    canvas.concat(inverseMatrix);
                }
                canvas.translate(-getX(), -getY());
                try {
                    super.drawChild(canvas, this, drawingTime);
                } catch (Throwable t) {
                    FileLog.e(t);
                }
            } finally {
                canvas.restore();
            }
            return;
        }

        updateColors();
        final float scrollX = getScrollX();
        final float scrollY = getScrollY();
        captureRect.set(position);
        captureRect.offset(scrollX, scrollY);
        collectSections();
        final int sectionColor = Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider);
        canvas.save();
        try {
            canvas.clipRect(position);
            canvas.translate(-scrollX, -scrollY);
            for (int i = 0; i < sectionsCount; i += SECTION_VALUES_COUNT) {
                if (!captureRect.intersects(
                        sections[i], sections[i + 1], sections[i + 2], sections[i + 3])) {
                    continue;
                }
                final int sectionAlpha = Math.round(Color.alpha(sectionColor) * Math.max(0.0f, Math.min(1.0f, sections[i + 4])));
                sectionPaint.setColor(ColorUtils.setAlphaComponent(sectionColor, sectionAlpha));
                cornerDst.set(sections[i], sections[i + 1], sections[i + 2], sections[i + 3]);
                canvas.drawRoundRect(cornerDst, sectionRadius, sectionRadius, sectionPaint);
            }

            canvas.save();
            try {
                canvas.translate(contentView.getX(), contentView.getY());
                final float contentX = contentView.getX();
                final float contentY = contentView.getY();
                if (contentView instanceof SectionsLinearLayout) {
                    final SectionsLinearLayout sectionsLayout = (SectionsLinearLayout) contentView;
                    for (int i = 0; i < sectionsLayout.getChildCount(); i++) {
                        final View child = sectionsLayout.getChildAt(i);
                        if (child.getVisibility() != View.VISIBLE) continue;
                        final float childLeft = contentX + child.getX();
                        final float childTop = contentY + child.getY();
                        if (!captureRect.intersects(
                                childLeft, childTop,
                                childLeft + child.getWidth(), childTop + child.getHeight())) continue;
                        sectionsLayout.drawChild(canvas, child, drawingTime);
                    }
                } else {
                    for (int i = 0; i < contentView.getChildCount(); i++) {
                        final View child = contentView.getChildAt(i);
                        if (child.getVisibility() != View.VISIBLE) continue;
                        final float childLeft = contentX + child.getX();
                        final float childTop = contentY + child.getY();
                        if (!captureRect.intersects(
                                childLeft, childTop,
                                childLeft + child.getWidth(), childTop + child.getHeight())) continue;
                        canvas.save();
                        canvas.translate(child.getX(), child.getY());
                        child.draw(canvas);
                        canvas.restore();
                    }
                }
            } finally {
                canvas.restore();
            }

            drawSectionCorners(canvas, captureRect);
        } finally {
            canvas.restore();
        }
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

    private void drawSectionCorners(Canvas canvas) {
        captureRect.set(
                getScrollX(), getScrollY(),
                getScrollX() + getWidth(), getScrollY() + getHeight()
        );
        drawSectionCorners(canvas, captureRect);
    }

    private void drawSectionCorners(Canvas canvas, RectF clipRect) {
        if (sectionsCount == 0 || sectionRadius <= 0.0f) return;
        ensureCornerBitmap();
        for (int i = 0; i < sectionsCount; i += SECTION_VALUES_COUNT) {
            final float left = sections[i];
            final float top = sections[i + 1];
            final float right = sections[i + 2];
            final float bottom = sections[i + 3];
            drawCorner(canvas, 0, left, top, left + bitmapSize, top + bitmapSize, clipRect);
            drawCorner(canvas, 1, right - bitmapSize, top, right, top + bitmapSize, clipRect);
            drawCorner(canvas, 2, left, bottom - bitmapSize, left + bitmapSize, bottom, clipRect);
            drawCorner(canvas, 3, right - bitmapSize, bottom - bitmapSize, right, bottom, clipRect);
        }
    }

    private void ensureCornerBitmap() {
        final int size = Math.max(1, (int) Math.ceil(sectionRadius));
        if (cornerBitmap != null && bitmapSize == size) return;
        bitmapSize = size;
        cornerBitmap = Bitmap.createBitmap(size * 2, size * 2, Bitmap.Config.ARGB_8888);
        final Canvas bitmapCanvas = new Canvas(cornerBitmap);
        bitmapCanvas.drawColor(backgroundPaint.getColor());
        final Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        bitmapCanvas.drawCircle(size, size, sectionRadius, clearPaint);
    }

    private void drawCorner(
            Canvas canvas, int corner,
            float left, float top, float right, float bottom,
            RectF clipRect
    ) {
        if (!clipRect.intersects(left, top, right, bottom)) return;
        final int srcLeft = (corner & 1) == 0 ? 0 : bitmapSize;
        final int srcTop = corner < 2 ? 0 : bitmapSize;
        cornerSrc.set(srcLeft, srcTop, srcLeft + bitmapSize, srcTop + bitmapSize);
        cornerDst.set(left, top, right, bottom);
        canvas.drawBitmap(cornerBitmap, cornerSrc, cornerDst, bitmapPaint);
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
        public boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime) {
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

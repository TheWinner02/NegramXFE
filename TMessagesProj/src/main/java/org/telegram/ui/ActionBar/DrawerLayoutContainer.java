package org.telegram.ui.ActionBar;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Outline;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.math.MathUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.lang.ref.WeakReference;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;

public class DrawerLayoutContainer extends FrameLayout {

    private static final int MIN_DRAWER_MARGIN = 64;

    private FrameLayout drawerLayout;
    private View drawerListView;
    private INavigationLayout parentActionBarLayout;
    private ActionBarLayout actionBarLayout;

    private boolean maybeStartTracking;
    private boolean startedTracking;
    private int startedTrackingX;
    private int startedTrackingY;
    private int startedTrackingPointerId;
    private VelocityTracker velocityTracker;
    private boolean beginTrackingSent;
    private AnimatorSet currentAnimation;

    private final Rect rect = new Rect();

    private final Paint scrimPaint = new Paint();
    private final Paint internalNavbarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean inLayout;
    private final int minDrawerMargin;
    private float scrimOpacity;
    private final Drawable shadowLeft;
    private boolean allowOpenDrawer = true;
    private boolean allowOpenDrawerBySwipe = true;

    private float drawerPosition;
    private boolean drawerOpened;
    public boolean allowDrawContent = true;

    private @Nullable WindowInsetsCompat lastWindowInsetsCompat;
    private @NonNull Insets systemAndCutoutInsets = Insets.NONE;
    private @NonNull Insets systemAndCutoutAndImeInsets = Insets.NONE;

    private final RectF drawerOrigin = new RectF();
    private WeakReference<View> drawerHomeButtonRef;
    private float drawerHomeButtonAlpha = 1f;
    private boolean drawerFromButton = false;
    private float drawerLastProgress = 0f;

    private final ViewOutlineProvider drawerOutlineProvider = new ViewOutlineProvider() {
        @Override
        public void getOutline(View view, Outline outline) {
            float progress = MathUtils.clamp(drawerLastProgress, 0f, 1f);
            float t = 1f - progress;
            if (drawerFromButton) {
                int l = (int) (drawerOrigin.left * t);
                int top = (int) (drawerOrigin.top * t);
                int r = (int) (drawerOrigin.right * t + view.getWidth() * progress);
                int b = (int) (drawerOrigin.bottom * t + view.getHeight() * progress);
                float radius = (drawerOrigin.height() / 2f) * t + AndroidUtilities.dp(28) * progress;
                radius = Math.min(radius, Math.min(r - l, b - top) / 2f);
                outline.setRoundRect(l, top, Math.max(l + 1, r), Math.max(top + 1, b), Math.max(0f, radius));
            } else {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), AndroidUtilities.dp(28));
            }
        }
    };

    private View findHomeButton() {
        if (parentActionBarLayout != null) {
            BaseFragment fragment = parentActionBarLayout.getLastFragment();
            if (fragment != null && fragment.getActionBar() != null) {
                View backButton = fragment.getActionBar().getBackButton();
                if (backButton != null && backButton.isAttachedToWindow() && backButton.isShown()) {
                    return backButton;
                }
            }
        }
        return xyz.nextalone.nagram.ui.fluid.FluidOrigins.take(getContext());
    }

    public DrawerLayoutContainer(Context context) {
        super(context);

        minDrawerMargin = (int) (MIN_DRAWER_MARGIN * AndroidUtilities.density + 0.5f);
        setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        setFocusableInTouchMode(true);

        ViewCompat.setOnApplyWindowInsetsListener(this, this::onApplyWindowInsets);
        setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        shadowLeft = getResources().getDrawable(R.drawable.menu_shadow);
    }

    public void setDrawerLayout(FrameLayout layout, View drawerListView) {
        drawerLayout = layout;
        this.drawerListView = drawerListView;
        addView(drawerLayout);
        drawerLayout.setVisibility(INVISIBLE);
        drawerListView.setVisibility(GONE);
        AndroidUtilities.runOnUIThread(() -> {
            if (drawerListView != null) {
                drawerListView.setVisibility(View.VISIBLE);
            }
        }, 500);
    }

    public void moveDrawerByX(float dx) {
        setDrawerPosition(drawerPosition + dx);
    }

    @Keep
    public void setDrawerPosition(float value) {
        if (drawerLayout == null) {
            return;
        }
        drawerPosition = value;
        int drawerW = drawerLayout.getMeasuredWidth();
        int drawerH = drawerLayout.getMeasuredHeight() > 0 ? drawerLayout.getMeasuredHeight() : getHeight();
        if (drawerPosition > drawerW) {
            drawerPosition = drawerW;
        } else if (drawerPosition < 0) {
            drawerPosition = 0;
        }
        float progress = drawerW > 0 ? (drawerPosition / (float) drawerW) : 0f;
        drawerLastProgress = progress;

        if (xyz.nextalone.nagram.ui.UIStyleEngine.isFluid() && drawerFromButton && drawerW > 0 && drawerH > 0) {
            drawerLayout.setTranslationX(drawerW);
            drawerLayout.setTranslationY(0f);
            float reveal = MathUtils.clamp(progress / 0.12f, 0f, 1f);
            drawerLayout.setAlpha(reveal);

            View homeBtn = drawerHomeButtonRef != null ? drawerHomeButtonRef.get() : null;
            if (homeBtn != null) {
                homeBtn.setAlpha(drawerHomeButtonAlpha * (1f - MathUtils.clamp(progress / 0.18f, 0f, 1f)));
            }

            drawerLayout.setOutlineProvider(drawerOutlineProvider);
            drawerLayout.setClipToOutline(true);
            drawerLayout.setElevation(AndroidUtilities.dp(16) * progress);
            drawerLayout.invalidateOutline();
        } else {
            drawerLayout.setTranslationX(drawerPosition);
            drawerLayout.setAlpha(1f);
            if (xyz.nextalone.nagram.ui.UIStyleEngine.isFluid()) {
                drawerLayout.setOutlineProvider(drawerOutlineProvider);
                drawerLayout.setClipToOutline(true);
                drawerLayout.setElevation(AndroidUtilities.dp(16) * progress);
                drawerLayout.invalidateOutline();
            }
        }

        if (drawerPosition > 0 && drawerListView != null && drawerListView.getVisibility() != View.VISIBLE) {
            drawerListView.setVisibility(View.VISIBLE);
        }

        final int newVisibility = drawerPosition > 0 ? VISIBLE : INVISIBLE;
        if (drawerLayout.getVisibility() != newVisibility) {
            drawerLayout.setVisibility(newVisibility);
        }
        setScrimOpacity(drawerW > 0 ? (drawerPosition / (float) drawerW) : 0);
    }

    @Keep
    public float getDrawerPosition() {
        return drawerPosition;
    }

    public void cancelCurrentAnimation() {
        if (currentAnimation != null) {
            currentAnimation.cancel();
            currentAnimation = null;
        }
    }

    public void openDrawer(boolean fast) {
        if (!allowOpenDrawer || drawerLayout == null) {
            return;
        }
        com.exteragram.messenger.utils.system.VibratorUtils.vibrateGesture(this, true);
        if (AndroidUtilities.isTablet() && parentActionBarLayout != null && parentActionBarLayout.getParentActivity() != null) {
            AndroidUtilities.hideKeyboard(parentActionBarLayout.getParentActivity().getCurrentFocus());
        }
        cancelCurrentAnimation();
        if (xyz.nextalone.nagram.ui.UIStyleEngine.isFluid()) {
            View homeBtn = findHomeButton();
            if (homeBtn != null && homeBtn.isAttachedToWindow() && homeBtn.isShown()) {
                int[] hostLoc = new int[2];
                getLocationOnScreen(hostLoc);
                Rect visible = new Rect();
                if (homeBtn.getGlobalVisibleRect(visible)) {
                    drawerOrigin.set(visible.left - hostLoc[0], visible.top - hostLoc[1],
                            visible.right - hostLoc[0], visible.bottom - hostLoc[1]);
                    drawerHomeButtonRef = new WeakReference<>(homeBtn);
                    drawerHomeButtonAlpha = homeBtn.getAlpha();
                    drawerFromButton = true;
                } else {
                    drawerFromButton = false;
                }
            } else {
                drawerFromButton = false;
            }
        } else {
            drawerFromButton = false;
        }

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(ObjectAnimator.ofFloat(this, "drawerPosition", drawerLayout.getMeasuredWidth()));
        if (xyz.nextalone.nagram.ui.UIStyleEngine.isFluid()) {
            animatorSet.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            animatorSet.setDuration(fast ? 240 : 380);
        } else {
            animatorSet.setInterpolator(new DecelerateInterpolator());
            if (fast) {
                animatorSet.setDuration(Math.max((int) (200.0f / Math.max(1, drawerLayout.getMeasuredWidth()) * (drawerLayout.getMeasuredWidth() - drawerPosition)), 50));
            } else {
                animatorSet.setDuration(250);
            }
        }
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                onDrawerAnimationEnd(true);
            }
        });
        animatorSet.start();
        currentAnimation = animatorSet;
    }

    public void closeDrawer(boolean fast) {
        if (drawerLayout == null) {
            return;
        }
        cancelCurrentAnimation();

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(ObjectAnimator.ofFloat(this, "drawerPosition", 0));
        if (xyz.nextalone.nagram.ui.UIStyleEngine.isFluid()) {
            animatorSet.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            animatorSet.setDuration(fast ? 220 : 340);
        } else {
            animatorSet.setInterpolator(new DecelerateInterpolator());
            if (fast) {
                animatorSet.setDuration(Math.max((int) (200.0f / Math.max(1, drawerLayout.getMeasuredWidth()) * drawerPosition), 50));
            } else {
                animatorSet.setDuration(250);
            }
        }
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                onDrawerAnimationEnd(false);
            }
        });
        animatorSet.start();
        currentAnimation = animatorSet;
    }

    private void onDrawerAnimationEnd(boolean opened) {
        startedTracking = false;
        currentAnimation = null;
        drawerOpened = opened;
        if (!opened) {
            if (drawerLayout != null) {
                drawerLayout.setTranslationY(0f);
                drawerLayout.setAlpha(1f);
                drawerLayout.setClipToOutline(false);
                drawerLayout.setElevation(0);
            }
            View homeBtn = drawerHomeButtonRef != null ? drawerHomeButtonRef.get() : null;
            if (homeBtn != null) {
                homeBtn.setAlpha(drawerHomeButtonAlpha);
            }
            drawerFromButton = false;
        }
        if (xyz.nextalone.nagram.ui.UIStyleEngine.isFluid() && parentActionBarLayout != null && parentActionBarLayout.getView() != null) {
            if (!opened) {
                View contentView = parentActionBarLayout.getView();
                contentView.setScaleX(1.0f);
                contentView.setScaleY(1.0f);
            }
        }
        if (!opened && Build.VERSION.SDK_INT >= 31 && parentActionBarLayout != null && parentActionBarLayout.getView() != null) {
            parentActionBarLayout.getView().setRenderEffect(null);
        }
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child != drawerLayout) {
                child.setImportantForAccessibility(opened ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
            }
        }
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
    }

    private void setScrimOpacity(float value) {
        scrimOpacity = value;
        if (Build.VERSION.SDK_INT >= 31 && parentActionBarLayout != null && parentActionBarLayout.getView() != null) {
            if (tw.nekomimi.nekogram.NekoConfig.blurBehindDrawer.Bool() && scrimOpacity > 0.005f) {
                float maxRadius = Math.max(1f, AndroidUtilities.dp(tw.nekomimi.nekogram.NekoConfig.blurRadiusDrawer.Int()));
                float radius = maxRadius * scrimOpacity;
                parentActionBarLayout.getView().setRenderEffect(android.graphics.RenderEffect.createBlurEffect(radius, radius, android.graphics.Shader.TileMode.CLAMP));
            } else {
                parentActionBarLayout.getView().setRenderEffect(null);
            }
        }
        invalidate();
    }

    public void setParentActionBarLayout(INavigationLayout layout) {
        parentActionBarLayout = layout;
    }

    public void setActionBarLayout(ActionBarLayout actionBarLayout) {
        this.actionBarLayout = actionBarLayout;
    }

    public boolean isDrawCurrentPreviewFragmentAbove() {
        return false;
    }

    public boolean presentFragment(BaseFragment fragment) {
        if (parentActionBarLayout != null) {
            boolean res = parentActionBarLayout.presentFragment(fragment);
            closeDrawer(false);
            return res;
        }
        return false;
    }

    public INavigationLayout getParentActionBarLayout() {
        return parentActionBarLayout;
    }

    public void closeDrawer() {
        if (drawerPosition != 0) {
            setDrawerPosition(0);
            onDrawerAnimationEnd(false);
        }
        if (Build.VERSION.SDK_INT >= 31 && parentActionBarLayout != null && parentActionBarLayout.getView() != null) {
            parentActionBarLayout.getView().setRenderEffect(null);
        }
    }

    public void setAllowOpenDrawer(boolean value, boolean animated) {
        allowOpenDrawer = value;
        if (!allowOpenDrawer && drawerPosition != 0) {
            if (!animated) {
                setDrawerPosition(0);
                onDrawerAnimationEnd(false);
            } else {
                closeDrawer(true);
            }
        }
    }

    public void setAllowOpenDrawerBySwipe(boolean value) {
        allowOpenDrawerBySwipe = value;
    }

    public boolean isAllowOpenDrawer() {
        return allowOpenDrawer;
    }

    public boolean isDrawerOpened() {
        return drawerOpened;
    }

    private void prepareForDrawerOpen(MotionEvent ev) {
        maybeStartTracking = false;
        startedTracking = true;
        drawerFromButton = false;
        if (ev != null) {
            startedTrackingX = (int) ev.getX();
        }
        beginTrackingSent = false;
    }

    public boolean onTouchEvent(MotionEvent ev) {
        if (parentActionBarLayout != null && !parentActionBarLayout.checkTransitionAnimation() && drawerLayout != null) {
            if (drawerOpened && ev != null && ev.getX() > drawerPosition && !startedTracking) {
                if (ev.getAction() == MotionEvent.ACTION_UP) {
                    closeDrawer(false);
                }
                return true;
            }

            if ((allowOpenDrawerBySwipe || drawerOpened) && allowOpenDrawer && parentActionBarLayout.getFragmentStack().size() == 1 && parentActionBarLayout.allowSwipe()) {
                if (ev != null && (ev.getAction() == MotionEvent.ACTION_DOWN || ev.getAction() == MotionEvent.ACTION_MOVE) && !startedTracking && !maybeStartTracking) {
                    if (!drawerOpened && ev.getX() > (getWidth() > 0 ? getWidth() * 0.25f : AndroidUtilities.dp(80))) {
                        return false;
                    }
                    View scrollingChild = findScrollingChild(this, ev.getX(), ev.getY());
                    if (scrollingChild != null) {
                        return false;
                    }
                    parentActionBarLayout.getView().getHitRect(rect);
                    startedTrackingX = (int) ev.getX();
                    startedTrackingY = (int) ev.getY();
                    if (rect.contains(startedTrackingX, startedTrackingY)) {
                        startedTrackingPointerId = ev.getPointerId(0);
                        maybeStartTracking = true;
                        cancelCurrentAnimation();
                        if (velocityTracker != null) {
                            velocityTracker.clear();
                        }
                    }
                } else if (ev != null && ev.getAction() == MotionEvent.ACTION_MOVE && ev.getPointerId(0) == startedTrackingPointerId) {
                    if (velocityTracker == null) {
                        velocityTracker = VelocityTracker.obtain();
                    }
                    float dx = (int) (ev.getX() - startedTrackingX);
                    float dy = Math.abs((int) ev.getY() - startedTrackingY);
                    velocityTracker.addMovement(ev);
                    if (maybeStartTracking && !startedTracking && (dx > 0 && dx / 3.0f > Math.abs(dy) && Math.abs(dx) >= AndroidUtilities.getPixelsInCM(0.2f, true) || drawerOpened && dx < 0 && Math.abs(dx) >= Math.abs(dy) && Math.abs(dx) >= AndroidUtilities.getPixelsInCM(0.4f, true))) {
                        prepareForDrawerOpen(ev);
                        startedTrackingX = (int) ev.getX();
                        requestDisallowInterceptTouchEvent(true);
                    } else if (startedTracking) {
                        if (!beginTrackingSent) {
                            if (getContext() instanceof Activity && ((Activity) getContext()).getCurrentFocus() != null) {
                                AndroidUtilities.hideKeyboard(((Activity) getContext()).getCurrentFocus());
                            }
                            beginTrackingSent = true;
                        }
                        moveDrawerByX(dx);
                        startedTrackingX = (int) ev.getX();
                    }
                } else if (ev == null || (ev.getPointerId(0) == startedTrackingPointerId && (ev.getAction() == MotionEvent.ACTION_CANCEL || ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_POINTER_UP))) {
                    if (velocityTracker == null) {
                        velocityTracker = VelocityTracker.obtain();
                    }
                    velocityTracker.computeCurrentVelocity(1000);
                    if (startedTracking || (drawerPosition != 0 && drawerPosition != drawerLayout.getMeasuredWidth())) {
                        float velX = velocityTracker.getXVelocity();
                        float velY = velocityTracker.getYVelocity();
                        boolean backAnimation = drawerPosition < drawerLayout.getMeasuredWidth() / 2.0f && (velX < 3500 || Math.abs(velX) < Math.abs(velY)) || velX < 0 && Math.abs(velX) >= 3500;
                        if (!backAnimation) {
                            openDrawer(!drawerOpened && Math.abs(velX) >= 3500);
                        } else {
                            closeDrawer(drawerOpened && Math.abs(velX) >= 3500);
                        }
                    }
                    startedTracking = false;
                    maybeStartTracking = false;
                    if (velocityTracker != null) {
                        velocityTracker.recycle();
                        velocityTracker = null;
                    }
                }
            } else {
                if (ev == null || (ev.getPointerId(0) == startedTrackingPointerId && (ev.getAction() == MotionEvent.ACTION_CANCEL || ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_POINTER_UP))) {
                    startedTracking = false;
                    maybeStartTracking = false;
                    if (velocityTracker != null) {
                        velocityTracker.recycle();
                        velocityTracker = null;
                    }
                }
            }
            return startedTracking;
        }
        return false;
    }

    private View findScrollingChild(ViewGroup parent, float x, float y) {
        int n = parent.getChildCount();
        for (int i = 0; i < n; i++) {
            View child = parent.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE) {
                continue;
            }
            child.getHitRect(rect);
            if (rect.contains((int) x, (int) y)) {
                if (child.canScrollHorizontally(-1)) {
                    return child;
                } else if (child instanceof ViewGroup) {
                    View v = findScrollingChild((ViewGroup) child, x - rect.left, y - rect.top);
                    if (v != null) {
                        return v;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return (parentActionBarLayout != null && parentActionBarLayout.checkTransitionAnimation()) || onTouchEvent(ev);
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        if (maybeStartTracking && !startedTracking) {
            onTouchEvent(null);
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        inLayout = true;
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);

            if (child.getVisibility() == GONE) {
                continue;
            }

            final LayoutParams lp = (LayoutParams) child.getLayoutParams();
            try {
                if (drawerLayout != child) {
                    child.layout(lp.leftMargin, lp.topMargin + getPaddingTop(), lp.leftMargin + child.getMeasuredWidth(), lp.topMargin + child.getMeasuredHeight() + getPaddingTop());
                } else {
                    child.layout(-child.getMeasuredWidth(), lp.topMargin + getPaddingTop(), 0, lp.topMargin + child.getMeasuredHeight() + getPaddingTop());
                }
            } catch (Exception e) {
                FileLog.e(e);
                if (BuildVars.DEBUG_VERSION) {
                    throw e;
                }
            }
        }
        inLayout = false;
    }

    @Override
    public void requestLayout() {
        if (!inLayout) {
            super.requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        setMeasuredDimension(widthSize, heightSize);

        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);

            if (child.getVisibility() == GONE) {
                continue;
            }

            final LayoutParams lp = (LayoutParams) child.getLayoutParams();

            if (drawerLayout != child) {
                final int contentWidthSpec = MeasureSpec.makeMeasureSpec(widthSize - lp.leftMargin - lp.rightMargin, MeasureSpec.EXACTLY);
                final int contentHeightSpec;
                if (lp.height > 0) {
                    contentHeightSpec = MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY);
                } else {
                    contentHeightSpec = MeasureSpec.makeMeasureSpec(heightSize - lp.topMargin - lp.bottomMargin, MeasureSpec.EXACTLY);
                }
                if (child instanceof ActionBarLayout) {
                    ActionBarLayout abl = (ActionBarLayout) child;
                    if (abl.storyViewerAttached()) {
                        child.forceLayout();
                    }
                }
                child.measure(contentWidthSpec, contentHeightSpec);
            } else {
                child.setPadding(0, 0, 0, 0);
                child.measure(getChildMeasureSpec(widthMeasureSpec, minDrawerMargin + lp.leftMargin + lp.rightMargin, lp.width), getChildMeasureSpec(heightMeasureSpec, lp.topMargin + lp.bottomMargin, lp.height));
            }
        }
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        if (actionBarLayout != null && actionBarLayout.getParent() == this) {
            actionBarLayout.parentDraw(this, canvas);
        }
        super.dispatchDraw(canvas);
    }

    @Override
    protected boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime) {
        if (!allowDrawContent) {
            return false;
        }
        if (xyz.nextalone.nagram.ui.UIStyleEngine.isFluid()) {
            final boolean drawingContent = child != drawerLayout;
            final boolean result = super.drawChild(canvas, child, drawingTime);
            if (drawingContent && scrimOpacity > 0) {
                scrimPaint.setColor((int) (((0x99000000 & 0xff000000) >>> 24) * scrimOpacity) << 24);
                canvas.drawRect(0, 0, getWidth(), getHeight(), scrimPaint);
            }
            return result;
        }
        final int height = getHeight();
        final boolean drawingContent = child != drawerLayout;
        int lastVisibleChild = 0;
        int clipLeft = 0, clipRight = getWidth();

        final int restoreCount = canvas.save();
        if (drawingContent) {
            final int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
                final View v = getChildAt(i);
                if (v.getVisibility() == VISIBLE && v != drawerLayout) {
                    lastVisibleChild = i;
                }
                if (v == child || v.getVisibility() != VISIBLE || v != drawerLayout || v.getHeight() < height) {
                    continue;
                }

                final int vright = (int) Math.ceil(v.getX()) + v.getMeasuredWidth();
                if (vright > clipLeft) {
                    clipLeft = vright;
                }
            }
            if (clipLeft != 0) {
                canvas.clipRect(clipLeft - AndroidUtilities.dp(1), 0, clipRight, getHeight());
            }
        }
        final boolean result = super.drawChild(canvas, child, drawingTime);
        canvas.restoreToCount(restoreCount);

        if (scrimOpacity > 0 && drawingContent) {
            if (indexOfChild(child) == lastVisibleChild) {
                scrimPaint.setColor((int) (((0x99000000 & 0xff000000) >>> 24) * scrimOpacity) << 24);
                canvas.drawRect(clipLeft, 0, clipRight, getHeight(), scrimPaint);
            }
        } else if (shadowLeft != null) {
            final float alpha = Math.max(0, Math.min(drawerPosition / AndroidUtilities.dp(20), 1.0f));
            if (alpha != 0) {
                shadowLeft.setBounds((int) drawerPosition, child.getTop(), (int) drawerPosition + shadowLeft.getIntrinsicWidth(), child.getBottom());
                shadowLeft.setAlpha((int) (0xff * alpha));
                shadowLeft.draw(canvas);
            }
        }
        return result;
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    public Paint getInternalNavbarPaint() {
        return internalNavbarPaint;
    }

    public void setInternalNavigationBarColor(int color) {
        if (internalNavbarPaint.getColor() != color) {
            internalNavbarPaint.setColor(color);
            invalidate();

            for (int a = 0, N = getChildCount(); a < N; a++) {
                getChildAt(a).invalidate();
            }
        }
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        super.addView(child, index, params);
        if (lastWindowInsetsCompat != null) {
            dispatchApplyWindowInsetsInternal(child, lastWindowInsetsCompat);
        }
    }

    private void dispatchApplyWindowInsetsInternal(View child, WindowInsetsCompat insets) {
        boolean canApplyInsets = child instanceof ActionBarLayout || child instanceof DrawerContainer || child.getTag() == null;
        if (canApplyInsets) {
            ViewCompat.dispatchApplyWindowInsets(child, insets);
        }
    }

    @NonNull
    private WindowInsetsCompat onApplyWindowInsets(@NonNull View ignoredV, @NonNull WindowInsetsCompat insets) {
        lastWindowInsetsCompat = insets;

        final Insets systemInsets = AndroidUtilities.getDefaultWindowInsets(insets, false);
        final Insets systemAndImeInsets = AndroidUtilities.getDefaultWindowInsets(insets, true);

        if (!systemAndCutoutInsets.equals(systemInsets) || !systemAndCutoutAndImeInsets.equals(systemAndImeInsets)) {
            AndroidUtilities.statusBarHeight = systemInsets.top;
            AndroidUtilities.navigationBarHeight = systemInsets.bottom;

            systemAndCutoutInsets = systemInsets;
            systemAndCutoutAndImeInsets = systemAndImeInsets;
            requestLayout();
        }

        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            dispatchApplyWindowInsetsInternal(child, insets);
        }

        invalidate();
        return WindowInsetsCompat.CONSUMED;
    }
}

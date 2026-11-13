package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Material 3 Expressive Button Group (Connected & Standard).
 * Implements the physics and morphology from MDC ButtonGroup:
 * - 15%-20% horizontal child size expansion when pressed using spring physics
 * - Asymmetrical corner radii for start, middle, and end children
 * - Spring corner morphing on touch
 * - Seamless connected layout with configurable spacing
 */
public class M3ExpressiveButtonGroup extends FrameLayout {

    private int spacing = dp(3);
    private float childSizeChange = 0.20f;
    private float outerCornerRadius = dp(24);
    private float innerCornerRadius = dp(8);
    private float pressedCornerRadius = dp(14);
    private boolean isConnected = true;

    private final ArrayList<View> visibleChildren = new ArrayList<>();
    private final Map<View, ChildState> childStates = new HashMap<>();

    public static class ChildState {
        public final View view;
        public final SpringAnimation springAnimation;
        public float progress = 0f;
        public boolean pressed = false;
        public float baseWeight = 1.0f;
        public M3ExpressiveButtonDrawable drawable;

        public ChildState(View view, Runnable onUpdate) {
            this.view = view;
            springAnimation = new SpringAnimation(new FloatValueHolder(0f));
            SpringForce force = new SpringForce(0f);
            force.setStiffness(500f);
            force.setDampingRatio(0.82f);
            springAnimation.setSpring(force);
            springAnimation.setMinimumVisibleChange(0.002f);
            xyz.nextalone.nagram.ui.fluid.FluidTiming.configure(view.getContext(), springAnimation);
            springAnimation.addUpdateListener((animation, value, velocity) -> {
                progress = value;
                if (drawable != null) {
                    drawable.setMorphProgress(progress);
                }
                onUpdate.run();
            });
        }
    }

    public M3ExpressiveButtonGroup(@NonNull Context context) {
        super(context);
        init();
    }

    public M3ExpressiveButtonGroup(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setClipChildren(false);
        setClipToPadding(false);
    }

    public void setSpacing(int spacing) {
        this.spacing = spacing;
        requestLayout();
    }

    public void setChildSizeChange(float childSizeChange) {
        this.childSizeChange = childSizeChange;
    }

    public void setOuterCornerRadius(float outerCornerRadius) {
        this.outerCornerRadius = outerCornerRadius;
        updateChildShapes();
    }

    public void setInnerCornerRadius(float innerCornerRadius) {
        this.innerCornerRadius = innerCornerRadius;
        updateChildShapes();
    }

    public void setPressedCornerRadius(float pressedCornerRadius) {
        this.pressedCornerRadius = pressedCornerRadius;
        updateChildShapes();
    }

    public void setConnected(boolean connected) {
        this.isConnected = connected;
        updateChildShapes();
    }

    public void registerChild(@NonNull View child, float baseWeight, @Nullable M3ExpressiveButtonDrawable drawable) {
        ChildState state = childStates.get(child);
        if (state == null) {
            state = new ChildState(child, this::applyChildLayouts);
            childStates.put(child, state);
        }
        state.baseWeight = baseWeight;
        state.drawable = drawable;
        if (drawable != null) {
            child.setBackground(drawable);
        }
        updateChildShapes();
    }

    public void setChildBaseWeight(@NonNull View child, float baseWeight) {
        ChildState state = childStates.get(child);
        if (state != null) {
            state.baseWeight = baseWeight;
            requestLayout();
        }
    }

    public void setChildDrawable(@NonNull View child, @NonNull M3ExpressiveButtonDrawable drawable) {
        ChildState state = childStates.get(child);
        if (state != null) {
            state.drawable = drawable;
            child.setBackground(drawable);
            updateChildShapes();
        }
    }

    @Override
    public void onViewAdded(View child) {
        super.onViewAdded(child);
        if (!childStates.containsKey(child)) {
            ChildState state = new ChildState(child, this::applyChildLayouts);
            childStates.put(child, state);
        }
        updateChildShapes();
    }

    @Override
    public void onViewRemoved(View child) {
        super.onViewRemoved(child);
        ChildState state = childStates.remove(child);
        if (state != null) {
            state.springAnimation.cancel();
        }
        updateChildShapes();
    }

    @Override protected void onDetachedFromWindow() {
        for (ChildState state : childStates.values()) {
            state.springAnimation.cancel(); state.progress = 0f; state.pressed = false;
            if (state.drawable != null) state.drawable.setMorphProgress(0f);
        }
        super.onDetachedFromWindow();
    }

    @Override
    public void childDrawableStateChanged(View child) {
        super.childDrawableStateChanged(child);
        ChildState state = childStates.get(child);
        if (state != null) {
            boolean isPressed = child.isPressed();
            if (state.pressed != isPressed) {
                state.pressed = isPressed;
                if (isPressed) {
                    com.exteragram.messenger.utils.system.VibratorUtils.vibrateClick(child);
                }
                xyz.nextalone.nagram.ui.fluid.FluidTiming.animate(getContext(), state.springAnimation, isPressed ? 1f : 0f);
            }
        }
    }

    private void updateVisibleChildren() {
        visibleChildren.clear();
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                visibleChildren.add(child);
                if (!childStates.containsKey(child)) {
                    childStates.put(child, new ChildState(child, this::applyChildLayouts));
                }
            }
        }
    }

    public void updateChildShapes() {
        updateVisibleChildren();
        int totalVisible = visibleChildren.size();
        if (totalVisible == 0) return;

        float outer = outerCornerRadius;
        float inner = isConnected ? innerCornerRadius : outerCornerRadius;
        float morph = pressedCornerRadius;

        for (int i = 0; i < totalVisible; i++) {
            View child = visibleChildren.get(i);
            ChildState state = childStates.get(child);
            if (state != null && state.drawable != null) {
                float[] restRadii;
                float[] pressedRadii = new float[]{morph, morph, morph, morph, morph, morph, morph, morph};

                if (totalVisible == 1 || !isConnected) {
                    restRadii = new float[]{outer, outer, outer, outer, outer, outer, outer, outer};
                } else if (i == 0) {
                    // Start child
                    restRadii = new float[]{outer, outer, inner, inner, inner, inner, outer, outer};
                } else if (i == totalVisible - 1) {
                    // End child
                    restRadii = new float[]{inner, inner, outer, outer, outer, outer, inner, inner};
                } else {
                    // Middle child
                    restRadii = new float[]{inner, inner, inner, inner, inner, inner, inner, inner};
                }
                state.drawable.setRadii(restRadii, pressedRadii);
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        updateVisibleChildren();
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (height == 0) {
            height = dp(56);
        }

        int count = visibleChildren.size();
        if (count > 0) {
            int availableWidth = width - getPaddingLeft() - getPaddingRight() - (count - 1) * spacing;
            int childHeight = height - getPaddingTop() - getPaddingBottom();
            for (int i = 0; i < count; i++) {
                View child = visibleChildren.get(i);
                ChildState state = childStates.get(child);
                float weight = state != null ? state.baseWeight : 1.0f;
                int childW = Math.max(dp(36), (int) (availableWidth / (float) count));
                child.measure(
                    MeasureSpec.makeMeasureSpec(childW, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY)
                );
            }
        }

        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        updateVisibleChildren();
        updateChildShapes();
        applyChildLayouts();
    }

    private void applyChildLayouts() {
        int count = visibleChildren.size();
        if (count == 0) return;

        int totalW = getWidth() - getPaddingLeft() - getPaddingRight();
        int totalH = getHeight() - getPaddingTop() - getPaddingBottom();
        if (totalW <= 0 || totalH <= 0) return;

        int availableW = totalW - (count - 1) * spacing;

        // Calculate dynamic weights based on spring press progress
        float[] weights = new float[count];
        float totalWeight = 0f;

        // Find total expansion from pressed buttons
        float totalExpansion = 0f;
        int pressedCount = 0;
        for (int i = 0; i < count; i++) {
            View child = visibleChildren.get(i);
            ChildState state = childStates.get(child);
            float progress = state != null ? state.progress : 0f;
            if (progress > 0.001f) {
                totalExpansion += childSizeChange * progress;
                pressedCount++;
            }
        }

        for (int i = 0; i < count; i++) {
            View child = visibleChildren.get(i);
            ChildState state = childStates.get(child);
            float base = state != null ? state.baseWeight : 1.0f;
            float progress = state != null ? state.progress : 0f;

            if (progress > 0.001f) {
                weights[i] = base * (1f + childSizeChange * progress);
            } else if (count > pressedCount && totalExpansion > 0f) {
                float shrink = totalExpansion / (float) (count - pressedCount);
                weights[i] = Math.max(0.4f * base, base * (1f - shrink));
            } else {
                weights[i] = base;
            }
            totalWeight += weights[i];
        }

        int curX = getPaddingLeft();
        int topY = getPaddingTop();

        for (int i = 0; i < count; i++) {
            View child = visibleChildren.get(i);
            int childW;
            if (i == count - 1) {
                childW = (getPaddingLeft() + totalW) - curX;
            } else {
                childW = Math.round((weights[i] / totalWeight) * availableW);
            }
            childW = Math.max(dp(28), childW);

            child.measure(
                MeasureSpec.makeMeasureSpec(childW, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(totalH, MeasureSpec.EXACTLY)
            );
            child.layout(curX, topY, curX + childW, topY + totalH);
            curX += childW + spacing;
        }
        invalidate();
    }
}

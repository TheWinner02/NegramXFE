package xyz.nextalone.nagram.ui.fluid;

import androidx.dynamicanimation.animation.SpringForce;

/**
 * Global physics and spring timing for the Fluid theme.
 * Strictly centralized: no local arbitrary multipliers.
 */
public final class FluidTiming {
    public static final float DAMPING_RATIO = 0.82f;
    public static final float MIN_VISIBLE_CHANGE = 0.001f;
    private static final float BASE_STIFFNESS = 140f;
    private static final float TIME_SCALE = 1.65f;

    private FluidTiming() {}

    /** Apply Fluid policy once when an existing shared widget creates its spring. */
    public static void configure(android.content.Context context, androidx.dynamicanimation.animation.SpringAnimation spring) {
        if (!xyz.nextalone.nagram.ui.UIStyleEngine.isFluid()) return;
        spring.getSpring().setDampingRatio(DAMPING_RATIO)
                .setStiffness(stiffness(FluidCapabilities.motionScale(context)));
        spring.setMinimumVisibleChange(MIN_VISIBLE_CHANGE);
        FluidCapabilities.track(context, spring);
    }

    /** Retarget without replacing the spring or losing its current velocity. */
    public static void animate(android.content.Context context, androidx.dynamicanimation.animation.SpringAnimation spring, float target) {
        boolean fluid = xyz.nextalone.nagram.ui.UIStyleEngine.isFluid();
        float scale = fluid ? FluidCapabilities.motionScale(context) : 1f;
        if (fluid) spring.getSpring().setStiffness(stiffness(Math.max(.01f, scale)));
        spring.animateToFinalPosition(target);
        if (fluid && scale <= 0f) spring.skipToEnd();
    }

    public static float stiffness(float motionScale) {
        float scale = motionScale * TIME_SCALE;
        return BASE_STIFFNESS / Math.max(0.01f, scale * scale);
    }

    public static SpringForce springForce(float motionScale) {
        return new SpringForce(1f)
                .setDampingRatio(DAMPING_RATIO)
                .setStiffness(stiffness(motionScale));
    }
}

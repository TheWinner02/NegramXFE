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

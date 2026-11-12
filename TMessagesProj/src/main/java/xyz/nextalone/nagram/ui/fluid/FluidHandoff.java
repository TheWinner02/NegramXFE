package xyz.nextalone.nagram.ui.fluid;

import android.graphics.RectF;

/**
 * Validates geometric rest and boundary tolerances for handoffs.
 */
public final class FluidHandoff {
    private static final float TOLERANCE = 0.75f;

    private FluidHandoff() {}

    public static boolean atBounds(RectF current, RectF target) {
        return target != null && !target.isEmpty()
                && Math.abs(current.left - target.left) <= TOLERANCE
                && Math.abs(current.top - target.top) <= TOLERANCE
                && Math.abs(current.right - target.right) <= TOLERANCE
                && Math.abs(current.bottom - target.bottom) <= TOLERANCE;
    }

    public static boolean atRest(float remaining, float travel) {
        return Math.abs(remaining) * Math.max(1f, travel) <= TOLERANCE;
    }
}

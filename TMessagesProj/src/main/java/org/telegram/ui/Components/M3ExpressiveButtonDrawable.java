package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;

import xyz.nextalone.nagram.ui.M3ColorRoles;

public class M3ExpressiveButtonDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private int backgroundColor;
    private int pressedOverlayColor;
    private int strokeColor;
    private M3ColorRoles.Role backgroundRole;
    private M3ColorRoles.Role pressedOverlayRole;
    private M3ColorRoles.Role strokeRole;
    private float strokeWidth;
    private float restRadius;
    private float pressedRadius;
    private int inset;
    private int alpha = 255;
    private boolean pressed;
    private boolean externalProgress;
    private float progress;
    private ValueAnimator animator;
    private androidx.dynamicanimation.animation.SpringAnimation fluidSpring;
    private ColorFilter colorFilter;

    private float[] restRadii;
    private float[] pressedRadii;
    private final android.graphics.Path path = new android.graphics.Path();
    private final float[] currentRadii = new float[8];

    public void setColors(int backgroundColor, int pressedOverlayColor) {
        this.backgroundColor = backgroundColor;
        this.pressedOverlayColor = pressedOverlayColor;
        this.backgroundRole = null;
        this.pressedOverlayRole = null;
        invalidateSelf();
    }

    public void setColorRoles(@Nullable M3ColorRoles.Role backgroundRole, @Nullable M3ColorRoles.Role pressedOverlayRole) {
        this.backgroundRole = backgroundRole;
        this.pressedOverlayRole = pressedOverlayRole;
        invalidateSelf();
    }

    public void setStroke(int strokeColor, float strokeWidth) {
        this.strokeColor = strokeColor;
        this.strokeRole = null;
        this.strokeWidth = strokeWidth;
        this.strokePaint.setStyle(Paint.Style.STROKE);
        this.strokePaint.setStrokeWidth(strokeWidth);
        invalidateSelf();
    }

    public void setStrokeRole(@Nullable M3ColorRoles.Role strokeRole) {
        this.strokeRole = strokeRole;
        invalidateSelf();
    }

    public void setInset(int inset) {
        this.inset = inset;
        invalidateSelf();
    }

    public void setRadii(float[] restRadii, float[] pressedRadii) {
        this.restRadii = restRadii;
        this.pressedRadii = pressedRadii;
        invalidateSelf();
    }

    public M3ExpressiveButtonDrawable(int backgroundColor, int pressedOverlayColor) {
        this(backgroundColor, pressedOverlayColor, 0, -1, 0);
    }

    public M3ExpressiveButtonDrawable(int backgroundColor, int pressedOverlayColor, float restRadius, int inset) {
        this(backgroundColor, pressedOverlayColor, restRadius, -1, inset);
    }

    public M3ExpressiveButtonDrawable(int backgroundColor, int pressedOverlayColor, float restRadius, float pressedRadius, int inset) {
        this.backgroundColor = backgroundColor;
        this.pressedOverlayColor = pressedOverlayColor;
        this.restRadius = restRadius;
        this.pressedRadius = pressedRadius;
        this.inset = inset;
    }

    public M3ExpressiveButtonDrawable(int backgroundColor, int pressedOverlayColor, float[] restRadii, float[] pressedRadii, int inset) {
        this.backgroundColor = backgroundColor;
        this.pressedOverlayColor = pressedOverlayColor;
        this.restRadius = 0;
        this.pressedRadius = -1;
        this.restRadii = restRadii;
        this.pressedRadii = pressedRadii;
        this.inset = inset;
    }

    public static M3ExpressiveButtonDrawable createConnectedLeft(int backgroundColor, int pressedOverlayColor) {
        float outer = dp(24);
        float inner = dp(8);
        float pressedInner = dp(14);
        return new M3ExpressiveButtonDrawable(
            backgroundColor, pressedOverlayColor,
            new float[]{outer, outer, inner, inner, inner, inner, outer, outer},
            new float[]{pressedInner, pressedInner, pressedInner, pressedInner, pressedInner, pressedInner, pressedInner, pressedInner},
            0
        );
    }

    public static M3ExpressiveButtonDrawable createConnectedMiddle(int backgroundColor, int pressedOverlayColor) {
        float inner = dp(8);
        float pressedInner = dp(14);
        return new M3ExpressiveButtonDrawable(
            backgroundColor, pressedOverlayColor,
            new float[]{inner, inner, inner, inner, inner, inner, inner, inner},
            new float[]{pressedInner, pressedInner, pressedInner, pressedInner, pressedInner, pressedInner, pressedInner, pressedInner},
            0
        );
    }

    public static M3ExpressiveButtonDrawable createConnectedRight(int backgroundColor, int pressedOverlayColor) {
        float outer = dp(24);
        float inner = dp(8);
        float pressedInner = dp(14);
        return new M3ExpressiveButtonDrawable(
            backgroundColor, pressedOverlayColor,
            new float[]{inner, inner, outer, outer, outer, outer, inner, inner},
            new float[]{pressedInner, pressedInner, pressedInner, pressedInner, pressedInner, pressedInner, pressedInner, pressedInner},
            0
        );
    }

    public static M3ExpressiveButtonDrawable createPrimary(int backgroundColor, int pressedOverlayColor, float cornerRadius, int inset) {
        M3ExpressiveButtonDrawable drawable = new M3ExpressiveButtonDrawable(backgroundColor, pressedOverlayColor, cornerRadius, inset);
        drawable.setColorRoles(M3ColorRoles.Role.PRIMARY, M3ColorRoles.Role.ON_PRIMARY);
        return drawable;
    }

    public static M3ExpressiveButtonDrawable createPrimary(int backgroundColor, int pressedOverlayColor, float cornerRadius, float pressedRadius, int inset) {
        M3ExpressiveButtonDrawable drawable = new M3ExpressiveButtonDrawable(backgroundColor, pressedOverlayColor, cornerRadius, pressedRadius, inset);
        drawable.setColorRoles(M3ColorRoles.Role.PRIMARY, M3ColorRoles.Role.ON_PRIMARY);
        return drawable;
    }

    public static M3ExpressiveButtonDrawable createSoftPrimary(int backgroundColor, int pressedOverlayColor, float cornerRadius, int inset) {
        M3ExpressiveButtonDrawable drawable = new M3ExpressiveButtonDrawable(backgroundColor, pressedOverlayColor, cornerRadius, inset);
        drawable.setColorRoles(M3ColorRoles.Role.PRIMARY_CONTAINER, M3ColorRoles.Role.ON_PRIMARY_CONTAINER);
        return drawable;
    }

    public static M3ExpressiveButtonDrawable createNeutral(int backgroundColor, int pressedOverlayColor, float cornerRadius, int inset) {
        M3ExpressiveButtonDrawable drawable = new M3ExpressiveButtonDrawable(backgroundColor, pressedOverlayColor, cornerRadius, inset);
        drawable.setColorRoles(M3ColorRoles.Role.SURFACE_CONTAINER_HIGH, M3ColorRoles.Role.ON_SURFACE);
        return drawable;
    }

    public void setMorphProgress(float progress) {
        if (fluidSpring != null) fluidSpring.cancel();
        externalProgress = true;
        if (animator != null) {
            animator.removeAllListeners();
            animator.cancel();
            animator = null;
        }
        progress = Utilities.clamp(progress, 1f, 0f);
        if (Math.abs(this.progress - progress) < 0.001f) {
            return;
        }
        this.progress = progress;
        invalidateSelf();
    }

    @Override
    protected boolean onStateChange(int[] stateSet) {
        boolean nextPressed = false;
        for (int state : stateSet) {
            if (state == android.R.attr.state_pressed) {
                nextPressed = true;
                break;
            }
        }
        if (pressed == nextPressed) {
            return false;
        }
        pressed = nextPressed;
        if (externalProgress) {
            return true;
        }
        animateTo(pressed ? 1f : 0f);
        return true;
    }

    @Override
    public boolean isStateful() {
        return true;
    }

    private void animateFluid(float target) {
        if (animator != null) { animator.removeAllListeners(); animator.cancel(); animator = null; }
        Drawable.Callback callback = getCallback();
        while (callback instanceof Drawable) callback = ((Drawable) callback).getCallback();
        android.content.Context context = callback instanceof android.view.View
                ? ((android.view.View) callback).getContext() : org.telegram.messenger.ApplicationLoader.applicationContext;
        if (context == null || callback == null) {
            if (fluidSpring != null) fluidSpring.cancel();
            progress = target; invalidateSelf(); return;
        }
        if (fluidSpring == null) {
            fluidSpring = new androidx.dynamicanimation.animation.SpringAnimation(this,
                    new androidx.dynamicanimation.animation.FloatPropertyCompat<M3ExpressiveButtonDrawable>("fluidCorners") {
                @Override public float getValue(M3ExpressiveButtonDrawable host) { return host.progress; }
                @Override public void setValue(M3ExpressiveButtonDrawable host, float value) {
                    host.progress = Math.max(0f, Math.min(1f, value)); host.invalidateSelf();
                }
            });
            fluidSpring.setSpring(xyz.nextalone.nagram.ui.fluid.FluidTiming.springForce(
                    xyz.nextalone.nagram.ui.fluid.FluidCapabilities.motionScale(context)));
            xyz.nextalone.nagram.ui.fluid.FluidTiming.configure(context, fluidSpring);
        }
        xyz.nextalone.nagram.ui.fluid.FluidTiming.animate(context, fluidSpring, target);
    }

    @Override public void jumpToCurrentState() {
        if (fluidSpring != null && !externalProgress) {
            fluidSpring.cancel(); progress = pressed ? 1f : 0f; invalidateSelf();
        }
        super.jumpToCurrentState();
    }

    private void animateTo(float target) {
        if (xyz.nextalone.nagram.ui.UIStyleEngine.isFluid()) { animateFluid(target); return; }
        if (fluidSpring != null) fluidSpring.cancel();
        if (animator != null) {
            animator.removeAllListeners();
            animator.cancel();
            animator = null;
        }
        if (Math.abs(progress - target) < 0.001f) {
            progress = target;
            invalidateSelf();
            return;
        }
        animator = ValueAnimator.ofFloat(progress, target);
        animator.addUpdateListener(animation -> {
            progress = (float) animation.getAnimatedValue();
            invalidateSelf();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                animator = null;
            }
        });
        if (target == 1f) {
            animator.setInterpolator(new DecelerateInterpolator(2.0f));
            animator.setDuration(120);
        } else {
            animator.setInterpolator(new OvershootInterpolator(2.5f));
            animator.setDuration(380);
        }
        animator.start();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        rect.set(getBounds());
        rect.inset(inset, inset);
        if (rect.isEmpty()) {
            return;
        }

        float alphaProgress = Utilities.clamp(progress, 1f, 0f);
        if (restRadii != null) {
            path.reset();
            for (int i = 0; i < 8; i++) {
                float from = restRadii[i];
                float to = pressedRadii != null ? pressedRadii[i] : dp(14);
                currentRadii[i] = Math.max(0, AndroidUtilities.lerp(from, to, progress));
            }
            path.addRoundRect(rect, currentRadii, android.graphics.Path.Direction.CW);
            paint.setColor(applyAlpha(resolveColor(backgroundColor, backgroundRole), alpha));
            paint.setColorFilter(colorFilter);
            if (Color.alpha(paint.getColor()) > 0) {
                canvas.drawPath(path, paint);
            }

            int resolvedPressedOverlayColor = resolveColor(pressedOverlayColor, pressedOverlayRole);
            int pressedAlpha = (int) (Color.alpha(resolvedPressedOverlayColor) * (alpha / 255f) * alphaProgress);
            if (pressedAlpha > 0) {
                paint.setColor((resolvedPressedOverlayColor & 0x00ffffff) | (pressedAlpha << 24));
                paint.setColorFilter(colorFilter);
                canvas.drawPath(path, paint);
            }
            if (strokeColor != 0 && strokeWidth > 0) {
                strokePaint.setColor(applyAlpha(resolveColor(strokeColor, strokeRole), alpha));
                strokePaint.setColorFilter(colorFilter);
                canvas.drawPath(path, strokePaint);
            }
            return;
        }

        float fromRadius = restRadius > 0 ? restRadius : rect.height() / 2f;
        float toRadius;
        if (pressedRadius >= 0) {
            toRadius = pressedRadius;
        } else if (fromRadius < rect.height() / 2f - dp(2)) {
            toRadius = rect.height() / 2f;
        } else {
            toRadius = dp(14);
        }
        float radius = Math.max(0, AndroidUtilities.lerp(fromRadius, toRadius, progress));

        paint.setColor(applyAlpha(resolveColor(backgroundColor, backgroundRole), alpha));
        paint.setColorFilter(colorFilter);
        if (Color.alpha(paint.getColor()) > 0) {
            canvas.drawRoundRect(rect, radius, radius, paint);
        }

        int resolvedPressedOverlayColor = resolveColor(pressedOverlayColor, pressedOverlayRole);
        int pressedAlpha = (int) (Color.alpha(resolvedPressedOverlayColor) * (alpha / 255f) * alphaProgress);
        if (pressedAlpha > 0) {
            paint.setColor((resolvedPressedOverlayColor & 0x00ffffff) | (pressedAlpha << 24));
            paint.setColorFilter(colorFilter);
            canvas.drawRoundRect(rect, radius, radius, paint);
        }
        if (strokeColor != 0 && strokeWidth > 0) {
            strokePaint.setColor(applyAlpha(resolveColor(strokeColor, strokeRole), alpha));
            strokePaint.setColorFilter(colorFilter);
            canvas.drawRoundRect(rect, radius, radius, strokePaint);
        }
    }

    public static M3ExpressiveButtonDrawable createOutlined(int backgroundColor, int strokeColor, int pressedOverlayColor, float cornerRadius) {
        return createOutlined(backgroundColor, strokeColor, pressedOverlayColor, cornerRadius, 0);
    }

    public static M3ExpressiveButtonDrawable createOutlined(int backgroundColor, int strokeColor, int pressedOverlayColor, float cornerRadius, int inset) {
        M3ExpressiveButtonDrawable drawable = new M3ExpressiveButtonDrawable(backgroundColor, pressedOverlayColor, cornerRadius, inset);
        drawable.setStroke(strokeColor, dp(1));
        return drawable;
    }

    public static M3ExpressiveButtonDrawable createOutlined(int backgroundColor, int strokeColor, int pressedOverlayColor, float cornerRadius, float pressedRadius, int inset) {
        M3ExpressiveButtonDrawable drawable = new M3ExpressiveButtonDrawable(backgroundColor, pressedOverlayColor, cornerRadius, pressedRadius, inset);
        drawable.setStroke(strokeColor, dp(1));
        return drawable;
    }

    public static M3ExpressiveButtonDrawable createOutlinedNeutral(int backgroundColor, int strokeColor, int pressedOverlayColor, float cornerRadius, int inset) {
        M3ExpressiveButtonDrawable drawable = createNeutral(backgroundColor, pressedOverlayColor, cornerRadius, inset);
        drawable.setStroke(strokeColor, dp(1));
        drawable.setStrokeRole(M3ColorRoles.Role.OUTLINE_VARIANT);
        return drawable;
    }

    private int resolveColor(int fallbackColor, @Nullable M3ColorRoles.Role role) {
        if (role == null) {
            return fallbackColor;
        }
        int color = M3ColorRoles.get(role, fallbackColor);
        return ColorUtils.setAlphaComponent(color, Color.alpha(fallbackColor));
    }

    private static int applyAlpha(int color, int alpha) {
        return (color & 0x00ffffff) | ((int) (Color.alpha(color) * (alpha / 255f)) << 24);
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        this.colorFilter = colorFilter;
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}

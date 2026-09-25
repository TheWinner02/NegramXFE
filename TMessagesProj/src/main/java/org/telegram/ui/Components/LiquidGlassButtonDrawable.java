package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

import xyz.nextalone.nagram.ui.UIStyleEngine;

public class LiquidGlassButtonDrawable extends Drawable {

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private final float[] currentRadii = new float[8];
    private int tintColor;
    private int contentColor;
    private float restRadius;
    private float pressedRadius;
    private int inset;
    private int alpha = 255;
    private float progress;
    private boolean pressed;
    private boolean externalProgress;
    private ValueAnimator animator;
    private ColorFilter colorFilter;
    private float[] restRadii;
    private float[] pressedRadii;
    private boolean isActionButton;

    public LiquidGlassButtonDrawable(int tintColor, int contentColor, float restRadius, float pressedRadius, int inset) {
        this.tintColor = tintColor;
        this.contentColor = contentColor;
        this.restRadius = restRadius;
        this.pressedRadius = pressedRadius;
        this.inset = inset;
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dp(1));
    }

    public static LiquidGlassButtonDrawable createForAction(int contentColor) {
        int tint = Theme.multAlpha(contentColor, 0.12f);
        LiquidGlassButtonDrawable drawable = new LiquidGlassButtonDrawable(
                tint,
                contentColor,
                UIStyleEngine.getLiquidGlassActionCornerRadius(),
                UIStyleEngine.getLiquidGlassActionPressedCornerRadius(),
                dp(4)
        );
        drawable.isActionButton = true;
        return drawable;
    }

    public void setColors(int tintColor, int contentColor) {
        this.tintColor = tintColor;
        this.contentColor = contentColor;
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

    public void setMorphProgress(float progress) {
        externalProgress = true;
        if (animator != null) {
            animator.removeAllListeners();
            animator.cancel();
            animator = null;
        }
        progress = Math.max(0f, Math.min(1f, progress));
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

    private void animateTo(float target) {
        if (animator != null) {
            animator.removeAllListeners();
            animator.cancel();
            animator = null;
        }
        animator = ValueAnimator.ofFloat(progress, target);
        animator.addUpdateListener(animation -> {
            progress = (float) animation.getAnimatedValue();
            invalidateSelf();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animator == animation) {
                    animator = null;
                    progress = target;
                    invalidateSelf();
                }
            }
        });
        animator.setInterpolator(UIStyleEngine.getIosSpringInterpolator());
        animator.setDuration(target == 1f ? 150 : 420);
        animator.start();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        rect.set(getBounds());
        rect.inset(inset, inset);
        if (rect.isEmpty()) {
            return;
        }

        float radius = Math.max(0, AndroidUtilities.lerp(restRadius, pressedRadius, progress));
        int fillAlpha;
        int strokeAlpha;
        int sheenAlpha;
        if (isActionButton) {
            fillAlpha = (int) (48 * progress * (alpha / 255f));
            strokeAlpha = (int) (64 * progress * (alpha / 255f));
            sheenAlpha = (int) (40 * progress * (alpha / 255f));
        } else {
            fillAlpha = (int) ((30 + 34 * progress) * (alpha / 255f));
            strokeAlpha = (int) ((72 + 48 * progress) * (alpha / 255f));
            sheenAlpha = (int) ((54 + 28 * (1f - progress)) * (alpha / 255f));
        }

        boolean customRadii = restRadii != null;
        if (customRadii) {
            path.reset();
            for (int i = 0; i < 8; i++) {
                float from = restRadii[i];
                float to = pressedRadii != null ? pressedRadii[i] : pressedRadius;
                currentRadii[i] = Math.max(0, AndroidUtilities.lerp(from, to, progress));
            }
            path.addRoundRect(rect, currentRadii, Path.Direction.CW);
        }

        fillPaint.setColor(withAlpha(tintColor, fillAlpha));
        fillPaint.setColorFilter(colorFilter);
        if (customRadii) {
            canvas.drawPath(path, fillPaint);
        } else {
            canvas.drawRoundRect(rect, radius, radius, fillPaint);
        }

        sheenPaint.setShader(new LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                withAlpha(Color.WHITE, sheenAlpha),
                withAlpha(contentColor, Math.max(12, sheenAlpha / 3)),
                Shader.TileMode.CLAMP
        ));
        sheenPaint.setColorFilter(colorFilter);
        if (customRadii) {
            canvas.drawPath(path, sheenPaint);
        } else {
            canvas.drawRoundRect(rect, radius, radius, sheenPaint);
        }
        sheenPaint.setShader(null);

        strokePaint.setColor(withAlpha(contentColor, strokeAlpha));
        strokePaint.setColorFilter(colorFilter);
        if (customRadii) {
            canvas.drawPath(path, strokePaint);
        } else {
            canvas.drawRoundRect(rect, radius, radius, strokePaint);
        }
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00ffffff) | (Math.max(0, Math.min(255, alpha)) << 24);
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

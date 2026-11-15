/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Keep;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.Theme;

import java.util.HashSet;
import xyz.nextalone.nagram.ui.UIStyleEngine;

public class RadialProgressView extends View implements Theme.Colorable {

    private long lastUpdateTime;
    private float radOffset;
    private float currentCircleLength;
    private boolean risingCircleLength;
    private float currentProgressTime;
    private RectF cicleRect = new RectF();
    private boolean useSelfAlpha;
    private float drawingCircleLenght;

    private int progressColor;

    private DecelerateInterpolator decelerateInterpolator;
    private AccelerateInterpolator accelerateInterpolator;
    private Paint progressPaint;
    private static final float rotationTime = 2000;
    private static final float risingTime = 500;
    private int size;
    private boolean hasCustomStrokeWidth;

    private float currentProgress;
    private float progressAnimationStart;
    private int progressTime;
    private float animatedProgress;
    private boolean toCircle;
    private float toCircleProgress;

    private boolean noProgress = true;
    private final Theme.ResourcesProvider resourcesProvider;

    public RadialProgressView(Context context) {
        this(context, null);
    }

    public RadialProgressView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        size = AndroidUtilities.dp(40);

        progressColor = getThemedColor(Theme.key_progressCircle);
        decelerateInterpolator = new DecelerateInterpolator();
        accelerateInterpolator = new AccelerateInterpolator();
        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setStrokeWidth(AndroidUtilities.dp(UIStyleEngine.isMaterial3Expressive() ? 4 : 3));
        progressPaint.setColor(progressColor);
        themedProgressColor = progressColor;
    }

    // the key progressColor came from (Theme.key_progressCircle by default, see setProgressColorKey), and the color it gave
    private int progressColorKey = Theme.key_progressCircle;
    private int themedProgressColor;

    /** same as setProgressColor(getThemedColor(key)), but follows theme changes */
    public void setProgressColorKey(int key) {
        progressColorKey = key;
        setProgressColor(getThemedColor(key));
        themedProgressColor = progressColor;
    }

    @Override
    public void updateColors() {
        try {
            if (progressPaint == null || progressColor != themedProgressColor) {
                return; // custom color set by the caller
            }
            final int color = getThemedColor(progressColorKey);
            themedProgressColor = color;
            if (color != progressColor) {
                final int alpha = progressPaint.getAlpha();
                setProgressColor(color);
                themedProgressColor = color;
                if (useSelfAlpha) {
                    progressPaint.setAlpha(alpha);
                }
                invalidate();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    @Override
    public void addColorKeys(HashSet<Integer> colors) {
        colors.add(progressColorKey);
    }

    public void setUseSelfAlpha(boolean value) {
        useSelfAlpha = value;
    }

    @Keep
    @Override
    public void setAlpha(float alpha) {
        super.setAlpha(alpha);
        if (useSelfAlpha) {
            Drawable background = getBackground();
            int a = (int) (alpha * 255);
            if (background != null) {
                background.setAlpha(a);
            }
            progressPaint.setAlpha(a);
        }
    }

    public void setNoProgress(boolean value) {
        noProgress = value;
    }

    public void setProgress(float value) {
        currentProgress = value;
        if (animatedProgress > value) {
            animatedProgress = value;
        }
        progressAnimationStart = animatedProgress;
        progressTime = 0;
    }

    public void sync(RadialProgressView from) {
        lastUpdateTime = from.lastUpdateTime;
        radOffset = from.radOffset;
        toCircle = from.toCircle;
        toCircleProgress = from.toCircleProgress;
        noProgress = from.noProgress;
        currentCircleLength = from.currentCircleLength;
        drawingCircleLenght = from.drawingCircleLenght;
        currentProgressTime = from.currentProgressTime;
        currentProgress = from.currentProgress;
        progressTime = from.progressTime;
        animatedProgress = from.animatedProgress;
        risingCircleLength = from.risingCircleLength;
        progressAnimationStart = from.progressAnimationStart;
        updateAnimation(17 * 5);
    }

    private void updateAnimation() {
        long newTime = System.currentTimeMillis();
        long dt = newTime - lastUpdateTime;
        if (dt > 17) {
            dt = 17;
        }
        lastUpdateTime = newTime;
        updateAnimation(dt);
    }

    private void updateAnimation(long dt) {
        radOffset += 360 * dt / rotationTime;
        int count = (int) (radOffset / 360);
        radOffset -= count * 360;

        if (toCircle && toCircleProgress != 1f) {
            toCircleProgress += 16 / 220f;
            if (toCircleProgress > 1f) {
                toCircleProgress = 1f;
            }
        } else if (!toCircle && toCircleProgress != 0f) {
            toCircleProgress -= 16 / 400f;
            if (toCircleProgress < 0) {
                toCircleProgress = 0f;
            }
        }

        if (noProgress) {
            if (toCircleProgress == 0) {
                currentProgressTime += dt;
                if (currentProgressTime >= risingTime) {
                    currentProgressTime = risingTime;
                }
                if (risingCircleLength) {
                    currentCircleLength = 4 + 266 * accelerateInterpolator.getInterpolation(currentProgressTime / risingTime);
                } else {
                    currentCircleLength = 4 - 270 * (1.0f - decelerateInterpolator.getInterpolation(currentProgressTime / risingTime));
                }

                if (currentProgressTime == risingTime) {
                    if (risingCircleLength) {
                        radOffset += 270;
                        currentCircleLength = -266;
                    }
                    risingCircleLength = !risingCircleLength;
                    currentProgressTime = 0;
                }
            } else {
                if (risingCircleLength) {
                    float old = currentCircleLength;
                    currentCircleLength = 4 + 266 * accelerateInterpolator.getInterpolation(currentProgressTime / risingTime);
                    currentCircleLength += 360 * toCircleProgress;
                    float dx = old - currentCircleLength;
                    if (dx > 0) {
                        radOffset += old - currentCircleLength;
                    }
                } else {
                    float old = currentCircleLength;
                    currentCircleLength = 4 - 270 * (1.0f - decelerateInterpolator.getInterpolation(currentProgressTime / risingTime));
                    currentCircleLength -= 364 * toCircleProgress;
                    float dx = old - currentCircleLength;
                    if (dx > 0) {
                        radOffset += old - currentCircleLength;
                    }
                }
            }
        } else {
            float progressDiff = currentProgress - progressAnimationStart;
            if (progressDiff > 0) {
                progressTime += dt;
                if (progressTime >= 200.0f) {
                    animatedProgress = progressAnimationStart = currentProgress;
                    progressTime = 0;
                } else {
                    animatedProgress = progressAnimationStart + progressDiff * AndroidUtilities.decelerateInterpolator.getInterpolation(progressTime / 200.0f);
                }
            }
            currentCircleLength = Math.max(4, 360 * animatedProgress);
        }
        invalidate();
    }

    public void setSize(int value) {
        size = value;
        invalidate();
    }

    public void setStrokeWidth(float value) {
        hasCustomStrokeWidth = true;
        progressPaint.setStrokeWidth(AndroidUtilities.dp(value));
    }

    public void setProgressColor(int color) {
        progressColor = color;
        progressPaint.setColor(progressColor);
    }

    public void toCircle(boolean toCircle, boolean animated) {
        this.toCircle = toCircle;
        if (!animated) {
            toCircleProgress = toCircle ? 1f : 0f;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        updateDefaultStrokeWidth();
        int x = (getMeasuredWidth() - size) / 2;
        int y = (getMeasuredHeight() - size) / 2;
        cicleRect.set(x, y, x + size, y + size);
        drawM3DeterminateTrack(canvas);
        drawProgressArc(canvas, cicleRect, radOffset, drawingCircleLenght = currentCircleLength);
        updateAnimation();
    }

    public void draw(Canvas canvas, float cx, float cy) {
        updateDefaultStrokeWidth();
        cicleRect.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy +  size / 2f);
        drawM3DeterminateTrack(canvas);
        drawProgressArc(canvas, cicleRect, radOffset, drawingCircleLenght = currentCircleLength);
        updateAnimation();
    }

    private void updateDefaultStrokeWidth() {
        if (!hasCustomStrokeWidth) {
            progressPaint.setStrokeWidth(AndroidUtilities.dp(UIStyleEngine.isMaterial3Expressive() ? 4 : 3));
        }
    }

    private void drawProgressArc(Canvas canvas, RectF rect, float startAngle, float sweepAngle) {
        if (UIStyleEngine.isMaterial3Expressive()) {
            M3WavyProgress.drawCircular(
                    canvas,
                    rect,
                    progressPaint,
                    startAngle,
                    sweepAngle,
                    progressPaint.getStrokeWidth(),
                    AndroidUtilities.dpf2(1.65f),
                    SystemClock.uptimeMillis() * 0.16f);
            return;
        }
        canvas.drawArc(rect, startAngle, sweepAngle, false, progressPaint);
    }

    private void drawM3DeterminateTrack(Canvas canvas) {
        if (!UIStyleEngine.isMaterial3Expressive() || noProgress || Math.abs(currentCircleLength) >= 359f) {
            return;
        }
        int oldAlpha = progressPaint.getAlpha();
        progressPaint.setAlpha((int) (oldAlpha * 0.14f));
        M3WavyProgress.drawCircular(
                canvas,
                cicleRect,
                progressPaint,
                0,
                360,
                progressPaint.getStrokeWidth(),
                AndroidUtilities.dpf2(1.2f),
                SystemClock.uptimeMillis() * 0.08f);
        progressPaint.setAlpha(oldAlpha);
    }

    public boolean isCircle() {
        return Math.abs(drawingCircleLenght) >= 360;
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }
}

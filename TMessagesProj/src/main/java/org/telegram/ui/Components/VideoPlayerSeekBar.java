/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.os.SystemClock;
import android.text.Layout;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.Utilities;

import java.util.ArrayList;
import java.util.Collections;

import xyz.nextalone.nagram.ui.UIStyleEngine;

public class VideoPlayerSeekBar {

    public interface SeekBarDelegate {
        void onSeekBarDrag(float progress);
        default void onSeekBarContinuousDrag(float progress) {
        }
    }

    private float progress;
    private static Paint paint;
    private static Paint strokePaint;
    private static int thumbWidth;
    private int thumbX = 0;
    private float animatedThumbX = 0;
    private int draggingThumbX = 0;
    private int thumbDX = 0;
    private boolean pressed = false;
    private boolean pressedDelayed = false;
    private int width;
    private int height;
    private SeekBarDelegate delegate;
    private int backgroundColor;
    private int cacheColor;
    private int circleColor;
    private int progressColor;
    private int backgroundSelectedColor;
    private RectF rect = new RectF();
    private boolean selected;
    private float animateFromBufferedProgress;
    private boolean animateResetBuffering;
    private float bufferedAnimationValue = 1f;
    private float bufferedProgress;
    private float currentRadius;
    private long lastUpdateTime;
    private View parentView;

    private int lineHeight = AndroidUtilities.dp(4);
    private int smallLineHeight = AndroidUtilities.dp(2);

    private float transitionProgress;
    private int horizontalPadding;
    private int smallLineColor;
    private boolean playing;

    private int fromThumbX = 0;
    private float animateThumbProgress = 1f;
    private AnimatedFloat animateThumbLoopBackProgress;
    private float loopBackWasThumbX;

    private float iosLensProgress = 0f;
    private android.animation.ValueAnimator iosLensAnimator;
    private static Paint glassPaint;

    private void animateIosLens(boolean active) {
        if (iosLensAnimator != null) {
            iosLensAnimator.cancel();
        }
        iosLensAnimator = android.animation.ValueAnimator.ofFloat(iosLensProgress, active ? 1f : 0f);
        iosLensAnimator.setDuration(active ? 150 : 200);
        iosLensAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
        iosLensAnimator.addUpdateListener(animation -> {
            iosLensProgress = (float) animation.getAnimatedValue();
            if (parentView != null) {
                parentView.invalidate();
            }
        });
        iosLensAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                iosLensProgress = active ? 1f : 0f;
                iosLensAnimator = null;
                if (parentView != null) {
                    parentView.invalidate();
                }
            }
        });
        iosLensAnimator.start();
    }

    public VideoPlayerSeekBar(View parent) {
        if (paint == null) {
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setColor(Color.BLACK);
            strokePaint.setStrokeWidth(1);
        }
        if (glassPaint == null) {
            glassPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        }
        parentView = parent;
        thumbWidth = AndroidUtilities.dp(24);
        currentRadius = AndroidUtilities.dp(6);
        animateThumbLoopBackProgress = new AnimatedFloat(0f, parent, 0, 300, CubicBezierInterpolator.EASE_OUT_QUINT);
    }

    public void setDelegate(SeekBarDelegate seekBarDelegate) {
        delegate = seekBarDelegate;
    }

    public boolean onTouch(int action, float x, float y) {
        if (action == MotionEvent.ACTION_DOWN) {
            if (transitionProgress > 0f) {
                return false;
            }
            int additionWidth = (height - thumbWidth) / 2;
            if (x >= -additionWidth && x <= width + additionWidth && y >= 0 && y <= height) {
                if (!(thumbX - additionWidth <= x && x <= thumbX + thumbWidth + additionWidth)) {
                    thumbX = (int) x - thumbWidth / 2;
                    if (thumbX < 0) {
                        thumbX = 0;
                    } else if (thumbX > width - thumbWidth) {
                        thumbX = thumbWidth - width;
                    }
                    animatedThumbX = thumbX;
                }
                pressed = pressedDelayed = true;
                draggingThumbX = thumbX;
                thumbDX = (int) (x - thumbX);
                if (UIStyleEngine.isIosLiquidGlass()) {
                    animateIosLens(true);
                }
                return true;
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (UIStyleEngine.isIosLiquidGlass()) {
                animateIosLens(false);
            }
            if (pressed) {
                animatedThumbX = thumbX = draggingThumbX;
                if (action == MotionEvent.ACTION_UP && delegate != null) {
                    delegate.onSeekBarDrag((float) thumbX / (float) (width - thumbWidth));
                }
                pressed = false;
                AndroidUtilities.runOnUIThread(() -> pressedDelayed = false, 50);
                return true;
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (pressed) {
                draggingThumbX = (int) (x - thumbDX);
                if (draggingThumbX < 0) {
                    draggingThumbX = 0;
                } else if (draggingThumbX > width - thumbWidth) {
                    draggingThumbX = width - thumbWidth;
                }
                if (delegate != null) {
                    delegate.onSeekBarContinuousDrag((float) draggingThumbX / (float) (width - thumbWidth));
                }
                return true;
            }
        }
        return false;
    }

    public void setColors(int background, int cache, int progress, int circle, int selected, int smallLineColor) {
        backgroundColor = background;
        cacheColor = cache;
        circleColor = circle;
        progressColor = progress;
        backgroundSelectedColor = selected;
        this.smallLineColor = smallLineColor;
    }

    public void setProgress(float progress, boolean animated) {
        if (Math.abs(this.progress - 1f) < 0.04f && Math.abs(progress) < 0.04f) {
            animateThumbLoopBackProgress.set(1, true);
            loopBackWasThumbX = thumbX;
        }
        this.progress = progress;
        int newThumb = (int) Math.ceil((width - thumbWidth) * progress);

        if (animated) {
            if (Math.abs(newThumb - thumbX) > AndroidUtilities.dp(10)) {
                float progressInterpolated = CubicBezierInterpolator.DEFAULT.getInterpolation(animateThumbProgress);
                fromThumbX = (int) (thumbX * progressInterpolated + fromThumbX * (1f - progressInterpolated));
                animateThumbProgress = 0;
            } else if (animateThumbProgress == 1f) {
                animateThumbProgress = 0;
                fromThumbX = thumbX;
            }
        }
        thumbX = newThumb;

        if (thumbX < 0) {
            thumbX = 0;
        } else if (thumbX > width - thumbWidth) {
            thumbX = width - thumbWidth;
        }
        if (Math.abs(animatedThumbX - thumbX) > AndroidUtilities.dp(8)) {
            animatedThumbX = thumbX;
        }
    }

    public void setProgress(float progress) {
        setProgress(progress, false);
    }

    public void setBufferedProgress(float value) {
        if (value != bufferedProgress) {
            animateFromBufferedProgress = bufferedProgress;
            animateResetBuffering = value < bufferedProgress;
            bufferedProgress = value;
            bufferedAnimationValue = 0;
        }
    }

    public float getProgress() {
        return (float) thumbX / (float) (width - thumbWidth);
    }

    public int getThumbX() {
        return (pressed ? draggingThumbX : thumbX) + thumbWidth / 2;
    }

    public boolean isDragging() {
        return pressed;
    }

    public void setSelected(boolean value) {
        selected = value;
    }

    public void setSize(int w, int h) {
        width = w;
        height = h;

        if (parentView != null) {
            parentView.invalidate();
        }
    }

    public int getWidth() {
        return width - thumbWidth;
    }


    public float getTransitionProgress() {
        return transitionProgress;
    }

    public void setTransitionProgress(float transitionProgress) {
        if (this.transitionProgress != transitionProgress) {
            this.transitionProgress = transitionProgress;
            parentView.invalidate();
        }
    }

    public int getHorizontalPadding() {
        return horizontalPadding;
    }

    public void setHorizontalPadding(int horizontalPadding) {
        this.horizontalPadding = horizontalPadding;
    }

    public void setPlaying(boolean playing) {
        if (this.playing != playing) {
            this.playing = playing;
            if (parentView != null) {
                parentView.invalidate();
            }
        }
    }

    private ArrayList<Pair<Float, CharSequence>> timestamps;
    private CharSequence lastCaption;
    private long lastVideoDuration;

    public void clearTimestamps() {
        timestamps = null;
        currentTimestamp = -1;
        timestampsAppearing = 0;
        if (timestampLabel != null) {
            timestampLabel[0] = timestampLabel[1] = null;
        }
        lastCaption = null;
        lastVideoDuration = -1;
    }

    public void updateTimestamps(MessageObject messageObject, long videoDuration) {
        if (messageObject == null || videoDuration < 0) {
            clearTimestamps();
            return;
        }
        CharSequence text = messageObject.caption;
        if (messageObject.isYouTubeVideo()) {
            if (messageObject.youtubeDescription == null && messageObject.messageOwner.media.webpage.description != null) {
                messageObject.youtubeDescription = SpannableString.valueOf(messageObject.messageOwner.media.webpage.description);
                MessageObject.addUrlsByPattern(messageObject.isOut(), messageObject.youtubeDescription, false, 3, (int) videoDuration, false);
            }
            text = messageObject.youtubeDescription;
        }
        if (text == lastCaption && lastVideoDuration == videoDuration) {
            return;
        }
        lastCaption = text;
        lastVideoDuration = videoDuration;
        if (!(text instanceof Spanned)) {
            timestamps = null;
            currentTimestamp = -1;
            timestampsAppearing = 0;
            if (timestampLabel != null) {
                timestampLabel[0] = timestampLabel[1] = null;
            }
            return;
        }
        Spanned spanned = (Spanned) text;
        URLSpanNoUnderline[] links;
        try {
            links = spanned.getSpans(0, spanned.length(), URLSpanNoUnderline.class);
        } catch (Exception e) {
            FileLog.e(e);
            timestamps = null;
            currentTimestamp = -1;
            timestampsAppearing = 0;
            if (timestampLabel != null) {
                timestampLabel[0] = timestampLabel[1] = null;
            }
            return;
        }
        timestamps = new ArrayList<>();
        timestampsAppearing = 0;
        if (timestampLabelPaint == null) {
            timestampLabelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            timestampLabelPaint.setTextSize(AndroidUtilities.dp(12));
            timestampLabelPaint.setColor(0xffffffff);
        }
        for (int i = 0; i < links.length; ++i) {
            URLSpanNoUnderline link = links[i];
            if (link != null && link.getURL() != null && link.label != null && link.getURL().startsWith("video?")) {
                Integer seconds = Utilities.parseInt(link.getURL().substring(6));
                if (seconds != null && seconds >= 0) {
                    float position = seconds * 1000L / (float) videoDuration;
                    String label = link.label;
                    SpannableStringBuilder builder = new SpannableStringBuilder(label);
                    Emoji.replaceEmoji(builder, timestampLabelPaint.getFontMetricsInt(), false);
                    timestamps.add(new Pair<>(position, builder));
                }
            }
        }
        Collections.sort(timestamps, (a, b) -> {
            if (a.first > b.first) {
                return 1;
            } else if (b.first > a.first) {
                return -1;
            } else {
                return 0;
            }
        });
    }

    public void draw(Canvas canvas, View view) {
        int effectiveLineHeight = UIStyleEngine.isIosLiquidGlass() ? AndroidUtilities.dp(5) : (UIStyleEngine.isMaterial3Expressive() ? Math.max(lineHeight, AndroidUtilities.dp(6)) : lineHeight);
        int effectiveSmallLineHeight = UIStyleEngine.isIosLiquidGlass() ? AndroidUtilities.dp(2.5f) : (UIStyleEngine.isMaterial3Expressive() ? Math.max(smallLineHeight, AndroidUtilities.dp(3)) : smallLineHeight);
        float yShift = UIStyleEngine.isMaterial3Expressive() ? AndroidUtilities.dp(8) : 0;
        rect.left = horizontalPadding + AndroidUtilities.lerp(thumbWidth / 2f, 0, transitionProgress);
        rect.top = AndroidUtilities.lerp((height - effectiveLineHeight) / 2f - yShift, height - AndroidUtilities.dp(3) - effectiveSmallLineHeight, transitionProgress);
        rect.bottom = AndroidUtilities.lerp((height + effectiveLineHeight) / 2f - yShift, height - AndroidUtilities.dp(3), transitionProgress);

        float thumbX = this.thumbX;
        animatedThumbX = Math.min(animatedThumbX, thumbX);
        animatedThumbX = AndroidUtilities.lerp(animatedThumbX, thumbX, .5f);
        if (Math.abs(thumbX - animatedThumbX) > 0.005f) {
            parentView.invalidate();
        }
        thumbX = animatedThumbX;

        float currentThumbX = thumbX;
        if (animateThumbProgress != 1f) {
            animateThumbProgress += 16 / 220f;
            if (animateThumbProgress >= 1f) {
                animateThumbProgress = 1f;
            } else {
                view.invalidate();
                float progressInterpolated = CubicBezierInterpolator.DEFAULT.getInterpolation(animateThumbProgress);
                currentThumbX = fromThumbX * (1f - progressInterpolated) + thumbX * progressInterpolated;
            }
        }

        float loopBack = animateThumbLoopBackProgress.set(0);
        if (pressed) {
            loopBack = 0;
        }

        // background
        rect.right = horizontalPadding + AndroidUtilities.lerp(width - thumbWidth / 2f, parentView.getWidth() - horizontalPadding * 2f, transitionProgress);
        setPaintColor(selected ? backgroundSelectedColor : backgroundColor, 1f - transitionProgress);
        drawProgressBar(canvas, rect, paint, false);

        if (bufferedAnimationValue != 1f) {
            bufferedAnimationValue += 16 / 100f;
            if (bufferedAnimationValue > 1) {
                bufferedAnimationValue = 1f;
            } else {
                parentView.invalidate();
            }
        }

        // buffered
        if (animateResetBuffering) {
            if (animateFromBufferedProgress > 0) {
                rect.right = horizontalPadding + AndroidUtilities.lerp(thumbWidth / 2f + animateFromBufferedProgress * (width - thumbWidth), parentView.getWidth() - horizontalPadding * 2f, transitionProgress);
                setPaintColor(selected ? backgroundSelectedColor : cacheColor, (1f - transitionProgress) * (1f - bufferedAnimationValue));
                drawProgressBar(canvas, rect, paint, false);
            }
            if (bufferedProgress > 0) {
                rect.right = horizontalPadding + AndroidUtilities.lerp(thumbWidth / 2f + bufferedProgress * (width - thumbWidth), parentView.getWidth() - horizontalPadding * 2f, transitionProgress);
                setPaintColor(selected ? backgroundSelectedColor : cacheColor, 1f - transitionProgress);
                drawProgressBar(canvas, rect, paint, false);
            }
        } else {
            float currentBufferedProgress = animateFromBufferedProgress * (1f - bufferedAnimationValue) + bufferedProgress * bufferedAnimationValue;
            if (currentBufferedProgress > 0) {
                rect.right = horizontalPadding + AndroidUtilities.lerp(thumbWidth / 2f + currentBufferedProgress * (width - thumbWidth), parentView.getWidth() - horizontalPadding * 2f, transitionProgress);
                setPaintColor(selected ? backgroundSelectedColor : cacheColor, 1f - transitionProgress);
                drawProgressBar(canvas, rect, paint, false);
            }
        }

        int newRad = AndroidUtilities.dp(pressed ? 8 : 6);
        if (currentRadius != newRad) {
            long newUpdateTime = SystemClock.elapsedRealtime();
            long dt = newUpdateTime - lastUpdateTime;
            lastUpdateTime = newUpdateTime;
            if (dt > 18) {
                dt = 16;
            }
            if (currentRadius < newRad) {
                currentRadius += AndroidUtilities.dp(1) * (dt / 60.0f);
                if (currentRadius > newRad) {
                    currentRadius = newRad;
                }
            } else {
                currentRadius -= AndroidUtilities.dp(1) * (dt / 60.0f);
                if (currentRadius < newRad) {
                    currentRadius = newRad;
                }
            }
            if (parentView != null) {
                parentView.invalidate();
            }
        }
        final float circleRadius = AndroidUtilities.lerp(currentRadius, 0, transitionProgress);

        if (loopBack > 0) {
            float wasLeft = rect.left;
            rect.right = horizontalPadding + AndroidUtilities.lerp(thumbWidth / 2f + (width - thumbWidth), parentView.getWidth() - horizontalPadding * 2f, transitionProgress);
            rect.left = AndroidUtilities.lerp(wasLeft, rect.right, 1f - loopBack);
            if (transitionProgress > 0f && rect.width() > 0) {
                // progress stroke
                strokePaint.setAlpha((int) (transitionProgress * 255 * 0.2f));
                drawProgressBar(canvas, rect, strokePaint, false);
            }
            setPaintColor(ColorUtils.blendARGB(progressColor, smallLineColor, transitionProgress), 1f);
            drawProgressBar(canvas, rect, paint, playing);

            rect.left = wasLeft;

            setPaintColor(ColorUtils.blendARGB(circleColor, getProgress() == 0 ? Color.TRANSPARENT : smallLineColor, transitionProgress), 1f - transitionProgress);
            float wasRight = horizontalPadding + AndroidUtilities.lerp(thumbWidth / 2f + loopBackWasThumbX, (parentView.getWidth() - horizontalPadding * 2f) * (loopBackWasThumbX / (float) (width - thumbWidth)), transitionProgress);
            canvas.drawCircle(wasRight, rect.centerY(), circleRadius * loopBack, paint);
        }

        boolean isIosGlass = UIStyleEngine.isIosLiquidGlass();

        // progress
        rect.right = horizontalPadding + AndroidUtilities.lerp(thumbWidth / 2f + (pressed ? draggingThumbX : currentThumbX), (parentView.getWidth() - horizontalPadding * 2f) * getProgress(), transitionProgress);
        if (transitionProgress > 0f && rect.width() > 0) {
            // progress stroke
            strokePaint.setAlpha((int) (transitionProgress * 255 * 0.2f));
            drawProgressBar(canvas, rect, strokePaint, false);
        }
        setPaintColor(ColorUtils.blendARGB(progressColor, smallLineColor, transitionProgress), 1f);
        drawProgressBar(canvas, rect, paint, playing);

        // circle / thumb
        if (isIosGlass && transitionProgress < 1f) {
            float cx = rect.right;
            float cy = rect.centerY();
            float fadeProgress = 1f - transitionProgress;

            float restW = AndroidUtilities.dpf2(28f);
            float restH = AndroidUtilities.dpf2(18f);
            float lensW = AndroidUtilities.dpf2(46f);
            float lensH = AndroidUtilities.dpf2(30f);

            float curW = AndroidUtilities.lerp(restW, lensW, iosLensProgress);
            float curH = AndroidUtilities.lerp(restH, lensH, iosLensProgress);
            float curRad = curH / 2f;

            // --- A. SOLID WHITE REST CAPSULE LAYER (fades out as lens blooms) ---
            if (iosLensProgress < 1.0f) {
                float whiteAlpha = (1.0f - iosLensProgress) * fadeProgress;

                // Soft ambient drop shadows
                glassPaint.setColor(ColorUtils.setAlphaComponent(0x000000, (int) (0x35 * whiteAlpha)));
                RectF r = AndroidUtilities.rectTmp;
                r.set(cx - curW / 2f, cy - curH / 2f + AndroidUtilities.dpf2(1.5f), cx + curW / 2f, cy + curH / 2f + AndroidUtilities.dpf2(1.5f));
                canvas.drawRoundRect(r, curRad, curRad, glassPaint);

                glassPaint.setColor(ColorUtils.setAlphaComponent(0x000000, (int) (0x15 * whiteAlpha)));
                r.set(cx - curW / 2f, cy - curH / 2f + AndroidUtilities.dpf2(2.5f), cx + curW / 2f, cy + curH / 2f + AndroidUtilities.dpf2(2.5f));
                canvas.drawRoundRect(r, curRad, curRad, glassPaint);

                // Pristine solid white capsule body
                glassPaint.setColor(ColorUtils.setAlphaComponent(0xFFFFFFFF, (int) (0xFF * whiteAlpha)));
                r.set(cx - curW / 2f, cy - curH / 2f, cx + curW / 2f, cy + curH / 2f);
                canvas.drawRoundRect(r, curRad, curRad, glassPaint);
            }

            // --- B. TRANSLUCENT LIQUID GLASS LENS LAYER (blooms when user touches / drags) ---
            if (iosLensProgress > 0.0f) {
                float lensAlpha = iosLensProgress * fadeProgress;
                RectF r = AndroidUtilities.rectTmp;

                // Expanded soft shadow
                glassPaint.setColor(ColorUtils.setAlphaComponent(0x000000, (int) (0x45 * lensAlpha)));
                r.set(cx - curW / 2f, cy - curH / 2f + AndroidUtilities.dpf2(2.5f), cx + curW / 2f, cy + curH / 2f + AndroidUtilities.dpf2(2.5f));
                canvas.drawRoundRect(r, curRad, curRad, glassPaint);

                // Translucent glass body (tinted white/frosted, reveals video progress underneath)
                int glassTint = ColorUtils.setAlphaComponent(0xFFFFFF, (int) (0x60 * lensAlpha));
                glassPaint.setColor(glassTint);
                r.set(cx - curW / 2f, cy - curH / 2f, cx + curW / 2f, cy + curH / 2f);
                canvas.drawRoundRect(r, curRad, curRad, glassPaint);

                // Glass dome specular reflection on upper half
                glassPaint.setShader(new LinearGradient(
                        cx, cy - curH / 2f, cx, cy,
                        ColorUtils.setAlphaComponent(0xFFFFFF, (int) (0x55 * lensAlpha)), 0x00FFFFFF, Shader.TileMode.CLAMP
                ));
                r.set(cx - curW / 2f + AndroidUtilities.dpf2(1f), cy - curH / 2f + AndroidUtilities.dpf2(0.8f), cx + curW / 2f - AndroidUtilities.dpf2(1f), cy);
                canvas.drawRoundRect(r, curRad, curRad, glassPaint);
                glassPaint.setShader(null);

                // Refractive specular rim
                r.set(cx - curW / 2f, cy - curH / 2f, cx + curW / 2f, cy + curH / 2f);
                glassPaint.setStyle(Paint.Style.STROKE);
                glassPaint.setStrokeWidth(AndroidUtilities.dpf2(1.2f));
                LinearGradient rimGrad = new LinearGradient(
                        cx - curW / 2f, cy, cx + curW / 2f, cy,
                        ColorUtils.setAlphaComponent(Color.WHITE, (int) (0xCC * lensAlpha)),
                        ColorUtils.setAlphaComponent(0xFFFFFF, (int) (0x40 * lensAlpha)),
                        Shader.TileMode.CLAMP
                );
                glassPaint.setShader(rimGrad);
                canvas.drawRoundRect(r, curRad, curRad, glassPaint);
                glassPaint.setShader(null);
                glassPaint.setStyle(Paint.Style.FILL);
            }
        } else {
            setPaintColor(ColorUtils.blendARGB(circleColor, getProgress() == 0 ? Color.TRANSPARENT : smallLineColor, transitionProgress), 1f - transitionProgress);
            canvas.drawCircle(rect.right, rect.centerY(), circleRadius * (1f - loopBack), paint);
        }

        drawTimestampLabel(canvas);
    }

    private float timestampsAppearing = 0;
    private long lastTimestampsAppearingUpdate;
    private final float TIMESTAMP_GAP = 1f;
    private static float[] tmpRadii;
    private static Path tmpPath;

    private void drawProgressBar(Canvas canvas, RectF rect, Paint paint, boolean wavy) {
        float radius = UIStyleEngine.isIosLiquidGlass() ? (rect.height() / 2f) : AndroidUtilities.dp(AndroidUtilities.lerp(2, 1, transitionProgress));
        if (timestamps == null || timestamps.isEmpty()) {
            if (wavy && UIStyleEngine.isMaterial3Expressive()) {
                M3WavyProgress.drawLinear(
                        canvas,
                        rect,
                        paint,
                        SystemClock.uptimeMillis() * 0.10f,
                        AndroidUtilities.lerp(AndroidUtilities.dpf2(1.8f), AndroidUtilities.dpf2(0.8f), transitionProgress),
                        AndroidUtilities.dp(26));
                return;
            }
            canvas.drawRoundRect(rect, radius, radius, paint);
        } else {
            float lineWidth = rect.bottom - rect.top;
            float left = horizontalPadding + AndroidUtilities.lerp(thumbWidth / 2f, 0, transitionProgress);
            float right = horizontalPadding + AndroidUtilities.lerp(width - thumbWidth / 2f, parentView.getWidth() - horizontalPadding * 2f, transitionProgress);
            AndroidUtilities.rectTmp.set(rect);
            float halfGap = AndroidUtilities.dp(TIMESTAMP_GAP * timestampsAppearing) / 2f;
            if (tmpPath == null) {
                tmpPath = new Path();
            }
            tmpPath.reset();
            float minDur = AndroidUtilities.dp(4) / (right - left);
            int start = -1, end = -1;
            for (int i = 0; i < timestamps.size(); ++i) {
                if (timestamps.get(i).first >= minDur) {
                    start = i;
                    break;
                }
            }
            if (start < 0) {
                start = 0;
            }
            for (int i = timestamps.size() - 1; i >= 0; --i) {
                if (1f - timestamps.get(i).first >= minDur) {
                    end = i + 1;
                    break;
                }
            }
            if (end < 0) {
                end = timestamps.size();
            }
            boolean first = true;
            for (int i = start; i <= end; ++i) {
                float from = i == start ? 0 : timestamps.get(i - 1).first;
                float to = i == end ? 1 : timestamps.get(i).first;
                while (i != end && i != 0 && i < timestamps.size() - 1 && timestamps.get(i).first - from <= minDur) {
                    i++;
                    to = timestamps.get(i).first;
                }

                AndroidUtilities.rectTmp.left = AndroidUtilities.lerp(left, right, from) + (i > 0 ? halfGap : 0);
                AndroidUtilities.rectTmp.right = AndroidUtilities.lerp(left, right, to) - (i < end ? halfGap : 0);

                boolean last;
                if (last = AndroidUtilities.rectTmp.right > rect.right) {
                    AndroidUtilities.rectTmp.right = rect.right;
                }
                if (AndroidUtilities.rectTmp.right < rect.left) {
                    continue;
                }
                if (AndroidUtilities.rectTmp.left < rect.left) {
                    AndroidUtilities.rectTmp.left = rect.left;
                }

                if (tmpRadii == null) {
                    tmpRadii = new float[8];
                }
                if (i == start || last && AndroidUtilities.rectTmp.left >= rect.left) {
                    tmpRadii[0] = tmpRadii[1] = tmpRadii[6] = tmpRadii[7] = radius;
                    tmpRadii[2] = tmpRadii[3] = tmpRadii[4] = tmpRadii[5] = radius * 0.7f * timestampsAppearing;
                } else if (i >= end) {
                    tmpRadii[0] = tmpRadii[1] = tmpRadii[6] = tmpRadii[7] = radius * 0.7f * timestampsAppearing;
                    tmpRadii[2] = tmpRadii[3] = tmpRadii[4] = tmpRadii[5] = radius;
                } else {
                    tmpRadii[0] = tmpRadii[1] = tmpRadii[6] = tmpRadii[7] =
                    tmpRadii[2] = tmpRadii[3] = tmpRadii[4] = tmpRadii[5] = radius * 0.7f * timestampsAppearing;
                }
                tmpPath.addRoundRect(AndroidUtilities.rectTmp, tmpRadii, Path.Direction.CW);

                if (last) {
                    break;
                }
            }
            canvas.drawPath(tmpPath, paint);
        }
    }


    private int currentTimestamp = -1, lastTimestamp = -1;
    private StaticLayout[] timestampLabel;
    private TextPaint timestampLabelPaint;
    private float timestampChangeT = 1;
    private int timestampChangeDirection;
    private long lastTimestampUpdate;
    private float lastWidth = -1;

    private void drawTimestampLabel(Canvas canvas) {
        if (timestamps == null || timestamps.isEmpty()) {
            return;
        }

        float progress = pressed || pressedDelayed ? (draggingThumbX / (float) (width - thumbWidth)) : (animatedThumbX / (float) (width - thumbWidth));

        int timestampIndex = -1;
        for (int i = timestamps.size() - 1; i >= 0; --i) {
            if (timestamps.get(i).first - 0.001f <= progress) {
                timestampIndex = i;
                break;
            }
        }

        if (timestampLabel == null) {
            timestampLabel = new StaticLayout[2];
        }

        float left = horizontalPadding + AndroidUtilities.lerp(thumbWidth / 2f, 0, transitionProgress);
        float right = horizontalPadding + AndroidUtilities.lerp(width - thumbWidth / 2f, parentView.getWidth() - horizontalPadding * 2f, transitionProgress);
        float rightPadded = horizontalPadding + (width - thumbWidth / 2f);
        float width = Math.abs(left - rightPadded) - AndroidUtilities.dp(16);

        if (lastWidth > 0 && Math.abs(lastWidth - width) > 0.01f) {
            if (timestampLabel[0] != null) {
                timestampLabel[0] = makeStaticLayout(timestampLabel[0].getText(), (int) width);
            }
            if (timestampLabel[1] != null) {
                timestampLabel[1] = makeStaticLayout(timestampLabel[1].getText(), (int) width);
            }
        }
        lastWidth = width;

        if (timestampIndex != currentTimestamp) {
            timestampLabel[1] = timestampLabel[0];
            if (pressed) {
                AndroidUtilities.vibrateCursor(parentView);
            }
            if (timestampIndex >= 0 && timestampIndex < timestamps.size()) {
                CharSequence label = timestamps.get(timestampIndex).second;
                if (label == null) {
                    timestampLabel[0] = null;
                } else {
                    timestampLabel[0] = makeStaticLayout(label, (int) width);
                }
            } else {
                timestampLabel[0] = null;
            }
            timestampChangeT = 0;
            if (timestampIndex == -1) {
                timestampChangeDirection = -1;
            } else if (currentTimestamp == -1) {
                timestampChangeDirection = 1;
            } else if (timestampIndex < currentTimestamp) {
                timestampChangeDirection = -1;
            } else if (timestampIndex > currentTimestamp) {
                timestampChangeDirection = 1;
            }
            lastTimestamp = currentTimestamp;
            currentTimestamp = timestampIndex;
        }
        if (timestampChangeT < 1f) {
            long tx = Math.min(17, Math.abs(SystemClock.elapsedRealtime() - lastTimestampUpdate));
            float duration = timestamps.size() > 8 ? 160f : 220f;
            timestampChangeT = Math.min(timestampChangeT + tx / duration, 1);
            parentView.invalidate();
            lastTimestampUpdate = SystemClock.elapsedRealtime();
        }
        if (timestampsAppearing < 1f) {
            long tx = Math.min(17, Math.abs(SystemClock.elapsedRealtime() - lastTimestampUpdate));
            timestampsAppearing = Math.min(timestampsAppearing + tx / 200f, 1);
            parentView.invalidate();
            lastTimestampsAppearingUpdate = SystemClock.elapsedRealtime();
        }
        float changeT = CubicBezierInterpolator.DEFAULT.getInterpolation(timestampChangeT);

        canvas.save();
        float bottom = AndroidUtilities.lerp((height + lineHeight) / 2f, height - AndroidUtilities.dp(3), transitionProgress);
        canvas.translate(left + (right - rightPadded) * (transitionProgress), bottom + AndroidUtilities.dp(12));
        if (timestampLabel[1] != null) {
            canvas.save();
            if (timestampChangeDirection != 0) {
                canvas.translate(AndroidUtilities.dp(8) + AndroidUtilities.dp(16) * -timestampChangeDirection * changeT, 0);
            }
            canvas.translate(0, -timestampLabel[1].getHeight() / 2f);
            timestampLabelPaint.setAlpha((int) (255 * (1f - transitionProgress) * (1f - changeT) * timestampsAppearing));
            timestampLabel[1].draw(canvas);
            canvas.restore();
        }
        if (timestampLabel[0] != null) {
            canvas.save();
            if (timestampChangeDirection != 0) {
                canvas.translate(AndroidUtilities.dp(8) + AndroidUtilities.dp(16) * timestampChangeDirection * (1f - changeT), 0);
            }
            canvas.translate(0, -timestampLabel[0].getHeight() / 2f);
            timestampLabelPaint.setAlpha((int) (255 * (1f - transitionProgress) * changeT * timestampsAppearing));
            timestampLabel[0].draw(canvas);
            canvas.restore();
        }
        canvas.restore();
    }

    private StaticLayout makeStaticLayout(CharSequence text, int width) {
        if (timestampLabelPaint == null) {
            timestampLabelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            timestampLabelPaint.setTextSize(AndroidUtilities.dp(12));
            timestampLabelPaint.setColor(0xffffffff);
        }
        if (text == null) {
            text = "";
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return StaticLayout.Builder.obtain(text, 0, text.length(), timestampLabelPaint, width)
                    .setMaxLines(1)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .setEllipsizedWidth(Math.min(AndroidUtilities.dp(400), width))
                    .build();
        } else {
            return new StaticLayout(
                text,
                0,
                text.length(),
                timestampLabelPaint,
                width,
                Layout.Alignment.ALIGN_CENTER,
                1,
                0,
                false,
                TextUtils.TruncateAt.END,
                Math.min(AndroidUtilities.dp(400), (int) width)
            );
        }
    }

    private void setPaintColor(int color, float alpha) {
        if (alpha < 1f) {
            color = ColorUtils.setAlphaComponent(color, (int) (Color.alpha(color) * alpha));
        }
        paint.setColor(color);
    }
}

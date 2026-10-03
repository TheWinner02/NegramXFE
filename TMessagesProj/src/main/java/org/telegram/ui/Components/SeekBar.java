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
import android.os.SystemClock;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.Collections;

import xyz.nextalone.nagram.ui.UIStyleEngine;

public class SeekBar {

    public interface SeekBarDelegate {
        void onSeekBarDrag(float progress);
        default void onSeekBarContinuousDrag(float progress) {

        }
        default void onSeekBarPressed() {}
        default void onSeekBarReleased() {}

        default boolean isSeekBarDragAllowed() {
            return true;
        }

        default boolean reverseWaveform() {
            return false;
        }
    }

    private static Paint paint;
    private static int thumbWidth;
    private float thumbProgress;
    private int thumbX = 0;
    private int draggingThumbX = 0;
    private int thumbDX = 0;
    private boolean pressed = false;
    private int width;
    private int height;
    private SeekBarDelegate delegate;
    private int backgroundColor;
    private int cacheColor;
    private int circleColor;
    private int progressColor;
    private int backgroundSelectedColor;
    private RectF rect = new RectF();
    private int lineHeight = AndroidUtilities.dp(2);
    private boolean selected;
    private float bufferedProgress;
    private float currentRadius;
    private long lastUpdateTime;
    private View parentView;
    private float alpha = 1f;
    private boolean wavyProgressActive;

    public SeekBar(View parent) {
        if (paint == null) {
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        }
        parentView = parent;
        thumbWidth = AndroidUtilities.dp(24);
        currentRadius = AndroidUtilities.dp(6);
    }

    public void setParent(View parent) {
        parentView = parent;
    }

    public void setDelegate(SeekBarDelegate seekBarDelegate) {
        delegate = seekBarDelegate;
    }

    private float iosLensProgress = 0f;
    private android.animation.ValueAnimator iosLensAnimator;

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

    public boolean onTouch(int action, float x, float y) {
        if (action == MotionEvent.ACTION_DOWN) {
            if (UIStyleEngine.isIosLiquidGlass()) {
                animateIosLens(true);
            }
            int additionWidth = (height - thumbWidth) / 2;
            if (x >= -additionWidth && x <= width + additionWidth && y >= 0 && y <= height) {
                if (!(thumbX - additionWidth <= x && x <= thumbX + thumbWidth + additionWidth)) {
                    thumbX = (int) x - thumbWidth / 2;
                    if (thumbX < 0) {
                        thumbX = 0;
                    } else if (thumbX > width - thumbWidth) {
                        thumbX = width - thumbWidth;
                    }
                }
                pressed = true;
                draggingThumbX = thumbX;
                thumbDX = (int) (x - thumbX);
                return true;
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (UIStyleEngine.isIosLiquidGlass()) {
                animateIosLens(false);
            }
            if (pressed) {
                thumbX = draggingThumbX;
                if (action == MotionEvent.ACTION_UP && delegate != null) {
                    delegate.onSeekBarDrag((float) thumbX / (float) (width - thumbWidth));
                }
                pressed = false;
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

    public void setColors(int background, int cache, int progress, int circle, int selected) {
        backgroundColor = background;
        cacheColor = cache;
        circleColor = circle;
        progressColor = progress;
        backgroundSelectedColor = selected;
    }

    public void setAlpha(float alpha) {
        this.alpha = alpha;
    }

    public void setProgress(float progress) {
        thumbProgress = progress;
        thumbX = (int) Math.ceil((width - thumbWidth) * thumbProgress);
        if (thumbX < 0) {
            thumbX = 0;
        } else if (thumbX > width - thumbWidth) {
            thumbX = width - thumbWidth;
        }
    }

    public void setBufferedProgress(float value) {
        bufferedProgress = value;
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
        if (width == w && height == h) {
            return;
        }
        width = w;
        height = h;
        setProgress(thumbProgress);
    }

    public int getWidth() {
        return width - thumbWidth;
    }

    public void setLineHeight(int value) {
        lineHeight = value;
    }

    public void setWavyProgressActive(boolean value) {
        if (wavyProgressActive != value) {
            wavyProgressActive = value;
            if (parentView != null) {
                parentView.invalidate();
            }
        }
    }

    public void draw(Canvas canvas) {
        if (alpha <= 0) {
            return;
        }
        if (alpha < 1) {
            canvas.saveLayerAlpha(0, 0, width, height, (int) (255 * alpha), Canvas.ALL_SAVE_FLAG);
        }
        int effectiveLineHeight = UIStyleEngine.isIosLiquidGlass() ? Math.round(AndroidUtilities.dpf2(4.5f)) : (UIStyleEngine.isMaterial3Expressive() ? Math.max(lineHeight, AndroidUtilities.dp(5)) : lineHeight);
        rect.set(thumbWidth / 2, height / 2 - effectiveLineHeight / 2, width - thumbWidth / 2, height / 2 + effectiveLineHeight / 2);
        paint.setColor(selected ? backgroundSelectedColor : backgroundColor);
        drawProgressBar(canvas, rect, paint, false);
        if (bufferedProgress > 0) {
            paint.setColor(selected ? backgroundSelectedColor : cacheColor);
            rect.set(thumbWidth / 2, height / 2 - effectiveLineHeight / 2, thumbWidth / 2 + bufferedProgress * (width - thumbWidth), height / 2 + effectiveLineHeight / 2);
            drawProgressBar(canvas, rect, paint, false);
        }
        rect.set(thumbWidth / 2, height / 2 - effectiveLineHeight / 2, thumbWidth / 2 + (pressed ? draggingThumbX : thumbX), height / 2 + effectiveLineHeight / 2);
        paint.setColor(progressColor);
        drawProgressBar(canvas, rect, paint, wavyProgressActive);
        paint.setColor(circleColor);

        if (UIStyleEngine.isIosLiquidGlass()) {
            drawIosGlassThumb(canvas);
        } else {
            int newRad = UIStyleEngine.isMaterial3Expressive() ? AndroidUtilities.dp(pressed ? 9 : 7) : AndroidUtilities.dp(pressed ? 8 : 6);
            if (currentRadius != newRad) {
                long newUpdateTime = SystemClock.elapsedRealtime();
                long dt = newUpdateTime - lastUpdateTime;
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

            canvas.drawCircle((pressed ? draggingThumbX : thumbX) + thumbWidth / 2, height / 2, currentRadius, paint);
        }

        if (alpha < 1) {
            canvas.restore();
        }

        updateTimestampAnimation();
    }

    private void drawIosGlassThumb(Canvas canvas) {
        float cx = (pressed ? draggingThumbX : thumbX) + thumbWidth / 2f;
        float cy = height / 2f;
        boolean isDay = Theme.isCurrentThemeDay();

        float restW = AndroidUtilities.dpf2(28f);
        float restH = AndroidUtilities.dpf2(18f);
        float lensW = AndroidUtilities.dpf2(46f);
        float lensH = AndroidUtilities.dpf2(30f);

        float curW = AndroidUtilities.lerp(restW, lensW, iosLensProgress);
        float curH = AndroidUtilities.lerp(restH, lensH, iosLensProgress);
        float curRad = curH / 2f;

        // --- A. SOLID WHITE REST CAPSULE LAYER ---
        if (iosLensProgress < 1.0f) {
            float whiteAlpha = 1.0f - iosLensProgress;

            // Soft ambient drop shadows
            paint.setColor(ColorUtils.setAlphaComponent(0x000000, (int) (0x35 * whiteAlpha)));
            rect.set(cx - curW / 2f, cy - curH / 2f + AndroidUtilities.dpf2(1.5f), cx + curW / 2f, cy + curH / 2f + AndroidUtilities.dpf2(1.5f));
            canvas.drawRoundRect(rect, curRad, curRad, paint);

            paint.setColor(ColorUtils.setAlphaComponent(0x000000, (int) (0x15 * whiteAlpha)));
            rect.set(cx - curW / 2f, cy - curH / 2f + AndroidUtilities.dpf2(2.5f), cx + curW / 2f, cy + curH / 2f + AndroidUtilities.dpf2(2.5f));
            canvas.drawRoundRect(rect, curRad, curRad, paint);

            // Pristine solid white capsule body
            paint.setColor(ColorUtils.setAlphaComponent(0xFFFFFFFF, (int) (0xFF * whiteAlpha)));
            rect.set(cx - curW / 2f, cy - curH / 2f, cx + curW / 2f, cy + curH / 2f);
            canvas.drawRoundRect(rect, curRad, curRad, paint);
        }

        // --- B. TRANSLUCENT LIQUID GLASS LENS LAYER ---
        if (iosLensProgress > 0.0f) {
            float lensAlpha = iosLensProgress;

            // Expanded soft shadow
            paint.setColor(ColorUtils.setAlphaComponent(0x000000, (int) (0x45 * lensAlpha)));
            rect.set(cx - curW / 2f, cy - curH / 2f + AndroidUtilities.dpf2(2.5f), cx + curW / 2f, cy + curH / 2f + AndroidUtilities.dpf2(2.5f));
            canvas.drawRoundRect(rect, curRad, curRad, paint);

            // Translucent tinted glass body
            int glassTint = isDay ? ColorUtils.setAlphaComponent(0xFFFFFF, (int) (0x45 * lensAlpha))
                    : ColorUtils.setAlphaComponent(0x18181A, (int) (0x80 * lensAlpha));
            paint.setColor(glassTint);
            rect.set(cx - curW / 2f, cy - curH / 2f, cx + curW / 2f, cy + curH / 2f);
            canvas.drawRoundRect(rect, curRad, curRad, paint);

            // Glass dome specular reflection on upper half
            paint.setShader(new LinearGradient(
                    cx, cy - curH / 2f, cx, cy,
                    ColorUtils.setAlphaComponent(0xFFFFFF, (int) (0x45 * lensAlpha)), 0x00FFFFFF, Shader.TileMode.CLAMP
            ));
            rect.set(cx - curW / 2f + AndroidUtilities.dpf2(1f), cy - curH / 2f + AndroidUtilities.dpf2(0.8f), cx + curW / 2f - AndroidUtilities.dpf2(1f), cy);
            canvas.drawRoundRect(rect, curRad, curRad, paint);
            paint.setShader(null);

            // Curved caustic / refractive glass rim
            rect.set(cx - curW / 2f, cy - curH / 2f, cx + curW / 2f, cy + curH / 2f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(AndroidUtilities.dpf2(1.2f));
            int causticColor = ColorUtils.blendARGB(progressColor != 0 ? progressColor : 0xFF007AFF, Color.WHITE, 0.45f);
            LinearGradient rimGrad = new LinearGradient(
                    cx - curW / 2f, cy, cx + curW / 2f, cy,
                    ColorUtils.setAlphaComponent(causticColor, (int) (0x99 * lensAlpha)),
                    ColorUtils.setAlphaComponent(0xFFFFFF, (int) (0x30 * lensAlpha)),
                    Shader.TileMode.CLAMP
            );
            paint.setShader(rimGrad);
            canvas.drawRoundRect(rect, curRad, curRad, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    protected void onTimestampUpdate(URLSpanNoUnderline link) {

    }

    private ArrayList<Pair<Float, URLSpanNoUnderline>> timestamps;
    private CharSequence lastCaption;
    private long lastVideoDuration;

    private float timestampsAppearing = 0;
    private long lastTimestampsAppearingUpdate;
    private final float TIMESTAMP_GAP = 1f;
    private static float[] tmpRadii;
    private static Path tmpPath;

    private int currentTimestamp = -1, lastTimestamp = -1;
    private StaticLayout[] timestampLabel;
    private TextPaint timestampLabelPaint;
    private float timestampChangeT = 1;
    private int timestampChangeDirection;
    private long lastTimestampUpdate;
    private float lastWidth = -1;

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

    public void updateTimestamps(MessageObject messageObject, Long videoDuration) {
        if (messageObject == null) {
            clearTimestamps();
            return;
        }
        if (videoDuration == null) {
            videoDuration = (long) messageObject.getDuration() * 1000L;
        }
        if (videoDuration == null || videoDuration < 0) {
            clearTimestamps();
            return;
        }
        CharSequence text = messageObject.caption;
        if (messageObject.isYouTubeVideo()) {
            if (messageObject.youtubeDescription == null && messageObject.messageOwner.media.webpage.description != null) {
                messageObject.youtubeDescription = SpannableString.valueOf(messageObject.messageOwner.media.webpage.description);
                MessageObject.addUrlsByPattern(messageObject.isOut(), messageObject.youtubeDescription, false, 3, (int) (long) videoDuration, false);
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
            try {
                URLSpanNoUnderline link = links[i];
                if (link != null && link.getURL() != null && link.label != null && link.getURL().startsWith("audio?")) {
                    Integer seconds = Utilities.parseInt(link.getURL().substring(6));
                    if (seconds != null && seconds >= 0) {
                        float position = seconds * 1000L / (float) videoDuration;
                        String label = link.label;
                        SpannableStringBuilder builder = new SpannableStringBuilder(label);
                        Emoji.replaceEmoji(builder, timestampLabelPaint.getFontMetricsInt(), false);
                        timestamps.add(new Pair<>(position, link));
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
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

    private void drawProgressBar(Canvas canvas, RectF rect, Paint paint, boolean wavy) {
        float radius = thumbWidth / 2f;
        if (timestamps == null || timestamps.isEmpty()) {
            if (wavy && UIStyleEngine.isMaterial3Expressive()) {
                M3WavyProgress.drawLinear(
                        canvas,
                        rect,
                        paint,
                        SystemClock.uptimeMillis() * 0.09f,
                        AndroidUtilities.dpf2(1.4f),
                        AndroidUtilities.dp(24));
                return;
            }
            canvas.drawRoundRect(rect, radius, radius, paint);
        } else {
            float lineWidth = rect.bottom - rect.top;
            float left = thumbWidth / 2f;
            float right = width - thumbWidth / 2f;
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

    private void updateTimestampAnimation() {
        if (timestamps == null || timestamps.isEmpty()) {
            return;
        }

        float progress = (pressed ? draggingThumbX : thumbX) / (float) (width - thumbWidth);

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

        float left = thumbWidth / 2f;
        float right = width - thumbWidth / 2f;
        float rightPadded = right;
        float width = Math.abs(left - rightPadded) - AndroidUtilities.dp(16 + 50);

        lastWidth = width;

        if (timestampIndex != currentTimestamp) {
            if (pressed) {
                AndroidUtilities.vibrateCursor(parentView);
            }
            currentTimestamp = timestampIndex;
            if (currentTimestamp >= 0 && currentTimestamp < timestamps.size()) {
                onTimestampUpdate(timestamps.get(currentTimestamp).second);
            }
        }
        if (timestampChangeT < 1f) {
            long tx = Math.min(17, Math.abs(SystemClock.elapsedRealtime() - lastTimestampUpdate));
            float duration = timestamps.size() > 8 ? 160f : 220f;
            timestampChangeT = Math.min(timestampChangeT + tx / duration, 1);
            if (parentView != null) {
                parentView.invalidate();
            }
            lastTimestampUpdate = SystemClock.elapsedRealtime();
        }
        if (timestampsAppearing < 1f) {
            long tx = Math.min(17, Math.abs(SystemClock.elapsedRealtime() - lastTimestampUpdate));
            timestampsAppearing = Math.min(timestampsAppearing + tx / 200f, 1);
            if (parentView != null) {
                parentView.invalidate();
            }
            lastTimestampsAppearingUpdate = SystemClock.elapsedRealtime();
        }
    }
}

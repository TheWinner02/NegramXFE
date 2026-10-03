/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.MotionEvent;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.util.StateSet;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.Keep;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.BaseCell;

import me.vkryl.android.animator.BoolAnimator;

import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.helpers.MonetHelper;
import xyz.nextalone.nagram.NaConfig;
import xyz.nextalone.nagram.ui.M3ColorRoles;

public class Switch extends View {
    private final BoolAnimator animatorIconVisibility = new BoolAnimator(this, CubicBezierInterpolator.EASE_OUT_QUINT, 380L, true);

    public static final int SWITCH_STYLE_DEFAULT = 0;
    public static final int SWITCH_STYLE_MODERN = 1;
    public static final int SWITCH_STYLE_MD3 = 2;
    public static final int SWITCH_STYLE_IOS_GLASS = 3;
    private int separateTrackColorKey = -1;
    private int lastCheckColor = Integer.MIN_VALUE;
    private final Paint googleBorderPaint;
    private final Drawable checkDrawable;

    private RectF rectF;

    private float progress;
    private ObjectAnimator checkAnimator;
    private ObjectAnimator iconAnimator;
    private float touchLensProgress = 0f;
    private ValueAnimator touchLensAnimator;
    private float transitLensProgress = 0f;
    private ValueAnimator transitLensAnimator;
    private boolean isIosDragging = false;
    private float touchStartX, touchStartY;

    private boolean attachedToWindow;
    private boolean isChecked;
    private Paint paint;
    private Paint paint2;

    private int drawIconType;
    private float iconProgress = 1.0f;

    private OnCheckedChangeListener onCheckedChangeListener;

    private int trackColorKey = Theme.key_fill_RedNormal;
    private int trackCheckedColorKey = Theme.key_switch2TrackChecked;
    private int thumbColorKey = Theme.key_windowBackgroundWhite;
    private int thumbCheckedColorKey = Theme.key_windowBackgroundWhite;

    private Drawable iconDrawable;
    private int lastIconColor;

    private boolean drawRipple;
    private RippleDrawable rippleDrawable;
    private Paint ripplePaint;
    private int[] pressedState = new int[]{android.R.attr.state_enabled, android.R.attr.state_pressed};
    private int colorSet;

    private boolean bitmapsCreated;
    private Bitmap[] overlayBitmap;
    private Canvas[] overlayCanvas;
    private Bitmap overlayMaskBitmap;
    private Canvas overlayMaskCanvas;
    private float overlayCx;
    private float overlayCy;
    private float overlayRad;
    private Paint overlayEraserPaint;
    private Paint overlayMaskPaint;

    private Theme.ResourcesProvider resourcesProvider;

    private int overrideColorProgress;

    public interface OnCheckedChangeListener {
        void onCheckedChanged(Switch view, boolean isChecked);
    }

    public Switch(Context context) {
        this(context, null);
    }

    public Switch(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        rectF = new RectF();

        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint2.setStyle(Paint.Style.STROKE);
        paint2.setStrokeCap(Paint.Cap.ROUND);
        paint2.setStrokeWidth(AndroidUtilities.dp(2));

        googleBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        googleBorderPaint.setStyle(Paint.Style.STROKE);
        googleBorderPaint.setStrokeCap(Paint.Cap.ROUND);
        googleBorderPaint.setStrokeWidth(AndroidUtilities.dp(1));
        checkDrawable = ContextCompat.getDrawable(context, R.drawable.floating_check).mutate();
        checkDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(trackCheckedColorKey, resourcesProvider), PorterDuff.Mode.MULTIPLY));

        setHapticFeedbackEnabled(!NekoConfig.disableVibration.Bool());
    }

    @Keep
    public void setProgress(float value) {
        if (progress == value) {
            return;
        }
        progress = value;
        invalidate();
    }

    @Keep
    public float getProgress() {
        return progress;
    }

    @Keep
    public void setIconProgress(float value) {
        if (iconProgress == value) {
            return;
        }
        iconProgress = value;
        invalidate();
    }

    @Keep
    public float getIconProgress() {
        return iconProgress;
    }

    private void cancelCheckAnimator() {
        if (checkAnimator != null) {
            checkAnimator.cancel();
            checkAnimator = null;
        }
        if (transitLensAnimator != null) {
            transitLensAnimator.cancel();
            transitLensAnimator = null;
            transitLensProgress = 0f;
        }
    }

    private void cancelIconAnimator() {
        if (iconAnimator != null) {
            iconAnimator.cancel();
            iconAnimator = null;
        }
    }

    public void setDrawIconType(int type) {
        drawIconType = type;
    }

    public void setDrawRipple(boolean value) {
        if (Build.VERSION.SDK_INT < 21 || value == drawRipple) {
            return;
        }
        drawRipple = value;

        if (rippleDrawable == null) {
            ripplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            ripplePaint.setColor(0xffffffff);
            Drawable maskDrawable;
            if (Build.VERSION.SDK_INT >= 23) {
                maskDrawable = null;
            } else {
                maskDrawable = new Drawable() {
                    @Override
                    public void draw(Canvas canvas) {
                        android.graphics.Rect bounds = getBounds();
                        canvas.drawCircle(bounds.centerX(), bounds.centerY(), AndroidUtilities.dp(18), ripplePaint);
                    }

                    @Override
                    public void setAlpha(int alpha) {

                    }

                    @Override
                    public void setColorFilter(ColorFilter colorFilter) {

                    }

                    @Override
                    public int getOpacity() {
                        return PixelFormat.UNKNOWN;
                    }
                };
            }
            ColorStateList colorStateList = new ColorStateList(
                new int[][]{StateSet.WILD_CARD},
                new int[]{0}
            );
            rippleDrawable = new BaseCell.RippleDrawableSafe(colorStateList, null, maskDrawable);
            if (Build.VERSION.SDK_INT >= 23) {
                rippleDrawable.setRadius(AndroidUtilities.dp(xyz.nextalone.nagram.ui.UIStyleEngine.isMaterial3Expressive() ? 24 : 18));
            }
            rippleDrawable.setCallback(this);
        }
        if (isChecked && colorSet != 2 || !isChecked && colorSet != 1) {
            int color = Theme.getColor(isChecked ? Theme.key_switchTrackBlueSelectorChecked : Theme.key_switchTrackBlueSelector, resourcesProvider);
            color = processColor(color);
            ColorStateList colorStateList = new ColorStateList(
                new int[][]{StateSet.WILD_CARD},
                new int[]{color}
            );
            rippleDrawable.setColor(colorStateList);
            colorSet = isChecked ? 2 : 1;
        }
        if (Build.VERSION.SDK_INT >= 28 && value) {
            rippleDrawable.setHotspot(isChecked ? 0 : AndroidUtilities.dp(100), AndroidUtilities.dp(18));
        }
        rippleDrawable.setState(value ? pressedState : StateSet.NOTHING);
        invalidate();
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return super.verifyDrawable(who) || rippleDrawable != null && who == rippleDrawable;
    }

    protected int processColor(int color) {
        return color;
    }

    public void setColors(int track, int trackChecked, int thumb, int thumbChecked) {
        trackColorKey = track;
        trackCheckedColorKey = trackChecked;
        thumbColorKey = thumb;
        thumbCheckedColorKey = thumbChecked;
    }

    private void animateToCheckedState(boolean newCheckedState) {
        boolean isIos = xyz.nextalone.nagram.ui.UIStyleEngine.isIosLiquidGlass();
        checkAnimator = ObjectAnimator.ofFloat(this, "progress", newCheckedState ? 1 : 0);
        checkAnimator.setDuration(isIos ? 260 : (xyz.nextalone.nagram.ui.UIStyleEngine.isMaterial3Expressive() ? 260 : 200));
        checkAnimator.setInterpolator(isIos ? CubicBezierInterpolator.DEFAULT : CubicBezierInterpolator.EASE_OUT_QUINT);
        checkAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                checkAnimator = null;
            }
        });
        checkAnimator.start();

        if (isIos) {
            if (transitLensAnimator != null) {
                transitLensAnimator.cancel();
            }
            transitLensAnimator = ValueAnimator.ofFloat(0f, 1f, 0f);
            transitLensAnimator.setDuration(260);
            transitLensAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
            transitLensAnimator.addUpdateListener(animation -> {
                transitLensProgress = (float) animation.getAnimatedValue();
                invalidate();
            });
            transitLensAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    transitLensProgress = 0f;
                    transitLensAnimator = null;
                    invalidate();
                }
            });
            transitLensAnimator.start();
        }
    }

    private void animateTouchLens(boolean active) {
        if (touchLensAnimator != null) {
            touchLensAnimator.cancel();
        }
        touchLensAnimator = ValueAnimator.ofFloat(touchLensProgress, active ? 1f : 0f);
        touchLensAnimator.setDuration(active ? 150 : 200);
        touchLensAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
        touchLensAnimator.addUpdateListener(animation -> {
            touchLensProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        touchLensAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                touchLensProgress = active ? 1f : 0f;
                touchLensAnimator = null;
                invalidate();
            }
        });
        touchLensAnimator.start();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!xyz.nextalone.nagram.ui.UIStyleEngine.isIosLiquidGlass() || !isEnabled()) {
            return super.onTouchEvent(event);
        }
        int action = event.getAction();
        if (action == MotionEvent.ACTION_DOWN) {
            touchStartX = event.getX();
            touchStartY = event.getY();
            isIosDragging = false;
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }
            animateTouchLens(true);
            return true;
        } else if (action == MotionEvent.ACTION_MOVE) {
            float dx = Math.abs(event.getX() - touchStartX);
            float dy = Math.abs(event.getY() - touchStartY);
            if (!isIosDragging && (dx > AndroidUtilities.touchSlop || dy > AndroidUtilities.touchSlop)) {
                if (dx > dy) {
                    isIosDragging = true;
                } else {
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(false);
                    }
                    animateTouchLens(false);
                    return false;
                }
            }
            if (isIosDragging) {
                int width = Math.min(dp(51), getMeasuredWidth());
                int x = (getMeasuredWidth() - width) / 2;
                float restThumbW = dpf2(28f);
                float thumbLeft = x + dpf2(2f) + restThumbW / 2f;
                float thumbRight = x + width - dpf2(2f) - restThumbW / 2f;
                float newProgress = (event.getX() - thumbLeft) / (thumbRight - thumbLeft);
                setProgress(Math.max(0f, Math.min(1f, newProgress)));
            }
            return true;
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            animateTouchLens(false);
            if (action == MotionEvent.ACTION_UP) {
                boolean newState = isIosDragging ? (progress > 0.5f) : !isChecked;
                setChecked(newState, true);
                if (onCheckedChangeListener != null) {
                    onCheckedChangeListener.onCheckedChanged(this, isChecked);
                }
            } else {
                setChecked(isChecked, true);
            }
            isIosDragging = false;
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(false);
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    private void animateIcon(boolean newCheckedState) {
        iconAnimator = ObjectAnimator.ofFloat(this, "iconProgress", newCheckedState ? 1 : 0);
        iconAnimator.setDuration(200);
        iconAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                iconAnimator = null;
            }
        });
        iconAnimator.start();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attachedToWindow = true;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attachedToWindow = false;
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        onCheckedChangeListener = listener;
    }

    public void setChecked(boolean checked, boolean animated) {
        setChecked(checked, drawIconType, animated);
    }

    public void setChecked(boolean checked, int iconType, boolean animated) {
        if (checked != isChecked) {
            isChecked = checked;
            com.exteragram.messenger.utils.system.VibratorUtils.vibrateToggle(this, checked);
            if (attachedToWindow && animated) {
                animateToCheckedState(checked);
            } else {
                cancelCheckAnimator();
                setProgress(checked ? 1.0f : 0.0f);
            }
            if (onCheckedChangeListener != null) {
                onCheckedChangeListener.onCheckedChanged(this, checked);
            }
        }
        setDrawIconType(iconType, animated);
    }

    public void setIcon(int icon) {
        if (icon != 0) {
            iconDrawable = getResources().getDrawable(icon).mutate();
            if (iconDrawable != null) {
                iconDrawable.setColorFilter(new PorterDuffColorFilter(lastIconColor = Theme.getColor(isChecked ? trackCheckedColorKey : trackColorKey, resourcesProvider), PorterDuff.Mode.MULTIPLY));
            }
        } else {
            iconDrawable = null;
        }
        invalidate();
    }

    public void setIconVisible(boolean visible, boolean animated) {
        animatorIconVisibility.setValue(visible, animated);
    }

    public void setDrawIconType(int iconType, boolean animated) {
        if (drawIconType != iconType) {
            drawIconType = iconType;
            if (attachedToWindow && animated) {
                animateIcon(iconType == 0);
            } else {
                cancelIconAnimator();
                setIconProgress(iconType == 0 ? 1.0f : 0.0f);
            }
        }
    }

    public boolean hasIcon() {
        return iconDrawable != null;
    }

    public boolean isChecked() {
        return isChecked;
    }

    public void setOverrideColor(int override) {
        if (overrideColorProgress == override) {
            return;
        }
        if (overlayBitmap == null) {
            try {
                overlayBitmap = new Bitmap[2];
                overlayCanvas = new Canvas[2];
                for (int a = 0; a < 2; a++) {
                    overlayBitmap[a] = Bitmap.createBitmap(getMeasuredWidth(), getMeasuredHeight(), Bitmap.Config.ARGB_8888);
                    overlayCanvas[a] = new Canvas(overlayBitmap[a]);
                }
                overlayMaskBitmap = Bitmap.createBitmap(getMeasuredWidth(), getMeasuredHeight(), Bitmap.Config.ARGB_8888);
                overlayMaskCanvas = new Canvas(overlayMaskBitmap);

                overlayEraserPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                overlayEraserPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

                overlayMaskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                overlayMaskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
                bitmapsCreated = true;
            } catch (Throwable e) {
                return;
            }
        }
        if (!bitmapsCreated) {
            return;
        }
        overrideColorProgress = override;
        overlayCx = 0;
        overlayCy = 0;
        overlayRad = 0;
        invalidate();
    }

    public void setOverrideColorProgress(float cx, float cy, float rad) {
        overlayCx = cx;
        overlayCy = cy;
        overlayRad = rad;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (getVisibility() != VISIBLE) {
            return;
        }

        int switchStyle = NaConfig.INSTANCE.getSwitchStyle().Int();
        if (xyz.nextalone.nagram.ui.UIStyleEngine.isMaterial3Expressive()) {
            switchStyle = SWITCH_STYLE_MD3;
        } else if (xyz.nextalone.nagram.ui.UIStyleEngine.isIosLiquidGlass()) {
            switchStyle = SWITCH_STYLE_IOS_GLASS;
        }
        if (switchStyle == SWITCH_STYLE_IOS_GLASS) {
            drawIosGlassSwitch(canvas);
            return;
        }
        if (switchStyle != SWITCH_STYLE_DEFAULT) {
            drawCustomSwitch(canvas, switchStyle);
            return;
        }

        int width = AndroidUtilities.dp(31);
        int thumb = AndroidUtilities.dp(20);
        int x = (getMeasuredWidth() - width) / 2;
        float y = (getMeasuredHeight() - AndroidUtilities.dpf2(14)) / 2;
        int tx = x + AndroidUtilities.dp(7) + (int) (AndroidUtilities.dp(17) * progress);
        int ty = getMeasuredHeight() / 2;


        int color1;
        int color2;
        float colorProgress;
        int r1;
        int r2;
        int g1;
        int g2;
        int b1;
        int b2;
        int a1;
        int a2;
        int red;
        int green;
        int blue;
        int alpha;
        int color;

        for (int a = 0; a < 2; a++) {
            if (a == 1 && overrideColorProgress == 0) {
                continue;
            }
            Canvas canvasToDraw = a == 0 ? canvas : overlayCanvas[0];

            if (a == 1) {
                overlayBitmap[0].eraseColor(0);
                paint.setColor(0xff000000);
                overlayMaskCanvas.drawRect(0, 0, overlayMaskBitmap.getWidth(), overlayMaskBitmap.getHeight(), paint);
                overlayMaskCanvas.drawCircle(overlayCx - getX(), overlayCy - getY(), overlayRad, overlayEraserPaint);
            }
            if (overrideColorProgress == 1) {
                colorProgress = a == 0 ? 0 : 1;
            } else if (overrideColorProgress == 2) {
                colorProgress = a == 0 ? 1 : 0;
            } else {
                colorProgress = progress;
            }

            color1 = processColor(Theme.getColor(trackColorKey, resourcesProvider));
            color2 = processColor(Theme.getColor(trackCheckedColorKey, resourcesProvider));
            if (a == 0 && iconDrawable != null && lastIconColor != (isChecked ? color2 : color1)) {
                iconDrawable.setColorFilter(new PorterDuffColorFilter(lastIconColor = (isChecked ? color2 : color1), PorterDuff.Mode.MULTIPLY));
            }

            r1 = Color.red(color1);
            r2 = Color.red(color2);
            g1 = Color.green(color1);
            g2 = Color.green(color2);
            b1 = Color.blue(color1);
            b2 = Color.blue(color2);
            a1 = Color.alpha(color1);
            a2 = Color.alpha(color2);

            red = (int) (r1 + (r2 - r1) * colorProgress);
            green = (int) (g1 + (g2 - g1) * colorProgress);
            blue = (int) (b1 + (b2 - b1) * colorProgress);
            alpha = (int) (a1 + (a2 - a1) * colorProgress);
            color = ((alpha & 0xff) << 24) | ((red & 0xff) << 16) | ((green & 0xff) << 8) | (blue & 0xff);
            paint.setColor(color);
            paint2.setColor(color);

            rectF.set(x, y, x + width, y + AndroidUtilities.dpf2(14));
            canvasToDraw.drawRoundRect(rectF, AndroidUtilities.dpf2(7), AndroidUtilities.dpf2(7), paint);
            canvasToDraw.drawCircle(tx, ty, AndroidUtilities.dpf2(10), paint);

            if (a == 0 && rippleDrawable != null) {
                rippleDrawable.setBounds(tx - AndroidUtilities.dp(18), ty - AndroidUtilities.dp(18), tx + AndroidUtilities.dp(18), ty + AndroidUtilities.dp(18));
                rippleDrawable.draw(canvasToDraw);
            } else if (a == 1) {
                canvasToDraw.drawBitmap(overlayMaskBitmap, 0, 0, overlayMaskPaint);
            }
        }
        if (overrideColorProgress != 0) {
            canvas.drawBitmap(overlayBitmap[0], 0, 0, null);
        }

        for (int a = 0; a < 2; a++) {
            if (a == 1 && overrideColorProgress == 0) {
                continue;
            }
            Canvas canvasToDraw = a == 0 ? canvas : overlayCanvas[1];

            if (a == 1) {
                overlayBitmap[1].eraseColor(0);
            }
            if (overrideColorProgress == 1) {
                colorProgress = a == 0 ? 0 : 1;
            } else if (overrideColorProgress == 2) {
                colorProgress = a == 0 ? 1 : 0;
            } else {
                colorProgress = progress;
            }

            color1 = Theme.getColor(thumbColorKey, resourcesProvider);
            color2 = processColor(Theme.getColor(thumbCheckedColorKey, resourcesProvider));
            r1 = Color.red(color1);
            r2 = Color.red(color2);
            g1 = Color.green(color1);
            g2 = Color.green(color2);
            b1 = Color.blue(color1);
            b2 = Color.blue(color2);
            a1 = Color.alpha(color1);
            a2 = Color.alpha(color2);

            red = (int) (r1 + (r2 - r1) * colorProgress);
            green = (int) (g1 + (g2 - g1) * colorProgress);
            blue = (int) (b1 + (b2 - b1) * colorProgress);
            alpha = (int) (a1 + (a2 - a1) * colorProgress);
            paint.setColor(((alpha & 0xff) << 24) | ((red & 0xff) << 16) | ((green & 0xff) << 8) | (blue & 0xff));

            canvasToDraw.drawCircle(tx, ty, AndroidUtilities.dp(8), paint);

            if (a == 0) {
                if (iconDrawable != null) {
                    final float factor = animatorIconVisibility.getFloatValue();
                    if (factor > 0) {
                        final boolean needScale = factor < 1;
                        if (needScale) {
                            canvas.save();
                            canvas.scale(factor, factor, tx, ty);
                        }
                        iconDrawable.setBounds(tx - iconDrawable.getIntrinsicWidth() / 2, ty - iconDrawable.getIntrinsicHeight() / 2, tx + iconDrawable.getIntrinsicWidth() / 2, ty + iconDrawable.getIntrinsicHeight() / 2);
                        iconDrawable.draw(canvasToDraw);
                        if (needScale) {
                            canvas.restore();
                        }
                    }
                } else if (drawIconType == 1) {
                    tx -= AndroidUtilities.dp(10.8f) - AndroidUtilities.dp(1.3f) * progress;
                    ty -= AndroidUtilities.dp(8.5f) - AndroidUtilities.dp(0.5f) * progress;
                    int startX2 = (int) AndroidUtilities.dpf2(4.6f) + tx;
                    int startY2 = (int) (AndroidUtilities.dpf2(9.5f) + ty);
                    int endX2 = startX2 + AndroidUtilities.dp(2);
                    int endY2 = startY2 + AndroidUtilities.dp(2);

                    int startX = (int) AndroidUtilities.dpf2(7.5f) + tx;
                    int startY = (int) AndroidUtilities.dpf2(5.4f) + ty;
                    int endX = startX + AndroidUtilities.dp(7);
                    int endY = startY + AndroidUtilities.dp(7);

                    startX = (int) (startX + (startX2 - startX) * progress);
                    startY = (int) (startY + (startY2 - startY) * progress);
                    endX = (int) (endX + (endX2 - endX) * progress);
                    endY = (int) (endY + (endY2 - endY) * progress);
                    canvasToDraw.drawLine(startX, startY, endX, endY, paint2);

                    startX = (int) AndroidUtilities.dpf2(7.5f) + tx;
                    startY = (int) AndroidUtilities.dpf2(12.5f) + ty;
                    endX = startX + AndroidUtilities.dp(7);
                    endY = startY - AndroidUtilities.dp(7);
                    canvasToDraw.drawLine(startX, startY, endX, endY, paint2);
                } else if (drawIconType == 2 || iconAnimator != null) {
                    paint2.setAlpha((int) (255 * (1.0f - iconProgress)));
                    canvasToDraw.drawLine(tx, ty, tx, ty - AndroidUtilities.dp(5), paint2);
                    canvasToDraw.save();
                    canvasToDraw.rotate(-90 * iconProgress, tx, ty);
                    canvasToDraw.drawLine(tx, ty, tx + AndroidUtilities.dp(4), ty, paint2);
                    canvasToDraw.restore();
                }
            }
            if (a == 1) {
                canvasToDraw.drawBitmap(overlayMaskBitmap, 0, 0, overlayMaskPaint);
            }
        }
        if (overrideColorProgress != 0) {
            canvas.drawBitmap(overlayBitmap[1], 0, 0, null);
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.Switch");
        info.setCheckable(true);
        info.setChecked(isChecked);
        //info.setContentDescription(isChecked ? LocaleController.getString(R.string.NotificationsOn) : LocaleController.getString(R.string.NotificationsOff));
    }

    public void setSeparateTrackColorKey(int separateTrackColorKey) {
        if (this.separateTrackColorKey == separateTrackColorKey) {
            return;
        }
        this.separateTrackColorKey = separateTrackColorKey;
        invalidate();
    }

    private void drawCustomSwitch(Canvas canvas, int switchStyle) {
        boolean isMd3 = switchStyle == SWITCH_STYLE_MD3;
        boolean isModern = switchStyle == SWITCH_STYLE_MODERN;
        boolean isM3Expressive = isMd3 && xyz.nextalone.nagram.ui.UIStyleEngine.isMaterial3Expressive();
        int width = isM3Expressive ? Math.min(dp(52), getMeasuredWidth()) : dp(36);
        int x = (getMeasuredWidth() - width) / 2;
        float trackHeight = isM3Expressive ? Math.min(dpf2(32), getMeasuredHeight()) : dpf2(20);
        float y = (getMeasuredHeight() - trackHeight) / 2;
        int tx = isM3Expressive ? x + dp(16) + (int) ((width - dp(32)) * progress) : x + dp(7) + (int) (dp(18) * progress);
        int thumbTx = isM3Expressive ? tx : Utilities.clamp(tx, x + width + dp(2), x + dp(10));
        int ty = getMeasuredHeight() / 2;

        int color1;
        int color2;
        int color;

        int trackCheckedFillKey = trackCheckedColorKey;
        int thumbCheckedKey = thumbCheckedColorKey;
        boolean useM3RoleColors = isM3Expressive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && MonetHelper.useMonetMd3Colors();
        if (!isM3Expressive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && MonetHelper.useMonetMd3Colors()) {
            trackCheckedFillKey = Theme.key_dialogRoundCheckBox;
            thumbCheckedKey = Theme.getActiveTheme().isMonetNight()
                    ? Theme.key_statisticChartRipple // a1_800
                    : Theme.key_chat_outInstant; // a1_10
        }

        float iconVisibilityFactor = animatorIconVisibility.getFloatValue();
        boolean hasVisibleIcon = iconDrawable != null && iconVisibilityFactor > 0;
        boolean isMd3PermissionStyle = isMd3 && trackColorKey == Theme.key_fill_RedNormal && (drawIconType == 1 || hasVisibleIcon);
        boolean isModernPermissionStyle = isModern && trackColorKey == Theme.key_fill_RedNormal && drawIconType == 1;
        boolean shouldDrawModernOffIcon = isModern && (hasVisibleIcon || isModernPermissionStyle);

        int trackColor = processColor(Theme.getColor(trackColorKey, resourcesProvider));
        int md3PermissionTrackColor = trackColor;
        if (isMd3PermissionStyle && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && MonetHelper.useMonetMd3Colors()) {
            md3PermissionTrackColor = processColor(MonetHelper.harmonizeColor(md3PermissionTrackColor));
        }
        int md3OffTrackFillColor = 0;
        if (isMd3) {
            md3OffTrackFillColor = isM3Expressive ? processColor(Theme.blendOver(
                    Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider),
                    Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon, resourcesProvider), Theme.isCurrentThemeDay() ? 0.16f : 0.18f)
            )) : processColor(Theme.blendOver(
                    Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider),
                    Theme.multAlpha(Theme.getColor(trackColorKey, resourcesProvider), Theme.isCurrentThemeDay() ? 0.2f : 0.1f)
            ));
        }
        int md3CheckedTrackColor = processColor(Theme.getColor(trackCheckedFillKey, resourcesProvider));
        int md3UncheckedOutlineColor = processColor(Theme.getColor(trackColorKey, resourcesProvider));
        int md3UncheckedThumbColor = isM3Expressive ? processColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon, resourcesProvider)) : processColor(Theme.getColor(trackColorKey, resourcesProvider));
        int md3CheckedThumbColor = processColor(Theme.getColor(thumbCheckedKey, resourcesProvider));
        int md3OffIconColor = md3OffTrackFillColor;
        int md3CheckIconColor = Theme.getColor(trackCheckedFillKey, resourcesProvider);
        if (useM3RoleColors) {
            md3OffTrackFillColor = processColor(resolveM3RoleColor(M3ColorRoles.Role.SURFACE_CONTAINER_HIGHEST, md3OffTrackFillColor));
            md3CheckedTrackColor = processColor(M3ColorRoles.primary(md3CheckedTrackColor));
            md3UncheckedOutlineColor = processColor(resolveM3RoleColor(M3ColorRoles.Role.OUTLINE, md3UncheckedOutlineColor));
            md3UncheckedThumbColor = processColor(resolveM3RoleColor(M3ColorRoles.Role.OUTLINE, md3UncheckedThumbColor));
            md3CheckedThumbColor = processColor(resolveM3RoleColor(M3ColorRoles.Role.ON_PRIMARY, md3CheckedThumbColor));
            md3OffIconColor = processColor(resolveM3RoleColor(M3ColorRoles.Role.ON_SURFACE_VARIANT, md3OffIconColor));
            md3CheckIconColor = md3CheckedTrackColor;
        }

        for (int a = 0; a < 2; a++) {
            if (a == 1 && overrideColorProgress == 0) {
                continue;
            }
            Canvas canvasToDraw = a == 0 ? canvas : overlayCanvas[0];

            if (a == 1) {
                overlayBitmap[0].eraseColor(0);
                paint.setColor(0xff000000);
                overlayMaskCanvas.drawRect(0, 0, overlayMaskBitmap.getWidth(), overlayMaskBitmap.getHeight(), paint);
                overlayMaskCanvas.drawCircle(overlayCx - getX(), overlayCy - getY(), overlayRad, overlayEraserPaint);
            }
            float colorProgress = getLayerColorProgress(a);

            int originalColor1;
            color1 = originalColor1 = isMd3 && !isMd3PermissionStyle ? md3OffTrackFillColor : isMd3PermissionStyle ? md3PermissionTrackColor : trackColor;
            color2 = md3CheckedTrackColor;

            if (!isMd3) {
                color1 = separateTrackColorKey >= 0 ? processColor(Theme.getColor(separateTrackColorKey, resourcesProvider)) : Color.TRANSPARENT;
            }

            if (a == 0 && iconDrawable != null && !isMd3 && !shouldDrawModernOffIcon && lastIconColor != (isChecked ? color2 : color1)) {
                iconDrawable.setColorFilter(new PorterDuffColorFilter(lastIconColor = (isChecked ? color2 : color1), PorterDuff.Mode.MULTIPLY));
            }

            color = lerpColor(color1, color2, colorProgress);
            paint.setColor(color);
            paint2.setColor(color);

            rectF.set(x, y, x + width, y + trackHeight);
            canvasToDraw.drawRoundRect(rectF, trackHeight / 2, trackHeight / 2, paint);

            color1 = isMd3 ? (isMd3PermissionStyle ? md3PermissionTrackColor : processColor(Theme.getColor(trackColorKey, resourcesProvider))) : originalColor1;
            if (isM3Expressive && !isMd3PermissionStyle) {
                color1 = md3UncheckedOutlineColor;
            }
            googleBorderPaint.setColor(lerpColor(color1, color2, colorProgress));
            googleBorderPaint.setStrokeWidth(isM3Expressive ? dpf2(2) : dp(1));

            canvasToDraw.drawRoundRect(rectF, trackHeight / 2, trackHeight / 2, googleBorderPaint);

            if (a == 0 && rippleDrawable != null) {
                int rippleRadius = isM3Expressive ? dp(24) : dp(18);
                rippleDrawable.setBounds(thumbTx - rippleRadius, ty - rippleRadius, thumbTx + rippleRadius, ty + rippleRadius);
                rippleDrawable.draw(canvasToDraw);
            } else if (a == 1) {
                canvasToDraw.drawBitmap(overlayMaskBitmap, 0, 0, overlayMaskPaint);
            }
        }
        if (overrideColorProgress != 0) {
            canvas.drawBitmap(overlayBitmap[0], 0, 0, null);
        }

        for (int a = 0; a < 2; a++) {
            if (a == 1 && overrideColorProgress == 0) {
                continue;
            }
            Canvas canvasToDraw = a == 0 ? canvas : overlayCanvas[1];

            if (a == 1) {
                overlayBitmap[1].eraseColor(0);
            }
            float colorProgress = getLayerColorProgress(a);

            int thumbUncheckedKey = isMd3 ? (isMd3PermissionStyle ? thumbCheckedKey : trackColorKey) : trackColorKey;
            color1 = isM3Expressive && !isMd3PermissionStyle ? md3UncheckedThumbColor : Theme.getColor(thumbUncheckedKey, resourcesProvider);
            color2 = md3CheckedThumbColor;
            paint.setColor(lerpColor(color1, color2, colorProgress));

            float thumbRadius = isM3Expressive ? dpf2(8 + 4 * progress) : dp(isMd3 ? 8 : shouldDrawModernOffIcon ? 7 + progress : 6 + 2 * progress);
            canvasToDraw.drawCircle(thumbTx, ty, thumbRadius, paint);

            if (isMd3 || shouldDrawModernOffIcon) {
                if (isMd3) {
                    int iconColor = isMd3PermissionStyle ? md3PermissionTrackColor : md3OffIconColor;
                    if (hasVisibleIcon) {
                        if (lastIconColor != iconColor) {
                            iconDrawable.setColorFilter(new PorterDuffColorFilter(lastIconColor = iconColor, PorterDuff.Mode.MULTIPLY));
                        }
                        final boolean needScale = iconVisibilityFactor < 1;
                        if (needScale) {
                            canvasToDraw.save();
                            canvasToDraw.scale(iconVisibilityFactor, iconVisibilityFactor, thumbTx, ty);
                        }
                        drawCenteredDrawable(canvasToDraw, iconDrawable, thumbTx, ty, 0.8f, (int) (255 * (1.0f - progress)));
                        if (needScale) {
                            canvasToDraw.restore();
                        }
                    } else {
                        drawCross(canvasToDraw, thumbTx, ty, iconColor, 1.0f - progress, isM3Expressive ? 2.5f : 3);
                    }
                    int checkColor = md3CheckIconColor;
                    if (lastCheckColor != checkColor) {
                        checkDrawable.setColorFilter(new PorterDuffColorFilter(checkColor, PorterDuff.Mode.MULTIPLY));
                        lastCheckColor = checkColor;
                    }
                    int iconWidth = checkDrawable.getIntrinsicWidth() / 2;
                    int iconHeight = checkDrawable.getIntrinsicHeight() / 2;
                    checkDrawable.setBounds(thumbTx - iconWidth / 2, ty - iconHeight / 2, thumbTx + iconWidth / 2, ty + iconHeight / 2);
                    checkDrawable.setAlpha((int) (255 * progress));
                    checkDrawable.draw(canvasToDraw);
                } else {
                    int iconColor = Theme.getColor(thumbColorKey, resourcesProvider);
                    if (hasVisibleIcon) {
                        if (lastIconColor != iconColor) {
                            iconDrawable.setColorFilter(new PorterDuffColorFilter(lastIconColor = iconColor, PorterDuff.Mode.MULTIPLY));
                        }
                        final boolean needScale = iconVisibilityFactor < 1;
                        if (needScale) {
                            canvasToDraw.save();
                            canvasToDraw.scale(iconVisibilityFactor, iconVisibilityFactor, thumbTx, ty);
                        }
                        drawCenteredDrawable(canvasToDraw, iconDrawable, thumbTx, ty, 0.7f, (int) (255 * (1.0f - progress)));
                        if (needScale) {
                            canvasToDraw.restore();
                        }
                    } else {
                        drawCross(canvasToDraw, thumbTx, ty, iconColor, 1.0f - progress, 2.5f);
                    }
                }
            }
            if (a == 1) {
                canvasToDraw.drawBitmap(overlayMaskBitmap, 0, 0, overlayMaskPaint);
            }
        }
        if (overrideColorProgress != 0) {
            canvas.drawBitmap(overlayBitmap[1], 0, 0, null);
        }
    }

    private float getLayerColorProgress(int layer) {
        if (overrideColorProgress == 1) {
            return layer == 0 ? 0 : 1;
        } else if (overrideColorProgress == 2) {
            return layer == 0 ? 1 : 0;
        }
        return progress;
    }

    private int resolveM3RoleColor(M3ColorRoles.Role role, int fallbackColor) {
        int color = M3ColorRoles.get(role, fallbackColor);
        return ColorUtils.setAlphaComponent(color, Color.alpha(fallbackColor));
    }

    private int lerpColor(int color1, int color2, float progress) {
        int red = (int) (Color.red(color1) + (Color.red(color2) - Color.red(color1)) * progress);
        int green = (int) (Color.green(color1) + (Color.green(color2) - Color.green(color1)) * progress);
        int blue = (int) (Color.blue(color1) + (Color.blue(color2) - Color.blue(color1)) * progress);
        int alpha = (int) (Color.alpha(color1) + (Color.alpha(color2) - Color.alpha(color1)) * progress);
        return ((alpha & 0xff) << 24) | ((red & 0xff) << 16) | ((green & 0xff) << 8) | (blue & 0xff);
    }

    private void drawCenteredDrawable(Canvas canvas, Drawable drawable, int cx, int cy, float scale, int alpha) {
        int iconWidth = (int) (drawable.getIntrinsicWidth() * scale);
        int iconHeight = (int) (drawable.getIntrinsicHeight() * scale);
        drawable.setBounds(cx - iconWidth / 2, cy - iconHeight / 2, cx + iconWidth / 2, cy + iconHeight / 2);
        drawable.setAlpha(alpha);
        drawable.draw(canvas);
        drawable.setAlpha(255);
    }

    private void drawCross(Canvas canvas, int cx, int cy, int color, float alpha, float sizeDp) {
        int oldAlpha = paint2.getAlpha();
        float oldStrokeWidth = paint2.getStrokeWidth();
        paint2.setColor(color);
        paint2.setAlpha((int) (255 * alpha));
        paint2.setStrokeWidth(dpf2(1.5f));
        int crossSize = dp(sizeDp);
        canvas.drawLine(cx - crossSize, cy - crossSize, cx + crossSize, cy + crossSize, paint2);
        canvas.drawLine(cx + crossSize, cy - crossSize, cx - crossSize, cy + crossSize, paint2);
        paint2.setStrokeWidth(oldStrokeWidth);
        paint2.setAlpha(oldAlpha);
    }

    private void drawIosGlassSwitch(Canvas canvas) {
        int width = Math.min(dp(51), getMeasuredWidth());
        float trackHeight = Math.min(dpf2(31), getMeasuredHeight());
        int x = (getMeasuredWidth() - width) / 2;
        float y = (getMeasuredHeight() - trackHeight) / 2;
        float trackRadius = trackHeight / 2f;

        float restThumbW = dpf2(28f);
        float restThumbH = dpf2(27f);
        float thumbLeft = x + dpf2(2f) + restThumbW / 2f;
        float thumbRight = x + width - dpf2(2f) - restThumbW / 2f;
        float thumbTx = AndroidUtilities.lerp(thumbLeft, thumbRight, progress);
        float ty = getMeasuredHeight() / 2f;

        boolean isDay = Theme.isCurrentThemeDay();
        int onTrackBaseColor = processColor(Theme.getColor(trackCheckedColorKey, resourcesProvider));
        int onTrackColor = onTrackBaseColor != 0 ? onTrackBaseColor : 0xFF34C759;
        int offTrackColor = isDay ? 0xFFE9E9EA : 0xFF39393D;

        float effectiveLensProgress = Math.max(touchLensProgress, transitLensProgress);

        for (int a = 0; a < 2; a++) {
            if (a == 1 && overrideColorProgress == 0) {
                continue;
            }
            Canvas canvasToDraw = a == 0 ? canvas : overlayCanvas[0];
            if (a == 1) {
                overlayBitmap[0].eraseColor(0);
                paint.setColor(0xff000000);
                overlayMaskCanvas.drawRect(0, 0, overlayMaskBitmap.getWidth(), overlayMaskBitmap.getHeight(), paint);
                overlayMaskCanvas.drawCircle(overlayCx - getX(), overlayCy - getY(), overlayRad, overlayEraserPaint);
            }

            float colorProgress = getLayerColorProgress(a);
            rectF.set(x, y, x + width, y + trackHeight);

            // 1. Base Track Fill: Rich iOS Green / Soft Inactive Gray
            int curTrackColor = lerpColor(offTrackColor, onTrackColor, colorProgress);
            paint.setColor(curTrackColor);
            canvasToDraw.drawRoundRect(rectF, trackRadius, trackRadius, paint);

            // 2. Inset subtle depth stroke
            googleBorderPaint.setColor(isDay ? 0x14000000 : 0x22000000);
            googleBorderPaint.setStrokeWidth(dpf2(1f));
            canvasToDraw.drawRoundRect(rectF, trackRadius, trackRadius, googleBorderPaint);

            if (a == 1) {
                canvasToDraw.drawBitmap(overlayMaskBitmap, 0, 0, overlayMaskPaint);
            }
        }
        if (overrideColorProgress != 0) {
            canvas.drawBitmap(overlayBitmap[0], 0, 0, null);
        }

        // Draw iOS Liquid Glass Thumb (Capsule at rest -> Expands to Translucent Glass Lens when interactive)
        for (int a = 0; a < 2; a++) {
            if (a == 1 && overrideColorProgress == 0) {
                continue;
            }
            Canvas canvasToDraw = a == 0 ? canvas : overlayCanvas[1];
            if (a == 1) {
                overlayBitmap[1].eraseColor(0);
            }

            float colorProgress = getLayerColorProgress(a);

            // Interpolated size between rest capsule and expanded glass lens
            float expandedThumbW = dpf2(38f);
            float expandedThumbH = dpf2(33f);
            float curW = AndroidUtilities.lerp(restThumbW, expandedThumbW, effectiveLensProgress);
            float curH = AndroidUtilities.lerp(restThumbH, expandedThumbH, effectiveLensProgress);
            float curRad = curH / 2f;

            // --- 1. SOLID WHITE REST LAYER (fades out as lens opens) ---
            if (effectiveLensProgress < 1.0f) {
                float whiteAlpha = 1.0f - effectiveLensProgress;

                // Soft ambient drop shadows
                paint.setColor(ColorUtils.setAlphaComponent(0x000000, (int) (0x30 * whiteAlpha)));
                rectF.set(thumbTx - curW / 2f, ty - curH / 2f + dpf2(1.5f), thumbTx + curW / 2f, ty + curH / 2f + dpf2(1.5f));
                canvasToDraw.drawRoundRect(rectF, curRad, curRad, paint);

                paint.setColor(ColorUtils.setAlphaComponent(0x000000, (int) (0x15 * whiteAlpha)));
                rectF.set(thumbTx - curW / 2f, ty - curH / 2f + dpf2(2.5f), thumbTx + curW / 2f, ty + curH / 2f + dpf2(2.5f));
                canvasToDraw.drawRoundRect(rectF, curRad, curRad, paint);

                // Pristine solid white capsule body
                paint.setColor(ColorUtils.setAlphaComponent(0xFFFFFFFF, (int) (0xFF * whiteAlpha)));
                rectF.set(thumbTx - curW / 2f, ty - curH / 2f, thumbTx + curW / 2f, ty + curH / 2f);
                canvasToDraw.drawRoundRect(rectF, curRad, curRad, paint);
            }

            // --- 2. TRANSLUCENT LIQUID GLASS LENS LAYER (blooms during touch/transit) ---
            if (effectiveLensProgress > 0.0f) {
                float lensAlpha = effectiveLensProgress;

                // Expanded soft shadow
                paint.setColor(ColorUtils.setAlphaComponent(0x000000, (int) (0x45 * lensAlpha)));
                rectF.set(thumbTx - curW / 2f, ty - curH / 2f + dpf2(2.5f), thumbTx + curW / 2f, ty + curH / 2f + dpf2(2.5f));
                canvasToDraw.drawRoundRect(rectF, curRad, curRad, paint);

                // Translucent tinted glass body (allows underlying track to show through)
                int glassTint = isDay ? ColorUtils.setAlphaComponent(0xFFFFFF, (int) (0x55 * lensAlpha))
                        : ColorUtils.setAlphaComponent(0x18181A, (int) (0x85 * lensAlpha));
                paint.setColor(glassTint);
                rectF.set(thumbTx - curW / 2f, ty - curH / 2f, thumbTx + curW / 2f, ty + curH / 2f);
                canvasToDraw.drawRoundRect(rectF, curRad, curRad, paint);

                // Glass dome specular reflection on upper half
                paint.setShader(new LinearGradient(
                        thumbTx, ty - curH / 2f, thumbTx, ty,
                        ColorUtils.setAlphaComponent(0xFFFFFF, (int) (0x45 * lensAlpha)), 0x00FFFFFF, Shader.TileMode.CLAMP
                ));
                rectF.set(thumbTx - curW / 2f + dpf2(1f), ty - curH / 2f + dpf2(0.8f), thumbTx + curW / 2f - dpf2(1f), ty);
                canvasToDraw.drawRoundRect(rectF, curRad, curRad, paint);
                paint.setShader(null);

                // Curved caustic / refractive glass rim
                rectF.set(thumbTx - curW / 2f, ty - curH / 2f, thumbTx + curW / 2f, ty + curH / 2f);
                googleBorderPaint.setStrokeWidth(dpf2(1.2f));
                if (!isDay) {
                    int causticColor = ColorUtils.blendARGB(onTrackColor, Color.WHITE, 0.45f);
                    int rimAccent = lerpColor(0x25FFFFFF, causticColor, colorProgress);
                    LinearGradient rimGrad = new LinearGradient(
                            thumbTx - curW / 2f, ty + curH / 2f, thumbTx + curW / 2f, ty - curH / 2f,
                            ColorUtils.setAlphaComponent(rimAccent, (int) (0x99 * lensAlpha)),
                            ColorUtils.setAlphaComponent(0xFFFFFF, (int) (0x45 * lensAlpha)),
                            Shader.TileMode.CLAMP
                    );
                    googleBorderPaint.setShader(rimGrad);
                } else {
                    LinearGradient rimGrad = new LinearGradient(
                            thumbTx, ty - curH / 2f, thumbTx, ty + curH / 2f,
                            ColorUtils.setAlphaComponent(0xFFFFFF, (int) (0x70 * lensAlpha)),
                            ColorUtils.setAlphaComponent(0x000000, (int) (0x20 * lensAlpha)),
                            Shader.TileMode.CLAMP
                    );
                    googleBorderPaint.setShader(rimGrad);
                }
                canvasToDraw.drawRoundRect(rectF, curRad, curRad, googleBorderPaint);
                googleBorderPaint.setShader(null);
            }

            if (iconDrawable != null && effectiveLensProgress < 0.9f) {
                int iconColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon, resourcesProvider);
                if (lastIconColor != iconColor) {
                    iconDrawable.setColorFilter(new PorterDuffColorFilter(lastIconColor = iconColor, PorterDuff.Mode.MULTIPLY));
                }
                float iconAlpha = (1.0f - effectiveLensProgress) * (1.0f - progress);
                drawCenteredDrawable(canvasToDraw, iconDrawable, (int) thumbTx, (int) ty, 0.7f, (int) (255 * iconAlpha));
            }

            if (a == 1) {
                canvasToDraw.drawBitmap(overlayMaskBitmap, 0, 0, overlayMaskPaint);
            }
        }
        if (overrideColorProgress != 0) {
            canvas.drawBitmap(overlayBitmap[1], 0, 0, null);
        }
    }
}

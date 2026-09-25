package org.telegram.ui.ActionBar;

import static org.telegram.messenger.AndroidUtilities.dp;
import org.telegram.messenger.AndroidUtilities;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;

public class ActionBarMenuSubItem extends FrameLayout {

    public AnimatedEmojiSpan.TextViewEmojis textView;
    public TextView subtextView;
    public RLottieImageView imageView;
    public boolean checkViewLeft;
    public CheckBox2 checkView;
    private ImageView rightIcon;
    private BackupImageView backupImageView;

    private int textColor;
    private int iconColor;
    private PorterDuff.Mode iconColorMode;
    private int selectorColor;
    private Paint iosDividerPaint;

    int selectorRad = 12;
    boolean top;
    boolean bottom;

    private int itemHeight = 48;
    protected final Theme.ResourcesProvider resourcesProvider;
    public Runnable openSwipeBackLayout;

    public ActionBarMenuSubItem(Context context, boolean top, boolean bottom) {
        this(context, false, top, bottom);
    }

    public ActionBarMenuSubItem(Context context, boolean needCheck, boolean top, boolean bottom) {
        this(context, needCheck ? 1 : 0, top, bottom, null);
    }

    public ActionBarMenuSubItem(Context context, boolean top, boolean bottom, Theme.ResourcesProvider resourcesProvider) {
        this(context, 0, top, bottom, resourcesProvider);
    }

    public ActionBarMenuSubItem(Context context, boolean needCheck, boolean top, boolean bottom, Theme.ResourcesProvider resourcesProvider) {
        this(context, needCheck ? 1 : 0, top, bottom, resourcesProvider);
    }

    public ActionBarMenuSubItem(Context context, int needCheck, boolean top, boolean bottom, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        this.top = top;
        this.bottom = bottom;

        boolean floatingBars = xyz.nextalone.nagram.ui.UIStyleEngine.shouldUseFloatingBars();
        textColor = getThemedColor(Theme.key_actionBarDefaultSubmenuItem);
        iconColor = getThemedColor(Theme.key_actionBarDefaultSubmenuItemIcon);
        iconColorMode = PorterDuff.Mode.MULTIPLY;
        selectorColor = getThemedColor(Theme.key_dialogButtonSelector);

        updateBackground();
        setPadding(dp(floatingBars ? 14 : 18), 0, dp(floatingBars ? 14 : 18), 0);

        imageView = new RLottieImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN));
        addView(imageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 40, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT)));

        textView = new AnimatedEmojiSpan.TextViewEmojis(context);
        textView.setLines(1);
        textView.setSingleLine(true);
        textView.setGravity(Gravity.LEFT);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setTextColor(textColor);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, floatingBars ? 15 : 16);
        if (floatingBars) {
            textView.setTypeface(AndroidUtilities.bold());
        }
        addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL));

        checkViewLeft = LocaleController.isRTL;
        makeCheckView(needCheck);
        org.telegram.ui.Components.ScaleStateListAnimator.apply(this);
    }

    @Override
    public boolean performClick() {
        com.exteragram.messenger.utils.system.VibratorUtils.vibrateClick(this);
        return super.performClick();
    }

    public void makeCheckView(int needCheck) {
        if (needCheck > 0) {
            checkView = new CheckBox2(getContext(), 26, resourcesProvider);
            checkView.setDrawUnchecked(false);
            checkView.setColor(-1, -1, Theme.key_actionBarDefaultSubmenuItem);
            checkView.setDrawBackgroundAsArc(-1);
            if (needCheck == 1) {
                checkViewLeft = !LocaleController.isRTL;
                addView(checkView, LayoutHelper.createFrame(26, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT)));
                textView.setPadding(!LocaleController.isRTL ? dp(34) : 0, 0, !LocaleController.isRTL ? 0 : dp(34), 0);
            } else {
                addView(checkView, LayoutHelper.createFrame(26, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT)));
                textView.setPadding(LocaleController.isRTL ? dp(34) : 0, 0, LocaleController.isRTL ? 0 : dp(34), 0);
            }
        }
    }

    public void setEmojiCacheType(int cacheType) {
        textView.setCacheType(cacheType);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(dp(itemHeight), View.MeasureSpec.EXACTLY));
        if (expandIfMultiline && textView.getLayout().getLineCount() > 1) {
            super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(dp(itemHeight + 8), View.MeasureSpec.EXACTLY));
        }
    }

    public void setItemHeight(int itemHeight) {
        this.itemHeight = itemHeight;
    }

    public void setChecked(boolean checked) {
        if (checkView == null) {
            return;
        }
        checkView.setChecked(checked, true);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setEnabled(isEnabled());
        if (checkView != null && checkView.isChecked()) {
            info.setCheckable(true);
            info.setChecked(checkView.isChecked());
            info.setClassName("android.widget.CheckBox");
        }
    }

    public void setCheckColor(int colorKey) {
        checkView.setColor(-1, -1, colorKey);
    }

    public void setRightIcon(int icon) {
        setRightIcon(icon, null);
    }

    public void setRightIcon(int icon, OnClickListener listener) {
        if (rightIcon == null) {
            rightIcon = new ImageView(getContext());
            rightIcon.setScaleType(ImageView.ScaleType.CENTER);
            rightIcon.setColorFilter(iconColor, PorterDuff.Mode.MULTIPLY);
            if (LocaleController.isRTL) {
                rightIcon.setScaleX(-1);
            }
            addView(rightIcon, LayoutHelper.createFrame(24, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT)));
        }
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) textView.getLayoutParams();
        if (LocaleController.isRTL) {
            layoutParams.leftMargin = rightIcon != null ? dp(32) : 0;
        } else {
            layoutParams.rightMargin = rightIcon != null ? dp(32) : 0;
        }
        textView.setLayoutParams(layoutParams);
        setPadding(dp(LocaleController.isRTL ? listener != null ? 0 : 8 : 18), 0, dp(LocaleController.isRTL ? 18 : listener != null ? 0 : 8), 0);
        if (icon == 0) {
            rightIcon.setVisibility(View.GONE);
        } else {
            rightIcon.setVisibility(View.VISIBLE);
            rightIcon.setImageResource(icon);
            if (listener != null) {
                rightIcon.getLayoutParams().width = dp(40);
                rightIcon.setOnClickListener(listener);
                rightIcon.setBackground(Theme.createRadSelectorDrawable(selectorColor, 6, 0, 0, 6));
            }
        }
    }

    public void setTextAndIcon(CharSequence text, int icon) {
        setTextAndIcon(text, icon, null);
    }

    boolean expandIfMultiline;

    public void setMultiline() {
        setMultiline(true);
    }

    public void setMultiline(boolean changeSize) {
        textView.setLines(2);
        if (changeSize) {
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        } else {
            expandIfMultiline = true;
        }
        textView.setSingleLine(false);
        textView.setGravity(Gravity.CENTER_VERTICAL);
    }

    public void setTextAndIcon(CharSequence text, int icon, Drawable iconDrawable) {
        textView.setText(text);
        if (icon != 0 || iconDrawable != null || checkView != null) {
            if (iconDrawable != null) {
                iconResId = 0;
                imageView.setImageDrawable(iconDrawable);
            } else {
                iconResId = icon;
                imageView.setImageResource(icon);
            }
            imageView.setVisibility(VISIBLE);
            textView.setPadding(checkViewLeft ? (checkView != null ? dp(43) : 0) : dp(icon != 0 || iconDrawable != null ? 43 : 0), 0, checkViewLeft ? dp(icon != 0 || iconDrawable != null ? 43 : 0) : (checkView != null ? dp(43) : 0), 0);
        } else {
            iconResId = 0;
            imageView.setVisibility(INVISIBLE);
            textView.setPadding(0, 0, 0, 0);
        }
    }

    public void setTextAndIcon(CharSequence text, ImageLocation imageLocation, String imageFilter, Drawable thumb, Object parentObject) {
        textView.setText(text);
        textView.setPadding(checkViewLeft ? (checkView != null ? dp(43) : 0) : dp(43), 0, checkViewLeft ? dp(43) : (checkView != null ? dp(43) : 0), 0);
        if (backupImageView == null) {
            backupImageView = new BackupImageView(getContext());
            backupImageView.setRoundRadius(dp(5));
            addView(backupImageView, LayoutHelper.createFrame(28, 28, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT)));
        }
        imageView.setVisibility(INVISIBLE);
        backupImageView.setImage(imageLocation, imageFilter, thumb, parentObject);
    }


    public void setIconColorImage(int iconColor) {
        if (backupImageView != null) {
            backupImageView.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN));
        }
    }

    public void setImageSize(int widthDp, int heightDp) {
        if (backupImageView != null) {
            backupImageView.setLayoutParams(LayoutHelper.createFrame(widthDp, heightDp, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT)));
        }
    }


    public ActionBarMenuSubItem setColors(int textColor, int iconColor) {
        setTextColor(textColor);
        setIconColor(iconColor);
        return this;
    }

    public void setTextColor(int textColor) {
        if (this.textColor != textColor) {
            textView.setTextColor(this.textColor = textColor);
        }
    }

    public void setIconColor(int iconColor) {
        setIconColor(iconColor, PorterDuff.Mode.SRC_IN);
    }

    public void setIconColor(int iconColor, PorterDuff.Mode mode) {
        if (this.iconColor != iconColor || this.iconColorMode != mode) {
            imageView.setColorFilter(new PorterDuffColorFilter(this.iconColor = iconColor, this.iconColorMode = mode));
        }
    }

    private ValueAnimator enabledAnimator;
    private boolean enabled;
    public void setEnabledByColor(boolean enabled, int colorDisabled, int colorEnabled) {
        if (enabledAnimator != null) {
            enabledAnimator.cancel();
        }
        enabledAnimator = ValueAnimator.ofFloat(this.enabled ? 1.0f : 0.0f, enabled ? 1.0f : 0.0f);
        this.enabled = enabled;
        enabledAnimator.addUpdateListener(anm -> {
            final float t = (float) anm.getAnimatedValue();
            setTextColor(ColorUtils.blendARGB(colorDisabled, colorEnabled, t));
            setIconColor(ColorUtils.blendARGB(colorDisabled, colorEnabled, t));
        });
        enabledAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                final float t = enabled ? 1.0f : 0.0f;
                setTextColor(ColorUtils.blendARGB(colorDisabled, colorEnabled, t));
                setIconColor(ColorUtils.blendARGB(colorDisabled, colorEnabled, t));
            }
        });
        enabledAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        enabledAnimator.start();
    }

    public void setEnabledByColor(boolean enabled, int textColorDisabled, int iconColorDisabled, int colorEnabled) {
        if (enabledAnimator != null) {
            enabledAnimator.cancel();
        }
        enabledAnimator = ValueAnimator.ofFloat(this.enabled ? 1.0f : 0.0f, enabled ? 1.0f : 0.0f);
        this.enabled = enabled;
        enabledAnimator.addUpdateListener(anm -> {
            final float t = (float) anm.getAnimatedValue();
            setTextColor(ColorUtils.blendARGB(textColorDisabled, colorEnabled, t));
            setIconColor(ColorUtils.blendARGB(iconColorDisabled, colorEnabled, t));
        });
        enabledAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                final float t = enabled ? 1.0f : 0.0f;
                setTextColor(ColorUtils.blendARGB(textColorDisabled, colorEnabled, t));
                setIconColor(ColorUtils.blendARGB(iconColorDisabled, colorEnabled, t));
            }
        });
        enabledAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        enabledAnimator.start();
    }

    private int iconResId;
    public int getIconResId() {
        return iconResId;
    }

    public void setIcon(int resId) {
        imageView.setImageResource(iconResId = resId);
    }

    public void setIcon(Drawable drawable) {
        iconResId = 0;
        imageView.setImageDrawable(drawable);
    }

    public void setAnimatedIcon(int resId) {
        iconResId = 0;
        imageView.setAnimation(resId, 24, 24);
    }

    public void onItemShown() {
        if (imageView.getAnimatedDrawable() != null) {
            imageView.getAnimatedDrawable().start();
        }
    }

    public void setVisibility(boolean visibility) {
        setVisibility(visibility ? View.VISIBLE : View.GONE);
    }

    public void setText(CharSequence text) {
        textView.setText(text);
    }

    public void setSubtextColor(int color) {
        if (subtextView != null) {
            subtextView.setTextColor(color);
        }
    }

    public void setSubtext(CharSequence text) {
        if (subtextView == null) {
            subtextView = new TextView(getContext());
            subtextView.setLines(1);
            subtextView.setSingleLine(true);
            subtextView.setGravity(Gravity.LEFT);
            subtextView.setEllipsize(TextUtils.TruncateAt.END);
            subtextView.setTextColor(getThemedColor(Theme.key_groupcreate_sectionText));
            subtextView.setVisibility(GONE);
            subtextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            subtextView.setPadding(LocaleController.isRTL ? 0 : dp(43), 0, LocaleController.isRTL ? dp(43) : 0, 0);
            addView(subtextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, 0, 10, 0, 0));
        }
        boolean visible = !TextUtils.isEmpty(text);
        boolean oldVisible = subtextView.getVisibility() == VISIBLE;
        if (visible != oldVisible) {
            subtextView.setVisibility(visible ? VISIBLE : GONE);
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) textView.getLayoutParams();
            layoutParams.bottomMargin = visible ? dp(10) : 0;
            textView.setLayoutParams(layoutParams);
        }
        subtextView.setText(text);
    }

    public AnimatedEmojiSpan.TextViewEmojis getTextView() {
        return textView;
    }

    public ImageView getImageView() {
        return imageView;
    }

    public void setSelectorColor(int selectorColor) {
        if (this.selectorColor != selectorColor) {
            this.selectorColor = selectorColor;
            updateBackground();
        }
    }

    public void updateSelectorBackground(boolean top, boolean bottom) {
        if (this.top == top && this.bottom == bottom) {
            return;
        }
        this.top = top;
        this.bottom = bottom;
        updateBackground();
    }

    public void updateSelectorBackground(boolean top, boolean bottom, int selectorRad) {
        if (this.top == top && this.bottom == bottom && this.selectorRad == selectorRad) {
            return;
        }
        this.top = top;
        this.bottom = bottom;
        this.selectorRad = selectorRad;
        updateBackground();
    }

    private final org.telegram.ui.Components.M3PressMorphHelper pressedMorphProgress = new org.telegram.ui.Components.M3PressMorphHelper(this);
    private final android.graphics.Path morphClipPath = new android.graphics.Path();
    private final android.graphics.RectF morphClipRect = new android.graphics.RectF();
    private final float[] morphRadii = new float[8];
    private final android.graphics.Paint morphBgPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        pressedMorphProgress.setPressed(pressed);
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (xyz.nextalone.nagram.ui.UIStyleEngine.isIosLiquidGlass()) {
            float progress = pressedMorphProgress.getProgress();
            if (progress > 0 || isPressed()) {
                morphClipRect.set(dp(6), dp(2), getMeasuredWidth() - dp(6), getMeasuredHeight() - dp(2));
                float r = dp(12);
                boolean isDark = Theme.isCurrentThemeDark();
                int pressColor = isDark ? 0x24FFFFFF : 0x14000000;
                morphBgPaint.setColor(Theme.multAlpha(pressColor, Math.max(progress, isPressed() ? 1f : 0f)));
                canvas.drawRoundRect(morphClipRect, r, r, morphBgPaint);
            }
            super.dispatchDraw(canvas);
            if (!bottom) {
                if (iosDividerPaint == null) {
                    iosDividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                }
                boolean isDark = Theme.isCurrentThemeDark();
                iosDividerPaint.setColor(isDark ? 0x1AFFFFFF : 0x12000000);
                iosDividerPaint.setStrokeWidth(dp(0.66f));
                float startX = dp(54);
                float endX = getWidth() - dp(16);
                float y = getHeight() - dp(0.33f);
                canvas.drawLine(startX, y, endX, y, iosDividerPaint);
            }
        } else if (xyz.nextalone.nagram.ui.UIStyleEngine.shouldUseFloatingBars()) {
            float progress = pressedMorphProgress.getProgress();
            float topR = AndroidUtilities.lerp(top ? dp(16) : dp(4), dp(16), progress);
            float bottomR = AndroidUtilities.lerp(bottom ? dp(16) : dp(4), dp(16), progress);
            float gap = AndroidUtilities.lerp(dp(1.5f), dp(3.5f), progress);

            morphClipRect.set(dp(5), gap, getMeasuredWidth() - dp(5), getMeasuredHeight() - gap);
            morphRadii[0] = morphRadii[1] = topR;
            morphRadii[2] = morphRadii[3] = topR;
            morphRadii[4] = morphRadii[5] = bottomR;
            morphRadii[6] = morphRadii[7] = bottomR;

            morphClipPath.rewind();
            morphClipPath.addRoundRect(morphClipRect, morphRadii, android.graphics.Path.Direction.CW);

            int cardColor = Theme.isCurrentThemeDark() ? 0x24ffffff : 0x10000000;
            morphBgPaint.setColor(cardColor);
            canvas.drawPath(morphClipPath, morphBgPaint);

            if (progress > 0 || isPressed()) {
                morphBgPaint.setColor(Theme.multAlpha(selectorColor, Math.max(progress * 0.35f, isPressed() ? 0.35f : 0f)));
                canvas.drawPath(morphClipPath, morphBgPaint);
            }

            canvas.save();
            canvas.clipPath(morphClipPath);
            super.dispatchDraw(canvas);
            canvas.restore();
        } else {
            super.dispatchDraw(canvas);
        }
    }

    public void updateBackground() {
        if (xyz.nextalone.nagram.ui.UIStyleEngine.isIosLiquidGlass()) {
            setBackground(null);
        } else if (xyz.nextalone.nagram.ui.UIStyleEngine.shouldUseFloatingBars()) {
            int topR = top ? AndroidUtilities.dp(16) : AndroidUtilities.dp(4);
            int bottomR = bottom ? AndroidUtilities.dp(16) : AndroidUtilities.dp(4);
            setBackground(Theme.createRadSelectorDrawable(selectorColor, topR, bottomR));
        } else {
            setBackground(Theme.createRadSelectorDrawable(selectorColor, top ? selectorRad : 0, bottom ? selectorRad : 0));
        }
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    public CheckBox2 getCheckView() {
        return checkView;
    }

    public void openSwipeBack() {
        if (openSwipeBackLayout != null) {
            openSwipeBackLayout.run();
        }
    }

    public ImageView getRightIcon() {
        return rightIcon;
    }
}

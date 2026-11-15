package org.telegram.ui.Components.chat.buttons;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.content.Context;
import android.graphics.BlendMode;
import android.graphics.BlendModeColorFilter;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.Components.CircularProgressDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.M3ExpressiveButtonDrawable;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;

import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

public class ChatActivityBlurredRoundButton extends FrameLayout implements FactorAnimator.Target, Theme.Colorable {
    public static final int CLICK_ZONE_MARGIN = 6;
    public static final int BUTTON_SIZE = 44;

    public ChatActivityBlurredRoundButton(Context context) {
        super(context);
    }

    private static final int ANIMATOR_ID_LOADING_VISIBILITY = 0;
    private static final int ANIMATOR_ID_IS_ENABLED = 1;

    private final BoolAnimator animatorLoadingVisibility = new BoolAnimator(ANIMATOR_ID_LOADING_VISIBILITY, this, CubicBezierInterpolator.EASE_OUT_QUINT, 320);
    private final BoolAnimator animatorIsEnabled = new BoolAnimator(ANIMATOR_ID_IS_ENABLED, this, CubicBezierInterpolator.EASE_OUT_QUINT, 320, true);

    private @Nullable ImageView imageView;
    private @Nullable ImageView loadingIndicatorView;
    private CircularProgressDrawable loadingIndicatorDrawable;
    private Theme.ResourcesProvider resourcesProvider;
    private boolean m3ExpressiveTextButton;
    private M3ExpressiveButtonDrawable m3ExpressivePressedBackground;

    private final org.telegram.ui.Components.M3PressMorphHelper pressedMorphProgress = new org.telegram.ui.Components.M3PressMorphHelper(this);

    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        pressedMorphProgress.setPressed(pressed);
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (backgroundDrawable != null) {
            backgroundDrawable.setBounds(0, 0, w, h);
        }
    }

    private final android.graphics.Path morphClipPath = new android.graphics.Path();
    private final android.graphics.RectF morphRect = new android.graphics.RectF();

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (xyz.nextalone.nagram.ui.UIStyleEngine.isMaterial3Expressive()) {
            float progress = pressedMorphProgress.getProgress();
            int innerPadding = getM3ExpressiveInnerPadding();

            morphClipPath.rewind();
            morphRect.set(innerPadding, innerPadding, getMeasuredWidth() - innerPadding, getMeasuredHeight() - innerPadding);
            float currentRad = AndroidUtilities.lerp(morphRect.height() / 2f, dp(8f), progress);
            morphClipPath.addRoundRect(morphRect, currentRad, currentRad, android.graphics.Path.Direction.CW);

            if (backgroundDrawable != null) {
                backgroundDrawable.setRadius(currentRad);
            }
            if (m3ExpressivePressedBackground != null) {
                m3ExpressivePressedBackground.setMorphProgress(progress);
            }

            canvas.save();
            canvas.clipPath(morphClipPath);
            if (backgroundDrawable != null) {
                backgroundDrawable.draw(canvas);
            }
            super.draw(canvas);
            canvas.restore();
        } else {
            if (backgroundDrawable != null) {
                backgroundDrawable.draw(canvas);
            }
            super.draw(canvas);
        }
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);
    }

    public void setIcon(@DrawableRes int resId) {
        setIcon(resId, 48);
    }
    public void setIcon(@DrawableRes int resId, int size) {
        if (imageView == null) {
            if (resId == 0) {
                return;
            }

            imageView = new ImageView(getContext());
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            addView(imageView, LayoutHelper.createFrame(size, size, Gravity.CENTER));
            checkUi_IconViewVisibility();
        }

        imageView.setImageResource(resId);
    }

    public void setIconPadding(int paddingTop) {
        if (imageView != null) {
            imageView.setPadding(0, paddingTop, 0, 0);
        }
    }

    private float buttonScaleY = 1;
    public void reverseIconByY() {
        buttonScaleY = -1;
        checkUi_IconViewVisibility();
    }

    private int appliedIconColor;

    public void setIconColor(int color) {
        if (imageView == null) {
            return;
        }
        appliedIconColor = color;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            imageView.setColorFilter(new BlendModeColorFilter(color, BlendMode.SRC_IN));
        } else {
            imageView.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        setEnabled(enabled, false);
    }

    public void setEnabled(boolean enabled, boolean animated) {
        super.setEnabled(enabled);
        animatorIsEnabled.setValue(enabled, animated);
    }

    private BlurredBackgroundDrawable backgroundDrawable;
    public void setBlurredBackgroundDrawable(BlurredBackgroundDrawable drawable) {
        backgroundDrawable = drawable;
        backgroundDrawable.setPadding(getM3ExpressiveInnerPadding());
        backgroundDrawable.setRadius(getM3ExpressiveRestRadius());
    }

    public ChatActivityBlurredRoundButton setM3ExpressiveTextButton(boolean value) {
        m3ExpressiveTextButton = value;
        if (backgroundDrawable != null) {
            backgroundDrawable.setPadding(getM3ExpressiveInnerPadding());
            backgroundDrawable.setRadius(getM3ExpressiveRestRadius());
        }
        updatePressedBackground();
        invalidate();
        return this;
    }

    private int getM3ExpressiveInnerPadding() {
        if (xyz.nextalone.nagram.ui.UIStyleEngine.isMaterial3Expressive() && m3ExpressiveTextButton) {
            return dp(4);
        }
        return dp(CLICK_ZONE_MARGIN);
    }

    private int getM3ExpressiveRestRadius() {
        if (xyz.nextalone.nagram.ui.UIStyleEngine.isMaterial3Expressive()) {
            return dp(m3ExpressiveTextButton ? 24 : 22);
        }
        return dp(BUTTON_SIZE / 2f);
    }

    private void updatePressedBackground() {
        final int color = Theme.getColor(Theme.key_glass_defaultIcon, resourcesProvider);
        int pressedColor = Theme.multAlpha(color, m3ExpressiveTextButton ? .18f : .15f);
        if (xyz.nextalone.nagram.ui.UIStyleEngine.isMaterial3Expressive()) {
            m3ExpressivePressedBackground = new M3ExpressiveButtonDrawable(0, pressedColor, getM3ExpressiveRestRadius(), getM3ExpressiveInnerPadding());
            setBackground(m3ExpressivePressedBackground);
        } else {
            m3ExpressivePressedBackground = null;
            setBackground(Theme.createInsetRoundRectDrawable(pressedColor, getM3ExpressiveRestRadius(), getM3ExpressiveInnerPadding()));
        }
    }

    public void showLoading(boolean loading, boolean animated) {
        if (loadingIndicatorView == null) {
            if (!loading) {
                return;
            }

            loadingIndicatorDrawable = new CircularProgressDrawable(AndroidUtilities.dp(18), AndroidUtilities.dp(1.7f), 0xFF757575);
            loadingIndicatorDrawable.setAngleOffset(90);

            loadingIndicatorView = new ImageView(getContext());
            loadingIndicatorView.setBackground(loadingIndicatorDrawable);
            loadingIndicatorView.setVisibility(GONE);

            addView(loadingIndicatorView, LayoutHelper.createFrame(46, 46, Gravity.CENTER));
        }

        if (!animatorLoadingVisibility.getValue() && animatorLoadingVisibility.getFloatValue() == 0) {
            loadingIndicatorDrawable.reset();
        }
        animatorLoadingVisibility.setValue(loading, animated);
    }

    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
        if (id == ANIMATOR_ID_LOADING_VISIBILITY) {
            checkUi_IconViewVisibility();
            checkUi_LoadingViewVisibility();
        }
        if (id == ANIMATOR_ID_IS_ENABLED) {
            checkUi_IconViewVisibility();
            checkUi_LoadingViewVisibility();
        }
    }
    public static ChatActivityBlurredRoundButton create(
        Context context,
        BlurredBackgroundDrawableViewFactory factory,
        BlurredBackgroundColorProvider colorProvider,
        Theme.ResourcesProvider resourcesProvider
    ) {
        ChatActivityBlurredRoundButton button;

        final int color = Theme.getColor(Theme.key_glass_defaultIcon, resourcesProvider);
        button = new ChatActivityBlurredRoundButton(context);
        button.resourcesProvider = resourcesProvider;
        button.setBlurredBackgroundDrawable(factory.create(button, colorProvider));
        button.setIconColor(color);
        button.setM3ExpressiveTextButton(xyz.nextalone.nagram.ui.UIStyleEngine.isMaterial3Expressive());
        int rad = dp(22);
        int pressedColor = Theme.multAlpha(color, .15f);
        ThemeBind.selectorBackground(button, Theme.createInsetRoundRectDrawable(pressedColor, rad, dp(6)), null, ThemeBind.multAlpha(Theme.key_glass_defaultIcon, .15f), resourcesProvider);

        return button;
    }

    public static ChatActivityBlurredRoundButton create(
        Context context,
        BlurredBackgroundDrawableViewFactory factory,
        BlurredBackgroundColorProvider colorProvider,
        Theme.ResourcesProvider resourcesProvider,
        @DrawableRes int res,
        int iconSize
    ) {
        ChatActivityBlurredRoundButton button;

        final int color = Theme.getColor(Theme.key_glass_defaultIcon, resourcesProvider);
        button = new ChatActivityBlurredRoundButton(context);
        button.resourcesProvider = resourcesProvider;
        button.setBlurredBackgroundDrawable(factory.create(button, colorProvider));
        button.setIcon(res, iconSize);
        button.setIconColor(color);
        button.updatePressedBackground();
        int rad = dp(22);
        int pressedColor = Theme.multAlpha(color, .15f);
        ThemeBind.selectorBackground(button, Theme.createInsetRoundRectDrawable(pressedColor, rad, dp(6)), null, ThemeBind.multAlpha(Theme.key_glass_defaultIcon, .15f), resourcesProvider);

        return button;
    }

    @Override
    public void updateColors() {
        if (backgroundDrawable != null) {
            backgroundDrawable.updateColors();
            invalidate();
        }

        final int color = Theme.getColor(Theme.key_glass_defaultIcon, resourcesProvider);
        if (imageView != null && color != appliedIconColor) {
            setIconColor(color);
        }
        if (xyz.nextalone.nagram.ui.UIStyleEngine.isMaterial3Expressive()) {
            updatePressedBackground();
        } else {
            int rad = dp(22);
            int pressedColor = Theme.multAlpha(color, .15f);
            if (getBackground() instanceof RippleDrawable) {
                Theme.setSelectorDrawableColor(getBackground(), pressedColor, true);
            } else {
                setBackground(Theme.createInsetRoundRectDrawable(pressedColor, rad, dp(6)));
            }
        }
    }

    private void checkUi_IconViewVisibility() {
        final float visibility = 1f - animatorLoadingVisibility.getFloatValue();
        final float alpha = lerp(visibility / 2f, visibility, animatorIsEnabled.getFloatValue());

        if (imageView != null) {
            imageView.setAlpha(alpha);
            imageView.setScaleX(lerp(0.4f, 1f, visibility));
            imageView.setScaleY(lerp(0.4f, 1f, visibility) * buttonScaleY);
            imageView.setVisibility(visibility > 0 ? VISIBLE : GONE);
        }
    }

    private void checkUi_LoadingViewVisibility() {
        final float visibility = animatorLoadingVisibility.getFloatValue();
        final float alpha = lerp(visibility / 2f, visibility, animatorIsEnabled.getFloatValue());

        if (loadingIndicatorView != null) {
            loadingIndicatorView.setAlpha(alpha);
            loadingIndicatorView.setScaleX(lerp(0.4f, 1f, visibility));
            loadingIndicatorView.setScaleY(lerp(0.4f, 1f, visibility));

            final int newVisibility = visibility > 0 ? VISIBLE : GONE;
            if (loadingIndicatorView.getVisibility() != newVisibility) {
                loadingIndicatorView.setVisibility(newVisibility);
                loadingIndicatorDrawable.reset();
            }
        }
    }
}

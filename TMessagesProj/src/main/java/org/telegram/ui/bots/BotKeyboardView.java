/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.bots;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.graphics.PorterDuff;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.utils.tlutils.TLKeyboardHelper;
import org.telegram.messenger.utils.tlutils.TlUtils;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_keyboard;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.M3ExpressiveButtonDrawable;
import org.telegram.ui.Components.M3ExpressiveButtonGroup;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.chat.ChatInputViewsContainer;
import org.telegram.ui.Components.inset.InAppKeyboardInsetView;
import org.telegram.ui.Components.spoilers.SpoilersTextView;

import java.util.ArrayList;

import me.vkryl.android.animator.ListAnimator;
import me.vkryl.android.animator.ReplaceAnimator;
import me.vkryl.core.lambda.Destroyable;
import xyz.nextalone.nagram.ui.UIStyleEngine;

@SuppressLint("ViewConstructor")
public class BotKeyboardView extends LinearLayout implements InAppKeyboardInsetView, ReplaceAnimator.Callback {
    private static final int BORDER_MARGIN = 8;
    private static final int MIDDLE_MARGIN = 4;

    private final Theme.ResourcesProvider resourcesProvider;
    private final FrameLayout frameLayout;
    private TLRPC.TL_replyKeyboardMarkup botButtons;
    private BotKeyboardViewDelegate delegate;
    private int panelHeight;
    private boolean isFullSize;
    private int buttonHeight;
    private final ArrayList<Button> buttonViews = new ArrayList<>();
    private final ScrollView scrollView;
    private final boolean expressive = UIStyleEngine.isMaterial3Expressive();

    private int getButtonGap() {
        return expressive ? 2 : MIDDLE_MARGIN;
    }

    public interface BotKeyboardViewDelegate {
        void didPressedButton(TL_keyboard.KeyboardButton button);
    }

    public BotKeyboardView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        setOrientation(VERTICAL);

        scrollView = new ScrollView(context);
        scrollView.setClipToPadding(false);
        addView(scrollView);

        frameLayout = new FrameLayout(context);
        scrollView.addView(frameLayout);
        updateColors();
    }

    public void updateColors() {
        AndroidUtilities.setScrollViewEdgeEffectColor(scrollView, getThemedColor(Theme.key_chat_emojiPanelBackground));
        for (int i = 0; i < buttonViews.size(); i++) {
            buttonViews.get(i).updateColors();
        }
        invalidate();
    }

    public void setDelegate(BotKeyboardViewDelegate botKeyboardViewDelegate) {
        delegate = botKeyboardViewDelegate;
    }

    public void setPanelHeight(int height) {
        panelHeight = height;
        if (isFullSize && botButtons != null && !botButtons.rows.isEmpty()) {
            buttonHeight = calculateButtonHeight();
            final int newHeight = dp(buttonHeight);
            for (ListAnimator.Entry<ButtonsLayout> entry : animator) {
                for (int a = 0, N = entry.item.getChildCount(); a < N; a++) {
                    View v = entry.item.getChildAt(a);
                    LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) v.getLayoutParams();
                    if (layoutParams.height != newHeight) {
                        layoutParams.height = newHeight;
                        v.setLayoutParams(layoutParams);
                    }
                }
            }
        }
    }

    public void invalidateViews() {
        for (int a = 0; a < buttonViews.size(); a++) {
            buttonViews.get(a).invalidate();
        }
    }

    public boolean isFullSize() {
        return isFullSize;
    }

    public void setButtons(TLRPC.TL_replyKeyboardMarkup buttons) {
        if (TlUtils.tlEquals(buttons, botButtons)) {
            return;
        }

        botButtons = buttons;
        buttonViews.clear();

        final float offset = scrollView.getScrollY();
        for (ListAnimator.Entry<ButtonsLayout> entry : animator) {
            entry.item.setTranslationY(entry.item.getTranslationY() - offset);
        }

        scrollView.scrollTo(0, 0);

        if (buttons != null && !botButtons.rows.isEmpty()) {
            ButtonsLayout container = new ButtonsLayout(getContext());
            container.setOrientation(VERTICAL);
            container.setAlpha(0);
            frameLayout.addView(container);

            isFullSize = !buttons.resize;
            buttonHeight = calculateButtonHeight();
            for (int a = 0; a < buttons.rows.size(); a++) {
                TL_keyboard.KeyboardButtonRow row = buttons.rows.get(a);

                LinearLayout layout = new LinearLayout(getContext());
                layout.setOrientation(LinearLayout.HORIZONTAL);
                if (expressive) {
                    layout.setLayoutDirection(LocaleController.isRTL ? LAYOUT_DIRECTION_RTL : LAYOUT_DIRECTION_LTR);
                }
                container.addView(layout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, buttonHeight, BORDER_MARGIN, a == 0 ? BORDER_MARGIN : getButtonGap(), BORDER_MARGIN, a == buttons.rows.size() - 1 ? BORDER_MARGIN : 0));

                float weight = 1.0f / row.buttons.size();
                for (int b = 0; b < row.buttons.size(); b++) {
                    TL_keyboard.KeyboardButton button = row.buttons.get(b);
                    Button textView = new Button(getContext(), button, container);
                    boolean rtl = expressive && LocaleController.isRTL;
                    textView.setPositionFlags(rtl ? b == row.buttons.size() - 1 : b == 0, a == 0,
                            rtl ? b == 0 : b == row.buttons.size() - 1, a == buttons.rows.size() - 1);

                    FrameLayout frame = new FrameLayout(getContext());
                    frame.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

                    layout.addView(frame, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, weight, 0, 0, b != row.buttons.size() - 1 ? getButtonGap() : 0, 0));
                    textView.setOnClickListener(v -> delegate.didPressedButton((TL_keyboard.KeyboardButton) v.getTag()));
                    if (!expressive) {
                        ScaleStateListAnimator.apply(textView, 0.02f, 1.5f);
                    }
                    buttonViews.add(textView);

                    textView.updateColors();
                }
            }

            animator.replace(container, true);
        } else {
            animator.clear(true);
        }
    }

    private class Button extends FrameLayout {
        private final SpoilersTextView textView;
        private final ImageView icon;
        private final TL_keyboard.KeyboardButton button;
        private boolean isLeft, isTop, isRight, isBottom;
        private final M3ExpressiveButtonGroup.ChildState expressiveState;

        public Button(Context context, TL_keyboard.KeyboardButton button, ButtonsLayout container) {
            super(context);
            this.button = button;
            expressiveState = expressive ? new M3ExpressiveButtonGroup.ChildState(this, container::layoutExpressiveButtons) : null;

            textView = new SpoilersTextView(context);
            textView.allowClickSpoilers = false;
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setTypeface(AndroidUtilities.bold());
            NotificationCenter.listenEmojiLoading(textView);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
            NotificationCenter.listenEmojiLoading(textView);

            setTag(button);

            SpannableStringBuilder ssb = new SpannableStringBuilder();
            if (button.style != null && button.style.icon != 0) {
                ssb.append("* ");
                ssb.setSpan(new AnimatedEmojiSpan(button.style.icon, textView.getPaint().getFontMetricsInt()), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            ssb.append(Emoji.replaceEmoji(button.text, textView.getPaint().getFontMetricsInt(), false));

            icon = new ImageView(getContext());
            ThemeBind.colorFilter(icon, Theme.key_chat_botKeyboardButtonText, BotKeyboardView.this.resourcesProvider, PorterDuff.Mode.SRC_ATOP);
            if (TLKeyboardHelper.isButtonWebView(button)) {
                icon.setImageResource(R.drawable.bot_webview);
                icon.setVisibility(VISIBLE);
            } else {
                icon.setVisibility(GONE);
            }
            addView(icon, LayoutHelper.createFrame(12, 12, Gravity.RIGHT | Gravity.TOP, 0, 8, 8, 0));

            textView.setText(ssb);
            if (expressive) {
                textView.setGravity(Gravity.CENTER);
                textView.setPadding(dp(7), 0, dp(7), 0);
            }
        }

        @Override
        protected void drawableStateChanged() {
            super.drawableStateChanged();
            if (expressiveState != null && expressiveState.pressed != isPressed()) {
                expressiveState.pressed = isPressed();
                if (isPressed()) {
                    com.exteragram.messenger.utils.system.VibratorUtils.vibrateClick(this);
                }
                expressiveState.springAnimation.animateToFinalPosition(isPressed() ? 1f : 0f);
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            if (expressiveState != null) {
                expressiveState.springAnimation.cancel();
                expressiveState.springAnimation.setStartValue(0f);
                expressiveState.progress = 0f;
                expressiveState.pressed = false;
                if (expressiveState.drawable != null) {
                    expressiveState.drawable.setMorphProgress(0f);
                }
            }
            super.onDetachedFromWindow();
        }

        public void setPositionFlags(boolean isLeft, boolean isTop, boolean isRight, boolean isBottom) {
            this.isLeft = isLeft;
            this.isTop = isTop;
            this.isRight = isRight;
            this.isBottom = isBottom;

            updateColors();
        }

        public void updateColors() {
            final int br = dp(ChatInputViewsContainer.INPUT_KEYBOARD_RADIUS - BORDER_MARGIN);
            final int dr = dp(11);

            int color = getThemedColor(Theme.key_chat_botKeyboardButtonBackground);
            int pressed = getThemedColor(Theme.key_chat_botKeyboardButtonBackgroundPressed);
            int textColor = getThemedColor(Theme.key_chat_botKeyboardButtonText);

            if (expressive) {
                textColor = getThemedColor(Theme.key_windowBackgroundWhiteBlackText);
                color = ColorUtils.blendARGB(getThemedColor(Theme.key_windowBackgroundWhite), textColor, 0.05f);
            }

            if (button.style != null) {
                if (button.style.bg_primary) {
                    color = Theme.multAlpha(getThemedColor(Theme.key_botKeyboard_button_primary), 0.8f);
                    pressed = ColorUtils.compositeColors(getThemedColor(Theme.key_listSelector), color);
                    textColor = Color.WHITE;
                } else if (button.style.bg_danger) {
                    color = Theme.multAlpha(getThemedColor(Theme.key_botKeyboard_button_danger), 0.8f);
                    pressed = ColorUtils.compositeColors(getThemedColor(Theme.key_listSelector), color);
                    textColor = Color.WHITE;
                } else if (button.style.bg_success) {
                    color = Theme.multAlpha(getThemedColor(Theme.key_botKeyboard_button_success), 0.8f);
                    pressed = ColorUtils.compositeColors(getThemedColor(Theme.key_listSelector), color);
                    textColor = Color.WHITE;
                }
            }

            icon.setColorFilter(textColor);
            textView.setTextColor(textColor);
            if (expressive) {
                final float outer = dp(24), inner = dp(8), down = dp(16);
                // The drawable takes an overlay, unlike the stock selector's full pressed fill.
                pressed = ColorUtils.setAlphaComponent(getThemedColor(Theme.key_listSelector),
                        Math.round(Color.alpha(getThemedColor(Theme.key_listSelector)) * 0.45f));
                float tl = isLeft && isTop ? outer : inner;
                float tr = isRight && isTop ? outer : inner;
                float brRadius = isRight && isBottom ? outer : inner;
                float bl = isLeft && isBottom ? outer : inner;
                M3ExpressiveButtonDrawable drawable = new M3ExpressiveButtonDrawable(color, pressed,
                        new float[]{tl, tl, tr, tr, brRadius, brRadius, bl, bl},
                        new float[]{down, down, down, down, down, down, down, down}, 0);
                drawable.setStroke(ColorUtils.setAlphaComponent(getThemedColor(Theme.key_windowBackgroundWhiteGrayIcon), 80), dp(1));
                drawable.setMorphProgress(expressiveState.progress);
                expressiveState.drawable = drawable;
                setBackground(drawable);
                return;
            }
            setBackground(Theme.createSimpleSelectorRoundRectDrawable(
                isLeft && isTop ? br : dr,
                isRight && isTop ? br : dr,
                isRight && isBottom ? br : dr,
                isLeft && isBottom ? br : dr,
                color, pressed, pressed
            ));
        }

    }

    public int getKeyboardHeight() {
        if (botButtons == null) {
            return 0;
        }
        return isFullSize ? panelHeight : botButtons.rows.size() * dp(buttonHeight) + dp(BORDER_MARGIN * 2) + (botButtons.rows.size() - 1) * dp(getButtonGap());
    }

    private int calculateButtonHeight() {
        int minimum = expressive ? 48 : 44;
        return !isFullSize ? minimum : (int) Math.max(minimum,
                (panelHeight - dp(BORDER_MARGIN * 2) - (botButtons.rows.size() - 1) * dp(getButtonGap()))
                        / botButtons.rows.size() / AndroidUtilities.density);
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }


    private int navigationBarHeight;

    @Override
    public void applyNavigationBarHeight(int height) {
        if (navigationBarHeight == height) {
            return;
        }
        navigationBarHeight = height;

        if (scrollView.getPaddingBottom() != height) {
            scrollView.setPadding(0, 0, 0, height);
        }
        invalidate();
    }

    @Override
    public void applyInAppKeyboardAnimatedHeight(float height) {

    }

    private final GradientDrawable fadeDrawable = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, null);
    private int lastFadeColor;

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);
        float navbarAlpha = AndroidUtilities.getNavigationBarThirdButtonsFactor(navigationBarHeight);
        if (navbarAlpha > 0) {
            final int color = Theme.multAlpha(getThemedColor(Theme.key_chat_emojiPanelBackground), navbarAlpha);
            if (lastFadeColor != color) {
                fadeDrawable.setColors(new int[] {color, Theme.multAlpha(color, 0.66f), ColorUtils.setAlphaComponent(color, 0)});
                lastFadeColor = color;
            }
            fadeDrawable.setBounds(0, getMeasuredHeight() - navigationBarHeight, getMeasuredWidth(), getMeasuredHeight());
            fadeDrawable.draw(canvas);
        }
    }

    private final ReplaceAnimator<ButtonsLayout> animator = new ReplaceAnimator<>(this, CubicBezierInterpolator.EASE_OUT_QUINT, 320);

    @Override
    public void onItemChanged(ReplaceAnimator<?> animato) {
        for (ListAnimator.Entry<ButtonsLayout> entry : animator) {
            final float visibility = entry.getVisibility();
            final float scale = lerp(0.7f, 1f, visibility);
            entry.item.setAlpha(visibility);
            entry.item.setScaleX(scale);
            entry.item.setScaleY(scale);
        }
    }

    private class ButtonsLayout extends LinearLayout implements Destroyable {
        public ButtonsLayout(Context context) {
            super(context);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            layoutExpressiveButtons();
        }

        private void layoutExpressiveButtons() {
            if (!expressive || getWidth() <= 0) {
                return;
            }
            for (int r = 0; r < getChildCount(); r++) {
                LinearLayout row = (LinearLayout) getChildAt(r);
                int count = row.getChildCount();
                if (count == 0 || row.getWidth() <= 0 || row.getHeight() <= 0) {
                    continue;
                }
                // Rows with the same number of buttons share column expansion, as in the settings grid.
                float[] progress = new float[count];
                for (int other = 0; other < getChildCount(); other++) {
                    LinearLayout otherRow = (LinearLayout) getChildAt(other);
                    if (otherRow.getChildCount() != count) {
                        continue;
                    }
                    for (int c = 0; c < count; c++) {
                        Button button = (Button) ((FrameLayout) otherRow.getChildAt(c)).getChildAt(0);
                        progress[c] = Math.max(progress[c], Math.max(0f, Math.min(1f, button.expressiveState.progress)));
                    }
                }
                float totalExpansion = 0f;
                int active = 0;
                for (float value : progress) {
                    if (value > 0f) {
                        totalExpansion += 0.18f * value;
                        active++;
                    }
                }
                float totalWeight = 0f;
                for (int c = 0; c < count; c++) {
                    progress[c] = progress[c] > 0f ? 1f + 0.18f * progress[c]
                            : 1f - (active < count ? totalExpansion / (count - active) : 0f);
                    totalWeight += progress[c];
                }
                int gap = dp(getButtonGap());
                int available = Math.max(0, row.getWidth() - gap * (count - 1));
                boolean rtl = row.getLayoutDirection() == LAYOUT_DIRECTION_RTL;
                int used = 0;
                float cumulativeWeight = 0f;
                for (int c = 0; c < count; c++) {
                    FrameLayout frame = (FrameLayout) row.getChildAt(c);
                    cumulativeWeight += progress[c];
                    int end = Math.round(available * cumulativeWeight / totalWeight);
                    int width = end - used;
                    int x = used + c * gap;
                    if (rtl) {
                        x = row.getWidth() - x - width;
                    }
                    frame.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                            MeasureSpec.makeMeasureSpec(row.getHeight(), MeasureSpec.EXACTLY));
                    frame.layout(x, 0, x + width, row.getHeight());
                    used = end;
                }
            }
        }

        @Override
        public void performDestroy() {
            ((ViewGroup) getParent()).removeView(this);
        }
    }

}

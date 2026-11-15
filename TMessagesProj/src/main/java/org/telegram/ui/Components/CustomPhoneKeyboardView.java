package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.PorterDuff;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextPaint;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.GestureDetectorCompat;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;

import tw.nekomimi.nekogram.NekoConfig;
import xyz.nextalone.nagram.ui.UIStyleEngine;

public class CustomPhoneKeyboardView extends ViewGroup {
    public final static int KEYBOARD_HEIGHT_DP = 230;

    private final static int SIDE_PADDING = 10, BUTTON_PADDING = 6;

    private final ImageView backButton;
    private EditText editText;
    private final View[] views = new View[12];
    private final KeyState[] keyStates = new KeyState[12];

    private View viewToFindFocus;

    private boolean dispatchBackWhenEmpty;
    private boolean runningLongClick;
    private final Runnable onBackButton = () -> {
        checkFindEditText();
        if (editText == null || editText.length() == 0 && !dispatchBackWhenEmpty) return;

        try {
            if ((!UIStyleEngine.isMaterial3Expressive() || runningLongClick) && !NekoConfig.disableVibration.Bool()) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            playSoundEffect(SoundEffectConstants.CLICK);
        } catch (Exception ignore) {}
        editText.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
        editText.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL));

        if (runningLongClick) {
            postDelayed(this.onBackButton, 50);
        }
    };

    private boolean postedLongClick;
    private final Runnable detectLongClick = () -> {
        postedLongClick = false;
        runningLongClick = true;
        onBackButton.run();
    };

    public CustomPhoneKeyboardView(Context context) {
        super(context);

        for (int i = 0; i < 11; i++) {
            if (i == 9) continue;

            String symbols;
            switch (i) {
                default:
                case 0:
                    symbols = "";
                    break;
                case 1:
                    symbols = "ABC";
                    break;
                case 2:
                    symbols = "DEF";
                    break;
                case 3:
                    symbols = "GHI";
                    break;
                case 4:
                    symbols = "JKL";
                    break;
                case 5:
                    symbols = "MNO";
                    break;
                case 6:
                    symbols = "PQRS";
                    break;
                case 7:
                    symbols = "TUV";
                    break;
                case 8:
                    symbols = "WXYZ";
                    break;
                case 10:
                    symbols = "+";
                    break;
            }
            String num = String.valueOf(i != 10 ? i + 1 : 0);
            views[i] = new NumberButtonView(context, num, symbols);
            views[i].setOnClickListener(v -> {
                checkFindEditText();
                if (editText == null) return;

                try {
                    if (!UIStyleEngine.isMaterial3Expressive() && !NekoConfig.disableVibration.Bool()) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                } catch (Exception ignore) {}
                if (editText instanceof EditTextBoldCursor) {
                    ((EditTextBoldCursor) editText).setTextWatchersSuppressed(true, false);
                }

                Editable text = editText.getText();
                int newSelection = editText.getSelectionEnd() == editText.length() ? -1 : editText.getSelectionStart() + num.length();
                if (editText.getSelectionStart() != -1 && editText.getSelectionEnd() != -1) {
                    editText.setText(text.replace(editText.getSelectionStart(), editText.getSelectionEnd(), num));
                    editText.setSelection(newSelection == -1 ? editText.length() : newSelection);
                } else {
                    editText.setText(num);
                    editText.setSelection(editText.length());
                }

                if (editText instanceof EditTextBoldCursor) {
                    ((EditTextBoldCursor) editText).setTextWatchersSuppressed(false, true);
                }
            });
            addView(views[i]);
        }

        GestureDetectorCompat backDetector = setupBackButtonDetector(context);
        backButton = new ImageView(context) {
            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    if (postedLongClick || runningLongClick) {
                        postedLongClick = false;
                        runningLongClick = false;
                        removeCallbacks(detectLongClick);
                        removeCallbacks(onBackButton);
                    }
                }
                super.onTouchEvent(event);
                return backDetector.onTouchEvent(event);
            }
        };
        backButton.setImageResource(R.drawable.msg_clear_input);
        ThemeBind.colorFilter(backButton, Theme.key_windowBackgroundWhiteBlackText, PorterDuff.Mode.SRC_ATOP);
        int pad = dp(11);
        backButton.setPadding(pad, pad, pad, pad);
        backButton.setOnClickListener(v -> {});
        addView(views[11] = backButton);

        for (int a = 0; a < views.length; a++) {
            final View v = views[a];
            if (v == null) continue;
            ScaleStateListAnimator.apply(v, 0.02f, 1.2f, !UIStyleEngine.isMaterial3Expressive());
            setButtonBackground(v, a);
        }
    }

    private static class KeyState {
        final SpringAnimation springAnimation;
        float progress;
        boolean pressed;
        M3ExpressiveButtonDrawable drawable;

        KeyState(Runnable onUpdate) {
            springAnimation = new SpringAnimation(new FloatValueHolder(0f));
            SpringForce force = new SpringForce(0f);
            force.setStiffness(500f);
            force.setDampingRatio(0.82f);
            springAnimation.setSpring(force);
            springAnimation.setMinimumVisibleChange(0.002f);
            springAnimation.addUpdateListener((animation, value, velocity) -> {
                progress = value;
                if (drawable != null) {
                    drawable.setMorphProgress(progress);
                }
                onUpdate.run();
            });
        }
    }

    @Override
    public void childDrawableStateChanged(View child) {
        super.childDrawableStateChanged(child);
        if (!UIStyleEngine.isMaterial3Expressive()) {
            return;
        }
        for (int i = 0; i < views.length; i++) {
            if (views[i] != child) {
                continue;
            }
            KeyState state = keyStates[i];
            if (state == null) {
                return;
            }
            boolean pressed = child.isPressed();
            if (state.pressed != pressed) {
                state.pressed = pressed;
                if (pressed) {
                    com.exteragram.messenger.utils.system.VibratorUtils.vibrateClick(child);
                }
                state.springAnimation.animateToFinalPosition(pressed ? 1f : 0f);
            }
            return;
        }
    }

    @Override
    public boolean canScrollHorizontally(int direction) {
        return true;
    }

    public void setDispatchBackWhenEmpty(boolean dispatchBackWhenEmpty) {
        this.dispatchBackWhenEmpty = dispatchBackWhenEmpty;
    }

    private GestureDetectorCompat setupBackButtonDetector(Context context) {
        int touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        return new GestureDetectorCompat(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(@NonNull MotionEvent e) {
                if (postedLongClick) {
                    removeCallbacks(detectLongClick);
                }
                postedLongClick = true;
                postDelayed(detectLongClick, 200);
                onBackButton.run();
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
                if ((postedLongClick || runningLongClick) && (Math.abs(distanceX) >= touchSlop || Math.abs(distanceY) >= touchSlop)) {
                    postedLongClick = false;
                    runningLongClick = false;
                    removeCallbacks(detectLongClick);
                    removeCallbacks(onBackButton);
                }
                return false;
            }
        });
    }

    public void setViewToFindFocus(View viewToFindFocus) {
        this.viewToFindFocus = viewToFindFocus;
    }

    public void checkFindEditText() {
        if (editText == null && viewToFindFocus != null) {
            View focus = viewToFindFocus.findFocus();
            if (focus instanceof EditText) {
                editText = (EditText) focus;
            }
        }
    }

    public void setEditText(EditText editText) {
        this.editText = editText;
        dispatchBackWhenEmpty = false;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (UIStyleEngine.isMaterial3Expressive()) {
            applyExpressiveKeyLayouts();
            return;
        }
        int btnWidth = (getWidth() - dp(SIDE_PADDING * 2 + BUTTON_PADDING * 2)) / 3;
        int btnHeight = (getHeight() - dp(SIDE_PADDING * 3 + BUTTON_PADDING * 2)) / 4;

        for (int i = 0; i < views.length; i++) {
            int rowX = i % 3, rowY = i / 3;
            int left = rowX * (btnWidth + dp(BUTTON_PADDING)) + dp(SIDE_PADDING);
            int top = rowY * (btnHeight + dp(BUTTON_PADDING)) + dp(SIDE_PADDING);
            if (views[i] != null) {
                views[i].layout(left, top, left + btnWidth, top + btnHeight);
            }
        }
    }

    private void applyExpressiveKeyLayouts() {
        int btnHeight = (getHeight() - dp(SIDE_PADDING * 3 + BUTTON_PADDING * 2)) / 4;
        int totalWidth = getWidth() - dp(SIDE_PADDING * 2);
        int availableWidth = totalWidth - dp(BUTTON_PADDING * 2);
        if (btnHeight <= 0 || availableWidth <= 0) {
            return;
        }

        for (int row = 0; row < 4; row++) {
            float totalExpansion = 0f;
            int pressedCount = 0;
            for (int col = 0; col < 3; col++) {
                int index = row * 3 + col;
                KeyState state = keyStates[index];
                float progress = state != null ? state.progress : 0f;
                if (progress > 0.001f) {
                    totalExpansion += 0.18f * progress;
                    pressedCount++;
                }
            }

            float[] weights = new float[3];
            float totalWeight = 0f;
            for (int col = 0; col < 3; col++) {
                int index = row * 3 + col;
                KeyState state = keyStates[index];
                float progress = state != null ? state.progress : 0f;
                if (progress > 0.001f) {
                    weights[col] = 1f + 0.18f * progress;
                } else if (pressedCount > 0 && pressedCount < 3) {
                    float shrink = totalExpansion / (3f - pressedCount);
                    weights[col] = Math.max(0.70f, 1f - shrink);
                } else {
                    weights[col] = 1f;
                }
                totalWeight += weights[col];
            }

            int left = dp(SIDE_PADDING);
            int top = row * (btnHeight + dp(BUTTON_PADDING)) + dp(SIDE_PADDING);
            for (int col = 0; col < 3; col++) {
                int index = row * 3 + col;
                int width = col == 2 ? dp(SIDE_PADDING) + totalWidth - left : Math.round((weights[col] / totalWeight) * availableWidth);
                View view = views[index];
                if (view != null) {
                    view.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(btnHeight, MeasureSpec.EXACTLY));
                    view.layout(left, top, left + width, top + btnHeight);
                }
                left += width + dp(BUTTON_PADDING);
            }
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));

        int btnWidth = (getWidth() - dp(SIDE_PADDING * 2 + BUTTON_PADDING * 2)) / 3;
        int btnHeight = (getHeight() - dp(SIDE_PADDING * 3 + BUTTON_PADDING * 2)) / 4;

        for (View v : views) {
            if (v != null) {
                v.measure(MeasureSpec.makeMeasureSpec(btnWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(btnHeight, MeasureSpec.EXACTLY));
            }
        }
    }

    private void setButtonBackground(View v, int index) {
        Drawable drawable = getButtonDrawable(index);
        if (UIStyleEngine.isMaterial3Expressive()) {
            if (keyStates[index] == null) {
                keyStates[index] = new KeyState(this::applyExpressiveKeyLayouts);
            }
            if (drawable instanceof M3ExpressiveButtonDrawable) {
                keyStates[index].drawable = (M3ExpressiveButtonDrawable) drawable;
            }
            v.setBackground(drawable);
        } else {
            ThemeBind.selectorBackground(v, drawable, ThemeBind.of(Theme.key_listSelector), ThemeBind.alpha(Theme.key_listSelector, 30));
        }
    }

    private static Drawable getButtonDrawable(int index) {
        final int defaultColor = Theme.getColor(Theme.key_listSelector);
        final int pressedColor = ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_listSelector), 30);

        if (UIStyleEngine.isMaterial3Expressive()) {
            M3ExpressiveButtonDrawable drawable = new M3ExpressiveButtonDrawable(defaultColor, pressedColor, getRestRadii(index), getPressedRadii(), 0);
            drawable.setStroke(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), 18), dp(1));
            return drawable;
        }

        final boolean isTop = index < 3;        // 0 1 2
        final boolean isLeft = index % 3 == 0;  // 0 3 6 9
        final boolean isRight = index % 3 == 2; // 2 5 8 11
        final boolean isBottom = index > 8;     // 9 10 11

        return Theme.createSimpleSelectorRoundRectDrawable(
            dp(isLeft && isTop ? 24 : 12),
            dp(isRight && isTop ? 24 : 12),
            dp(isRight && isBottom ? 24 : 12),
            dp(/*index == 10 || index == 6 ||*/ isLeft && isBottom ? 24 : 12),
            defaultColor, pressedColor, pressedColor);
    }

    private static float[] getPressedRadii() {
        float pressed = dp(18);
        return new float[]{pressed, pressed, pressed, pressed, pressed, pressed, pressed, pressed};
    }

    private static float[] getRestRadii(int index) {
        final float outer = dp(24);
        final float inner = dp(8);
        boolean isLeft = index % 3 == 0 || index == 10;
        boolean isRight = index % 3 == 2;
        return new float[]{
            isLeft ? outer : inner, isLeft ? outer : inner,
            isRight ? outer : inner, isRight ? outer : inner,
            isRight ? outer : inner, isRight ? outer : inner,
            isLeft ? outer : inner, isLeft ? outer : inner
        };
    }

    private int lastBackButtonColor, lastButtonsSelectorColor;

    // called from LoginActivity.updateColors (every theme frame): recolor in place, no allocations
    public void updateColors() {
        final int backColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
        if (backColor != lastBackButtonColor || backButton.getColorFilter() == null) {
            lastBackButtonColor = backColor;
            backButton.setColorFilter(backColor, PorterDuff.Mode.SRC_ATOP);
        }
        final int defaultColor = Theme.getColor(Theme.key_listSelector);
        final int pressedColor = ColorUtils.setAlphaComponent(defaultColor, 30);
        final boolean selectorChanged = defaultColor != lastButtonsSelectorColor;
        lastButtonsSelectorColor = defaultColor;
        for (int a = 0; a < views.length; a++) {
            View v = views[a];
            if (v != null) {
                if (UIStyleEngine.isMaterial3Expressive()) {
                    setButtonBackground(v, a);
                } else if (v.getBackground() != null) {
                    if (selectorChanged) {
                        Theme.setSelectorDrawableColor(v.getBackground(), defaultColor, false);
                        Theme.setSelectorDrawableColor(v.getBackground(), pressedColor, true);
                    }
                } else {
                    setButtonBackground(v, a);
                }

                if (v instanceof NumberButtonView) {
                    ((NumberButtonView) v).updateColors();
                }
            }
        }
    }

    private final static class NumberButtonView extends View implements Theme.Colorable {
        private final TextPaint numberTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint symbolsTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final String mNumber;
        private final String mSymbols;
        private final Rect rect = new Rect();

        public NumberButtonView(Context context, String number, String symbols) {
            super(context);
            mNumber = number;
            mSymbols = symbols;

            numberTextPaint.setTextSize(dp(24));
            symbolsTextPaint.setTextSize(dp(14));

            applyColors();
        }

        private void applyColors() {
            numberTextPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            symbolsTextPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        }

        @Override
        public void updateColors() {
            applyColors();
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float symbolsWidth = symbolsTextPaint.measureText(mSymbols);
            float numberWidth = numberTextPaint.measureText(mNumber);

            numberTextPaint.getTextBounds(mNumber, 0, mNumber.length(), rect);
            float textOffsetNumber = rect.height() / 2f;
            symbolsTextPaint.getTextBounds(mSymbols, 0, mSymbols.length(), rect);
            float textOffsetSymbols = rect.height() / 2f;

            canvas.drawText(mNumber, getWidth() * 0.25f - numberWidth / 2f, getHeight() / 2f + textOffsetNumber, numberTextPaint);
            canvas.drawText(mSymbols, getWidth() * 0.7f - symbolsWidth / 2f, getHeight() / 2f + textOffsetSymbols, symbolsTextPaint);
        }
    }
}

package org.telegram.ui.Components.chat;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.RoundedCorner;
import android.view.View;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.WindowInsets;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.blur3.BlurredBackgroundWithFadeDrawable;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.inset.InAppKeyboardInsetView;
import org.telegram.ui.Components.inset.WindowInsetsProvider;

import xyz.nextalone.nagram.ui.UIStyleEngine;

public class ChatInputViewsContainer extends FrameLayout {
    public static final int INPUT_BUBBLE_RADIUS = 22;
    public static final int INPUT_KEYBOARD_RADIUS = 29;

    public static final int INPUT_BUBBLE_BOTTOM = 9;

    private WindowInsetsProvider windowInsetsProvider;

    private final View fadeView;
    private final FrameLayout inputIslandBubbleContainer;
    private final FrameLayout inAppKeyboardBubbleContainer;

    public ChatInputViewsContainer(@NonNull Context context) {
        super(context);

        inputIslandBubbleContainer = new FrameLayout(context) {
            @Override
            public void onViewAdded(View child) {
                super.onViewAdded(child);
                if (child instanceof ChatActivityEnterView) {
                    ((ChatActivityEnterView) child).addTextChangedListener(new TextWatcher() {
                        @Override
                        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                        @Override
                        public void onTextChanged(CharSequence s, int start, int before, int count) {
                            invalidate();
                        }

                        @Override
                        public void afterTextChanged(Editable s) {}
                    });
                }
            }
        };
        addView(inputIslandBubbleContainer,
            LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

        inAppKeyboardBubbleContainer = new FrameLayout(context) {
            @Override
            public void addView(View child, int width, int height) {
                super.addView(child, width, height);
                checkViewsPositions();
            }
        };
        addView(inAppKeyboardBubbleContainer,
            LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

        fadeView = new View(context) {
            @Override
            protected void dispatchDraw(@NonNull Canvas canvas) {
                if (backgroundWithFadeDrawable != null) {
                    backgroundWithFadeDrawable.draw(canvas);
                }
                super.dispatchDraw(canvas);
            }
        };
    }

    public View getFadeView() {
        return fadeView;
    }

    public void setWindowInsetsProvider(WindowInsetsProvider windowInsetsProvider) {
        this.windowInsetsProvider = windowInsetsProvider;
    }



    public boolean drawInputBackground = true;
    public BlurredBackgroundDrawable blurredBackgroundDrawable;
    public BlurredBackgroundDrawable blurredBackgroundDrawableLeft;
    public BlurredBackgroundDrawable blurredBackgroundDrawableRight;
    private BlurredBackgroundDrawable underKeyboardBackgroundDrawable;

    public void setInputIslandBubbleDrawable(BlurredBackgroundDrawable drawable) {
        blurredBackgroundDrawable = drawable;
        if (blurredBackgroundDrawable != null) {
            blurredBackgroundDrawable.setPadding(dp(7));
            blurredBackgroundDrawable.setRadius(dp(INPUT_BUBBLE_RADIUS));
        }
    }

    public void setInputIslandDrawables(BlurredBackgroundDrawable center, BlurredBackgroundDrawable left, BlurredBackgroundDrawable right) {
        blurredBackgroundDrawable = center;
        blurredBackgroundDrawableLeft = left;
        blurredBackgroundDrawableRight = right;

        if (blurredBackgroundDrawable != null) {
            blurredBackgroundDrawable.setPadding(dp(7));
            blurredBackgroundDrawable.setRadius(dp(INPUT_BUBBLE_RADIUS));
        }
        if (blurredBackgroundDrawableLeft != null) {
            blurredBackgroundDrawableLeft.setPadding(dp(7));
            blurredBackgroundDrawableLeft.setRadius(dp(INPUT_BUBBLE_RADIUS));
        }
        if (blurredBackgroundDrawableRight != null) {
            blurredBackgroundDrawableRight.setPadding(dp(7));
            blurredBackgroundDrawableRight.setRadius(dp(INPUT_BUBBLE_RADIUS));
        }
    }

    public void setUnderKeyboardBackgroundDrawable(BlurredBackgroundDrawable drawable) {
        underKeyboardBackgroundDrawable = drawable;
        underKeyboardBackgroundDrawable.enableInAppKeyboardOptimization();
        underKeyboardBackgroundDrawable.setRadius(dp(INPUT_KEYBOARD_RADIUS), dp(INPUT_KEYBOARD_RADIUS), 0, 0);
        underKeyboardBackgroundDrawable.setThickness(dp(32));
        underKeyboardBackgroundDrawable.setIntensity(0.4f);
    }

    public void updateColors() {
        if (blurredBackgroundDrawable != null) {
            blurredBackgroundDrawable.updateColors();
        }
        if (blurredBackgroundDrawableLeft != null) {
            blurredBackgroundDrawableLeft.updateColors();
        }
        if (blurredBackgroundDrawableRight != null) {
            blurredBackgroundDrawableRight.updateColors();
        }
        if (underKeyboardBackgroundDrawable != null) {
            underKeyboardBackgroundDrawable.updateColors();
        }
        invalidate();
    }

    public boolean isEnterViewVisible() {
        for (int i = 0; i < inputIslandBubbleContainer.getChildCount(); i++) {
            View child = inputIslandBubbleContainer.getChildAt(i);
            if (child instanceof ChatActivityEnterView) {
                return child.getVisibility() == VISIBLE;
            }
        }
        return false;
    }

    public boolean enterViewHasText() {
        for (int i = 0; i < inputIslandBubbleContainer.getChildCount(); i++) {
            View child = inputIslandBubbleContainer.getChildAt(i);
            if (child instanceof ChatActivityEnterView) {
                ChatActivityEnterView enterView = (ChatActivityEnterView) child;
                return enterView.getVisibility() == VISIBLE && enterView.hasText() && !enterView.isRecordingAudioVideo();
            }
        }
        return false;
    }

    public boolean isAiButtonVisible() {
        for (int i = 0; i < inputIslandBubbleContainer.getChildCount(); i++) {
            View child = inputIslandBubbleContainer.getChildAt(i);
            if (child instanceof ChatActivityEnterView) {
                ChatActivityEnterView enterView = (ChatActivityEnterView) child;
                return enterView.getVisibility() == VISIBLE && enterView.isAiButtonVisible();
            }
        }
        return false;
    }

    public int getEnterViewTextFieldHeight() {
        for (int i = 0; i < inputIslandBubbleContainer.getChildCount(); i++) {
            View child = inputIslandBubbleContainer.getChildAt(i);
            if (child instanceof ChatActivityEnterView) {
                return ((ChatActivityEnterView) child).getTextFieldHeight();
            }
        }
        return dp(ChatActivityEnterView.DEFAULT_HEIGHT);
    }


    @NonNull
    public FrameLayout getInputIslandBubbleContainer() {
        return inputIslandBubbleContainer;
    }

    @NonNull
    public FrameLayout getInAppKeyboardBubbleContainer() {
        return inAppKeyboardBubbleContainer;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        checkViewsPositions();
        checkInAppKeyboardChild();
    }



    private void checkInAppKeyboardViewHeight() {
        LayoutParams lp = (LayoutParams) inAppKeyboardBubbleContainer.getLayoutParams();

        final int oldHeight = lp.height;
        final int newHeight = windowInsetsProvider.getInAppKeyboardRecommendedViewHeight();

        if (oldHeight != newHeight) {
            lp.height = newHeight;
            requestLayout();
        }
    }

    private final Path underKeyboardPath = new Path();

    private int currentBlurredHeight;
    private void checkBlurredHeight(boolean force) {
        checkViewsPositions();

        final int blurredHeight = inputBubbleHeightRound + dp(INPUT_BUBBLE_BOTTOM) + Math.round(maxBottomInset);
        if (currentBlurredHeight != blurredHeight || force) {
            currentBlurredHeight = blurredHeight;

            final int r = dp(INPUT_KEYBOARD_RADIUS);
            tmpRectF.set(0, getMeasuredHeight() - imeBottomInset, getMeasuredWidth(), getMeasuredHeight());
            underKeyboardPath.rewind();
            underKeyboardPath.addRoundRect(tmpRectF, new float[] {r, r, r, r, 0, 0, 0, 0}, Path.Direction.CW);
            underKeyboardPath.close();
            invalidate();
        }
    }

    private float maxBottomInset;
    private float imeBottomInset;
    private boolean needDrawInAppKeyboard;

    public void checkInsets() {
        maxBottomInset = windowInsetsProvider.getAnimatedMaxBottomInset();
        imeBottomInset = windowInsetsProvider.getAnimatedImeBottomInset();

        needDrawInAppKeyboard = windowInsetsProvider.inAppViewIsVisible();

        if ((inAppKeyboardBubbleContainer.getVisibility() == VISIBLE) != needDrawInAppKeyboard) {
            inAppKeyboardBubbleContainer.setVisibility(needDrawInAppKeyboard ? VISIBLE : GONE);
        }

        checkInAppKeyboardViewHeight();
        checkBlurredHeight(false);
        checkInAppKeyboardChild();

        if (underKeyboardBackgroundDrawable != null) {
            int leftBottomRadius = 0;
            int rightBottomRadius = 0;
            if (Build.VERSION.SDK_INT >= 31) {
                final WindowInsets insets = getRootWindowInsets();
                if (insets != null) {
                    final RoundedCorner bottomLeft = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT);
                    final RoundedCorner bottomRight = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT);
                    leftBottomRadius = bottomLeft == null ? 0 : bottomLeft.getRadius();
                    rightBottomRadius = bottomRight == null ? 0 : bottomRight.getRadius();
                }
            }
            underKeyboardBackgroundDrawable.setRadius(dp(INPUT_KEYBOARD_RADIUS), dp(INPUT_KEYBOARD_RADIUS), rightBottomRadius, leftBottomRadius, true);
        }
    }

    private void checkViewsPositions() {
        inputIslandBubbleContainer.setTranslationY(-maxBottomInset - dp(INPUT_BUBBLE_BOTTOM));
        inAppKeyboardBubbleContainer.setTranslationY(inAppKeyboardBubbleContainer.getMeasuredHeight() - imeBottomInset);
    }


    private void checkInAppKeyboardChild() {
        final int navbarHeight = windowInsetsProvider.getCurrentNavigationBarInset();
        final float keyboardHeight = windowInsetsProvider.getAnimatedImeBottomInset();

        for (int a = 0, N = inAppKeyboardBubbleContainer.getChildCount(); a < N; a++) {
            final View child = inAppKeyboardBubbleContainer.getChildAt(a);
            if (child instanceof InAppKeyboardInsetView) {
                InAppKeyboardInsetView insetView = (InAppKeyboardInsetView) child;
                insetView.applyNavigationBarHeight(navbarHeight);
                insetView.applyInAppKeyboardAnimatedHeight(keyboardHeight);
            }
        }
    }



    /* */

    private float inputBubbleOffsetLeft;
    private float inputBubbleOffsetRight;

    private float inputBubbleHeight;
    private int inputBubbleHeightRound;
    public void setInputBubbleHeight(float height) {
        inputBubbleHeight = height;
        inputBubbleHeightRound = Math.round(inputBubbleHeight);
        checkBlurredHeight(false);
    }

    public void setInputBubbleOffsets(float left, float right) {
        inputBubbleOffsetLeft = left;
        inputBubbleOffsetRight = right;
        invalidate();
    }

    public float getInputBubbleHeight() {
        return inputBubbleHeight;
    }

    public float getInputBubbleTop() {
        return getInputBubbleBottom() - getInputBubbleHeight();
    }

    public float getInputBubbleBottom() {
        return getMeasuredHeight() - maxBottomInset - dp(INPUT_BUBBLE_BOTTOM);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        checkBlurredHeight(true);
        checkDrawableBounds();
        checkViewsPositions();
        checkInAppKeyboardChild();
    }

    /* Render */

    private final Rect tmpRect = new Rect();
    private final Rect tmpRectLeft = new Rect();
    private final Rect tmpRectCenter = new Rect();
    private final Rect tmpRectRight = new Rect();
    private final RectF tmpRectF = new RectF();
    private final AnimatedFloat mergeProgress = new AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final AnimatedFloat leftIslandExpandProgress = new AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        underKeyboardBackgroundDrawable.setBounds(
            0,
            getMeasuredHeight() - (int) imeBottomInset,
            getMeasuredWidth(),
            Math.max(getMeasuredHeight(), getMeasuredHeight() - (int) imeBottomInset + dp(INPUT_KEYBOARD_RADIUS * 2))
        );

        final int blurTop = getMeasuredHeight() - currentBlurredHeight;
        final int islandBottom = blurTop + inputBubbleHeightRound + (int) bubbleInputTranlationY;

        if (xyz.nextalone.nagram.ui.UIStyleEngine.isIosLiquidGlass() && isEnterViewVisible() && blurredBackgroundDrawableLeft != null && blurredBackgroundDrawableRight != null) {
            final int buttonSize = dp(ChatActivityEnterView.DEFAULT_HEIGHT);
            final int islandRadius = buttonSize / 2;
            final int islandGap = dp(8);
            final int sideMargin = dp(7);
            final int pad = dp(7);

            final boolean hasText = enterViewHasText();
            final float p = mergeProgress.set(hasText ? 1f : 0f);

            final boolean aiVisible = isAiButtonVisible();
            final float p_left = leftIslandExpandProgress.set(aiVisible ? 1f : 0f);
            final int expandedLeftHeight = getEnterViewTextFieldHeight();
            final int currentLeftHeight = Math.round(AndroidUtilities.lerp((float) buttonSize, (float) expandedLeftHeight, p_left));

            // Left Island (Emoji button + optional AI button when expanded into vertical capsule)
            tmpRectLeft.set(
                sideMargin - pad,
                islandBottom - currentLeftHeight - pad,
                sideMargin + buttonSize + pad,
                islandBottom + pad
            );
            blurredBackgroundDrawableLeft.setRadius(islandRadius);
            blurredBackgroundDrawableLeft.setBounds(tmpRectLeft);

            // Right Island (Send / Mic)
            tmpRectRight.set(
                getMeasuredWidth() - sideMargin - buttonSize - pad,
                islandBottom - buttonSize - pad,
                getMeasuredWidth() - sideMargin + pad,
                islandBottom + pad
            );
            blurredBackgroundDrawableRight.setRadius(islandRadius);
            blurredBackgroundDrawableRight.setBounds(tmpRectRight);
            blurredBackgroundDrawableRight.setAlpha(Math.round(currentInputBubbleAlpha * (1f - p)));

            // Center Island (Message text input + emoji + reply header)
            // Morphs smoothly between unmerged (ends before gap) and merged (covers right button)
            final int centerVisualLeft = sideMargin + buttonSize + islandGap;
            final int centerVisualRight = getMeasuredWidth() - sideMargin - buttonSize - islandGap;
            final int mergedVisualRight = getMeasuredWidth() - sideMargin;
            final int centerCurrentRight = Math.round(AndroidUtilities.lerp((float) centerVisualRight, (float) mergedVisualRight, p));

            tmpRectCenter.set(
                centerVisualLeft - pad,
                islandBottom - inputBubbleHeightRound - pad,
                centerCurrentRight + pad,
                islandBottom + pad
            );
            blurredBackgroundDrawable.setRadius(islandRadius);
            blurredBackgroundDrawable.setBounds(tmpRectCenter);

            if (drawInputBackground) {
                blurredBackgroundDrawableLeft.draw(canvas);
                if (p < 1f && blurredBackgroundDrawableRight.getAlpha() > 0) {
                    canvas.save();
                    canvas.clipRect(centerCurrentRight, 0, getMeasuredWidth(), getMeasuredHeight());
                    blurredBackgroundDrawableRight.draw(canvas);
                    canvas.restore();
                }
                blurredBackgroundDrawable.draw(canvas);
            }
        } else {
            tmpRect.set(
                Math.round(inputBubbleOffsetLeft),
                0,
                getMeasuredWidth() - Math.round(inputBubbleOffsetRight),
                inputBubbleHeightRound
            );
            tmpRect.inset(0, -dp(7));
            tmpRect.offset(0, blurTop + (int) bubbleInputTranlationY);

            blurredBackgroundDrawable.setBounds(tmpRect);
            if (drawInputBackground)
                blurredBackgroundDrawable.draw(canvas);
        }

        if (needDrawInAppKeyboard) {
            underKeyboardBackgroundDrawable.draw(canvas);
        }

        super.dispatchDraw(canvas);
    }

    @Override
    protected boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime) {
        final boolean needClip = child == inAppKeyboardBubbleContainer;
        if (needClip) {
            canvas.save();
            canvas.clipPath(underKeyboardBackgroundDrawable.getPath());
        }

        final boolean result = super.drawChild(canvas, child, drawingTime);
        if (needClip) {
            canvas.restore();
        }

        return result;
    }





    private BlurredBackgroundWithFadeDrawable backgroundWithFadeDrawable;

    public void setBackgroundWithFadeDrawable(BlurredBackgroundWithFadeDrawable backgroundWithFadeDrawable) {
        this.backgroundWithFadeDrawable = backgroundWithFadeDrawable;
    }

    private float blurredBottomHeight;
    public void setBlurredBottomHeight(float height) {
        if (blurredBottomHeight != height) {
            blurredBottomHeight = height;
            checkDrawableBounds();
        }
    }

    private float bubbleInputTranlationY;
    public void setInputBubbleTranslationY(float translationY) {
        this.bubbleInputTranlationY = translationY;
        invalidate();
    }

    private int currentInputBubbleAlpha = 255;
    public void setInputBubbleAlpha(int alpha) {
        currentInputBubbleAlpha = alpha;
        if (blurredBackgroundDrawable != null) {
            blurredBackgroundDrawable.setAlpha(alpha);
        }
        if (blurredBackgroundDrawableLeft != null) {
            blurredBackgroundDrawableLeft.setAlpha(alpha);
        }
        if (blurredBackgroundDrawableRight != null) {
            blurredBackgroundDrawableRight.setAlpha(alpha);
        }
    }

    private void checkDrawableBounds() {
        if (backgroundWithFadeDrawable == null) {
            return;
        }

        final int oldBound = backgroundWithFadeDrawable.getBounds().top;
        final int newBound = getMeasuredHeight() - Math.round(blurredBottomHeight);

        if (oldBound != newBound) {
            backgroundWithFadeDrawable.setBounds(0, newBound, getMeasuredWidth(), getMeasuredHeight());
            fadeView.invalidate(0, Math.max(0, Math.min(oldBound, newBound)), getMeasuredWidth(), getMeasuredHeight());
            invalidate(0, Math.max(0, Math.min(oldBound, newBound)), getMeasuredWidth(), getMeasuredHeight());
        }
    }


    private boolean captured;

    private boolean containsVisual(Rect bounds, int pad, int x, int y) {
        return x >= bounds.left + pad && x <= bounds.right - pad && y >= bounds.top + pad && y <= bounds.bottom - pad;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action = event.getAction();

        if (action == MotionEvent.ACTION_DOWN) {
            final int x = (int) event.getX();
            final int y = (int) event.getY();

            if (xyz.nextalone.nagram.ui.UIStyleEngine.isIosLiquidGlass() && isEnterViewVisible() && blurredBackgroundDrawableLeft != null && blurredBackgroundDrawableRight != null) {
                final int pad = dp(7);
                captured = (blurredBackgroundDrawable != null && blurredBackgroundDrawable.getAlpha() == 255 && containsVisual(tmpRectCenter, pad, x, y))
                    || (blurredBackgroundDrawableLeft != null && blurredBackgroundDrawableLeft.getAlpha() == 255 && containsVisual(tmpRectLeft, pad, x, y))
                    || (blurredBackgroundDrawableRight != null && blurredBackgroundDrawableRight.getAlpha() > 0 && containsVisual(tmpRectRight, pad, x, y))
                    || (underKeyboardBackgroundDrawable != null && underKeyboardBackgroundDrawable.getBounds().contains(x, y));
            } else {
                captured = (blurredBackgroundDrawable != null && blurredBackgroundDrawable.getAlpha() == 255 && blurredBackgroundDrawable.getBounds().contains(x, y))
                    || (underKeyboardBackgroundDrawable != null && underKeyboardBackgroundDrawable.getBounds().contains(x, y));
            }

        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            captured = false;
        }

        return captured;
    }
}

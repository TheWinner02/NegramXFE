/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EmojiTextView;
import org.telegram.ui.Components.LayoutHelper;

public class TextDetailSettingsCell extends FrameLayout {

    private TextView textView;
    private TextView valueTextView;
    private ImageView imageView;
    private boolean needDivider;
    private boolean multiline;

    public TextDetailSettingsCell(Context context) {
        super(context);

        textView = new EmojiTextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 21, 10, 21, 0));

        valueTextView = new EmojiTextView(context);
        valueTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        valueTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        valueTextView.setLines(1);
        valueTextView.setMaxLines(1);
        valueTextView.setSingleLine(true);
        valueTextView.setPadding(0, 0, 0, 0);
        addView(valueTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 21, 35, 21, 0));

        imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY));
        imageView.setVisibility(GONE);
        addView(imageView, LayoutHelper.createFrame(52, 52, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 8, 6, 8, 0));

        setMultilineDetail(true);

    }

    private boolean isM3Expressive() {
        return xyz.nextalone.nagram.ui.UIStyleEngine.isMaterial3Expressive();
    }

    private void updateIconLayout(boolean visible) {
        FrameLayout.LayoutParams iconParams = (FrameLayout.LayoutParams) imageView.getLayoutParams();
        if (!visible) {
            imageView.setVisibility(GONE);
            imageView.setBackground(null);
            iconParams.width = AndroidUtilities.dp(52);
            iconParams.height = AndroidUtilities.dp(52);
            iconParams.leftMargin = AndroidUtilities.dp(8);
            iconParams.rightMargin = AndroidUtilities.dp(8);
            iconParams.topMargin = AndroidUtilities.dp(6);
            textView.setPadding(0, 0, 0, 0);
            valueTextView.setPadding(0, 0, 0, multiline ? AndroidUtilities.dp(12) : 0);
        } else if (isM3Expressive()) {
            imageView.setVisibility(VISIBLE);
            iconParams.width = AndroidUtilities.dp(28);
            iconParams.height = AndroidUtilities.dp(28);
            iconParams.leftMargin = AndroidUtilities.dp(18);
            iconParams.rightMargin = AndroidUtilities.dp(18);
            iconParams.topMargin = AndroidUtilities.dp(18);
            int textInset = AndroidUtilities.dp(58);
            textView.setPadding(LocaleController.isRTL ? 0 : textInset, 0, LocaleController.isRTL ? textInset : 0, 0);
            valueTextView.setPadding(LocaleController.isRTL ? 0 : textInset, 0, LocaleController.isRTL ? textInset : 0, multiline ? AndroidUtilities.dp(12) : 0);
        } else {
            imageView.setVisibility(VISIBLE);
            iconParams.width = AndroidUtilities.dp(52);
            iconParams.height = AndroidUtilities.dp(52);
            iconParams.leftMargin = AndroidUtilities.dp(8);
            iconParams.rightMargin = AndroidUtilities.dp(8);
            iconParams.topMargin = AndroidUtilities.dp(6);
            int textInset = AndroidUtilities.dp(50);
            textView.setPadding(LocaleController.isRTL ? 0 : textInset, 0, LocaleController.isRTL ? textInset : 0, 0);
            valueTextView.setPadding(LocaleController.isRTL ? 0 : textInset, 0, LocaleController.isRTL ? textInset : 0, multiline ? AndroidUtilities.dp(12) : 0);
        }
        imageView.setLayoutParams(iconParams);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (!multiline) {
            int cellHeight = xyz.nextalone.nagram.ui.UIStyleEngine.isMaterial3Expressive() ? 72 : 64;
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(cellHeight) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));
        } else {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        }
    }

    public TextView getTextView() {
        return textView;
    }

    public TextView getValueTextView() {
        return valueTextView;
    }

    public void setMultilineDetail(boolean value) {
        multiline = value;
        if (value) {
            valueTextView.setLines(0);
            valueTextView.setMaxLines(0);
            valueTextView.setSingleLine(false);
            valueTextView.setPadding(0, 0, 0, AndroidUtilities.dp(12));
        } else {
            valueTextView.setLines(1);
            valueTextView.setMaxLines(1);
            valueTextView.setSingleLine(true);
            valueTextView.setPadding(0, 0, 0, 0);
        }
    }

    public void setTextAndValue(CharSequence text, CharSequence value, boolean divider) {
        textView.setText(text);
        if (TextUtils.isEmpty(value)) {
            valueTextView.setVisibility(GONE);
        } else {
            valueTextView.setVisibility(VISIBLE);
        }
        valueTextView.setText(value);
        needDivider = divider;
        updateIconLayout(false);
        setWillNotDraw(!divider);
    }

    public void setTextAndValueAndIcon(String text, CharSequence value, int resId, boolean divider) {
        textView.setText(text);
        valueTextView.setText(value);
        imageView.setImageResource(resId);
        imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY));
        imageView.setBackground(null);
        updateIconLayout(true);
        needDivider = divider;
        setWillNotDraw(!divider);
    }

    public void setValue(CharSequence value) {
        valueTextView.setText(value);
    }

    public void setTextWithEmojiAnd21Value(String text, CharSequence value, boolean divider) {
        textView.setText(Emoji.replaceEmoji(text, textView.getPaint().getFontMetricsInt(), false));
        valueTextView.setText(value);
        needDivider = divider;
        setWillNotDraw(!divider);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        textView.invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider && Theme.dividerPaint != null) {
            int origColor = Theme.dividerPaint.getColor();
            int alpha = Theme.dividerPaint.getAlpha();
            float origStroke = Theme.dividerPaint.getStrokeWidth();
            boolean isIosGlass = xyz.nextalone.nagram.ui.UIStyleEngine.isIosLiquidGlass();
            if (isIosGlass) {
                boolean isDark = Theme.isCurrentThemeDark();
                Theme.dividerPaint.setColor(isDark ? 0x24FFFFFF : 0x24000000);
                Theme.dividerPaint.setStrokeWidth(AndroidUtilities.dpf2(0.5f));
            } else if (xyz.nextalone.nagram.ui.UIStyleEngine.isMaterial3Expressive()) {
                Theme.dividerPaint.setAlpha((int) (alpha * 0.38f));
            }
            float y = getMeasuredHeight() - (isIosGlass ? AndroidUtilities.dpf2(0.5f) : 1);
            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(imageView.getVisibility() == VISIBLE ? 71 : 20), y, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(imageView.getVisibility() == VISIBLE ? 71 : 20) : 0), y, Theme.dividerPaint);
            if (isIosGlass) {
                Theme.dividerPaint.setColor(origColor);
                Theme.dividerPaint.setStrokeWidth(origStroke);
            } else {
                Theme.dividerPaint.setAlpha(alpha);
            }
        }
    }
}

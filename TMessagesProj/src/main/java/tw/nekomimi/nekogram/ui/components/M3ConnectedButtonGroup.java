package tw.nekomimi.nekogram.ui.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.graphics.ColorUtils;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import xyz.nextalone.nagram.ui.UIStyleEngine;

public class M3ConnectedButtonGroup extends View {

    public interface OnItemSelectedListener {
        void onItemSelected(int index);
    }

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private final float[] radii = new float[8];

    private Theme.ResourcesProvider resourcesProvider;
    private String[] items = new String[0];
    private int selectedIndex;
    private int pressedIndex = -1;
    private int morphIndex = -1;
    private float pressedProgress;
    private final SpringAnimation pressedAnimation;
    private OnItemSelectedListener listener;

    public M3ConnectedButtonGroup(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setClickable(true);
        setFocusable(true);
        textPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14, getResources().getDisplayMetrics()));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(AndroidUtilities.bold());
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(AndroidUtilities.dp(1));
        pressedAnimation = new SpringAnimation(new FloatValueHolder(0f));
        SpringForce force = new SpringForce(0f);
        force.setStiffness(500f);
        force.setDampingRatio(0.82f);
        pressedAnimation.setSpring(force);
        pressedAnimation.setMinimumVisibleChange(0.002f);
        pressedAnimation.addUpdateListener((animation, value, velocity) -> {
            pressedProgress = value;
            if (pressedProgress <= 0.002f && pressedIndex < 0) {
                morphIndex = -1;
            }
            invalidate();
        });
    }

    public void setResourcesProvider(Theme.ResourcesProvider resourcesProvider) {
        this.resourcesProvider = resourcesProvider;
        invalidate();
    }

    public void setItems(String[] items, int selectedIndex) {
        this.items = items != null ? items : new String[0];
        this.selectedIndex = Math.max(0, Math.min(selectedIndex, Math.max(0, this.items.length - 1)));
        requestLayout();
        invalidate();
    }

    public void setOnItemSelectedListener(OnItemSelectedListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = getPreferredHeight(width);
        setMeasuredDimension(width, height);
    }

    public int getPreferredHeight(int width) {
        int rows = getRowCount(width);
        return rows * AndroidUtilities.dp(48) + (rows - 1) * AndroidUtilities.dp(2);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int count = items.length;
        if (count == 0) {
            return;
        }

        final boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        final float gap = AndroidUtilities.dp(2);
        final int rows = getRowCount(getWidth());
        final int columns = Math.max(1, (int) Math.ceil(count / (float) rows));
        final float[] columnWidths = getColumnWidths(columns, gap);
        final float segmentHeight = (getHeight() - getPaddingTop() - getPaddingBottom() - gap * (rows - 1)) / rows;
        final boolean liquidGlass = UIStyleEngine.isIosLiquidGlass();
        if (liquidGlass) {
            final boolean isDark = Theme.isCurrentThemeDark();
            final float containerRad = AndroidUtilities.dp(10);
            final float thumbRad = AndroidUtilities.dp(8);
            final int containerBg = isDark ? 0x24767680 : 0x14767680;
            final int thumbBg = isDark ? 0xFF636366 : 0xFFFFFFFF;
            final int activeTextColor = isDark ? 0xFFFFFFFF : 0xFF000000;
            final int inactiveTextColor = isDark ? 0xFF8E8E93 : 0xFF6E6E73;

            for (int r = 0; r < rows; r++) {
                float rowTop = getPaddingTop() + r * (segmentHeight + gap);
                float rowBottom = rowTop + segmentHeight;
                rect.set(getPaddingLeft(), rowTop, getWidth() - getPaddingRight(), rowBottom);
                backgroundPaint.setColor(containerBg);
                canvas.drawRoundRect(rect, containerRad, containerRad, backgroundPaint);
                strokePaint.setColor(isDark ? 0x25FFFFFF : 0x35FFFFFF);
                strokePaint.setStrokeWidth(AndroidUtilities.dpf2(0.66f));
                canvas.drawRoundRect(rect, containerRad, containerRad, strokePaint);
            }

            for (int visualIndex = 0; visualIndex < count; visualIndex++) {
                int row = visualIndex / columns;
                int column = visualIndex % columns;
                int visualColumn = rtl ? columns - column - 1 : column;
                int itemIndex = row * columns + (rtl ? visualColumn : column);
                if (itemIndex < 0 || itemIndex >= count) {
                    continue;
                }
                float left = getColumnLeft(columnWidths, visualColumn, gap);
                float right = left + columnWidths[visualColumn];
                float top = getPaddingTop() + row * (segmentHeight + gap);
                float bottom = top + segmentHeight;
                rect.set(left, top, right, bottom);

                boolean selected = itemIndex == selectedIndex;
                boolean pressed = itemIndex == pressedIndex;

                if (selected) {
                    RectF thumbRect = AndroidUtilities.rectTmp;
                    thumbRect.set(left + AndroidUtilities.dp(2), top + AndroidUtilities.dp(2), right - AndroidUtilities.dp(2), bottom - AndroidUtilities.dp(2));
                    backgroundPaint.setColor(thumbBg);
                    if (!isDark) {
                        backgroundPaint.setShadowLayer(AndroidUtilities.dp(2.5f), 0, AndroidUtilities.dp(1f), 0x22000000);
                    } else {
                        backgroundPaint.setShadowLayer(0, 0, 0, 0);
                    }
                    canvas.drawRoundRect(thumbRect, thumbRad, thumbRad, backgroundPaint);
                    backgroundPaint.setShadowLayer(0, 0, 0, 0);

                    strokePaint.setColor(isDark ? 0x30FFFFFF : 0x40FFFFFF);
                    strokePaint.setStrokeWidth(AndroidUtilities.dpf2(0.66f));
                    canvas.drawRoundRect(thumbRect, thumbRad, thumbRad, strokePaint);

                    textPaint.setColor(activeTextColor);
                    textPaint.setTypeface(AndroidUtilities.bold());
                } else {
                    if (pressed) {
                        RectF pressRect = AndroidUtilities.rectTmp;
                        pressRect.set(left + AndroidUtilities.dp(2), top + AndroidUtilities.dp(2), right - AndroidUtilities.dp(2), bottom - AndroidUtilities.dp(2));
                        backgroundPaint.setColor(isDark ? 0x1AFFFFFF : 0x0D000000);
                        canvas.drawRoundRect(pressRect, thumbRad, thumbRad, backgroundPaint);
                    }
                    if (visualColumn < columns - 1) {
                        int nextItemIndex = row * columns + (rtl ? columns - (column + 1) - 1 : column + 1);
                        if (nextItemIndex >= 0 && nextItemIndex < count && nextItemIndex != selectedIndex && !selected) {
                            strokePaint.setColor(isDark ? 0x20FFFFFF : 0x18000000);
                            strokePaint.setStrokeWidth(AndroidUtilities.dpf2(0.66f));
                            canvas.drawLine(right + gap / 2f, top + AndroidUtilities.dp(7), right + gap / 2f, bottom - AndroidUtilities.dp(7), strokePaint);
                        }
                    }
                    textPaint.setColor(inactiveTextColor);
                    textPaint.setTypeface(null);
                }
                drawLabel(canvas, items[itemIndex], rect);
            }
            return;
        }

        final int surfaceColor = Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider);
        final int accentColor = Theme.getColor(Theme.key_switch2TrackChecked, resourcesProvider);
        final int selectedColor = accentColor;
        final int outlineColor = ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon, resourcesProvider), 80);
        final int selectorColor = Theme.getColor(Theme.key_listSelector, resourcesProvider);
        final int unselectedColor = ColorUtils.blendARGB(surfaceColor, Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), 0.05f);

        for (int visualIndex = 0; visualIndex < count; visualIndex++) {
            int row = visualIndex / columns;
            int column = visualIndex % columns;
            int visualColumn = rtl ? columns - column - 1 : column;
            int itemIndex = row * columns + (rtl ? visualColumn : column);
            if (itemIndex < 0 || itemIndex >= count) {
                continue;
            }
            float left = getColumnLeft(columnWidths, visualColumn, gap);
            float right = left + columnWidths[visualColumn];
            float top = getPaddingTop() + row * (segmentHeight + gap);
            float bottom = top + segmentHeight;
            rect.set(left, top, right, bottom);

            boolean selected = itemIndex == selectedIndex;
            boolean pressed = itemIndex == pressedIndex;
            boolean morphing = itemIndex == morphIndex;
            int fillColor = selected ? selectedColor : unselectedColor;
            if (liquidGlass && pressed) {
                fillColor = ColorUtils.blendARGB(fillColor, Theme.multAlpha(accentColor, 0.22f), 0.55f);
            } else if (pressed && !selected) {
                fillColor = ColorUtils.blendARGB(fillColor, selectorColor, 0.45f);
            } else if (pressed) {
                fillColor = ColorUtils.blendARGB(fillColor, Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider), 0.12f);
            }

            backgroundPaint.setColor(fillColor);
            buildSegmentPath(row, visualColumn, rows, columns, morphing ? pressedProgress : 0f);
            canvas.drawPath(path, backgroundPaint);
            if (!selected || liquidGlass) {
                strokePaint.setColor(outlineColor);
                canvas.drawPath(path, strokePaint);
            }

            textPaint.setColor(selected
                    ? (liquidGlass ? accentColor : Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider))
                    : Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            drawLabel(canvas, items[itemIndex], rect);
        }
    }

    private void drawLabel(Canvas canvas, String text, RectF bounds) {
        float availableWidth = Math.max(0, bounds.width() - AndroidUtilities.dp(14));
        if (textPaint.measureText(text) <= availableWidth) {
            Paint.FontMetrics fontMetrics = textPaint.getFontMetrics();
            float textY = bounds.centerY() - (fontMetrics.ascent + fontMetrics.descent) / 2f;
            canvas.drawText(text, bounds.centerX(), textY, textPaint);
            return;
        }

        String firstLine = text;
        String secondLine = "";
        int split = findBestSplit(text, availableWidth);
        if (split > 0) {
            firstLine = text.substring(0, split).trim();
            secondLine = text.substring(split).trim();
        }
        firstLine = TextUtils.ellipsize(firstLine, textPaint, availableWidth, TextUtils.TruncateAt.END).toString();
        secondLine = TextUtils.ellipsize(secondLine, textPaint, availableWidth, TextUtils.TruncateAt.END).toString();
        Paint.FontMetrics fontMetrics = textPaint.getFontMetrics();
        float lineHeight = fontMetrics.descent - fontMetrics.ascent;
        float firstY = bounds.centerY() - lineHeight / 2f - fontMetrics.ascent - AndroidUtilities.dp(1);
        canvas.drawText(firstLine, bounds.centerX(), firstY, textPaint);
        if (!secondLine.isEmpty()) {
            canvas.drawText(secondLine, bounds.centerX(), firstY + lineHeight, textPaint);
        }
    }

    private int findBestSplit(String text, float availableWidth) {
        int best = -1;
        float bestOverflow = Float.MAX_VALUE;
        for (int i = 1; i < text.length() - 1; i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                continue;
            }
            String first = text.substring(0, i).trim();
            String second = text.substring(i).trim();
            float overflow = Math.max(textPaint.measureText(first), textPaint.measureText(second)) - availableWidth;
            overflow = Math.abs(overflow);
            if (overflow < bestOverflow) {
                bestOverflow = overflow;
                best = i;
            }
        }
        return best;
    }

    private float[] getColumnWidths(int columns, float gap) {
        float[] widths = new float[columns];
        if (columns <= 0) {
            return widths;
        }
        float availableWidth = getWidth() - getPaddingLeft() - getPaddingRight() - gap * (columns - 1);
        float totalExpansion = 0f;
        int pressedColumn = getPressedVisualColumn(columns);
        for (int i = 0; i < columns; i++) {
            if (i == pressedColumn) {
                totalExpansion += 0.18f * pressedProgress;
            }
        }
        float totalWeight = 0f;
        for (int i = 0; i < columns; i++) {
            float weight;
            if (i == pressedColumn) {
                weight = 1f + 0.18f * pressedProgress;
            } else if (pressedColumn >= 0 && columns > 1 && totalExpansion > 0f) {
                weight = Math.max(0.5f, 1f - totalExpansion / (columns - 1));
            } else {
                weight = 1f;
            }
            widths[i] = weight;
            totalWeight += weight;
        }
        for (int i = 0; i < columns; i++) {
            widths[i] = Math.max(AndroidUtilities.dp(28), availableWidth * widths[i] / totalWeight);
        }
        return widths;
    }

    private float getColumnLeft(float[] widths, int visualColumn, float gap) {
        float left = getPaddingLeft();
        for (int i = 0; i < visualColumn && i < widths.length; i++) {
            left += widths[i] + gap;
        }
        return left;
    }

    private int getPressedVisualColumn(int columns) {
        if (morphIndex < 0 || pressedProgress <= 0.001f || columns <= 0) {
            return -1;
        }
        final boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        int rows = getRowCount(getWidth());
        int column = morphIndex % columns;
        if (morphIndex / columns >= rows) {
            return -1;
        }
        return rtl ? columns - column - 1 : column;
    }

    private void buildSegmentPath(int row, int column, int rows, int columns, float progress) {
        boolean liquidGlass = UIStyleEngine.isIosLiquidGlass();
        float outer = AndroidUtilities.dp(liquidGlass ? 21 : 24);
        float inner = AndroidUtilities.dp(liquidGlass ? 10 : 8);
        float pressed = AndroidUtilities.dp(liquidGlass ? 15 : 16);
        boolean top = row == 0;
        boolean bottom = row == rows - 1;
        boolean left = column == 0;
        boolean right = column == columns - 1;
        radii[0] = AndroidUtilities.lerp(top && left ? outer : inner, pressed, progress);
        radii[1] = radii[0];
        radii[2] = AndroidUtilities.lerp(top && right ? outer : inner, pressed, progress);
        radii[3] = radii[2];
        radii[4] = AndroidUtilities.lerp(bottom && right ? outer : inner, pressed, progress);
        radii[5] = radii[4];
        radii[6] = AndroidUtilities.lerp(bottom && left ? outer : inner, pressed, progress);
        radii[7] = radii[6];
        path.reset();
        path.addRoundRect(rect, radii, Path.Direction.CW);
    }

    private int getRowCount(int width) {
        return items.length > 3 ? 2 : 1;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled() || items.length == 0) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressedIndex = findItemIndex(event.getX(), event.getY());
                if (pressedIndex >= 0) {
                    morphIndex = pressedIndex;
                    com.exteragram.messenger.utils.system.VibratorUtils.vibrateClick(this);
                    pressedAnimation.animateToFinalPosition(1f);
                }
                invalidate();
                return pressedIndex >= 0;
            case MotionEvent.ACTION_MOVE:
                int moveIndex = findItemIndex(event.getX(), event.getY());
                if (moveIndex != pressedIndex) {
                    pressedIndex = moveIndex;
                    if (pressedIndex >= 0) {
                        morphIndex = pressedIndex;
                        pressedAnimation.animateToFinalPosition(1f);
                    } else {
                        pressedAnimation.animateToFinalPosition(0f);
                    }
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                int upIndex = findItemIndex(event.getX(), event.getY());
                int oldPressedIndex = pressedIndex;
                pressedIndex = -1;
                pressedAnimation.animateToFinalPosition(0f);
                invalidate();
                if (upIndex >= 0 && upIndex == oldPressedIndex) {
                    performClick();
                    if (upIndex != selectedIndex) {
                        selectedIndex = upIndex;
                        invalidate();
                        if (listener != null) {
                            listener.onItemSelected(upIndex);
                        }
                    }
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                pressedIndex = -1;
                pressedAnimation.animateToFinalPosition(0f);
                invalidate();
                return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private int findItemIndex(float x, float y) {
        int count = items.length;
        if (count == 0) {
            return -1;
        }
        final boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        final float gap = AndroidUtilities.dp(2);
        final int rows = getRowCount(getWidth());
        final int columns = Math.max(1, (int) Math.ceil(count / (float) rows));
        final float[] columnWidths = getColumnWidths(columns, gap);
        final float segmentHeight = (getHeight() - getPaddingTop() - getPaddingBottom() - gap * (rows - 1)) / rows;
        float relativeX = x - getPaddingLeft();
        float relativeY = y - getPaddingTop();
        int visualColumn = -1;
        float left = 0;
        for (int i = 0; i < columns; i++) {
            float right = left + columnWidths[i];
            if (relativeX >= left && relativeX <= right) {
                visualColumn = i;
                break;
            }
            left = right + gap;
        }
        int row = (int) (relativeY / (segmentHeight + gap));
        if (visualColumn < 0 || visualColumn >= columns || row < 0 || row >= rows) {
            return -1;
        }
        float segmentTop = row * (segmentHeight + gap);
        if (relativeY < segmentTop || relativeY > segmentTop + segmentHeight) {
            return -1;
        }
        int column = rtl ? columns - visualColumn - 1 : visualColumn;
        int index = row * columns + column;
        return index < count ? index : -1;
    }
}

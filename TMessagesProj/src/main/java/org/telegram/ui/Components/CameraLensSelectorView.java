package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import tw.nekomimi.nekogram.NekoConfig;

public class CameraLensSelectorView extends LinearLayout {

    public interface Delegate {
        void onZoomRatioSelected(float zoomRatio);
    }

    private final ArrayList<Float> zoomRatios = new ArrayList<>();
    private final ArrayList<TextView> buttons = new ArrayList<>();
    private final ArrayList<M3ExpressiveButtonDrawable> expressiveDrawables = new ArrayList<>();
    private final boolean expressive;
    private final M3ExpressiveButtonGroup expressiveGroup;
    private Delegate delegate;
    private float selectedZoomRatio = 1f;
    private int currentOrientation = -1;

    public CameraLensSelectorView(Context context) {
        super(context);
        setGravity(Gravity.CENTER);
        setClipChildren(false);
        setClipToPadding(false);
        expressive = xyz.nextalone.nagram.ui.UIStyleEngine.isMaterial3Expressive();
        if (expressive) {
            setOrientation(HORIZONTAL);
            expressiveGroup = new M3ExpressiveButtonGroup(context);
            expressiveGroup.setConnected(true);
            expressiveGroup.setSpacing(AndroidUtilities.dp(3));
            expressiveGroup.setChildSizeChange(0.18f);
            expressiveGroup.setOuterCornerRadius(AndroidUtilities.dp(23));
            expressiveGroup.setInnerCornerRadius(AndroidUtilities.dp(8));
            expressiveGroup.setPressedCornerRadius(AndroidUtilities.dp(14));
            addView(expressiveGroup, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        } else {
            expressiveGroup = null;
        }
    }

    public void setDelegate(Delegate delegate) {
        this.delegate = delegate;
    }

    public void setZoomRatios(List<Float> ratios) {
        zoomRatios.clear();
        buttons.clear();
        expressiveDrawables.clear();
        if (expressive) {
            expressiveGroup.removeAllViews();
        } else {
            removeAllViews();
        }
        if (ratios == null) {
            return;
        }
        for (Float ratio : ratios) {
            if (ratio == null || ratio <= 0f) {
                continue;
            }
            zoomRatios.add(ratio);
            TextView button = createButton(ratio);
            buttons.add(button);
            if (expressive) {
                M3ExpressiveButtonDrawable drawable = new M3ExpressiveButtonDrawable(
                        0x66000000, 0x44ffffff, AndroidUtilities.dp(23), AndroidUtilities.dp(14), 0);
                expressiveDrawables.add(drawable);
                expressiveGroup.addView(button);
                expressiveGroup.registerChild(button, 1f, drawable);
            } else {
                LayoutParams params = new LayoutParams(AndroidUtilities.dp(40), AndroidUtilities.dp(40));
                params.setMargins(AndroidUtilities.dp(3), AndroidUtilities.dp(3), AndroidUtilities.dp(3), AndroidUtilities.dp(3));
                addView(button, params);
            }
        }
        updateSelection(false);
    }

    public void setSelectedZoomRatio(float zoomRatio, boolean animated) {
        selectedZoomRatio = zoomRatio;
        updateSelection(animated);
    }

    private TextView createButton(float zoomRatio) {
        TextView button = new TextView(getContext());
        button.setGravity(Gravity.CENTER);
        button.setText(formatZoomRatio(zoomRatio));
        button.setTextSize(13);
        button.setTypeface(AndroidUtilities.bold());
        button.setContentDescription(formatZoomRatio(zoomRatio));
        button.setOnClickListener(view -> {
            selectedZoomRatio = zoomRatio;
            updateSelection(true);
            if (!expressive) {
                try {
                    if (!NekoConfig.disableVibration.Bool()) {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    }
                } catch (Exception ignored) {
                }
            }
            if (delegate != null) {
                delegate.onZoomRatioSelected(zoomRatio);
            }
        });
        return button;
    }

    private void updateSelection(boolean animated) {
        int selectedIndex = findClosestZoomIndex(selectedZoomRatio);
        for (int i = 0; i < buttons.size(); i++) {
            TextView button = buttons.get(i);
            boolean selected = i == selectedIndex;
            button.setTextColor(selected ? Color.BLACK : Color.WHITE);
            if (expressive) {
                M3ExpressiveButtonDrawable drawable = expressiveDrawables.get(i);
                drawable.setColors(selected ? Color.WHITE : 0x66000000,
                        selected ? 0x33000000 : 0x44ffffff);
            } else {
                button.setBackground(createBackground(selected));
            }
            if (!expressive && animated && selected) {
                button.setScaleX(0.85f);
                button.setScaleY(0.85f);
                button.animate().scaleX(1f).scaleY(1f).setDuration(180).start();
            } else if (!expressive && !selected) {
                button.animate().cancel();
                button.setScaleX(1f);
                button.setScaleY(1f);
            }
        }
        if (expressive) {
            expressiveGroup.updateChildShapes();
        }
    }

    private int findClosestZoomIndex(float zoomRatio) {
        int result = -1;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0; i < zoomRatios.size(); i++) {
            float distance = Math.abs(zoomRatios.get(i) - zoomRatio);
            if (distance < bestDistance) {
                bestDistance = distance;
                result = i;
            }
        }
        return result;
    }

    private StateListDrawable createBackground(boolean selected) {
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_pressed}, createCircle(selected ? 0xffdddddd : 0x99000000));
        background.addState(new int[0], createCircle(selected ? Color.WHITE : 0x66000000));
        return background;
    }

    private GradientDrawable createCircle(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    private String formatZoomRatio(float zoomRatio) {
        if (Math.abs(zoomRatio - Math.round(zoomRatio)) < 0.05f) {
            return String.format(Locale.US, "%dx", Math.round(zoomRatio));
        }
        return String.format(Locale.US, "%.1fx", zoomRatio);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (expressive) {
            int count = Math.max(1, zoomRatios.size());
            int desiredWidth = AndroidUtilities.dp(44) * count + AndroidUtilities.dp(3) * (count - 1);
            int desiredHeight = AndroidUtilities.dp(46);
            int width = resolveSize(desiredWidth, widthMeasureSpec);
            int height = resolveSize(desiredHeight, heightMeasureSpec);
            expressiveGroup.measure(
                    MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
            setMeasuredDimension(width, height);
            return;
        }
        int orientation = View.MeasureSpec.getSize(widthMeasureSpec) >= View.MeasureSpec.getSize(heightMeasureSpec)
                ? HORIZONTAL : VERTICAL;
        if (currentOrientation != orientation) {
            currentOrientation = orientation;
            setOrientation(orientation);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (expressive) {
            expressiveGroup.layout(0, 0, right - left, bottom - top);
            return;
        }
        super.onLayout(changed, left, top, right, bottom);
    }
}

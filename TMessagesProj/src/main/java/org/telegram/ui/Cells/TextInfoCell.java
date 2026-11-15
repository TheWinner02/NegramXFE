/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.Components.LayoutHelper;
import xyz.nextalone.nagram.ui.M3ColorRoles;

public class TextInfoCell extends FrameLayout {

    private TextView textView;

    public TextInfoCell(Context context) {
        super(context);

        textView = new TextView(context);
        ThemeBind.textColor(textView, Theme.key_windowBackgroundWhiteGrayText5);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        textView.setGravity(Gravity.CENTER);
        textView.setPadding(0, AndroidUtilities.dp(19), 0, AndroidUtilities.dp(19));
        addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 17, 0, 17, 0));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    }

    public void setText(String text) {
        textView.setText(text);
    }

    private int getThemedColor(int key) {
        int fallbackColor = Theme.getColor(key);
        if (!xyz.nextalone.nagram.ui.UIStyleEngine.isMaterial3Expressive()) {
            return fallbackColor;
        }
        if (key == Theme.key_windowBackgroundWhiteGrayText5 || key == Theme.key_windowBackgroundWhiteGrayText || key == Theme.key_windowBackgroundWhiteGrayText2 || key == Theme.key_windowBackgroundWhiteGrayText3 || key == Theme.key_windowBackgroundWhiteGrayText4) {
            return resolveM3RoleColor(M3ColorRoles.Role.ON_SURFACE_VARIANT, fallbackColor);
        }
        return fallbackColor;
    }

    private static int resolveM3RoleColor(M3ColorRoles.Role role, int fallbackColor) {
        int color = M3ColorRoles.get(role, fallbackColor);
        return ColorUtils.setAlphaComponent(color, Color.alpha(fallbackColor));
    }
}

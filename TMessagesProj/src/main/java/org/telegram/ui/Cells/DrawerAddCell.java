/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class DrawerAddCell extends FrameLayout {

    private TextView textView;

    public DrawerAddCell(Context context) {
        super(context);

        textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_chats_menuItemText));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        textView.setCompoundDrawablePadding(AndroidUtilities.dp(29));
        boolean isM3 = xyz.nextalone.nagram.ui.UIStyleEngine.isMaterial3Expressive();
        boolean isIosGlass = xyz.nextalone.nagram.ui.UIStyleEngine.isIosLiquidGlass();
        int textLeft = (isM3 || isIosGlass) ? 16 : 19;
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, textLeft, 0, 16, 0));

        if (isM3 || isIosGlass) {
            org.telegram.ui.Components.ScaleStateListAnimator.apply(this, 0.03f, 1.2f);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        boolean isGrouped = xyz.nextalone.nagram.ui.UIStyleEngine.isMaterial3Expressive() || xyz.nextalone.nagram.ui.UIStyleEngine.isIosLiquidGlass();
        int h = isGrouped ? 56 : 48;
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(h), MeasureSpec.EXACTLY));
        updateColors();
    }

    public void updateColors() {
        textView.setTextColor(Theme.getColor(Theme.key_chats_menuItemText));
        textView.setText(LocaleController.getString(R.string.AddAccount));
        Drawable drawable = getResources().getDrawable(R.drawable.msg_add);
        if (drawable != null) {
            drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_menuItemIcon), PorterDuff.Mode.MULTIPLY));
        }
        textView.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateColors();
    }
}

package xyz.nextalone.nagram.ui.fluid;

import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewParent;
import org.telegram.ui.ActionBar.Theme;

import java.lang.ref.WeakReference;

/**
 * Live anchor tracking a source view in screen space,
 * with fallbacks for recycled list cells.
 */
public final class FluidAnchor {
    private final WeakReference<View> source;
    private final Object identity;
    private final float initialAlpha;
    private final Rect visible = new Rect();
    private final RectF lastBounds = new RectF();
    private final int[] location = new int[2];
    public final int color;

    public FluidAnchor(View view) {
        this.source = new WeakReference<>(view);
        this.identity = view != null ? view.getTag() : null;
        this.initialAlpha = view != null ? view.getAlpha() : 1f;

        int fill = Theme.getColor(Theme.key_windowBackgroundWhite);
        if (view != null) {
            Drawable bg = view.getBackground();
            if (bg instanceof ColorDrawable) {
                fill = ((ColorDrawable) bg).getColor();
            } else {
                View curr = view;
                while (curr != null) {
                    Drawable b = curr.getBackground();
                    if (b instanceof ColorDrawable) {
                        fill = ((ColorDrawable) b).getColor();
                        break;
                    }
                    ViewParent p = curr.getParent();
                    curr = (p instanceof View) ? (View) p : null;
                }
            }
        }
        this.color = fill;
        read(lastBounds);
    }

    public View validSource() {
        View view = source.get();
        if (view != null && view.isAttachedToWindow() && (identity == null || view.getTag() == identity)) {
            return view;
        }
        return null;
    }

    public void read(RectF out) {
        View view = validSource();
        if (view != null && view.getLocalVisibleRect(visible)) {
            view.getLocationOnScreen(location);
            lastBounds.set(
                    visible.left + location[0],
                    visible.top + location[1],
                    visible.right + location[0],
                    visible.bottom + location[1]
            );
        }
        out.set(lastBounds);
    }

    public void cover(float progress) {
        View view = validSource();
        if (view == null) return;
        float p = progress < 0.12f ? (1f - progress / 0.12f) : ((progress - 0.12f) / 0.23f);
        p = Math.max(0f, Math.min(1f, p));
        view.setAlpha(initialAlpha * p * p * (3f - 2f * p));
    }

    public void release() {
        View view = validSource();
        if (view != null) {
            view.setAlpha(initialAlpha);
        }
    }
}

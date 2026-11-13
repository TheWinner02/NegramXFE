package xyz.nextalone.nagram.ui.fluid;

import android.content.Context;
import android.content.ContextWrapper;
import android.app.Activity;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import java.lang.ref.WeakReference;
import xyz.nextalone.nagram.ui.UIStyleEngine;

/** Short-lived, one-use touch origins. Scrolling and different Activities cannot supply an anchor. */
public final class FluidOrigins {
    private static WeakReference<View> origin = new WeakReference<>(null);
    private static long recorded;
    private static float downX, downY;
    private FluidOrigins() {}

    public static void observe(View root, MotionEvent event) {
        if (!UIStyleEngine.isFluid()) return;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            downX = event.getRawX(); downY = event.getRawY();
            origin = new WeakReference<>(hit(root, downX, downY, 0));
            recorded = SystemClock.uptimeMillis();
        } else if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_POINTER_DOWN
                || action == MotionEvent.ACTION_MOVE && Math.hypot(event.getRawX() - downX, event.getRawY() - downY)
                > ViewConfiguration.get(root.getContext()).getScaledTouchSlop()) {
            origin.clear();
        }
    }

    /** A menu action can open a sheet after the return spring outlives the original touch. */
    public static void handoff(View source) {
        if (!UIStyleEngine.isFluid() || source == null || !source.isAttachedToWindow()
                || !source.isShown() || !source.isEnabled() || source.getAlpha() <= 0f || !usable(source)) return;
        origin = new WeakReference<>(source);
        recorded = SystemClock.uptimeMillis();
    }

    private static boolean usable(View view) {
        if (view instanceof androidx.recyclerview.widget.RecyclerView
                || view instanceof android.widget.AbsListView
                || view instanceof android.widget.ScrollView
                || view instanceof android.widget.HorizontalScrollView) return false;
        View root = view.getRootView();
        // Empty space / custom touch dispatch must never select the entire chat or page.
        return !(view instanceof ViewGroup && !(view.getParent() instanceof androidx.recyclerview.widget.RecyclerView)
            && view.getWidth() > root.getWidth() * .75f && view.getHeight() > root.getHeight() * .55f);
    }

    public static View take(Context context) {
        View view = origin.get();
        origin.clear();
        if (view == null || !view.isAttachedToWindow() || !view.isShown() || !usable(view)
                || SystemClock.uptimeMillis() - recorded > 1800L
                || activity(context) == null || activity(context) != activity(view.getContext())) return null;
        return view;
    }

    private static Activity activity(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) return (Activity) context;
            Context next = ((ContextWrapper) context).getBaseContext();
            if (next == context) break;
            context = next;
        }
        return null;
    }

    private static View hit(View view, float x, float y, int depth) {
        if (depth > 24 || !view.isShown() || !view.isEnabled() || view.getAlpha() <= 0f) return null;
        android.graphics.Rect bounds = new android.graphics.Rect();
        android.graphics.Point offset = new android.graphics.Point();
        if (!view.getGlobalVisibleRect(bounds, offset)) return null;
        int[] screen = new int[2]; view.getLocationOnScreen(screen);
        bounds.offset(screen[0] - offset.x, screen[1] - offset.y);
        if (!bounds.contains((int) x, (int) y)) return null;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                View found = hit(group.getChildAt(i), x, y, depth + 1);
                if (found != null) return found;
            }
        }
        return usable(view) && (view.isClickable() || view.isLongClickable()
                || view.getParent() instanceof androidx.recyclerview.widget.RecyclerView) ? view : null;
    }
}

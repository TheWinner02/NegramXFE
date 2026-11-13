package xyz.nextalone.nagram.ui.fluid;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.view.Window;
import android.view.WindowManager;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.ActionBarPopupWindow.ActionBarPopupWindowLayout;
import java.lang.ref.WeakReference;

/** Shared simple toolbar menu: real native content in a full-window, reversible surface. */
public final class FluidMenuDialog extends Dialog {
    private final WeakReference<View> origin;
    private final ActionBarPopupWindowLayout menu;
    private final Surface surface;
    private final float originAlpha;
    private final Runnable beforeAction;
    private Runnable unregisterBack;
    private View touchReceiver;
    private View pendingItem;
    private boolean closing, finishing, awaitingTouchEnd, actionCommitted;
    private java.util.function.Consumer<Boolean> menuClosed;
    private final View.OnAttachStateChangeListener originLifecycle = new View.OnAttachStateChangeListener() {
        @Override public void onViewAttachedToWindow(View view) {}
        @Override public void onViewDetachedFromWindow(View view) {
            pendingItem = null; touchReceiver = null; view.setPressed(false);
            closing = true; finishClose();
        }
    };

    public void setOnMenuClosed(java.util.function.Consumer<Boolean> callback) { menuClosed = callback; }
    public void notifyMenuClosed() {
        java.util.function.Consumer<Boolean> callback = menuClosed; menuClosed = null;
        if (callback != null) callback.accept(actionCommitted);
    }

    public FluidMenuDialog(Context context, View source, ActionBarPopupWindowLayout menu, Runnable beforeAction) {
        super(context);
        origin = new WeakReference<>(source);
        originAlpha = source.getAlpha();
        this.beforeAction = beforeAction;
        this.menu = menu;
        source.addOnAttachStateChangeListener(originLifecycle);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        surface = new Surface(context);
        if (menu.getParent() instanceof ViewGroup) ((ViewGroup) menu.getParent()).removeView(menu);
        menu.setAnimationEnabled(false);
        menu.animate().cancel();
        menu.setScaleX(1f); menu.setScaleY(1f); menu.setAlpha(1f);
        menu.setTranslationX(0f); menu.setTranslationY(0f);
        menu.setBackScaleY(1f); menu.setBackAlpha(255);
        for (int i = 0; i < menu.getItemsCount(); i++) {
            View item = menu.getItemAt(i);
            item.setAlpha(item.isEnabled() ? 1f : .5f); item.setTranslationY(0f);
        }
        surface.addView(menu);
        setContentView(surface);
        setCanceledOnTouchOutside(false);
    }

    public boolean isReturning() { return closing && !finishing; }

    public void toggle() {
        if (closing) {
            closing = false; awaitingTouchEnd = false; pendingItem = null;
            View source = origin.get(); if (source != null) source.setAlpha(0f);
            surface.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            surface.animateTo(1f);
        } else dismiss();
    }

    @Override public void show() {
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setWindowAnimations(0);
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
            if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false);
            View source = origin.get();
            if (source != null) window.getDecorView().setSystemUiVisibility(source.getRootView().getSystemUiVisibility());
            window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
            window.setNavigationBarColor(android.graphics.Color.TRANSPARENT);
        }
        super.show();
        window = getWindow();
        if (window != null) {
            window.setLayout(-1, -1);
            unregisterBack = FluidPredictiveBack.installDialog(window, surface.backMotion());
        }
    }

    public void dismissWithAction(View item) {
        if (closing || finishing || item == null || !item.isEnabled()) return;
        pendingItem = item;
        dismiss();
    }

    @Override public void onBackPressed() { dismiss(); }

    @Override public void dismiss() {
        if (finishing || closing) return;
        surface.cancelPress();
        closing = true;
        surface.refreshOrigin();
        surface.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        surface.animateTo(0f);
    }

    private void finishClose() {
        if (touchReceiver != null) { awaitingTouchEnd = true; return; }
        if (finishing) return;
        finishing = true;
        View selected = pendingItem; pendingItem = null;
        actionCommitted = selected != null;
        surface.spring.cancel();
        View source = origin.get(); if (source != null) source.removeOnAttachStateChangeListener(originLifecycle);
        restoreOrigin();
        if (unregisterBack != null) { unregisterBack.run(); unregisterBack = null; }
        surface.removeView(menu);
        super.dismiss();
        // The original menu item's click listener remains the sole owner of its Telegram action.
        if (selected != null) { beforeAction.run(); selected.performClick(); }
    }

    private void restoreOrigin() {
        View source = origin.get();
        if (source != null) source.setAlpha(originAlpha);
    }

    @Override protected void onStop() {
        surface.cancelPress();
        surface.spring.cancel(); restoreOrigin(); pendingItem = null;
        View source = origin.get(); if (source != null) source.removeOnAttachStateChangeListener(originLifecycle);
        if (unregisterBack != null) { unregisterBack.run(); unregisterBack = null; }
        super.onStop();
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        View source = origin.get();
        if (touchReceiver == null && closing && action == MotionEvent.ACTION_DOWN && source != null) {
            int[] xy = new int[2]; source.getLocationOnScreen(xy);
            if (event.getRawX() >= xy[0] && event.getRawX() < xy[0] + source.getWidth()
                    && event.getRawY() >= xy[1] && event.getRawY() < xy[1] + source.getHeight()) {
                pendingItem = null; // A fresh gesture revokes an action queued by the returning menu.
                touchReceiver = source.getRootView();
            }
        }
        if (touchReceiver != null) {
            View receiver = touchReceiver;
            int[] xy = new int[2]; receiver.getLocationOnScreen(xy);
            MotionEvent forwarded = MotionEvent.obtain(event);
            forwarded.offsetLocation(event.getRawX() - event.getX() - xy[0], event.getRawY() - event.getY() - xy[1]);
            try { if (receiver.isAttachedToWindow()) receiver.dispatchTouchEvent(forwarded); }
            finally { forwarded.recycle(); }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                touchReceiver = null;
                if (awaitingTouchEnd && closing) finishClose();
                awaitingTouchEnd = false;
            }
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    private final class Surface extends ViewGroup {
        private final RectF source = new RectF(), target = new RectF(), body = new RectF();
        private final Path contour = new Path();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int[] xy = new int[2];
        private final Rect hitBounds = new Rect();
        private final SpringAnimation spring;
        private float progress, flow, downX, downY;
        private long previewFrameTime;
        private boolean started, reversalGesture, handoffPosted, gestureCanceled;
        private View pressedItem;

        Surface(Context context) {
            super(context); setWillNotDraw(false); setClipChildren(false); setClipToPadding(false);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            spring = new SpringAnimation(this, new FloatPropertyCompat<Surface>("fluidMenu") {
                @Override public float getValue(Surface host) { return host.progress; }
                @Override public void setValue(Surface host, float value) { host.progress = value; host.invalidate(); }
            });
            spring.setSpring(FluidTiming.springForce(1f));
            spring.setMinimumVisibleChange(FluidTiming.MIN_VISIBLE_CHANGE);
            FluidCapabilities.track(context, spring);
            spring.addUpdateListener((animation, value, velocity) -> flow = Math.max(-1f, Math.min(1f, velocity * .5f)));
            spring.addEndListener((animation, canceled, value, velocity) -> {
                if (!canceled && closing) finishClose();
                else if (!canceled) setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
            });
        }

        void refreshOrigin() {
            View button = origin.get();
            getLocationOnScreen(xy); int left = xy[0], top = xy[1];
            if (button != null && button.isAttachedToWindow()) {
                button.getLocationOnScreen(xy);
                source.set(xy[0] - left, xy[1] - top, xy[0] - left + button.getWidth(), xy[1] - top + button.getHeight());
            }
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec), height = MeasureSpec.getSize(heightSpec);
            setMeasuredDimension(width, height);
            menu.measure(MeasureSpec.makeMeasureSpec(Math.max(1, Math.min(width - AndroidUtilities.dp(32), AndroidUtilities.dp(360))), MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(Math.max(1, height - AndroidUtilities.dp(120)), MeasureSpec.AT_MOST));
        }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            refreshOrigin();
            int margin = AndroidUtilities.dp(12);
            android.view.WindowInsets insets = getRootWindowInsets();
            int safeTop = insets == null ? margin : insets.getSystemWindowInsetTop() + margin;
            int safeBottom = insets == null ? margin : insets.getSystemWindowInsetBottom() + margin;
            float left = Math.max(margin, Math.min(getWidth() - margin - menu.getMeasuredWidth(), source.right - menu.getMeasuredWidth()));
            float top = Math.max(safeTop, Math.min(getHeight() - safeBottom - menu.getMeasuredHeight(), source.bottom + AndroidUtilities.dp(8)));
            target.set(left, top, left + menu.getMeasuredWidth(), top + menu.getMeasuredHeight());
            menu.layout(Math.round(target.left), Math.round(target.top), Math.round(target.right), Math.round(target.bottom));
            if (!started) {
                started = true;
                View button = origin.get(); if (button != null) button.setAlpha(0f);
                animateTo(closing ? 0f : 1f);
            }
        }

        void animateTo(float target) {
            if (!started) { requestLayout(); return; }
            FluidCapabilities.refresh();
            float scale = FluidCapabilities.motionScale(getContext());
            if (scale <= 0f) {
                spring.cancel(); progress = target; invalidate();
                if (closing) finishClose();
                else setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
                return;
            }
            spring.getSpring().setStiffness(FluidTiming.stiffness(scale));
            spring.animateToFinalPosition(target);
        }

        private void geometry() {
            float p = Math.max(0f, Math.min(1f, progress));
            body.set(mix(source.left, target.left, p), mix(source.top, target.top, p),
                    mix(source.right, target.right, p), mix(source.bottom, target.bottom, p));
            float radius = mix(Math.min(source.width(), source.height()) / 2f, AndroidUtilities.dp(24), p);
            float displacement = Math.min(AndroidUtilities.dp(10), Math.min(body.width(), body.height()) * .08f)
                    * (float) Math.sin(Math.PI * p) * flow;
            float directionX = (target.centerX() - source.centerX()) / Math.max(1f, getWidth());
            float directionY = (target.centerY() - source.centerY()) / Math.max(1f, getHeight());
            float pullX = Math.max(-body.width() * .025f, Math.min(body.width() * .025f, directionX * displacement));
            float pullY = Math.max(-body.height() * .025f, Math.min(body.height() * .025f, directionY * displacement));
            float k = .55228475f;
            float x = body.left, y = body.top, right = body.right, bottom = body.bottom;
            radius = Math.max(0f, Math.min(radius, Math.min(body.width(), body.height()) / 2f));
            contour.rewind(); contour.moveTo(x + radius, y);
            edge(x + radius, y, right - radius, y, 0f, pullY);
            contour.cubicTo(right - radius + k * radius, y, right, y + radius - k * radius, right, y + radius);
            edge(right, y + radius, right, bottom - radius, pullX, 0f);
            contour.cubicTo(right, bottom - radius + k * radius, right - radius + k * radius, bottom, right - radius, bottom);
            edge(right - radius, bottom, x + radius, bottom, 0f, pullY);
            contour.cubicTo(x + radius - k * radius, bottom, x, bottom - radius + k * radius, x, bottom - radius);
            edge(x, bottom - radius, x, y + radius, pullX, 0f);
            contour.cubicTo(x, y + radius - k * radius, x + radius - k * radius, y, x + radius, y); contour.close();
        }

        /** Bend the edge locally while keeping its endpoints tangent to the original corners. */
        private void edge(float x0, float y0, float x1, float y1, float pullX, float pullY) {
            float dx = x1 - x0, dy = y1 - y0;
            if (Math.abs(dx) + Math.abs(dy) < .01f) { contour.lineTo(x1, y1); return; }
            float mx = (x0 + x1) * .5f, my = (y0 + y1) * .5f;
            contour.cubicTo(x0 + dx * .2f, y0 + dy * .2f,
                    x0 + dx * .3f + pullX, y0 + dy * .3f + pullY, mx + pullX, my + pullY);
            contour.cubicTo(mx + dx * .2f + pullX, my + dy * .2f + pullY,
                    mx + dx * .3f, my + dy * .3f, x1, y1);
        }

        private boolean atOrigin() {
            geometry();
            return Math.abs(body.left - source.left) <= .75f && Math.abs(body.top - source.top) <= .75f
                    && Math.abs(body.right - source.right) <= .75f && Math.abs(body.bottom - source.bottom) <= .75f;
        }

        @Override protected void dispatchDraw(Canvas canvas) {
            geometry(); float p = Math.max(0f, Math.min(1f, progress));
            if (closing && atOrigin()) {
                restoreOrigin();
                if (!handoffPosted) {
                    handoffPosted = true;
                    post(() -> { handoffPosted = false; if (closing && atOrigin()) finishClose(); });
                }
                return;
            }
            canvas.drawColor(android.graphics.Color.argb(Math.round(32 * p), 0, 0, 0));
            paint.setColor(menu.getBackgroundColor());
            canvas.drawPath(contour, paint);
            int save = canvas.save(); canvas.clipPath(contour);
            View button = origin.get();
            if (button != null && p < .15f) {
                int layer = canvas.saveLayerAlpha(source, Math.round(255 * originAlpha * (1f - p / .15f)));
                canvas.translate(source.left, source.top); button.draw(canvas); canvas.restoreToCount(layer);
            }
            float alpha = Math.max(0f, Math.min(1f, (p - .15f) / .85f));
            if (alpha > 0f) {
                int layer = canvas.saveLayerAlpha(target, Math.round(alpha * 255));
                super.dispatchDraw(canvas); canvas.restoreToCount(layer);
            }
            canvas.restoreToCount(save);
        }

        @Override public boolean onInterceptTouchEvent(MotionEvent event) {
            // Once open, native menu children own scrolling, ripple/press and accessibility.
            // Their shared click listeners defer terminal actions until the return completes.
            if (closing || progress < .99f) return true;
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                return !target.contains(event.getX(), event.getY());
            }
            return false;
        }

        private void cancelPress() {
            if (pressedItem != null) pressedItem.setPressed(false);
            pressedItem = null;
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                cancelPress(); gestureCanceled = false;
                downX = event.getX(); downY = event.getY();
                if (closing) {
                    geometry(); reversalGesture = true;
                    if (body.contains(downX, downY)) toggle();
                    return true;
                }
                if (progress >= .99f) {
                    for (int i = 0; i < menu.getItemsCount(); i++) {
                        View child = menu.getItemAt(i);
                        if (!child.isEnabled() || !child.isShown()) continue;
                        child.getDrawingRect(hitBounds);
                        offsetDescendantRectToMyCoords(child, hitBounds);
                        if (hitBounds.contains((int) downX, (int) downY)) { pressedItem = child; break; }
                    }
                    if (pressedItem != null) pressedItem.setPressed(true);
                }
            } else if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_MOVE
                    && Math.hypot(event.getX() - downX, event.getY() - downY) > ViewConfiguration.get(getContext()).getScaledTouchSlop()) {
                gestureCanceled = true; cancelPress();
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                if (reversalGesture) { reversalGesture = false; return true; }
                View selected = pressedItem; cancelPress();
                if (action == MotionEvent.ACTION_CANCEL || gestureCanceled) return true;
                if (Math.hypot(event.getX() - downX, event.getY() - downY) > ViewConfiguration.get(getContext()).getScaledTouchSlop()) return true;
                if (selected != null) dismissWithAction(selected);
                else if (!body.contains(event.getX(), event.getY())) dismiss();
            }
            return true;
        }

        FluidPredictiveBack.Motion backMotion() {
            FluidPredictiveBack.Motion motion = FluidPredictiveBack.spring(getContext(), spring, () -> started && !finishing,
                    () -> progress, value -> {
                        long now = android.os.SystemClock.uptimeMillis();
                        float next = (float) value;
                        if (now > previewFrameTime) {
                            float velocity = Math.max(-3f, Math.min(3f, (next - progress) * 1000f / (now - previewFrameTime)));
                            flow = Math.max(-1f, Math.min(1f, velocity * .5f));
                        }
                        previewFrameTime = now;
                        progress = next; invalidate();
                    }, FluidMenuDialog.this::dismiss);
            return new FluidPredictiveBack.Motion() {
                @Override public void start() {
                    if (!started || finishing || FluidCapabilities.motionScale(getContext()) <= 0f) return;
                    cancelPress();
                    closing = false; pendingItem = null; awaitingTouchEnd = false;
                    View button = origin.get(); if (button != null) button.setAlpha(0f);
                    previewFrameTime = android.os.SystemClock.uptimeMillis();
                    refreshOrigin(); motion.start();
                }
                @Override public void update(float value) { motion.update(value); }
                @Override public void cancel() { motion.cancel(); }
                @Override public void commit() { motion.commit(); }
            };
        }

    }

    private static float mix(float a, float b, float p) { return a + (b - a) * p; }
}

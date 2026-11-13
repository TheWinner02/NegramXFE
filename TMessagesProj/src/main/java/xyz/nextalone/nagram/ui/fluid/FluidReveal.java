package xyz.nextalone.nagram.ui.fluid;

import android.graphics.Outline;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import java.util.function.BooleanSupplier;
import org.telegram.messenger.AndroidUtilities;
import xyz.nextalone.nagram.ui.UIStyleEngine;

/** Reveals the actual native surface; neither children nor text are scaled or copied. */
public final class FluidReveal implements FluidMotion {
    private final View view;
    private final RectF source = new RectF(), frame = new RectF(), surface = new RectF();
    private final java.util.function.Consumer<RectF> surfaceBounds;
    private final ViewOutlineProvider originalOutline;
    private final boolean originalClip;
    private final SpringAnimation spring;
    private ViewTreeObserver.OnPreDrawListener pending;
    private ProgressListener listener;
    private Runnable opened, closed, closeCancelled;
    private float progress;
    private boolean started, closing, disposed, seeking;
    private final boolean fromBottom;
    private final ViewOutlineProvider outline = new ViewOutlineProvider() {
        @Override public void getOutline(View target, Outline out) {
            float p = Math.max(0f, Math.min(1f, progress));
            resolveSurfaceBounds();
            frame.set(mix(source.left, surface.left, p), mix(source.top, surface.top, p),
                    mix(source.right, surface.right, p), mix(source.bottom, surface.bottom, p));
            float radius = mix(Math.min(source.width(), source.height()) / 2f,
                    UIStyleEngine.getDialogCornerRadius(), p);
            out.setRoundRect(Math.round(frame.left), Math.round(frame.top), Math.round(frame.right),
                    Math.round(frame.bottom), Math.max(0f, Math.min(radius, Math.min(frame.width(), frame.height()) / 2f)));
        }
    };
    private final View.OnAttachStateChangeListener detach = new View.OnAttachStateChangeListener() {
        @Override public void onViewAttachedToWindow(View target) {}
        @Override public void onViewDetachedFromWindow(View target) { abort(); }
    };

    public FluidReveal(View view, View origin, boolean fromBottom) {
        this(view, origin, fromBottom, null);
    }

    public FluidReveal(View view, View origin, boolean fromBottom, java.util.function.Consumer<RectF> surfaceBounds) {
        this.view = view; this.fromBottom = fromBottom; this.surfaceBounds = surfaceBounds;
        originalOutline = view.getOutlineProvider(); originalClip = view.getClipToOutline();
        if (origin != null && origin.isAttachedToWindow()) {
            int[] xy = new int[2]; origin.getLocationOnScreen(xy);
            source.set(xy[0], xy[1], xy[0] + origin.getWidth(), xy[1] + origin.getHeight());
        }
        spring = new SpringAnimation(this, new FloatPropertyCompat<FluidReveal>("fluidSurface") {
            @Override public float getValue(FluidReveal host) { return host.progress; }
            @Override public void setValue(FluidReveal host, float value) { host.render(value); }
        });
        spring.setSpring(FluidTiming.springForce(FluidCapabilities.motionScale(view.getContext())));
        spring.setMinimumVisibleChange(FluidTiming.MIN_VISIBLE_CHANGE);
        FluidCapabilities.track(view.getContext(), spring);
        spring.addEndListener((animation, canceled, value, velocity) -> {
            if (canceled || disposed || seeking) return;
            if (closing) finishClose();
            else { restoreOutline(); runOpened(); }
        });
        view.addOnAttachStateChangeListener(detach);
    }

    private void resolveSurfaceBounds() {
        surface.set(0, 0, view.getWidth(), view.getHeight());
        if (surfaceBounds != null) surfaceBounds.accept(surface);
        if (!surface.intersect(0, 0, view.getWidth(), view.getHeight()) || surface.isEmpty()) {
            surface.set(0, 0, view.getWidth(), view.getHeight());
        }
    }

    /** Live video surfaces must remain in their native hierarchy, never drawn into a bridge. */
    public static boolean needsNativeDrawing(View view) {
        if (view.getVisibility() != View.VISIBLE) return false;
        if (view instanceof android.view.SurfaceView || view instanceof android.view.TextureView) return true;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (needsNativeDrawing(group.getChildAt(i))) return true;
        }
        return false;
    }

    @Override public void setProgressListener(ProgressListener listener) { this.listener = listener; }
    @Override public void setOnCloseCancelled(Runnable listener) { closeCancelled = listener; }
    @Override public boolean isStarted() { return started && !disposed; }

    @Override public void open(Runnable completion) {
        if (disposed) return;
        opened = completion;
        if (started) { closing = false; closed = null; retarget(1f); return; }
        if (pending != null) return;
        FluidCapabilities.refresh();
        pending = () -> {
            if (view.getWidth() == 0 || view.getHeight() == 0) return true;
            removePending();
            int[] xy = new int[2]; view.getLocationOnScreen(xy);
            resolveSurfaceBounds();
            float diameter = Math.min(AndroidUtilities.dp(56), Math.min(surface.width(), surface.height()));
            float cx = source.isEmpty() ? surface.centerX() : source.centerX() - xy[0];
            float cy = source.isEmpty() ? (fromBottom ? surface.bottom - diameter / 2f : surface.centerY())
                    : source.centerY() - xy[1];
            cx = Math.max(surface.left + diameter / 2f, Math.min(surface.right - diameter / 2f, cx));
            cy = Math.max(surface.top + diameter / 2f, Math.min(surface.bottom - diameter / 2f, cy));
            source.set(cx - diameter / 2f, cy - diameter / 2f, cx + diameter / 2f, cy + diameter / 2f);
            started = true;
            if (!FluidCapabilities.isMotionEnabled(view.getContext())) {
                render(1f); restoreOutline(); runOpened();
            } else {
                view.setOutlineProvider(outline); view.setClipToOutline(true);
                render(0f); retarget(1f);
            }
            return true;
        };
        view.getViewTreeObserver().addOnPreDrawListener(pending);
        view.invalidate();
    }

    private void render(float value) {
        progress = value; view.invalidateOutline(); view.invalidate();
        if (listener != null) listener.onProgress(Math.max(0f, Math.min(1f, value)));
    }
    private void retarget(float end) {
        view.setOutlineProvider(outline); view.setClipToOutline(true);
        float scale = FluidCapabilities.motionScale(view.getContext());
        spring.getSpring().setStiffness(FluidTiming.stiffness(Math.max(.01f, scale)));
        spring.animateToFinalPosition(end);
        if (scale <= 0f) spring.skipToEnd();
    }
    @Override public void close(Runnable completion) {
        if (closing) return;
        if (disposed) { if (completion != null) completion.run(); return; }
        closed = completion; closing = true;
        if (!started) { removePending(); finishClose(); return; }
        retarget(0f);
    }
    private void finishClose() {
        Runnable done = closed; closed = null;
        abort();
        if (done != null) done.run();
    }
    private void runOpened() { Runnable done = opened; opened = null; if (done != null) done.run(); }
    private void restoreOutline() { view.setOutlineProvider(originalOutline); view.setClipToOutline(originalClip); }
    private void removePending() {
        if (pending != null && view.getViewTreeObserver().isAlive()) view.getViewTreeObserver().removeOnPreDrawListener(pending);
        pending = null;
    }
    @Override public void abort() {
        if (disposed) return;
        disposed = true; opened = closed = null; seeking = false;
        removePending(); spring.cancel(); restoreOutline(); view.removeOnAttachStateChangeListener(detach);
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (!started || disposed || !spring.isRunning() && !seeking) return false;
        if (closing && closeCancelled != null && event.getActionMasked() == MotionEvent.ACTION_UP) {
            closing = false; closed = null; closeCancelled.run(); retarget(1f);
        }
        return true;
    }
    @Override public FluidPredictiveBack.Motion backMotion(Runnable committed, BooleanSupplier canPreview) {
        return new FluidPredictiveBack.Motion() {
            private FluidPredictiveBack.Motion preview;
            @Override public void start() {
                if (closing && closeCancelled == null || !isStarted() || !canPreview.getAsBoolean() || !FluidCapabilities.isMotionEnabled(view.getContext())) return;
                if (closing && closeCancelled != null) closeCancelled.run();
                closing = false; closed = null; seeking = true;
                view.setOutlineProvider(outline); view.setClipToOutline(true);
                preview = FluidPredictiveBack.spring(view.getContext(), spring, () -> isStarted() && !closing,
                        () -> progress, value -> render((float) value), () -> { retarget(1f); committed.run(); });
                preview.start();
            }
            @Override public void update(float value) {
                if (preview != null && !FluidCapabilities.isMotionEnabled(view.getContext())) cancel();
                else if (preview != null) preview.update(value);
            }
            @Override public void cancel() { seeking = false; if (preview != null) preview.cancel(); preview = null; }
            @Override public void commit() {
                seeking = false;
                FluidPredictiveBack.Motion current = preview; preview = null;
                if (current != null) current.commit(); else committed.run();
            }
        };
    }
    private static float mix(float a, float b, float p) { return a + (b - a) * p; }
}

package xyz.nextalone.nagram.ui.fluid;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.FrameLayout;
import androidx.core.graphics.ColorUtils;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;

import java.lang.ref.WeakReference;

/**
 * Global Container Morphing Coordinator for the Fluid theme.
 * Implements continuous geometric surface transformation using organic cubic-spline contours,
 * native-size live View drawing (no bitmap snapshots), interruptible physics and a single spring.
 * The bridge owns rendering/input only during motion; the original hierarchy owns settled content.
 */
public final class FluidTransform implements FluidMotion {

    private final WeakReference<View> sourceRef;
    private final FluidSourceGuard sourceGuard;
    private WeakReference<View> targetRef;
    private final WeakReference<Window> windowRef;
    private final WeakReference<BottomSheet> bottomSheetRef;

    private ViewGroup host;
    private MorphBridge bridge;
    private SpringAnimation spring;
    private ViewTreeObserver.OnPreDrawListener pendingLayout;

    private float sourceAlpha = 1f;
    private float targetAlpha = 1f;
    private int targetAccessibility;
    private boolean started;
    private boolean closing;
    private boolean disposed;
    private Runnable onDismissComplete;

    private Runnable onOpenComplete;
    private ProgressListener progressListener;
    private Runnable onCloseCancelled;
    private boolean seeking;
    private final View.OnAttachStateChangeListener detachListener = new View.OnAttachStateChangeListener() {
        @Override public void onViewAttachedToWindow(View view) {}
        @Override public void onViewDetachedFromWindow(View view) { abort(); }
    };

    public void setProgressListener(ProgressListener listener) { progressListener = listener; }
    public void setOnCloseCancelled(Runnable listener) { onCloseCancelled = listener; }

    private void retarget(float endpoint) {
        spring.getSpring().setStiffness(FluidTiming.stiffness(Math.max(.01f, FluidCapabilities.motionScale(host.getContext()))));
        spring.animateToFinalPosition(endpoint);
        if (!FluidCapabilities.isMotionEnabled(host.getContext())) spring.skipToEnd();
    }

    private void render(float value) {
        if (bridge == null || disposed) return;
        bridge.progress = value;
        bridge.invalidate();
        if (progressListener != null) progressListener.onProgress(Math.max(0f, Math.min(1f, value)));
    }

    public static FluidTransform attach(BottomSheet bottomSheet, View sourceView) {
        if (bottomSheet == null || sourceView == null) return null;
        return new FluidTransform(bottomSheet, sourceView);
    }

    public static FluidTransform create(Window window, View target, View source) {
        if (window == null || target == null || source == null) return null;
        return new FluidTransform(window, target, source);
    }

    private FluidTransform(BottomSheet bottomSheet, View source) {
        this.bottomSheetRef = new WeakReference<>(bottomSheet);
        this.windowRef = new WeakReference<>(bottomSheet.getWindow());
        this.targetRef = new WeakReference<>(bottomSheet.getContainerView() != null ? bottomSheet.getContainerView() : bottomSheet.getContainer());
        this.sourceRef = new WeakReference<>(source);
        this.sourceGuard = new FluidSourceGuard(source);
    }

    private FluidTransform(Window window, View target, View source) {
        this.bottomSheetRef = null;
        this.windowRef = new WeakReference<>(window);
        this.targetRef = new WeakReference<>(target);
        this.sourceRef = new WeakReference<>(source);
        this.sourceGuard = new FluidSourceGuard(source);
    }

    public void open(Runnable completion) {
        if (disposed) return;
        onOpenComplete = completion;
        if (started) { reopen(); return; }
        if (pendingLayout != null) return;
        closing = false;
        onDismissComplete = null;
        View source = sourceRef.get();
        BottomSheet bs = bottomSheetRef != null ? bottomSheetRef.get() : null;
        if (bs != null && bs.getContainerView() != null) {
            targetRef = new WeakReference<>(bs.getContainerView());
        }
        View target = targetRef.get();
        Window window = windowRef.get();

        if (source == null || target == null || window == null || !source.isAttachedToWindow()) {
            return;
        }

        if (bs != null && bs.container != null) {
            host = bs.container;
        } else {
            View decor = window.getDecorView();
            if (!(decor instanceof ViewGroup)) return;
            host = (ViewGroup) decor;
        }

        FluidCapabilities.motionScale(target.getContext());
        FluidCapabilities.refresh();
        target.addOnAttachStateChangeListener(detachListener);
        targetAlpha = target.getAlpha();
        targetAccessibility = target.getImportantForAccessibility();
        target.setAlpha(0f);
        target.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

        pendingLayout = () -> {
            if (target.getWidth() <= 0 || target.getHeight() <= 0 || host.getWidth() <= 0) {
                return true;
            }
            removeLayoutListener();

            if (disposed) return true;
            View origin = sourceRef.get();
            if (origin == null || !origin.isAttachedToWindow() || !origin.isShown() || !sourceGuard.valid()) {
                restoreTarget();
                if (progressListener != null) progressListener.onProgress(1f);
                runOpenComplete();
                return true;
            }

            if (!FluidCapabilities.isMotionEnabled(host.getContext())) {
                restoreTarget();
                if (progressListener != null) progressListener.onProgress(1f);
                runOpenComplete();
                return true;
            }
            sourceAlpha = origin.getAlpha();
            bridge = new MorphBridge(host.getContext());
            bridge.capture(origin, target);
            origin.setAlpha(0f);
            addBridge();

            spring = new SpringAnimation(bridge, new FloatPropertyCompat<MorphBridge>("fluidContainerProgress") {
                @Override
                public float getValue(MorphBridge view) {
                    return view.progress;
                }

                @Override
                public void setValue(MorphBridge view, float value) {
                    render(value);
                }
            });

            float scale = FluidCapabilities.motionScale(host.getContext());
            spring.setSpring(FluidTiming.springForce(scale));
            FluidCapabilities.track(host.getContext(), spring);
            spring.setMinimumVisibleChange(FluidTiming.MIN_VISIBLE_CHANGE);
            spring.addEndListener((animation, canceled, value, velocity) -> {
                if (canceled || disposed || seeking) return;
                if (closing) {
                    finishClose();
                } else {
                    render(1f);
                    removeBridge();
                    restoreTarget();
                    runOpenComplete();
                }
            });

            started = true;
            render(0f);
            spring.animateToFinalPosition(1f);
            return false; // The bridge already displays the first morphed frame.
        };

        target.getViewTreeObserver().addOnPreDrawListener(pendingLayout);
        target.invalidate();
    }

    public void close(Runnable completion) {
        if (closing) return;
        this.onDismissComplete = completion;
        View origin = sourceRef.get();
        View target = targetRef.get();

        if (!started || bridge == null || origin == null || !origin.isAttachedToWindow() || !sourceGuard.valid()
                || !FluidCapabilities.isMotionEnabled(host != null ? host.getContext() : null)) {
            render(0f);
            dispose();
            if (target != null) target.setAlpha(0f);
            if (completion != null) completion.run();
            return;
        }

        closing = true;
        if (!spring.isRunning() && target != null) {
            bridge.capture(origin, target);
        }
        if (target != null) {
            target.setAlpha(0f);
            target.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
        addBridge();
        retarget(0f);
    }

    public boolean isRunning() {
        return spring != null && spring.isRunning();
    }

    public boolean isStarted() {
        return started && !disposed;
    }

    public boolean isClosing() {
        return closing;
    }

    private void runOpenComplete() {
        Runnable done = onOpenComplete;
        onOpenComplete = null;
        if (done != null) done.run();
    }

    private void reopen() {
        if (!started || disposed) return;
        closing = false;
        onDismissComplete = null;
        // Retarget the same spring, preserving live geometry and velocity.
        if (onCloseCancelled != null) onCloseCancelled.run();
        retarget(1f);
    }

    private void finishClose() {
        if (!started) return;
        Runnable done = onDismissComplete;
        onDismissComplete = null;
        render(0f);
        dispose();
        // Keep the native target hidden until its owning Dialog is actually removed.
        View target = targetRef.get();
        if (target != null) target.setAlpha(0f);
        if (done != null) done.run();
    }

    public void abort() {
        onDismissComplete = null;
        dispose();
        restoreTarget();
    }

    private void addBridge() {
        if (bridge != null && bridge.getParent() == null && host != null) {
            host.addView(bridge, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            bridge.layout(0, 0, host.getWidth(), host.getHeight());
        }
    }

    private void removeBridge() {
        if (bridge != null && host != null && bridge.getParent() == host) {
            host.removeView(bridge);
        }
    }

    private void restoreTarget() {
        View target = targetRef.get();
        if (target != null) {
            target.setAlpha(targetAlpha);
            target.setImportantForAccessibility(targetAccessibility);
        }
    }

    private void removeLayoutListener() {
        View target = targetRef.get();
        if (pendingLayout != null && target != null && target.getViewTreeObserver().isAlive()) {
            target.getViewTreeObserver().removeOnPreDrawListener(pendingLayout);
        }
        pendingLayout = null;
    }

    private void dispose() {
        if (disposed) return;
        disposed = true;
        onOpenComplete = null;
        seeking = false;
        View target = targetRef.get();
        if (target != null) target.removeOnAttachStateChangeListener(detachListener);
        removeLayoutListener();
        if (spring != null) spring.cancel();
        removeBridge();
        restoreTarget();

        View origin = sourceRef.get();
        if (started && origin != null) {
            origin.setAlpha(sourceAlpha);
        }
        started = false;
    }

    /** Consume window coordinates during a morph, without cross-window touch forwarding. */
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (!started || disposed || bridge == null || bridge.getParent() == null) return false;
        if (event.getActionMasked() == MotionEvent.ACTION_UP && closing && onCloseCancelled != null) {
            float x = event.getRawX(), y = event.getRawY();
            if (bridge.frame.contains(x, y) || bridge.start.contains(x, y)) reopen();
        }
        return true;
    }

    public FluidPredictiveBack.Motion backMotion(Runnable committed, java.util.function.BooleanSupplier canPreview) {
        return new FluidPredictiveBack.Motion() {
            private FluidPredictiveBack.Motion preview;

            @Override
            public void start() {
                View origin = sourceRef.get();
                View target = targetRef.get();
                if (closing && onCloseCancelled == null || !canPreview.getAsBoolean() || !started || disposed || bridge == null || origin == null || target == null
                        || !origin.isAttachedToWindow() || !sourceGuard.valid()
                        || !FluidCapabilities.isMotionEnabled(host != null ? host.getContext() : null)) {
                    return;
                }
                if (closing && onCloseCancelled != null) onCloseCancelled.run();
                closing = false;
                onDismissComplete = null;
                seeking = true;
                if (!spring.isRunning()) bridge.capture(origin, target);
                preview = FluidPredictiveBack.spring(
                        host.getContext(),
                        spring,
                        () -> started && !closing,
                        () -> bridge.progress,
                        value -> {
                            render((float) value);
                        },
                        () -> {
                            // A discard confirmation or a layout can consume Back. Restore the
                            // surface unless dismiss() immediately retargets this spring to zero.
                            retarget(1f);
                            committed.run();
                        }
                );
                preview.start();
                target.setAlpha(0f);
                target.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                addBridge();
            }

            @Override
            public void update(float progress) {
                if (preview != null && !FluidCapabilities.isMotionEnabled(host.getContext())) {
                    cancel();
                } else if (preview != null) preview.update(progress);
            }

            @Override
            public void cancel() {
                seeking = false;
                if (preview != null) preview.cancel();
                preview = null;
            }

            @Override
            public void commit() {
                seeking = false;
                FluidPredictiveBack.Motion current = preview;
                preview = null;
                if (current != null) current.commit();
                else committed.run();
            }
        };
    }

    /**
     * MorphBridge: Renders the continuous 64-point cubic-spline contour and draws the live views.
     */
    private final class MorphBridge extends View {
        private static final int STEPS = 8;
        private static final int COUNT = STEPS * 8;

        private final RectF start = new RectF();
        private final RectF end = new RectF();
        private final RectF frame = new RectF();
        private final RectF sourceBody = new RectF();

        private final Path shape = new Path();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float[] points = new float[COUNT * 2];
        private final int[] location = new int[2];

        private float progress;
        private float sourceRadius;
        private float directionX;
        private float directionY;
        private final float[] targetCorners = new float[4];
        private int sourceColor;
        private int targetColor;
        private int count;
        private float targetX, targetY;

        MorphBridge(Context context) {
            super(context);
            setWillNotDraw(false);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            setClickable(true);
        }

        void capture(View origin, View target) {
            target.getLocationOnScreen(location);
            targetX = location[0]; targetY = location[1];
            BottomSheet sheet = bottomSheetRef == null ? null : bottomSheetRef.get();
            if (sheet != null) sheet.getFluidSurfaceBounds(end);
            else end.set(0, 0, target.getWidth(), target.getHeight());
            if (end.isEmpty()) end.set(0, 0, target.getWidth(), target.getHeight());
            end.offset(targetX, targetY);
            updateOrigin(origin);

            sourceColor = Theme.getColor(Theme.key_windowBackgroundWhite, sheet == null ? null : sheet.getResourcesProvider());
            for (View view = origin; view != null; ) {
                if (view.getBackground() instanceof ColorDrawable) {
                    sourceColor = ((ColorDrawable) view.getBackground()).getColor();
                    break;
                }
                view = view.getParent() instanceof View ? (View) view.getParent() : null;
            }

            targetColor = sheet == null ? Theme.getColor(Theme.key_dialogBackground) : sheet.getFluidSurfaceColor();
            if (target.getBackground() instanceof ColorDrawable) {
                targetColor = ((ColorDrawable) target.getBackground()).getColor();
            }

            float sheetRadius = AndroidUtilities.dp(28f);
            targetCorners[0] = sheetRadius;
            targetCorners[1] = sheetRadius;
            targetCorners[2] = 0f;
            targetCorners[3] = 0f;
        }

        private void updateOrigin(View origin) {
            origin.getLocationOnScreen(location);
            start.set(location[0], location[1], location[0] + origin.getWidth(), location[1] + origin.getHeight());
            sourceRadius = Math.min(start.width(), start.height()) / 2f;

            float dx = start.centerX() - end.centerX();
            float dy = start.centerY() - end.centerY();
            float distance = Math.max(1f, (float) Math.hypot(dx, dy));
            directionX = dx / distance;
            directionY = dy / distance;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            View origin = sourceRef.get();
            View target = targetRef.get();

            float p = Math.max(0f, Math.min(1.04f, progress));
            float alphaProgress = Math.max(0f, Math.min(1f, p));

            float topProgress = smooth(0.12f, 1f, alphaProgress);
            frame.set(
                    mix(start.left, end.left, p),
                    mix(start.top, end.top, topProgress),
                    mix(start.right, end.right, p),
                    mix(start.bottom, end.bottom, p)
            );

            float half = Math.max(0f, Math.min(frame.width(), frame.height()) / 2f);
            float tl = Math.min(half, Math.max(0f, mix(sourceRadius, targetCorners[0], p)));
            float tr = Math.min(half, Math.max(0f, mix(sourceRadius, targetCorners[1], p)));
            float br = Math.min(half, Math.max(0f, mix(sourceRadius, targetCorners[2], p)));
            float bl = Math.min(half, Math.max(0f, mix(sourceRadius, targetCorners[3], p)));

            count = 0;
            edge(frame.left + tl, frame.top, frame.right - tr, frame.top);
            arc(frame.right - tr, frame.top + tr, tr, -Math.PI / 2);
            edge(frame.right, frame.top + tr, frame.right, frame.bottom - br);
            arc(frame.right - br, frame.bottom - br, br, 0);
            edge(frame.right - br, frame.bottom, frame.left + bl, frame.bottom);
            arc(frame.left + bl, frame.bottom - bl, bl, Math.PI / 2);
            edge(frame.left, frame.bottom - bl, frame.left, frame.top + tl);
            arc(frame.left + tl, frame.top + tl, tl, Math.PI);

            shape.reset();
            shape.moveTo(points[0], points[1]);
            for (int i = 0; i < count; i++) {
                int prev = ((i + count - 1) % count) * 2;
                int current = i * 2;
                int next = ((i + 1) % count) * 2;
                int after = ((i + 2) % count) * 2;

                shape.cubicTo(
                        points[current] + (points[next] - points[prev]) / 6f,
                        points[current + 1] + (points[next + 1] - points[prev + 1]) / 6f,
                        points[next] - (points[after] - points[current]) / 6f,
                        points[next + 1] - (points[after + 1] - points[current + 1]) / 6f,
                        points[next],
                        points[next + 1]
                );
            }
            shape.close();

            getLocationOnScreen(location);
            int save = canvas.save();
            canvas.translate(-location[0], -location[1]);

            paint.setColor(ColorUtils.blendARGB(sourceColor, targetColor, alphaProgress));
            canvas.drawPath(shape, paint);
            canvas.clipPath(shape);

            // Draw origin view (native size, fading out gently)
            float outgoing = 1f - smooth(0.015f, 0.08f, alphaProgress);
            if (origin != null && outgoing > 0f) {
                int alpha = Math.round(outgoing * sourceAlpha * 255f);
                sourceBody.set(
                        start.centerX() - origin.getWidth() / 2f - 1f,
                        start.centerY() - origin.getHeight() / 2f - 1f,
                        start.centerX() + origin.getWidth() / 2f + 1f,
                        start.centerY() + origin.getHeight() / 2f + 1f
                );
                int layer = alpha >= 255 ? canvas.save() : canvas.saveLayerAlpha(sourceBody, alpha);
                canvas.translate(start.centerX() - origin.getWidth() / 2f, start.centerY() - origin.getHeight() / 2f);
                origin.draw(canvas);
                canvas.restoreToCount(layer);
            }

            // Draw target layout (native scale, smooth incoming crossfade)
            float incoming = smooth(0.35f, 0.80f, alphaProgress);
            if (incoming > 0f && target != null) {
                int alpha = Math.round(incoming * targetAlpha * 255f);
                int layer = alpha >= 255 ? canvas.save() : canvas.saveLayerAlpha(frame, alpha);
                canvas.translate(targetX, targetY);
                target.draw(canvas);
                canvas.restoreToCount(layer);
            }

            canvas.restoreToCount(save);
        }

        private void edge(float x1, float y1, float x2, float y2) {
            for (int i = 0; i < STEPS; i++) {
                float f = i / (float) STEPS;
                point(mix(x1, x2, f), mix(y1, y2, f));
            }
        }

        private void arc(float cx, float cy, float radius, double startAngle) {
            for (int i = 0; i < STEPS; i++) {
                double a = startAngle + Math.PI / 2 * i / STEPS;
                point(cx + radius * (float) Math.cos(a), cy + radius * (float) Math.sin(a));
            }
        }

        private void point(float x, float y) {
            float p = Math.max(0f, Math.min(1f, progress));
            float envelope = smooth(0f, 0.10f, p) * (1f - smooth(0.20f, 0.62f, p));
            float dx = x - frame.centerX();
            float dy = y - frame.centerY();
            float distance = Math.max(1f, (float) Math.hypot(dx, dy));
            float facing = Math.max(0f, (dx * directionX + dy * directionY) / distance);

            float weight = facing * facing * envelope * 0.12f;
            float tipX = start.centerX() + directionX * sourceRadius * (1f - p);
            float tipY = start.centerY() + directionY * sourceRadius * (1f - p);

            points[count * 2] = mix(x, tipX, weight);
            points[count * 2 + 1] = mix(y, tipY, weight);
            count++;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) { return true; }
    }

    private static float mix(float a, float b, float p) {
        return a + (b - a) * p;
    }

    private static float smooth(float low, float high, float value) {
        float p = Math.max(0f, Math.min(1f, (value - low) / (high - low)));
        return p * p * (3f - 2f * p);
    }
}

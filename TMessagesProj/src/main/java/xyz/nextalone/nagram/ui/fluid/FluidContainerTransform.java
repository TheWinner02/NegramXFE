package xyz.nextalone.nagram.ui.fluid;

import android.graphics.Outline;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

import java.lang.ref.WeakReference;

/**
 * Real-view Container Transform for the Fluid theme.
 * Transforms the real container's visible geometry, clipping, and corner radius
 * without fake screenshots or scaling content. Content maintains native proportions
 * and is progressively revealed via GPU-accelerated Outline clipping.
 */
public final class FluidContainerTransform {

    private final WeakReference<View> sourceRef;
    private final WeakReference<ViewGroup> containerViewRef;
    private final WeakReference<Drawable> dimDrawableRef;
    private final int dimBehindAlpha;

    private float progress = 0f;
    private boolean morphing = false;
    private boolean closing = false;
    private boolean opened = false;
    private Runnable onComplete;

    private final RectF startBounds = new RectF();
    private final RectF targetBounds = new RectF();
    private final RectF currentBounds = new RectF();
    private final RectF cachedSourceBounds = new RectF();

    private final int[] sourceLoc = new int[2];
    private final int[] sheetLoc = new int[2];

    private float initialSourceAlpha = 1f;
    private float targetRadius = AndroidUtilities.dp(28f);
    private float sourceRadius = AndroidUtilities.dp(28f);

    private SpringAnimation spring;
    private ViewTreeObserver.OnPreDrawListener preDrawListener;
    private ViewOutlineProvider originalOutlineProvider;

    private final ViewOutlineProvider morphOutlineProvider = new ViewOutlineProvider() {
        @Override
        public void getOutline(View view, Outline outline) {
            computeCurrentBounds(progress);
            float curRadius = mix(sourceRadius, targetRadius, progress);
            outline.setRoundRect(
                    Math.round(currentBounds.left),
                    Math.round(currentBounds.top),
                    Math.round(currentBounds.right),
                    Math.round(currentBounds.bottom),
                    Math.max(0f, curRadius)
            );
        }
    };

    public FluidContainerTransform(View sourceView, ViewGroup containerView, Drawable dimDrawable, int dimAlpha, float sheetRadius) {
        this.sourceRef = new WeakReference<>(sourceView);
        this.containerViewRef = new WeakReference<>(containerView);
        this.dimDrawableRef = new WeakReference<>(dimDrawable);
        this.dimBehindAlpha = dimAlpha;
        this.targetRadius = sheetRadius;

        if (sourceView != null) {
            this.initialSourceAlpha = sourceView.getAlpha();
            this.sourceRadius = Math.min(sourceView.getWidth() > 0 ? sourceView.getWidth() : AndroidUtilities.dp(56),
                    sourceView.getHeight() > 0 ? sourceView.getHeight() : AndroidUtilities.dp(56)) / 2f;
        }
    }

    public void open() {
        open(null);
    }

    public void open(Runnable onOpenEnd) {
        ViewGroup containerView = containerViewRef.get();
        View sourceView = sourceRef.get();
        if (containerView == null || sourceView == null || !sourceView.isAttachedToWindow()) {
            return;
        }

        closing = false;
        morphing = true;
        opened = false;

        // Ensure container is laid out before measuring final bounds
        preDrawListener = () -> {
            if (containerView.getWidth() <= 0 || containerView.getHeight() <= 0) {
                return true;
            }
            if (preDrawListener != null && containerView.getViewTreeObserver().isAlive()) {
                containerView.getViewTreeObserver().removeOnPreDrawListener(preDrawListener);
                preDrawListener = null;
            }

            setupGeometry(containerView, sourceView);
            startSpring(1f, () -> {
                morphing = false;
                opened = true;
                containerView.setAlpha(1f);
                containerView.setClipToOutline(false);
                if (originalOutlineProvider != null) {
                    containerView.setOutlineProvider(originalOutlineProvider);
                }
                if (onOpenEnd != null) {
                    onOpenEnd.run();
                }
            });
            return true;
        };

        containerView.getViewTreeObserver().addOnPreDrawListener(preDrawListener);
        containerView.invalidate();
    }

    private void setupGeometry(ViewGroup containerView, View sourceView) {
        sourceView.getLocationOnScreen(sourceLoc);
        containerView.getLocationOnScreen(sheetLoc);

        float sLeft = sourceLoc[0] - sheetLoc[0];
        float sTop = sourceLoc[1] - sheetLoc[1];
        float sRight = sLeft + sourceView.getWidth();
        float sBottom = sTop + sourceView.getHeight();

        startBounds.set(sLeft, sTop, sRight, sBottom);
        cachedSourceBounds.set(startBounds);
        sourceRadius = Math.min(startBounds.width(), startBounds.height()) / 2f;

        targetBounds.set(0, 0, containerView.getWidth(), containerView.getHeight());

        originalOutlineProvider = containerView.getOutlineProvider();
        containerView.setOutlineProvider(morphOutlineProvider);
        containerView.setClipToOutline(true);

        updateProgress(0f);
    }

    private void startSpring(float finalPos, Runnable onEnd) {
        ViewGroup containerView = containerViewRef.get();
        if (containerView == null) return;

        if (spring != null) {
            spring.cancel();
        }

        spring = new SpringAnimation(containerView, new FloatPropertyCompat<ViewGroup>("fluidProgress") {
            @Override
            public float getValue(ViewGroup view) {
                return progress;
            }

            @Override
            public void setValue(ViewGroup view, float value) {
                updateProgress(value);
            }
        });

        float scale = FluidCapabilities.motionScale(containerView.getContext());
        spring.setSpring(FluidTiming.springForce(scale));
        FluidCapabilities.track(containerView.getContext(), spring);
        spring.setMinimumVisibleChange(FluidTiming.MIN_VISIBLE_CHANGE);
        spring.addEndListener((animation, canceled, value, velocity) -> {
            if (canceled) return;
            if (onEnd != null) onEnd.run();
        });

        spring.animateToFinalPosition(finalPos);
    }

    public void updateProgress(float p) {
        progress = Math.max(0f, Math.min(1.05f, p));
        ViewGroup containerView = containerViewRef.get();
        if (containerView != null) {
            containerView.invalidateOutline();
            float contentAlpha = Math.max(0f, Math.min(1f, progress / 0.12f));
            containerView.setAlpha(contentAlpha);
        }

        Drawable dim = dimDrawableRef.get();
        if (dim != null) {
            dim.setAlpha(Math.round(dimBehindAlpha * Math.min(1f, progress)));
        }

        View source = sourceRef.get();
        if (source != null) {
            float sAlpha = Math.max(0f, 1f - (progress / 0.15f));
            source.setAlpha(initialSourceAlpha * sAlpha);
        }
    }

    private void computeCurrentBounds(float p) {
        float normP = Math.max(0f, Math.min(1f, p));
        float topP = smooth(0.06f, 1.0f, normP);
        float bottomExt = mix(startBounds.bottom, targetBounds.bottom + (normP * targetRadius), normP);

        currentBounds.set(
                mix(startBounds.left, targetBounds.left, normP),
                mix(startBounds.top, targetBounds.top, topP),
                mix(startBounds.right, targetBounds.right, normP),
                bottomExt
        );
    }

    public void close(Runnable completion) {
        if (closing) return;
        closing = true;
        morphing = true;
        this.onComplete = completion;

        ViewGroup containerView = containerViewRef.get();
        if (containerView == null || !FluidCapabilities.isMotionEnabled(containerView.getContext())) {
            finishClose();
            return;
        }

        containerView.setClipToOutline(true);
        containerView.setOutlineProvider(morphOutlineProvider);
        startSpring(0f, this::finishClose);
    }

    private void finishClose() {
        morphing = false;
        opened = false;
        closing = false;

        View source = sourceRef.get();
        if (source != null) {
            source.setAlpha(initialSourceAlpha);
        }

        ViewGroup containerView = containerViewRef.get();
        if (containerView != null) {
            containerView.setAlpha(1f);
            containerView.setClipToOutline(false);
            if (originalOutlineProvider != null) {
                containerView.setOutlineProvider(originalOutlineProvider);
            }
        }

        if (onComplete != null) {
            Runnable done = onComplete;
            onComplete = null;
            done.run();
        }
    }

    public boolean isMorphing() {
        return morphing;
    }

    public boolean isClosing() {
        return closing;
    }

    public boolean isOpened() {
        return opened;
    }

    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (morphing && !closing) {
            if (!currentBounds.contains(ev.getX(), ev.getY())) {
                return true; // Ignore touch outside visible clipped surface while morphing
            }
        }
        return false;
    }

    public FluidPredictiveBack.Motion backMotion(Runnable committed) {
        return new FluidPredictiveBack.Motion() {
            private FluidPredictiveBack.Motion preview;

            @Override
            public void start() {
                ViewGroup containerView = containerViewRef.get();
                if (containerView == null || closing || !opened
                        || !FluidCapabilities.isMotionEnabled(containerView.getContext())) {
                    return;
                }
                morphing = true;
                containerView.setClipToOutline(true);
                containerView.setOutlineProvider(morphOutlineProvider);
                preview = FluidPredictiveBack.spring(
                        containerView.getContext(),
                        spring,
                        () -> opened && !closing,
                        () -> progress,
                        value -> updateProgress((float) value),
                        committed
                );
                preview.start();
            }

            @Override
            public void update(float progress) {
                if (preview != null) preview.update(progress);
            }

            @Override
            public void cancel() {
                if (preview != null) preview.cancel();
                preview = null;
                ViewGroup containerView = containerViewRef.get();
                if (containerView != null) {
                    containerView.setClipToOutline(false);
                    if (originalOutlineProvider != null) {
                        containerView.setOutlineProvider(originalOutlineProvider);
                    }
                }
            }

            @Override
            public void commit() {
                FluidPredictiveBack.Motion current = preview;
                preview = null;
                if (current != null) current.commit();
                else committed.run();
            }
        };
    }

    public void abort() {
        if (spring != null) spring.cancel();
        finishClose();
    }

    private static float mix(float a, float b, float p) {
        return a + (b - a) * p;
    }

    private static float smooth(float low, float high, float value) {
        float p = Math.max(0f, Math.min(1f, (value - low) / (high - low)));
        return p * p * (3f - 2f * p);
    }
}

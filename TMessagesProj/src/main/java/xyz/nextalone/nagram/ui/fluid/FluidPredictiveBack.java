package xyz.nextalone.nagram.ui.fluid;

import android.content.Context;
import android.os.Build;
import android.view.Window;
import android.window.BackEvent;
import android.window.OnBackAnimationCallback;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.dynamicanimation.animation.SpringAnimation;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * Predictive back gestures for Fluid containers and sheets.
 * Advances geometry dynamically with finger touch, committing or cancelling continuously.
 */
public final class FluidPredictiveBack {
    private FluidPredictiveBack() {}

    public interface Motion {
        void start();
        void update(float progress);
        void cancel();
        void commit();
    }

    public static Motion spring(Context context, SpringAnimation spring, BooleanSupplier available,
                                DoubleSupplier value, DoubleConsumer render, Runnable commit) {
        return new Motion() {
            private boolean seeking;
            private float origin, velocity, lastValue;
            private long lastTime;

            @Override
            public void start() {
                seeking = available.getAsBoolean() && FluidCapabilities.motionScale(context) > 0f;
                if (!seeking) return;
                origin = Math.max(0f, Math.min(1f, (float) value.getAsDouble()));
                spring.cancel();
                velocity = 0f;
                lastValue = origin;
                lastTime = android.os.SystemClock.uptimeMillis();
            }

            @Override
            public void update(float progress) {
                if (seeking && available.getAsBoolean()) {
                    float next = origin * (1f - 0.9f * Math.max(0f, Math.min(1f, progress)));
                    long now = android.os.SystemClock.uptimeMillis();
                    if (now > lastTime) {
                        velocity = Math.max(-3f, Math.min(3f, (next - lastValue) * 1000f / (now - lastTime)));
                    }
                    lastValue = next;
                    lastTime = now;
                    render.accept(next);
                }
            }

            @Override
            public void cancel() {
                if (!seeking) return;
                seeking = false;
                if (!available.getAsBoolean()) return;
                float scale = FluidCapabilities.motionScale(context);
                spring.setStartVelocity(releaseVelocity());
                spring.getSpring().setStiffness(FluidTiming.stiffness(Math.max(0.01f, scale)));
                spring.animateToFinalPosition(1f);
                if (scale <= 0f) spring.skipToEnd();
            }

            private float releaseVelocity() {
                return (android.os.SystemClock.uptimeMillis() - lastTime > 80L) ? 0f : velocity;
            }

            @Override
            public void commit() {
                if (seeking) {
                    spring.setStartVelocity(releaseVelocity());
                    spring.getSpring().setStiffness(FluidTiming.stiffness(
                            Math.max(0.01f, FluidCapabilities.motionScale(context))));
                }
                seeking = false;
                commit.run();
            }
        };
    }

    public static Runnable installDialog(Window window, Motion motion) {
        if (window == null || Build.VERSION.SDK_INT < 33 || motion == null) return () -> {};
        return Build.VERSION.SDK_INT >= 34 ? Api34.install(window, motion) : Api33.install(window, motion);
    }

    @RequiresApi(34)
    private static final class Api34 {
        static Runnable install(Window window, Motion motion) {
            OnBackInvokedDispatcher dispatcher = window.getOnBackInvokedDispatcher();
            OnBackAnimationCallback callback = new OnBackAnimationCallback() {
                @Override public void onBackStarted(@NonNull BackEvent event) { motion.start(); }
                @Override public void onBackProgressed(@NonNull BackEvent event) { motion.update(event.getProgress()); }
                @Override public void onBackCancelled() { motion.cancel(); }
                @Override public void onBackInvoked() { motion.commit(); }
            };
            dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
            return () -> dispatcher.unregisterOnBackInvokedCallback(callback);
        }
    }

    @RequiresApi(33)
    private static final class Api33 {
        static Runnable install(Window window, Motion motion) {
            OnBackInvokedDispatcher dispatcher = window.getOnBackInvokedDispatcher();
            OnBackInvokedCallback callback = motion::commit;
            dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
            return () -> dispatcher.unregisterOnBackInvokedCallback(callback);
        }
    }
}

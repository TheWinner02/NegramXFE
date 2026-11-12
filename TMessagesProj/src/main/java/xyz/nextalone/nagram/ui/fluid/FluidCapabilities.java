package xyz.nextalone.nagram.ui.fluid;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import androidx.annotation.MainThread;
import androidx.core.content.ContextCompat;
import androidx.dynamicanimation.animation.SpringAnimation;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * Global policy for physical movement in the Fluid theme.
 * Monitors system animation scale, battery saver, and coordinates all active spring animations.
 */
public final class FluidCapabilities {
    private static final ArrayList<WeakReference<SpringAnimation>> springs = new ArrayList<>();
    private static Context application;
    private static volatile boolean motionDisabled;
    private static volatile boolean initialized;
    private static volatile float cachedScale = 1f;
    private static Handler main;
    private static int registrations;
    private static final Runnable refreshTask = FluidCapabilities::refreshPolicy;

    private FluidCapabilities() {}

    public static float motionScale(Context context) {
        ensureInitialized(context);
        return cachedScale;
    }

    public static boolean isMotionEnabled(Context context) {
        ensureInitialized(context);
        return !motionDisabled && cachedScale > 0f;
    }

    private static float readScale(Context context) {
        if (context == null) return 1f;
        try {
            PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (power != null && power.isPowerSaveMode()) {
                return 0f;
            }
            float scale = Settings.Global.getFloat(context.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
            return Float.isFinite(scale) ? Math.max(0f, scale) : 1f;
        } catch (Throwable ignored) {
            return 1f;
        }
    }

    private static void ensureInitialized(Context context) {
        if (initialized) return;
        synchronized (FluidCapabilities.class) {
            if (initialized) return;
            application = context != null ? context.getApplicationContext() : org.telegram.messenger.ApplicationLoader.applicationContext;
            if (application == null) return;

            main = new Handler(Looper.getMainLooper());
            try {
                ContextCompat.registerReceiver(
                        application,
                        new BroadcastReceiver() {
                            @Override
                            public void onReceive(Context ignored, Intent intent) {
                                refresh();
                            }
                        },
                        new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
                        ContextCompat.RECEIVER_NOT_EXPORTED
                );
            } catch (Throwable ignored) {}

            try {
                application.getContentResolver().registerContentObserver(
                        Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
                        false,
                        new ContentObserver(main) {
                            @Override
                            public void onChange(boolean selfChange) {
                                refresh();
                            }
                        }
                );
            } catch (Throwable ignored) {}

            cachedScale = readScale(application);
            motionDisabled = cachedScale <= 0f;
            initialized = true;
        }
    }

    @MainThread
    public static void track(Context context, SpringAnimation animation) {
        if (animation == null) return;
        ensureInitialized(context);
        if (++registrations % 64 == 0) {
            springs.removeIf(reference -> reference.get() == null);
        }
        springs.add(new WeakReference<>(animation));
        animation.addUpdateListener((running, value, velocity) -> {
            if (motionDisabled && animation.isRunning() && animation.canSkipToEnd()
                    && (value != animation.getSpring().getFinalPosition() || velocity != 0f)) {
                animation.skipToEnd();
            }
        });
    }

    public static void refresh() {
        if (!initialized) return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            refreshPolicy();
        } else if (main != null) {
            main.removeCallbacks(refreshTask);
            main.post(refreshTask);
        }
    }

    private static void refreshPolicy() {
        cachedScale = readScale(application);
        motionDisabled = cachedScale <= 0f;
        if (!motionDisabled) return;

        springs.removeIf(reference -> reference.get() == null);
        for (WeakReference<SpringAnimation> reference : new ArrayList<>(springs)) {
            SpringAnimation animation = reference.get();
            if (animation != null && animation.isRunning() && animation.canSkipToEnd()) {
                animation.skipToEnd();
            }
        }
    }
}

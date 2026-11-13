package xyz.nextalone.nagram.ui.fluid;

import android.view.MotionEvent;
import java.util.function.BooleanSupplier;

/** One lifecycle contract for anchored transforms and unanchored native surface reveals. */
public interface FluidMotion {
    interface ProgressListener { void onProgress(float progress); }
    void setProgressListener(ProgressListener listener);
    void setOnCloseCancelled(Runnable listener);
    void open(Runnable completion);
    void close(Runnable completion);
    void abort();
    boolean isStarted();
    boolean dispatchTouchEvent(MotionEvent event);
    FluidPredictiveBack.Motion backMotion(Runnable committed, BooleanSupplier canPreview);
}

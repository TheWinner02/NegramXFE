package xyz.nextalone.nagram.ui.fluid;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.content.Context;
import android.view.animation.LinearInterpolator;

import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;

/** Drives an existing together-timeline with Fluid physics, retaining its native end callbacks. */
public final class FluidAnimator {
    private static final long TIMELINE = 1000L;
    private final AnimatorSet animator;
    private final SpringAnimation spring;
    private boolean finished;

    private FluidAnimator(Context context, AnimatorSet animator) {
        this.animator = animator;
        spring = new SpringAnimation(new FloatValueHolder(0f));
        spring.setSpring(FluidTiming.springForce(FluidCapabilities.motionScale(context)));
        spring.setMinimumVisibleChange(FluidTiming.MIN_VISIBLE_CHANGE);
        FluidCapabilities.track(context, spring);
        spring.addUpdateListener((animation, value, velocity) -> {
            if (!finished) animator.setCurrentPlayTime((long) (Math.max(0f, Math.min(1f, value)) * TIMELINE));
        });
        spring.addEndListener((animation, cancelled, value, velocity) -> {
            if (cancelled || finished) return;
            finished = true;
            animator.end();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                finished = true;
                spring.cancel();
            }
        });
    }

    public static FluidAnimator start(Context context, AnimatorSet animator) {
        FluidAnimator motion = new FluidAnimator(context, animator);
        animator.setDuration(TIMELINE);
        animator.setInterpolator(new LinearInterpolator());
        if (FluidCapabilities.motionScale(context) <= 0f) {
            // AnimatorSet.end() before start() need not initialize values or deliver its
            // parent callbacks. Start and finish in this UI turn so owners always unlock.
            animator.start();
            if (animator.isStarted()) animator.end();
        } else {
            animator.start();
            animator.pause();
            animator.setCurrentPlayTime(0L);
            FluidTiming.animate(context, motion.spring, 1f);
        }
        return motion;
    }

    public boolean isRunning() {
        return !finished && spring.isRunning();
    }

    /** Owner teardown must not deliver callbacks into a destroyed viewer. */
    public void abort() {
        if (finished) return;
        finished = true;
        spring.cancel();
        animator.removeAllListeners();
        animator.cancel();
    }
}

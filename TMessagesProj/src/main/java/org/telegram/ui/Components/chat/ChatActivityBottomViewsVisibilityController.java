package org.telegram.ui.Components.chat;

import java.util.Arrays;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import org.telegram.messenger.ApplicationLoader;
import xyz.nextalone.nagram.ui.UIStyleEngine;
import xyz.nextalone.nagram.ui.fluid.FluidAnimator;
import xyz.nextalone.nagram.ui.fluid.FluidCapabilities;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.ListAnimator;
import me.vkryl.android.animator.ReplaceAnimator;
import me.vkryl.core.BitwiseUtils;

public class ChatActivityBottomViewsVisibilityController implements ReplaceAnimator.Callback {
    private final float[] visibilityValues = new float[32];
    private final Runnable onValuesChanged;

    private final ReplaceAnimator<Integer> replaceAnimator;
    private final boolean fluid = UIStyleEngine.isFluid();
    private AnimatorSet fluidAnimation;
    private boolean destroyed;

    public ChatActivityBottomViewsVisibilityController(Runnable onValuesChanged) {
        if (fluid) {
            // ReplaceAnimator already exposes a manual timeline. Keep its priority and
            // interrupted-entry bookkeeping; only Fluid owns the progression clock.
            this.replaceAnimator = new ReplaceAnimator<>(this);
        } else {
            this.replaceAnimator = new ReplaceAnimator<>(this, AnimatorUtils.DECELERATE_INTERPOLATOR, 240);
        }
        this.onValuesChanged = onValuesChanged;
    }



    public float getVisibility(int containerId) {
        return visibilityValues[containerId];
    }

    private int visibilityFlags = 1;

    public void setViewVisible(int containerId, boolean isVisible, boolean animated) {
        if (destroyed) return;
        final int oldPriorityContainerId = getCurrentPriorityContainerId();
        visibilityFlags = BitwiseUtils.setFlag(visibilityFlags, 1 << containerId, isVisible);

        final int newPriorityContainerId = getCurrentPriorityContainerId();
        if (oldPriorityContainerId != newPriorityContainerId) {
            if (fluid) {
                AnimatorSet previous = fluidAnimation;
                fluidAnimation = null;
                if (previous != null) previous.cancel();
                boolean move = animated && FluidCapabilities.isMotionEnabled(ApplicationLoader.applicationContext);
                replaceAnimator.replace(newPriorityContainerId, move);
                if (move) {
                    ValueAnimator progress = ValueAnimator.ofFloat(0f, 1f);
                    progress.addUpdateListener(a -> replaceAnimator.applyAnimation((float) a.getAnimatedValue()));
                    AnimatorSet motion = new AnimatorSet();
                    motion.playTogether(progress);
                    fluidAnimation = motion;
                    motion.addListener(new AnimatorListenerAdapter() {
                        @Override public void onAnimationEnd(Animator animation) {
                            if (fluidAnimation == animation) fluidAnimation = null;
                        }
                    });
                    FluidAnimator.start(ApplicationLoader.applicationContext, motion);
                }
            } else {
                replaceAnimator.replace(newPriorityContainerId, animated);
            }
        }
    }

    public void destroy() {
        if (destroyed) return;
        destroyed = true;
        AnimatorSet previous = fluidAnimation;
        fluidAnimation = null;
        if (previous != null) previous.cancel();
        replaceAnimator.stopAnimation(false);
        replaceAnimator.clear(false);
        Arrays.fill(visibilityValues, 0f);
    }

    public int getCurrentPriorityContainerId() {
        return 31 - Integer.numberOfLeadingZeros(visibilityFlags);
    }


    @Override
    public void onItemChanged(ReplaceAnimator<?> animator) {
        onItemChanged();
    }

    @Override
    public void onForceApplyChanges(ReplaceAnimator<?> animator) {
        onItemChanged();
    }

    private void onItemChanged () {
        if (destroyed) return;
        Arrays.fill(visibilityValues, 0);
        for (ListAnimator.Entry<Integer> entry: replaceAnimator) {
            final int containerId = entry.item;
            visibilityValues[containerId] = entry.getVisibility();
        }
        onValuesChanged.run();
    }
}

package xyz.nextalone.nagram.ui.fluid;

import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.Property;
import android.view.View;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import org.telegram.messenger.R;
import xyz.nextalone.nagram.ui.UIStyleEngine;

/** Expands only the native background. Text, icons and hit targets keep their native dimensions. */
public final class FluidPressMotion {
    private final View view;
    private final SpringAnimation spring;
    private final Rect rest = new Rect();
    private Drawable background;
    private float progress, target;
    private boolean haptic, disposed;
    private final View.OnAttachStateChangeListener detach = new View.OnAttachStateChangeListener() {
        @Override public void onViewAttachedToWindow(View owner) {}
        @Override public void onViewDetachedFromWindow(View owner) { spring.cancel(); target = 0f; render(0f); }
    };
    private final View.OnLayoutChangeListener layout = (owner, l, t, r, b, ol, ot, or, ob) -> {
        if (r - l != or - ol || b - t != ob - ot) {
            background = owner.getBackground();
            rest.set(0, 0, owner.getWidth(), owner.getHeight());
            render(progress);
        }
    };

    private FluidPressMotion(View view, boolean haptic) {
        this.view = view; this.haptic = haptic;
        spring = new SpringAnimation(this, new FloatPropertyCompat<FluidPressMotion>("fluidPress") {
            @Override public float getValue(FluidPressMotion host) { return host.progress; }
            @Override public void setValue(FluidPressMotion host, float value) { host.render(value); }
        });
        spring.setSpring(FluidTiming.springForce(FluidCapabilities.motionScale(view.getContext())));
        spring.setMinimumVisibleChange(FluidTiming.MIN_VISIBLE_CHANGE);
        FluidCapabilities.track(view.getContext(), spring);
        view.addOnAttachStateChangeListener(detach);
        view.addOnLayoutChangeListener(layout);
    }

    public static void apply(View view, boolean haptic) {
        clear(view);
        FluidPressMotion host = new FluidPressMotion(view, haptic);
        view.setTag(R.id.fluid_press_motion, host);
        Property<View, Float> state = new Property<View, Float>(Float.class, "fluidPressState") {
            @Override public Float get(View owner) { return host.target; }
            @Override public void set(View owner, Float value) { host.retarget(value); }
        };
        // StateListAnimator selects the destination; the shared spring owns all visual timing.
        ObjectAnimator down = ObjectAnimator.ofFloat(view, state, 1f); down.setDuration(0);
        ObjectAnimator up = ObjectAnimator.ofFloat(view, state, 0f); up.setDuration(0);
        StateListAnimator states = new StateListAnimator();
        states.addState(new int[]{android.R.attr.state_enabled, android.R.attr.state_pressed}, down);
        states.addState(new int[0], up);
        view.setStateListAnimator(states);
    }
    public static void clear(View view) {
        Object tag = view.getTag(R.id.fluid_press_motion);
        if (!(tag instanceof FluidPressMotion)) return;
        FluidPressMotion host = (FluidPressMotion) tag;
        host.disposed = true;
        host.spring.cancel(); host.render(0f);
        view.removeOnAttachStateChangeListener(host.detach);
        view.removeOnLayoutChangeListener(host.layout);
        view.setTag(R.id.fluid_press_motion, null);
    }
    private void retarget(float value) {
        if (disposed || target == value) return;
        target = value;
        if (!UIStyleEngine.isFluid() || !view.isAttachedToWindow()
                || !FluidCapabilities.isMotionEnabled(view.getContext())) { spring.cancel(); render(0f); return; }
        if (value > 0f && haptic) com.exteragram.messenger.utils.system.VibratorUtils.vibrateClick(view);
        FluidTiming.animate(view.getContext(), spring, value);
    }
    private void render(float value) {
        progress = value;
        Drawable current = view.getBackground();
        if (current == null) return;
        if (current != background) {
            background = current; rest.set(current.getBounds());
            if (rest.isEmpty()) rest.set(0, 0, view.getWidth(), view.getHeight());
        }
        float p = Math.max(0f, Math.min(1.04f, value));
        int dx = Math.round(Math.min(view.getWidth() * .035f, 3f * view.getResources().getDisplayMetrics().density) * p);
        int dy = Math.round(Math.min(view.getHeight() * .035f, 2f * view.getResources().getDisplayMetrics().density) * p);
        current.setBounds(rest.left - dx, rest.top - dy, rest.right + dx, rest.bottom + dy);
        view.invalidate();
    }
}

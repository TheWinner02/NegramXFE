package xyz.nextalone.nagram.ui.fluid;

import xyz.nextalone.nagram.ui.UIStyleEngine;

/** A covered settings list keeps its bound rows until the return morph has finished. */
public final class FluidSettingsRefresh {
    private final Runnable refresh;
    private boolean covered, pending;

    public FluidSettingsRefresh(Runnable refresh) {
        this.refresh = refresh;
    }

    public void onResume() {
        if (UIStyleEngine.isFluid() && covered) {
            pending = true;
        } else {
            pending = false;
            refresh.run();
        }
    }

    public void onHidden() {
        covered = true;
    }

    public void onVisible() {
        covered = false;
        if (pending) {
            pending = false;
            refresh.run();
        }
    }
}

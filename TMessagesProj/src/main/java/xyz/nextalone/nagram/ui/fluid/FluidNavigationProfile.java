package xyz.nextalone.nagram.ui.fluid;

/** Depth only: every navigation kind retains the common spring and native gesture progress. */
public enum FluidNavigationProfile {
    PAGE(.12f, .06f, 1f),
    DETAIL(.075f, .03f, .72f),
    PEER(.04f, .012f, .45f);

    private final float parallax, scaleReduction;
    public final float scrimFactor;

    FluidNavigationProfile(float parallax, float scaleReduction, float scrimFactor) {
        this.parallax = parallax;
        this.scaleReduction = scaleReduction;
        this.scrimFactor = scrimFactor;
    }

    public static FluidNavigationProfile resolve(boolean peerConversation, int stackDepth) {
        if (peerConversation) return PEER;
        return stackDepth > 2 ? DETAIL : PAGE;
    }

    public float backgroundTranslation(float width, float covered) {
        return -Math.max(0f, width) * parallax * clamp(covered);
    }

    public float backgroundScale(float covered) {
        return 1f - scaleReduction * clamp(covered);
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}

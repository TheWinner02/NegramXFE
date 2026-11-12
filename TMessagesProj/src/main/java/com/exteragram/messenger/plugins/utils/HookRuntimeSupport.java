package com.exteragram.messenger.plugins.utils;

import android.os.Build;

/** ARM ART hooks must not run against an x86 ART runtime through native translation. */
public final class HookRuntimeSupport {
    private HookRuntimeSupport() {}

    public static boolean canLoadNativeHooks() {
        // SUPPORTED_ABIS can include arm64-v8a as a translated secondary ABI on x86.
        // Merely finding ARM in that list does not mean the host ART runtime is ARM.
        for (String abi : Build.SUPPORTED_ABIS) {
            if ("x86".equals(abi) || "x86_64".equals(abi)) return false;
        }
        return true;
    }
}

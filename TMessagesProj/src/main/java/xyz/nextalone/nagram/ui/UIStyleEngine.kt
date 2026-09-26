package xyz.nextalone.nagram.ui

import android.view.animation.Interpolator
import android.view.animation.OvershootInterpolator
import androidx.core.view.animation.PathInterpolatorCompat
import org.telegram.messenger.AndroidUtilities
import xyz.nextalone.nagram.NaConfig

object UIStyleEngine {

    // Spring / Expressive Interpolator (Google Material 3 Expressive Motion)
    @JvmStatic
    val expressiveSpringInterpolator: Interpolator by lazy {
        PathInterpolatorCompat.create(0.2f, 1.4f, 0.3f, 1.0f)
    }

    // iOS Fluid Spring Interpolator (Natural Damping)
    val iosSpringInterpolator: Interpolator by lazy {
        PathInterpolatorCompat.create(0.25f, 1.0f, 0.5f, 1.0f)
    }

    // Overshoot bouncy for small badges / switches
    @JvmStatic
    val bouncyInterpolator: Interpolator by lazy {
        OvershootInterpolator(1.8f)
    }

    @JvmStatic
    fun isMaterial3Expressive(): Boolean = NaConfig.isMaterial3Expressive()

    @JvmStatic
    fun isIosLiquidGlass(): Boolean = NaConfig.isIosLiquidGlass()

    @JvmStatic
    fun isClassic(): Boolean = NaConfig.isClassic()

    @JvmStatic
    fun getBubbleCornerRadius(): Float {
        return when {
            isMaterial3Expressive() -> AndroidUtilities.dp(22f).toFloat()
            isIosLiquidGlass() -> AndroidUtilities.dp(18f).toFloat()
            else -> AndroidUtilities.dp(16f).toFloat()
        }
    }

    @JvmStatic
    fun getCardCornerRadius(): Float {
        return when {
            isMaterial3Expressive() -> AndroidUtilities.dp(24f).toFloat()
            isIosLiquidGlass() -> AndroidUtilities.dp(32f).toFloat()
            else -> AndroidUtilities.dp(12f).toFloat()
        }
    }

    @JvmStatic
    fun getDialogCornerRadius(): Float {
        return when {
            isMaterial3Expressive() -> AndroidUtilities.dp(28f).toFloat()
            isIosLiquidGlass() -> AndroidUtilities.dp(20f).toFloat()
            else -> AndroidUtilities.dp(14f).toFloat()
        }
    }

    @JvmStatic
    fun shouldUseFloatingBars(): Boolean {
        return isMaterial3Expressive() || isIosLiquidGlass()
    }

    @JvmStatic
    fun shouldUseLiquidGlassHeader(): Boolean {
        return isIosLiquidGlass()
    }
}

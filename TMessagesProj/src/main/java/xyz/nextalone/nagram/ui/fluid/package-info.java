/**
 * Shared Fluid motion for the optional UI style, with opaque Expressive surfaces.
 *
 * <p>FluidTiming is the single source of spring stiffness, damping and visual precision.
 * FluidCapabilities observes battery saver and system animation scale, finishes tracked springs
 * through their normal completion callbacks and refreshes policy after Activity resume.
 *
 * <p>ActionBarLayout retains its navigation, lifecycle and predictive-Back renderers, using Fluid
 * physics. Its legacy time watchdog cannot cut off a still-running Fluid spring. Content-specific
 * shared avatar/media transitions keep their existing geometry and ownership.
 *
 * <p>BottomSheet uses the real-view FluidTransform when a live origin is available, otherwise
 * FluidReveal clips the native surface. SurfaceView/TextureView content stays in its native
 * hierarchy. FluidOrigins only consumes a recent touch from the same Activity and drops scrolls;
 * FluidTransform rejects recycled list origins. Subclasses that release resources in dismiss()
 * cannot reverse dismissal unless they explicitly opt in and defer that cleanup.
 *
 * <p>Simple ActionBarMenuItem menus automatically use FluidMenuDialog: a full-window surface
 * hosting the actual menu hierarchy. Native rows retain scrolling and input; terminal actions
 * run after the return completes and are revoked on reopening. Each dialog owns its completion
 * callback. Menus with subpages, non-dismissing controls or custom top content retain their
 * existing path until those interactions receive a dedicated surface integration.
 *
 * <p>AlertDialog and other ActionBarPopupWindow paths use native-size surface reveals. Popup item animations
 * and platform window entrance animations do not compete with the Fluid surface. Dialog Back
 * previews retain cancellation and non-cancelable behavior. Pending layouts, outlines and Back
 * registrations are restored at teardown.
 *
 * <p>Switches and both Expressive button groups use tracked, retargetable springs. Standalone
 * Expressive drawables morph through the same policy. ScaleStateListAnimator delegates Fluid
 * presses to background-only expansion: labels, icons and touch targets are not scaled. The
 * native group layout remains responsible for neighbouring buttons' reaction.
 *
 * <p>Other UI styles retain their original motion. Gesture displacement, playback timelines,
 * indeterminate loading loops and specialized content transitions are not globally time-warped.
 */
package xyz.nextalone.nagram.ui.fluid;

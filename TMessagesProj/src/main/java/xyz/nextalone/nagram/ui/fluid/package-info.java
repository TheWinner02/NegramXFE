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
 * Vertical sheet pulls retain the real native hierarchy and use the common spring to return
 * to rest or dismiss downwards. Predictive Back separately seeks the anchored transformation.
 * Scroll containers and page-sized groups cannot become automatic origins. Embedded sheets register with the attached ViewRoot,
 * with keyboard dismissal preceding a sheet return. Native reveals respect the sheet's visible
 * surface bounds. Menu actions hand off their live opener after the return completes so delayed
 * sheets do not lose the origin solely because the original touch expired.
 *
 * <p>Simple ActionBarMenuItem menus automatically use FluidMenuDialog: a full-window surface
 * hosting the actual menu hierarchy. Native rows retain scrolling and input; terminal actions
 * run after the return completes and are revoked on reopening. Each dialog owns its completion
 * callback. Menus with subpages, non-dismissing controls or custom top content retain their
 * existing path until those interactions receive a dedicated surface integration.
 *
 * <p>BottomSheet can route Back to an internal native surface before its outer transform.
 * Chat attachments use this for the expanded camera: its actual texture geometry and controls
 * follow a single Fluid spring and predictive cancellation, keeping native session teardown.
 * Documents permit an outer sheet preview only at the root, outside search and selections;
 * folder navigation retains native history ownership while using the common spring timing.
 * The opaque attachment bar owns complete touch sequences above the collapsed preview;
 * thumbnail hit testing excludes clipped pixels, preventing hidden camera content from
 * consuming File/category taps after the camera returns.
 *
 * <p>The shared chat composer routes its three-dot/recording options through FluidMenuDialog,
 * anchored to the actual pressed button above the input. It preserves the native dim/blur
 * settings, defers terminal actions until the return completes, and revokes them on owner teardown.
 *
 * <p>Flat ItemOptions context menus share FluidMenuDialog automatically. Their native action
 * order, dontDismiss(), checked rows and long-press selection remain owned by ItemOptions.
 * Returning menus reverse directly on a fresh origin touch, without rebuilding the menu.
 * Placement follows the requested gravity, offsets and above/below preference. Composite,
 * moving, blurred and keyboard-focused menus retain their native popup path with an explicit
 * source for FluidReveal.
 *
 * <p>AlertDialog and other ActionBarPopupWindow paths use native-size surface reveals. Popup item animations
 * and platform window entrance animations do not compete with the Fluid surface. Dialog Back
 * previews retain cancellation and non-cancelable behavior. Pending layouts, outlines and Back
 * registrations are restored at teardown.
 *
 * <p>PopupWindow entrances start in the shared show methods and are idempotent when owners
 * also request an entrance. Composite chat menus drive their reaction strip from the same
 * FluidReveal progress; the old popup scaling and independent reaction timer do not compete.
 * Popup windows register predictive Back on their own attached ViewRoot with overlay priority;
 * a gesture snapshots the innermost open subpage, otherwise it drives the outer FluidReveal.
 * Cancellation returns through the same spring without repeating subpage-open callbacks.
 * Registration is removed on detach/dismiss and late platform callbacks are ignored.
 * PopupSwipeBackLayout uses retargetable Fluid springs for subpage progress and changing height.
 * Swipes track the finger directly, resume from the current position and release into the
 * common spring. Detachment cancels work and releases notification locks without stale callbacks.
 *
 * <p>Switches and both Expressive button groups use tracked, retargetable springs. Standalone
 * Expressive drawables morph through the same policy. ScaleStateListAnimator delegates Fluid
 * presses to background-only expansion: labels, icons and touch targets are not scaled. The
 * native group layout remains responsible for neighbouring buttons' reaction.
 *
 * <p>PhotoViewer retains its native shared-thumbnail geometry and player ownership. FluidAnimator
 * seeks the existing together-timeline with the common tracked spring, including drag/zoom
 * settling and neighbouring-media changes. The legacy 500ms watchdog does not truncate a
 * running Fluid transition. Predictive Back previews the native media transform and backdrop;
 * cancellation springs back without releasing the player, while commit hands the current
 * geometry to the native thumbnail return. Caption/keyboard, editing, text selection, quality
 * selection, embedded web players and PiP keep their own Back handling. Return motion can be
 * interrupted by another gesture; attached ViewRoot registrations and timelines stop on teardown.
 *
 * <p>Other UI styles retain their original motion. Gesture displacement, playback timelines,
 * indeterminate loading loops and specialized content transitions are not globally time-warped.
 */
package xyz.nextalone.nagram.ui.fluid;

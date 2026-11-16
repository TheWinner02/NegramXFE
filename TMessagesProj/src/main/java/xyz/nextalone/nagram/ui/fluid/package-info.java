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
 * Page morphs resolve their origin once per gesture, so list updates cannot move the return
 * destination mid-flight. Generic origins share the recycled-row guard with sheets; avatar/title
 * handoffs require a captured matching dialog row. Settling or cancelling releases the frozen origin.
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
 * <p>List-to-chat navigation expands from a validated DialogCell through FluidChatTransition.
 * ActionBarLayout retains its native stack, lifecycle and common spring; its displacement drives
 * the rounded surface and shared live avatar/title instead of a lateral page slide. Messages
 * reveal at their natural layout size. Back and cancellation use the same geometry, including
 * reversal during opening or a cancelled predictive return. The source is reacquired by dialog
 * identity; a missing/offscreen source produces an unanchored return rather than targeting a
 * recycled row. Shared header alpha is restored on completion and detach. Preview, sheet,
 * replacement, split-pane and unrelated navigation paths retain their existing presentation.
 *
 * <p>Navigation, preview returns, drawer and action-mode surfaces share FluidTiming. Drawer
 * springs are tracked for policy changes during movement; disabling motion still delivers native
 * completion callbacks. Search, profile, topics and pager entrances use the same reduced-motion
 * completion path instead of falling back to a timed animation. System/insets-driven keyboard
 * motion retains the IME clock; standalone app panels can use the tracked Fluid timeline.
 *
 * <p>FluidNavigationProfile distinguishes page depth, nested details and peer conversation changes.
 * It modulates background parallax, scale and dimming without changing the shared spring,
 * foreground travel or live origins. Opening, touch Back, predictive Back and cancellation resolve
 * the same stack edge and use the same background geometry. Previews retain their own depth.
 *
 * <p>EmojiTabsStrip drives its moving indicator, selected icons and expanding category strip
 * with FluidAnimator instead of separate fixed durations. Replacing a selection cancels the
 * owning together-timeline, stale icon callbacks cannot overwrite the new selection, and detach
 * settles local UI before panel reuse. Hardware emoji preparation retains its immediate path;
 * keyboard and gesture-driven panel geometry continue to use their native clocks.
 * Fluid category taps replace an active grid scroll instead of being discarded. Distant targets
 * use the same interruptible RecyclerView scroller rather than the input-locking fast-row handoff;
 * reduced motion places the new category directly, and panel detach stops the old scroll.
 *
 * <p>Profile controls use the shared Fluid timeline for the floating write button and media
 * header controls. Header shadow follows the same progress rather than a second delayed phase.
 * Replaced or destroyed owners revoke their completion callbacks before cancelling their native
 * AnimatorSets, while preserving FluidAnimator's listener to stop the tracked spring.
 *
 * <p>The audio player's enlarged cover and its backdrop share one tracked spring. Reversing
 * a cover tap retains position and velocity; a fresh tap during return reopens the preview.
 * Delayed blur results are scoped to the active preview generation. Sheet dismissal stops local
 * physics while retaining the outgoing cover; detach releases its image and backdrop. Reduced
 * motion and non-animated closures settle immediately without a separate alpha/scale timeline.
 *
 * <p>Chat editing history options and type-validation layout changes use the Fluid timeline.
 * History replacement revokes stale visibility callbacks; validation ignores unchanged heights
 * and adds each new layout delta to the current translated position. Detach settles local layout
 * offsets, while fragment teardown cancels the history owner and its tracked timeline.
 *
 * <p>Shared reveal and morph returns validate the live opener, including offscreen rows and
 * same-position rebinds in adapters without stable IDs. Missing origins retain a neutral animated
 * return within the current surface; recycled content never becomes the shared source. Non-stable
 * adapters share a weakly owned update observer instead of registering one for every transition.
 *
 * <p>Background press expansion and release use the same tracked timing and system motion scale.
 * ScrollSlidingTabStrip uses a retargetable position spring, preserving current deformation when
 * selection changes during movement. Detach and reduced motion settle the indicator immediately.
 * Chat bottom-bar replacement keeps native priority and visibility bookkeeping while Fluid drives
 * its manual timeline. Replacement cancels the previous clock and chat destruction revokes callbacks.
 *
 * <p>Other UI styles retain their original motion. Gesture displacement, playback timelines,
 * indeterminate loading loops and specialized content transitions are not globally time-warped.
 */
package xyz.nextalone.nagram.ui.fluid;

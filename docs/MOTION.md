# Motion implementation audit · 0.4.0

The default is **Maximal**. Version 0.4 remeasures artwork during shared transitions to preserve bitmap aspect, fits compact series banners in a landscape viewport, and retains season-selector position while data updates. Version 0.3 adds real title-logo artwork and an animated explicit quality dialog while preserving stable native video/subtitle layout. The poster bridge remains until a rendered video frame is reported. In 0.2.0, opening/closing a series no longer slides and scales the parent scene while shared artwork moves. The shared bounds use a crop-preserving resize mode and a damped bounds spring; only the artwork participates, excluding card play/progress overlays. The hero’s additional fixed zoom was removed. Header chrome renders above the shared-image overlay, with coordinated opacity, so artwork no longer covers its gradient, title or actions. A rapid open → Back → different series regression test runs on phone and TV. `design/Motion.kt` and the controls in `ui/Components.kt` centralize the initial motion system. Finite Compose animations inherit Android’s animator duration scale. When Android animations are disabled, the theme selects Reduced; user Reduced suppresses shared artwork, parallax, bursts and overshoot in the main press/focus/navigation primitives.

Artwork now carries its own animated clip after the shared-bounds modifier. Rounded catalogue cards morph from a 12dp corner radius to the square detail banner using the same damped spring settings as the bounds; returning reverses the shape, including an interrupted transition. The origin's rounded/square shape is saved with navigation state, so full-width spotlight artwork stays square and recreation preserves the correct return shape. `SharedArtworkCornerTest` checks actual overlay pixels during opening, closing, and reversal on phone and TV; `CinemaFlowTest` explicitly enables Maximal motion and checks both origin types through recreation. Standard and Reduced retain their existing fade behavior.

| Action | Implemented recipe | Remaining work |
| --- | --- | --- |
| Launch / welcome | Native splash, orange mark, staggered title/copy/actions, cinematic artwork | Animated vector mark and radial reveal |
| Button / icon | Immediate compression, spring release, bounded ripple, optional touch haptic | Bespoke path morphs rather than icon crossfade/scale |
| Navigation | Spring indicator, icon lift/color, simultaneous slide/fade/depth of scenes | Direction-aware travel, predictive Back |
| Open / return details | Shared artwork bounds and corner radius with a damped spring; saved origin shape; interruptible reverse transition; distinct opening haptic on touch devices | Shared title geometry, fallback when origin disappears, exhaustive focus restoration |
| TV cards | TV Compose focus scale and orange border; immediate focus with animated treatment | Traveling ring, artwork crop parallax and metadata reveal |
| Home scroll | Bounded hero parallax; rows fade/translate into place once per saved composition | Smooth hero condensation, pull-to-refresh |
| Search / filter | Material focused field treatment, local clear response, spring/color chips, loading and result reveals | Floating label choreography, stable-ID spring grid rearrangement across every filter |
| Season | Press/fill on selection and lazy-item replacement/movement | Layered season sheet, directional crossfade |
| Add to list | Plus/check state; local orange ring/particle burst only after store emission; animated snackbar | Artwork echo toward My List; real service mutation reconciliation |
| Remove / undo | Saved-state update, lazy-row reflow, undo affordance | Retraction of an individual card within a grid row |
| Profile | Avatar accent crossfade, spring press/focus | Avatar-led scene handoff and adjacent avatar recession |
| Toggle / languages | Spring thumb with color change; bounded language dialogs with restored selection and TV focus | Bespoke associated-icon morph |
| Error / retry | Honest messages, retry transitions, local progress; failure notices do not use a success check | Dedicated recoil/shake and network fixture injection UI |
| Dialog | Fading platform backdrop and layered content reveal | Surface spring/corner choreography and animated dismissal |
| Player entry | Poster bridge until actual Media3 video is ready; prompt fade; finite orange play badge/ring; resumed playback feedback independent of buffering | Full poster sweep and play-symbol path morph |
| Player controls | Edge slide/fade, transport icon changes, native animated slider, auto-hide/reveal; mobile side double-tap seeking with directional/time feedback; throttled timeline haptic ticks | Drag-time bubble |
| Tracks | Actual available track menu, animated selection confirmation | Traveling selection marker and menu-sheet spring |
| Next chapter / cancel | Known next demo chapter, countdown fill, cancel restores stable controls | Circular countdown and artwork expansion |
| Resize / rotation | Runtime bottom-nav / rail change with retained app and playback state | Bounds interpolation across layout classes |
| Leave demo | Confirmation and scene transition, persisted state retained | Full account-content retraction choreography when live logout exists |

The first milestone establishes reusable expressive primitives, rather than declaring the full animation catalogue complete. No fake skip-intro, skip-credits, quality options, activation QR code, or unavailable subtitle tracks are presented. Maximal motion is retained as the default during optimization.

Decorative bursts have a fixed ten-particle limit and finite lifetime; there are no app-owned infinite decorative animation loops. Reveals use opacity and placement transforms rather than per-frame expansion of entire rows. Input triggers state immediately and finite Compose animations retarget; there is no explicit animation-event queue. Media video remains in an Android player surface; transformations apply to scene transitions/posters and surrounding chrome, not to the running film’s layout.

Playback feedback expands a ring only in Maximal, settles subtly in Standard, and changes opacity only in Reduced. Android's zero animator duration scale suppresses the decoration. Series-opening and timeline haptics use distinct Android semantic effects, honor the active profile and system settings, and are disabled on TV. Scrubbing emits at most one tick per 50 ms, drops intermediate crossings instead of queuing pulses, and resets when a drag finishes or is cancelled.

Screen recordings and measured emulator limitations are listed in `IMPLEMENTATION.md`. Hardware performance, screen-reader traversal, high refresh rates, low-power devices, and the complete rapid-input matrix remain future validation work.

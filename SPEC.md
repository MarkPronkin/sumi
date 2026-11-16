# Sumi Android app specification

Updated 16 November 2026. This is the implementation handoff for Sumi, an unofficial Crunchyroll client for Android phones, tablets, and Android TV, based on [aarron-lee/crunchyroll-linux](https://github.com/aarron-lee/crunchyroll-linux).

The user selected **Cinema with true OLED black backgrounds** and requested **an animation for every action, deliberately over the top**. These are the governing product decisions. Build the Android app from this specification without reopening the choice of visual direction.

## Confirmed requirements

- Deliver an installable Android app with layouts and interaction patterns for phones, tablets, and Android TV.
- Use the Cinema design: immersive anime artwork, strong titles, black surfaces, rounded cards, and Crunchyroll orange accents.
- Use `#000000` for the main app background and navigation backgrounds on every device.
- Make motion a major product feature. Every app interaction needs a designed animated response, including loading, success, errors, cancellation, and return navigation.
- The default motion setting is Maximal. Generous springs, overshoot, layered transitions, shared artwork, parallax, and small celebrations are encouraged.
- Reuse the reference project's useful service behavior and feature coverage where it remains compatible with Android.
- This handoff authorizes specification work. Android implementation begins in the next chat.

## Existing project state and design reference

The workspace currently contains HTML design studies and their artwork. There is no native Android implementation, verified Crunchyroll integration, or complete motion system yet. Existing search, watchlist, and player interactions are local mockups using sample data and promotional artwork.

| File | Purpose |
| --- | --- |
| [design/crunchyroll-oled-design-options.html](design/crunchyroll-oled-design-options.html) | Approved visual reference. Select **Cinema**, then switch between Phone, Tablet, and Android TV. |
| [design/options.fragment.html](design/options.fragment.html) | Editable source of the current design gallery, including Cinema's layout and colors. |
| [design/build_preview.py](design/build_preview.py) | Rebuilds the gallery with embedded artwork using `python3 design/build_preview.py`. |
| [design/crunchyroll-design-options.html](design/crunchyroll-design-options.html) | Earlier design exploration. |
| [design/assets/sources.json](design/assets/sources.json) | Sources for the sample artwork used in the studies. |

The gallery also contains Zen and Panels. **Cinema OLED is the selected design.** The outer gallery controls, dotted stage, device bezel, fake status bar, and commentary beside the phone are presentation elements and must not become app UI. Its small text is scaled for the study; use accessible Android text sizes in the real app.

## Product experience

The app should make it easy to choose a profile, resume a show, discover anime, manage a watchlist, and watch an episode with the desired audio and subtitles. Home should give the featured artwork room to breathe while keeping Continue Watching close to the first viewport.

The finalized project and app name is **Sumi**, with the description **Unofficial Crunchyroll client**. Identify its unofficial status in onboarding and About. Use real account and catalogue information when connected; fixture data belongs in an explicitly labeled demo mode. Preserve the existing Android application ID so updates retain installed sessions and preferences.

### Feature scope

| Area | Required behavior |
| --- | --- |
| Sign in | Support the verified account sign-in flow, session restoration, token refresh, actionable failures, and logout. Prefer device activation on TV if the service actually supports it for this client. |
| Profiles | Select and switch profiles supported by the service. Scope watchlists, history, and preferences to the active profile. |
| Home | Featured show, Continue Watching, discovery rows, and links to full collections. Use service-provided rows where available. |
| Browse | Categories and genres, paginated collections, language indicators, and useful loading and empty states. |
| Search | Debounced queries, clear query, results, empty results, retry, and cancellation of stale searches. Preserve query and position on return. |
| Show details | Artwork, title, synopsis, supported metadata, available languages, watchlist action, seasons, and episodes. |
| Episodes | Season selection, thumbnails, episode title, duration, progress, availability, and play or resume. |
| Watchlist | Add and remove shows, persist changes, recover from failed mutations, and open show details. |
| History | Recently watched content, progress, and resume. Include remove or clear actions only if supported by the service. |
| Player | Play and pause, seek, resume, buffering, audio and subtitle selection, track availability, quality selection where supported, next episode, and exit. |
| Settings | Preferred languages, autoplay next episode, motion intensity, haptics on supported touch devices, account, and About. |

Skip Intro and Skip Credits require actual timing metadata. Autoplay next episode requires a known next episode. Do not show working-looking controls for unsupported service features. Picture-in-picture is a later enhancement after core phone and tablet playback works.

Casting, offline downloads, social features, custom recommendation services, and public store publication are outside the initial build. Keep the architecture open to future additions without implementing them speculatively.

## Cinema OLED visual system

### Color tokens

These are the approved starting values taken from the Cinema OLED study.

| Token | Value | Use |
| --- | --- | --- |
| Background | `#000000` | Main canvas, system bar treatment, navigation, player surround, and secondary screens. |
| Surface | `#101010` | Raised controls, sheets, inputs, and bounded content where separation is useful. |
| Primary text | `#F3F3F5` | Titles, body text, and essential labels. |
| Secondary text | `#A7A7AF` | Supporting metadata and inactive labels, subject to contrast checks. |
| Accent | `#FF7A26` | Primary actions, active navigation, progress, selected states, and motion highlights. |
| Text on accent | `#111111` | Text and icons on filled orange buttons. |
| Divider | `#28282C` | Quiet structural separation. |

Black must remain true black around the artwork. Restrict gradients to artwork legibility and temporary effects; do not tint the entire screen charcoal. Use orange selectively so the anime art stays dominant. Validate text contrast on its actual background, including over imagery and during transitions.

### Typography and components

- Use **DM Sans** for navigation, body copy, metadata, and controls. Bundle font files for the Android build rather than depending on a runtime font download.
- Use **Barlow Condensed** or an equivalent condensed display face for large show titles and featured headings. Prefer actual title artwork when the service supplies an appropriate asset.
- Starting phone type scale: body 16sp, secondary text 12–14sp, section headings 18–22sp, featured title 40–56sp. Adapt for content, width, and user font scaling.
- Use a 4dp spacing grid, generally 16–24dp side padding on touch devices, and consistent 12dp card corners. Large sheets may use 24dp corners.
- Make touch targets at least 48dp, including icon buttons. Keep action labels visible where an icon would be ambiguous.
- Landscape episode artwork is generally 16:9; portrait show artwork is generally 2:3. Crop deliberately and preserve recognizable faces.
- Use proper vector icons in the app. The study's Unicode symbols and CSS brand sketch are placeholders.
- Include image placeholders and loading, empty, error, offline, disabled, pressed, selected, and focused states for each relevant component.
- Home, watchlists, and episode collections must scroll naturally. Nothing essential may sit permanently behind navigation or player controls.

## Adaptive layouts and input

Adapt to available window space at runtime. Treat TV as a distinct interaction environment, rather than deciding that every wide window is a TV. Android's [adaptive layout guidance](https://developer.android.com/develop/ui/compose/build-adaptive-apps) and [window size classes](https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes) provide the implementation basis.

| Environment | Layout and behavior |
| --- | --- |
| Compact touch window, normally below 600dp wide | Bottom navigation with Home, Browse, My List, and Profile. Search is prominent inside Browse. Profile exposes History, Settings, and account actions. Large artwork leads Home; content follows vertically. |
| Medium touch window, normally 600–839dp wide | Navigation rail, wider hero, and additional visible cards. Use available height sensibly in landscape and split screen. |
| Expanded touch window, normally 840dp and above | Navigation rail and richer content composition. Use list and detail panes where they improve episode browsing; preserve readable text widths. |
| Android TV | Dedicated 16:9 composition, larger readable labels, top navigation or a focusable side rail, spacious rows, and remote-first controls. Include reachable Profile and Settings destinations. |

Phone and tablet browsing must handle rotation and resizing without losing the selected profile, query, scroll position, season, or playback state. Playback supports landscape. Respect system insets, cutouts, gesture navigation, and available height.

### TV focus contract

- Every flow must work using only D-pad, Select, and Back, including sign-in, settings, dialogs, and player track selection.
- Move to the nearest logical item in the pressed direction. Preserve row position and restore focus when returning from details or playback.
- Exactly one control owns focus at a time. Show a clear orange focus ring and animated scale or elevation without obscuring neighboring items.
- Focus changes immediately; its visual treatment can animate afterward. Rapid remote presses must retarget motion rather than queue transitions.
- Keep focused content visible and leave room for scaled cards and rings. No clipping, focus traps, or unreachable controls.
- Back closes the current overlay first, then returns through navigation. Repeated Back must provide a predictable route out of the app.
- Support media transport keys. Gamepad and keyboard input are useful extensions of the same focus behavior.

Use [Compose for TV](https://developer.android.com/training/tv/playback/compose) and Android's [TV navigation guidance](https://developer.android.com/training/tv/get-started/navigation) for the TV implementation.

## Screen requirements

### Launch and account entry

Use the Android splash mechanism, then continue into a brief orange-on-black reveal. Restore the session without holding the user on a mandatory cinematic intro. Returning users reach their profile or Home; signed-out users reach sign-in. Account failures must explain a next action.

TV should use a supported activation flow when available. Otherwise provide a usable remote-compatible sign-in route. Do not invent an activation backend or display a nonfunctional QR code.

### Home

The hero features anime artwork, a strong title, concise metadata, Start Watching or Resume, and a watchlist action. The image fades into true black. On wider screens, add a short synopsis without creating a dense text panel.

Continue Watching follows, using landscape cards with progress and remaining time. Discovery rows follow beneath it. Selecting a collection heading or View All opens a real collection. Avoid automatically advancing the hero while the user is interacting with it.

### Browse and search

Offer categories and a dedicated search field. Show results as soon as the latest response arrives, with cancellation or versioning to prevent stale queries replacing newer results. Animate result changes using stable content IDs. Keep the keyboard and caret stable while typing.

### Details and episodes

Expand selected artwork into the detail header, with a reversible path back to the originating card. Preserve catalogue position. Make season, language availability, episode progress, and the primary play action easy to find. Changing seasons updates the episode collection without losing navigation context.

### Watchlist and history

Use the same artwork and progress components as discovery. Animate additions and removal with clear confirmation. If a mutation is optimistic, restore the previous state and provide retry when it fails. Keep account-backed state separate from demo state.

### Player

Use a real media player with touch and remote controls, persistent preferred track choices when available, resume progress, and an optional next-episode countdown with Cancel. Save progress on pause, exit, and suitable periodic intervals, and send it to the service when supported.

Controls animate into place and disappear after inactivity. Touch or remote input reveals them again. Track menus and settings remain focusable and legible. Hide unavailable tracks rather than inventing choices. Preserve playback position across supported track changes and lifecycle events.

Animate poster artwork and surrounding chrome during entry. A protected video surface may not support the same capture and transformation techniques as a static image; use a poster bridge until the actual video surface is ready. Show buffering and failures truthfully.

## Motion direction

**The user explicitly wants extravagant animation for every action. Maximal motion is a core requirement, not an optional polish pass.** Build reusable motion components early and use them across the entire app.

The overall character is cinematic, elastic, and expressive. Combine an immediate response with a longer animated flourish: a button compresses on press, springs past its resting size on release, and sends an orange highlight toward the destination. Artwork and titles travel between screens. Menus unfold in layers. Success and failure have distinct motion signatures.

Every app-owned action must map to a motion recipe. This includes taps, remote focus, tab changes, scrolling, filters, text input, dialogs, toggles, sliders, network completion, retries, back gestures, and cancellation. System-owned interfaces keep their platform behavior.

### Motion timing and geometry

These values are starting design targets, to be tuned on real hardware and centralized as tokens.

| Motion token | Starting target |
| --- | --- |
| Immediate acknowledgement | Begin within 80ms of input. |
| Press | Scale approximately 1.00 → 0.95 over 80–120ms. |
| Release | Spring approximately 0.95 → 1.035 → 1.00 over 250–450ms. |
| Small state change | 160–280ms, often with a spring or icon morph. |
| Navigation | 350–550ms, with simultaneous outgoing and incoming content. |
| Artwork expansion | 450–700ms; poster or hero bounds, crop, corners, and title move together. |
| Choreographed reveal | 35–55ms stagger between nearby elements; cap the total delay so visible controls become usable promptly. |
| Celebration | 500–900ms for a local burst, ripple, or orbit that dissipates. |
| Typical translation | 8–28dp for controls and rows; larger travel for major scene changes. |
| TV focused card | Scale approximately 1.06–1.08, with an orange ring and bounded glow. |

Use spring families for tactile, floating, and large artwork movement. Prefer interruption and retargeting from current values over restarting animations from zero. Input and state changes must not wait for the flourish to finish.

### Required animation catalogue

| Action or transition | Maximal motion recipe |
| --- | --- |
| Cold launch | Orange mark draws or assembles on black; a soft radial reveal introduces the first screen and nearby content arrives in layers. |
| Sign-in submission | Button compresses, label morphs into progress, and the surrounding form gently draws inward. Success expands into the next scene; failure returns the form with a short directional shake and visible error text. |
| Profile selection | Selected avatar springs forward; adjacent avatars recede; its orange accent or artwork leads the transition into Home. |
| Button and icon press | Compression, release overshoot, and a bounded orange ripple. Toggle icons morph between their actual states. |
| Bottom navigation or rail selection | Orange selection indicator glides with a spring; selected icon lifts or morphs; destination content enters with direction-aware translation and depth. |
| TV focus movement | Ring travels to the new target, the card rises and scales, artwork shifts slightly within its crop, and relevant metadata reveals. The previous target springs back. |
| Opening a show | Artwork expands into the detail header; title and key metadata travel with it; remaining detail content arrives in a short stagger. |
| Back from details | Reverse the artwork transition to the original card, restoring exact scroll position and focus. Provide a coherent fallback if that card is no longer available. |
| Starting playback | Play symbol morphs, orange emphasis sweeps across the poster, and the artwork expands toward the player. Controls arrive as the real video becomes ready. |
| Adding to watchlist | Plus morphs to a check, a compact orange particle or ring burst marks success, and a small artwork echo travels toward My List. |
| Removing from watchlist | Check unwinds to plus; the removed card retracts; remaining cards spring into their new positions. Offer an animated undo affordance where useful. |
| Scrolling Home | Artwork has bounded parallax, the hero condenses smoothly, and newly encountered rows rise and settle. Do not replay full reveals on every tiny scroll reversal. |
| Refreshing content | Pull interaction stretches a small orange motif; release turns it into progress; updated rows settle into place without resetting the user's position unnecessarily. |
| Search activation and typing | Field expands with an animated focus outline and floating label. Clearing text has a local erase or collapse motion. Results enter and leave in short staggered groups while the input remains still. |
| Category or filter selection | Chip compresses and fills; orange selection travels; matching content rearranges using stable IDs and spring movement. |
| Season or language selection | Selector opens into a layered menu or sheet; the active marker slides; episode rows transition with directional movement and artwork crossfades. |
| Loading artwork or data | Local skeleton highlights sweep in a restrained orange-neutral band; resolved content crossfades and settles into the existing geometry. Keep the surrounding screen black. |
| Empty results | A small animated motif assembles with the message and the next useful action. |
| Error and retry | Affected control or panel makes a short recoil; error text and retry appear together. Retry reconnects into progress and success resolves back into content. |
| Sheet or dialog | Backdrop fades while the surface rises with a spring; corners and contents settle in layers. Dismiss reverses the motion, following the gesture when applicable. |
| Settings toggle | Thumb travels with overshoot, track color flows, and the associated icon morphs or gives a short accent pulse. |
| Slider or seek drag | Thumb grows slightly, a value or time bubble follows the drag, and the fill tracks continuously. Release settles the thumb and confirms the chosen position. |
| Play and pause | Transport icon morphs; a brief centered confirmation scales and dissolves while controls remain stable. |
| Player controls show or hide | Controls slide and fade from their screen edges, with a coherent sequence for title, timeline, and transport actions. |
| Audio or subtitle change | Selection marker slides, chosen row springs once, and the player displays an animated confirmation without moving subtitle placement. |
| Skip intro or credits | Button fills with an orange sweep, retracts after activation, and the timeline animates toward the actual new position. |
| Next episode | Countdown ring progresses around the next action; episode artwork expands on confirmation. Cancel retracts the countdown and restores stable playback controls. |
| Toast or snackbar | Surface rises and settles with its icon drawing in; dismissal retracts it. Avoid obscuring subtitles and focused controls. |
| Rotation or window resizing | Navigation and content bounds interpolate to their new arrangement; maintain selected content and playback state. |
| Logout | Confirmation dialog appears with layered motion; confirmed logout retracts account content into a clean signed-out scene. |

Keep success celebrations orange and local to the action. Avoid turning routine interactions into full-screen flashes. Haptics may reinforce touch actions when enabled; UI sounds are off by default.

### Motion correctness and accessibility

- Provide **Maximal**, **Standard**, and **Reduced** motion settings. Maximal is the default requested experience. Reduced replaces large travel, parallax, particles, and overshoot with brief fades or immediate updates.
- Respect Android animation accessibility settings, including disabled animations. Stop decorative loops when the app is backgrounded or their components leave the viewport.
- Keep the actual video and subtitle layout stable during playback. Animate controls and surrounding UI without distorting the film.
- Accept rapid repeated input. Cancel or retarget superseded effects; avoid growing queues, duplicate actions, stale transitions, and lost focus.
- Distinguish visual completion from network completion. An animated check must not falsely imply a failed server mutation succeeded permanently.
- Preserve focus and semantics through animation. Use screen-reader announcements for actual state changes; avoid announcing decorative motion.
- Keep motion state out of business logic and scope it to the relevant component or transition.

Use native [Compose animation APIs](https://developer.android.com/develop/ui/compose/animation/introduction) and [shared element transitions](https://developer.android.com/develop/ui/compose/animation/shared-elements) where suitable. Validate protected playback separately from artwork transitions.

## Engineering defaults

These are implementation defaults rather than additional choices made by the user. The next builder may adjust them for a concrete compatibility reason and record that reason.

- Use Kotlin and Jetpack Compose for native Android UI, with TV-specific Compose components and focus handling.
- Share catalogue models, repositories, authentication, preferences, playback coordination, artwork loading, and motion tokens across device layouts.
- Use AndroidX Media3 ExoPlayer and MediaSession for playback and media key integration. Media3 supports DRM through Android MediaDrm; actual service compatibility still requires validation. See [Media3 DRM guidance](https://developer.android.com/media/media3/exoplayer/drm).
- Use coroutines and Flow for asynchronous state; ViewModels expose explicit loading, content, empty, and error states.
- Use a maintained HTTP client and typed serialization. Select compatible stable library versions when implementation begins and lock them in a version catalogue.
- Use DataStore for preferences and an appropriate persistent cache for catalogue and progress. Protect persistent authentication material with Android-backed key storage; exclude credentials from logs and source control.
- Keep real and fixture repositories behind the same interfaces so UI and motion work can continue while service integration is being verified.
- Start with a single application supporting touch and TV launchers, unless an early dependency or packaging constraint justifies separate application modules.
- Proposed minimum Android version: API 26. Validate device needs and DRM behavior before finalizing it; choose compile and target SDK versions against the stable toolchain available at build time.
- Keep modules or packages focused: app, design system and motion, data and service integration, playback, and feature UI. Avoid a large speculative module tree before there is working functionality.

## Upstream reuse and playback validation

The upstream project is an Electron-based Linux frontend descended from a Tizen app. Its README covers authentication, profiles, home, details, episodes, playback, search, history, categories, watchlists, track selection, and controller input. Use it as a behavior and integration reference. Its existing HTML and desktop shell do not establish Android playback compatibility. See the [upstream README](https://github.com/aarron-lee/crunchyroll-linux#readme) and [package manifest](https://github.com/aarron-lee/crunchyroll-linux/blob/master/package.json).

Inspect these files first:

- [service.js](https://github.com/aarron-lee/crunchyroll-linux/blob/master/server/js/core/service.js) for authentication, catalogue calls, profile operations, and playback requests.
- [session.js](https://github.com/aarron-lee/crunchyroll-linux/blob/master/server/js/core/session.js) for session and refresh behavior.
- [player.js](https://github.com/aarron-lee/crunchyroll-linux/blob/master/server/js/core/player.js) for playback sessions and Widevine request and response handling.
- [crunchyroll.js](https://github.com/aarron-lee/crunchyroll-linux/blob/master/server/js/core/mappers/crunchyroll.js) for mapping service responses to UI models.
- [login.js](https://github.com/aarron-lee/crunchyroll-linux/blob/master/server/js/screen/login.js) for account entry behavior.

At the time of the design handoff, the service code contains a Samsung TV playback route, and the player uses a Widevine license proxy with content and video-token headers and response decoding. Treat these as upstream implementation details requiring a native Android compatibility check. Do not assume that reproducing an endpoint or changing a client identifier produces working Android access.

Before committing to the full integration, verify a representative authenticated playback path: sign in, select a profile, retrieve catalogue data, request an entitled stream, obtain a valid license, play with Media3, select an available audio and subtitle track, save progress, and close the playback session correctly. Use the user's account through secure credential entry when real-account testing is needed. Do not require secrets in a chat message or commit them to the project.

If authentication or playback is blocked, record the exact failing step and continue independent native UI and motion work using labeled fixtures. A demo build is a useful milestone; live account streaming is complete only after it works on a real supported device.

Check upstream file provenance before copying code. The repository's [LICENSE](https://github.com/aarron-lee/crunchyroll-linux/blob/master/LICENSE) contains Apache 2.0 text while its package manifest declares ISC; preserve applicable notices and resolve that discrepancy for the reused material. Sample artwork in this workspace is design reference material; production artwork should come from the authenticated catalogue or other appropriate assets.

## Implementation sequence

### First pass

Read this specification and inspect Cinema in the OLED reference. Audit the upstream service and playback paths and establish the Android toolchain. Record service assumptions, dependency choices, and any concrete integration blocker.

Create a native app that launches on a phone and Android TV early. Establish the true black theme, orange accent, typography, navigation, focus behavior, and shared motion primitives before spreading them across feature screens.

### Complete native experience with fixtures

Implement Home, Browse and Search, Details and Episodes, My List, History, Profile, Settings, and player UI using consistent fixture repositories. Implement the full motion catalogue as each flow is added. Use an appropriate test stream for player mechanics and label demo content clearly.

Deliver runnable debug builds for touch and TV with the selected Cinema identity, useful empty and failure states, a motion setting, and a representative end-to-end interaction path.

### Connected experience

Complete authentication, profiles, catalogue access, watchlist and progress mutations, and real service playback using the verified integration path. Replace fixtures through repository bindings rather than rewriting screens. Exercise session expiration, unavailable content, language changes, buffering, and cleanup.

### Device polish and handoff

Refine motion on phone, tablet, and TV hardware, fix accessibility and focus gaps, measure performance, and document build and install commands. Provide installable artifacts and a precise account of tested functionality and remaining limitations.

## Acceptance criteria

1. The app installs and launches on a supported phone, tablet, and Android TV environment. Document the exact devices or emulators exercised and whether playback was also checked on real hardware.
2. Main screens, navigation, and player surrounds use true `#000000`. Cinema orange and the approved typography are recognizable throughout.
3. Home matches the selected design's hierarchy: immersive hero, clear play and watchlist actions, Continue Watching, then discovery.
4. Touch layouts respond to available window size, font scaling, rotation, and split screen without obscured controls or lost state.
5. A remote alone can complete all TV flows, with visible focus, logical directions, predictable Back, and correct focus restoration.
6. Every implemented action has press or focus feedback and an appropriate transition or state-change animation. Include success, error, loading, cancellation, and reverse navigation in the motion audit.
7. Maximal motion is visibly expressive, including artwork expansion, elastic navigation, icon morphs, staggered reveals, and watchlist celebrations. It must not be reduced to a global fade applied to every screen.
8. Rapid taps and repeated D-pad input do not queue long animations, duplicate requests, strand overlays, or lose focus.
9. Reduced motion and disabled system animations produce a functional alternative. TalkBack labels and announcements describe actions and state correctly.
10. Search suppresses stale responses, watchlist changes recover from failure, and account state is isolated by profile.
11. Real playback supports the verified stream and DRM path, available tracks, resume, next episode, and session cleanup. Catalogue access alone does not satisfy playback acceptance.
12. Controls never report a successful network action solely because its animation finished. Fixture mode is clearly distinguished from connected mode.
13. Scrolling and interaction target a smooth 60fps on the declared baseline devices; use higher refresh rates when practical. Profile demanding artwork and focus transitions and report observed jank rather than assuming it is acceptable.
14. Performance optimizations preserve the expressive default. Bound expensive glow, blur, particle counts, and simultaneous animations; prefer animating transforms and opacity over repeatedly rebuilding large layouts.
15. Relevant integration, state restoration, focus navigation, and motion interruption checks pass. Provide representative recordings from phone and TV so animation quality can be reviewed.
16. Build instructions, debug artifacts, demo versus connected status, upstream attribution, and remaining service limitations are documented.

## Prompt for the next chat

> Read `SPEC.md` and inspect the Cinema direction in `design/crunchyroll-oled-design-options.html`. Build the unofficial Crunchyroll Android app described there for phones, tablets, and Android TV. Cinema OLED is the selected design, and deliberately extravagant animation for every app action is a firm requirement. Start by inspecting the upstream project and validating the native authentication and playback approach, then implement the app with reusable motion primitives and adaptive layouts. Use labeled fixtures for independent UI work while live integration is being validated. Make reasonable implementation decisions and continue working; ask only when a genuinely blocking product choice or user-provided secret is required. Preserve the existing design references and report runnable builds, checks performed, and any real integration blockers.

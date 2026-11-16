# Implementation status

Updated 16 November 2026.

## Player and My Content update

Mobile side double taps seek backward/forward by a profile-specific interval: 5, 10 (default), 15, or 30 seconds. Settings, visible transport buttons, hardware media keys, and Media3 seek increments use the same interval. The center remains a control-toggle region, and controls/slider retain their own gestures. Seeking clamps to the current duration and preserves pause/play state. Series-opening haptics and manually scrubbed second-boundary ticks respect profile/system settings and TV suppression; fast scrubs coalesce pulses without catch-up. Finite play/resume feedback respects motion settings and keeps the native video surface stable.

My Content replaces My List with Watched shows, Watchlist, and History. Watched shows groups played episodes once per series, including completed entries and titles absent from Home. Account views share paginated history with overlapping-page reconciliation; refresh retains the loaded window. Tabs and scroll positions restore, legacy routes map to their matching tabs, and demo Watchlist undo remains tied to its original profile. History contains the latest available checkpoint per episode, not every rewatch event.

Preferred audio/subtitles now offer 22 languages and regional variants in bounded, scrollable dialogs with TV focus. Subtitles Off and legacy saved codes remain compatible. Playback prioritizes exact locales, then explicit same-language fallbacks; changing subtitle renditions retains the existing position-preserving path. Region names also appear in the player track menu. Available tracks still depend on the episode.

Validation: debug and instrumentation APK assembly, 100 JVM tests, 38 phone API33 tests, and 11 TV tests pass. Lint has zero errors and 29 warnings; APK signature verification passes. The actual player test verifies side double taps, matching button/Media3 increments, per-profile persistence, activity recreation, and pause preservation. Other checks cover feedback restoration, haptic policies, language dialog scrolling/focus, regional matching, account history pagination, restored content tabs/scroll positions, Watchlist undo, and existing artwork/playback navigation. Reports, APK checksum, and phone/TV captures are in [the feature build manifest](../artifacts/player-content/build-manifest.json). Live signed-in playback and physical haptic feel were not revalidated for this update.

## What changed in 0.7.2

Mobile Home and detail banners retain the existing poster source, render it with Fit, and remove the added logo/title overlay. Details use a taller 2:3 viewport. Home measures the metadata and spotlight controls first, fitting the full poster into the remaining height so its actions clear bottom navigation. The bottom shade covers only the final 12% of the artwork area. Metadata and actions sit below the image. Phones also avoid logo-image requests when their wider landscape layout uses a text heading; tablets and TV retain title-logo artwork. Banner accessibility labels retain the series name.

Validation includes 57 JVM tests and nine phone UI tests. A local Jujutsu Kaisen poster with an embedded title reproduces the report in the actual app, using a populated logo URL to verify that no extra logo node is created in portrait, after recreation, or in phone landscape. The fixture is packaged only in the instrumentation APK. Home and details PNG captures, test output, checksum and verification limits are in `artifacts/v0.7.2/`.

## What changed in 0.7.1

MyAnimeList scores and ranks now load from Tenrai’s active public API. The original Jikan public service had been discontinued on October 1, 2026; its endpoint timed out and could not supply the ratings added in 0.7.0. The replacement retains exact title/year matching, the original MAL score and rank fields, request isolation, cache behavior and retries. Attribution now identifies Tenrai. Crunchyroll and IMDb request paths are unchanged.

A regression test checks the production MAL request host, API path, title query and score/rank parsing. An explicitly selected Android network test exercises the production repository with real ratings for three titles. See [rating behavior and sources](RATINGS.md) and `artifacts/v0.7.1/build-manifest.json` for validation results and limits.

Validation: 57 JVM tests and six Android tests on the phone API33 emulator pass. The live Android test returned real MAL scores and ranks for Frieren, Solo Leveling and DAN DA DAN within the existing deadline. Assembly and lint pass with zero lint errors and 27 existing warnings. The APK signature matches 0.7.0. Physical hardware and TV were not retested for this provider change.

## What changed in 0.7.0

Show details now include Crunchyroll, MyAnimeList and IMDb audience scores, plus MyAnimeList’s overall rank. The adaptive cards appear above the synopsis and retain each source’s original scale. Each source loads independently, with explicit loading, unavailable and retry states. Scores are cached in memory and optional failures do not block episodes or playback. Demo mode uses clearly labeled fixtures.

Crunchyroll ratings use the authenticated review endpoint. External scores come from Jikan and Cinemeta through a separate unauthenticated client. Matching requires a unique normalized title or alias, a consistent release year where known, and the correct media type. Missing and ambiguous matches stay unavailable. See [rating behavior, sources and validation limits](RATINGS.md) and `artifacts/v0.7.0/build-manifest.json`.

Validation: 56 JVM tests and five Android UI tests each on phone and TV pass (66 checks). Assembly and lint pass with zero lint errors and 27 existing warnings. Tests include show retargeting/recreation, original score scales and MAL rank, error/retry states, large text, card wrapping and season scrolling. The APK signature matches 0.6.5. Cinemeta returned live metadata; Jikan timed out from this machine and Crunchyroll review requests used an authenticated mock session. Physical hardware was not retested.

## What changed in 0.6.5

Entering playback on a phone requests sensor landscape, supporting both landscape directions even with system auto-rotate disabled. The request applies only to non-TV configurations with smallest width below 600dp. The player route owns it outside episode crossfades, and the previous orientation policy is saved through activity recreation. Leaving playback restores that policy; configuration-change disposal leaves the active playback request intact. Tablets and TV retain their existing behavior.

Phone emulator tests check automatic landscape entry, all four immersive player menus, resume/recreation, episode changes, and restoration of both an explicit portrait policy and the original automatic policy over repeated entries. See `artifacts/v0.6.5/build-manifest.json` for test results, APK checksum and verification limits.

## What changed in 0.6.4

Playback now explicitly hides Android's status and navigation bars with immersive mode. The request is owned by the active player route outside the animated episode content, so the outgoing episode cannot restore system bars during a chapter change. Resume and window-focus callbacks reapply the request. Leaving playback removes the callbacks, restores the previous system-bar behavior and shows the bars again.

Tracks, Quality, Upscaler and Display info use the same immersive dialog wrapper because each dialog owns a separate Android window. Edge swipes can temporarily reveal the bars using Android's normal transient-bar behavior.

Validation: 42 JVM tests and four phone emulator tests pass. New tests inspect actual status/navigation-bar visibility on the focused activity or dialog window, covering all four menus, dismissal, background/resume, activity recreation, landscape/portrait rotation, episode changes and exit. Existing tests verify next-chapter navigation and sample-video skip/pause behavior. Assembly, signature verification and lint pass. Physical hardware, TV and XREAL/Unbound were not retested for this patch. Results and checksum are in `artifacts/v0.6.4/build-manifest.json`.

## What changed in 0.6.3

Mobile headers now select `poster_tall` before the wide hero URL. A failed image request advances to the next available source instead of stopping at an error placeholder. Tablet/TV keep the wide hero first. A rendering test loads contrasting portrait/landscape fixtures through Coil and checks actual pixels for mobile selection, wide selection and failed-portrait fallback.

Home no longer features the first popularity result unconditionally. The initial selection varies, Previous/Next select other titles, Refresh loads fresh connected content, and choosing Home advances the spotlight. Between catalogue refreshes, returning from details retains the selected series for the shared-artwork transition; recreation also preserves it. Connected Home refreshes stale catalogue data after 15 minutes on entry/resume. Artwork metadata is fetched for the selected spotlight only, without loading its episodes; partial browse/watchlist responses retain previously fetched posters and logos. A fresh catalogue refresh permits updated spotlight metadata to be fetched again.

Validation: 42 JVM tests, eight Android tests each on phone and TV, plus tablet portrait/landscape and phone landscape header checks pass (61 checks). Tests cover actual loaded image pixels, failed-image fallback, metadata cache preservation/refresh, spotlight navigation and refresh, selection after recreation, and the selected series' Details/Play actions. Assembly, signature verification and lint pass with zero lint errors and 27 existing warnings. Live catalogue responses use fixtures; UI flow captures use demo content. Physical hardware and XREAL/Unbound were not retested. Results and checksum are in `artifacts/v0.6.3/build-manifest.json`.

## What changed in 0.6.2

Home and series details have separate phone and tablet/TV header compositions. Phones use a tall 4:5 artwork area, a centered title/logo over the lower fade, and centered metadata/actions. Detail artwork extends behind the status bar while Back retains safe inset padding. Tablet/TV details restore full-width landscape artwork with a left-side readability gradient and left-aligned copy. Header height can grow with copy instead of clipping controls.

Both headers prefer clean hero backgrounds, keeping poster artwork on catalogue cards. Full-color logos are preferred, with monochromatic artwork and readable text as fallbacks. Mobile logos have a smaller, centered image area; large-screen logos keep their original left alignment. Shared artwork/chrome layering and season-scroll anchoring are retained.

Validation: 41 JVM tests, six Compose flow tests on each of the phone and TV emulators, and three additional responsive header checks pass (56 checks). Tablet portrait/landscape and phone landscape are tested through explicit emulator window sizes. Header bounds, centered phone headings, safe Back/heading spacing, season anchoring and shared-artwork transitions are covered. Debug assembly, APK signature verification and lint pass with zero lint errors and 27 existing warnings. Captures use the demo catalogue; live artwork/logo selection uses response fixtures. Physical hardware and XREAL/Unbound were not retested. Results and checksum are recorded in `artifacts/v0.6.2/build-manifest.json`.

## What changed in 0.6.1

The player now loads optional provider skip-event metadata for the actual audio edition, falling back to the original episode and legacy intro metadata when necessary. Skip recap, Skip intro and Skip titles appear only inside their timestamp ranges and stay available when the other controls fade. Seeks preserve pause/play state and save progress. Skipping titles stops at the supplied credits end, preserving post-credits content. Missing, invalid or unavailable markers never block playback. Marker jobs are cancelled on episode changes, backgrounding and exit.

Next chapter now resolves through the service's up-next endpoint on every playback entry, including Continue Watching and history. It no longer relies on opening a series page to populate the season cache. Returned episodes are cached before navigation; unrelated recommendations and the current episode are ignored. Lookup failure offers Retry, and autoplay observes a next episode that arrives after playback has ended.

Validation: all 41 JVM tests and two targeted player tests on each of the phone and TV emulators pass (45 checks). Tests cover malformed/missing markers, public metadata transport and cancellation, a history-only episode cache, next-season navigation, actual sample-video seeks, hidden controls, TV D-pad OK, preserved pause, post-credits content and recreation. Assembly, signature verification and lint pass with no lint errors. Account responses use fixtures; a signed-in live-account check and physical-device verification were not repeated. Results and checksum are recorded in `artifacts/v0.6.1/build-manifest.json`.

## What changed in 0.6.0

The player adds an extensible Off/FSR 1 picker and per-profile 0–100% sharpness. AMD's upstream FP32 EASU and RCAS kernels run as two Media3 GPU passes. Processing follows the viewport and active physical display dimensions, preserves aspect ratio and follows adaptive video-resolution changes. Native-resolution video bypasses both kernels; 0% sharpness bypasses only RCAS. HDR and unsupported GPUs retain standard scaling with an explanation.

Phones and XREAL retain TextureView; Android TV uses fixed physical-resolution SurfaceView buffers when FSR is selected. A 4K TV emulator with a 1080p logical UI verifies actual 3840 × 2160 processing and surface buffers. Display reports include upscaler and output dimensions. See [UPSCALING.md](UPSCALING.md) for implementation, licensing and hardware limits.

The default Media3 effects graph is initialized before preparation so switching FSR on/off during playback works. Clear and publicly licensed software Widevine video are verified through this path. Three existing lint errors were corrected: the API 27 navigation-bar attribute is now version-scoped, playback uses LocalActivity, and the profile badge observes account state.

Validation: 32 JVM tests, 10 phone emulator tests and three targeted TV emulator tests pass (45 total). Debug assembly, APK signature verification and lint pass; existing nonfatal lint warnings remain. Results, checksum and hardware limits are recorded in `artifacts/v0.6.0/build-manifest.json`.

## Compatibility retained from 0.5.1

Spatial compatibility has been reported to work with OnePlus 15 / Android 17 / Unbound / XREAL One. That exact TextureView/software Widevine configuration is now the only playback path, including on phones and TVs. Mode selection, display-based switching and the stored mode preference are removed. Upgrades ignore the former mode value immediately and remove its storage key during later preference writes. Display checks and copyable reports remain available as diagnostics.

## Previous compatibility work in 0.5.0

Spatial compatibility adds a device-supported software Widevine request and TextureView for external spatial windows. Automatic mode detects missing secure/protected display capabilities; Standard preserves the ordinary player path. Mode changes retain episode position and pause state. Settings and the player expose mode selection plus a copyable display report. Settings also offers clear and public Widevine diagnostic clips, isolated from account sessions/history. See [display compatibility](DISPLAY_COMPATIBILITY.md) for limits and the OnePlus/Unbound investigation.

## Account library fixes retained from 0.4.0

1. **Resume and history:** connected Home has Continue Watching; Profile exposes account Watch history. History loads in pages. Playback reads cloud playheads before opening the stream, preserving newer/unsynced local checkpoints. The series primary action resumes the relevant episode, and Start over explicitly resets it. Near-finished episodes are not treated as complete solely because they passed 95%.
2. **My List:** episode panels returned by the discover watchlist endpoint are resolved to their owning series. Saved entries now survive reload. Add to My List/In My List are visible actions, with loading/error/retry states and stale-fetch protection around mutations. Account caches remain profile-scoped.
3. **Series artwork:** landscape banner artwork is retained separately from the wide hero background. The 0.4.0 contained-image header has been replaced by the adaptive 0.6.2 header described above. Shared artwork remeasures with changing bounds instead of scaling its entire previously measured layout.
4. **Season switching:** the selector stays anchored. Existing episode rows remain visible but disabled while the selected season loads; successful data commits are versioned, and the scroll anchor is restored at the episode section. A minimum episode-section height prevents a short season from collapsing the scroll range back into the hero.
5. **Subtitle formatting:** service-provided pre-rendered subtitle manifests are preferred when available, preserving authored fonts, colors, positions and line layout. Tracks offers language/Styled choices and Off. Manifest changes keep the current playback position. Soft sidecars remain a fallback for languages without a styled rendition.

The 0.3.1 password-header fix, stable installation identity, native Widevine player, explicit quality picker and OLED/Maximal motion defaults are retained.

## Install

Install `artifacts/cinema-oled-0.7.2.apk` over the previous version. The application ID and debug signing key remain the same; clearing app data is unnecessary. Account session material remains encrypted in Android Keystore-backed storage, and passwords are not persisted.

## Verification

Run checks on one emulator explicitly:

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home \
  ./scripts/check-android.sh emulator-5554
```

The 0.5.1 suite passes 28 JVM checks and eight UI/security tests on each of the phone and TV emulators (44 total). The three tests for the removed mode-selection policy have been retired. Display checks now verify that default playback attaches a real TextureView and opens an actual licensed L3 session without choosing a mode; the removed mode controls remain absent after recreation. Pause/seek/resume, history isolation and the other existing UI checks remain covered. No fresh account login is needed for public test clips; reported successful account playback is the physical-device evidence. The previous 0.4.0 account scenario passed on both emulators. It includes response fixtures for episode-panel watchlists, history/pagination/playhead mapping, local/cloud progress ordering, styled subtitle manifest selection and banner metadata. UI checks cover season-anchor retention, banner bounds, interrupted artwork transitions, navigation, local list persistence, login privacy and playback mechanics.

The opt-in account path exercises real history and watchlist loading; a reversible add/remove/reload for a series not previously saved; cloud resume; real season switching; fetched logos/banner; Android CDM licensing and rendered native video; styled subtitles Off/on while video continues; quality switching, account playhead sync, lease renewal and cleanup. Test changes to a previously existing episode playhead are restored after the check.

## Device scope and test-runner incident

Phone emulator: Android 13 / API 33, ARM64 Google APIs, Pixel 6 profile. TV emulator: Android TV 16 / API 36, ARM64, 1920×1080.

An unrestricted Android test command also picked up a newly connected physical CPH2747 phone running Android 17. Its old Espresso input harness failed before UI tests could execute, and test cleanup removed the debug package. The prior working 0.3.1 APK was restored successfully. Package removal may have cleared that phone’s local sign-in data; the physical device is not counted as a passing UI verification target. Further runs explicitly select an emulator. `scripts/check-android.sh` rejects physical-device IDs to prevent accidental targeting. The Gradle connected-test task also requires an explicit ANDROID_SERIAL before device installation can start.

## Current artifacts

- `artifacts/cinema-oled-0.7.2.apk`
- `artifacts/v0.7.2/build-manifest.json`: checksum, verification summary and limits.
- `artifacts/v0.7.2/`: native screenshots, JVM and phone emulator test results.

## Previous account verification artifacts

- `artifacts/cinema-oled-0.4.0.apk`
- `artifacts/v0.4.0/build-manifest.json`: checksum and verification summary.
- `artifacts/v0.4.0/test-results/`: matching passing JVM/emulator XML results.
- `artifacts/v0.4.0/phone-library-final.txt`, `tv-library-final.txt`: account feature outcomes without credentials or signed stream material.
- `artifacts/v0.4.0/series-banner.png`, `title-logo.png`, `quality-menu.png`: public series/player UI captures; protected movie content is not exported.

## Limits

Styled subtitle availability is controlled by the service. Languages without a provider-styled rendition use Media3’s soft subtitle fallback and may not preserve every advanced ASS effect. Switching between embedded styled subtitles and Off/languages briefly reparses the stream at the current position. Physical hardware, all remote-only flows, extended background/long-session behavior, TalkBack/font scaling and release-build frame performance still need broader coverage. Named custom Crunchylists, casting and downloads are not part of this watchlist implementation.

See `INTEGRATION.md`, `MOTION.md` and `THIRD_PARTY.md` for additional protocol, animation and attribution details.

# Sumi — unofficial Crunchyroll client

Sumi is an independent, unofficial Crunchyroll client for Android phones, tablets, and Android TV, built with Kotlin and Jetpack Compose. The selected Cinema design uses true black, orange accents, bundled DM Sans / Barlow Condensed, and Maximal motion by default.

**Player and My Content update:** double-tap either side of mobile video to rewind or advance, with a saved 5/10/15/30-second interval in Profile → Settings (10 seconds by default). Opening a series has distinct haptic feedback; manually scrubbing the timeline ticks at second boundaries, with rapid movement coalesced. Play and resume receive brief orange feedback that respects motion settings. My Content replaces My List with Watched shows, Watchlist, and History, including account pagination and restored tab/scroll positions. Audio and subtitle preferences offer 22 languages and regional variants in scrollable selectors, plus subtitles Off. Exact regional tracks are preferred when available. History shows the latest available checkpoint per episode; it does not reconstruct every rewatch session. The feature build is [sumi-player-content.apk](artifacts/player-content/sumi-player-content.apk).

**Interface refinement:** shared page headings, quieter selection chips, outlined secondary actions, consistent profile panels, and catalogue grids that adapt to the available width and text size. Settings and History retain the Profile navigation selection. Reduced motion avoids unnecessary control layers. Matching screenshots from the actual phone and TV apps are in [the before/after gallery](artifacts/ui-polish/comparison.html).

**Smooth artwork corners:** opening a title gradually straightens the card's rounded corners as the artwork expands; returning restores them smoothly, even when Back interrupts the animation. Full-width spotlight artwork stays square.

**Status: 0.7.4 adds title logos to compact mobile Home.** The mobile spotlight uses the same landscape hero artwork as TV in a shallow 16:9 banner, with the same localized title-logo artwork as TV and metadata over a bottom fade. Logo images keep their aspect ratio in a compact area; titles fall back to readable text while loading or if artwork is missing or fails. Home supports logos in both phone orientations. Play, watchlist, and details actions sit in a compact row underneath, leaving room for Continue Watching on the first screen. Larger text can expand the banner instead of clipping its content. Mobile series details retain the full 2:3 portrait poster without a duplicate logo. Tablet and TV layouts, saved accounts, and smooth artwork transitions are retained. The latest APK is `artifacts/home-logo/sumi-home-logo-0.7.4.apk`; the previous portrait-Home build remains in `artifacts/corner-transition/`.

The 0.7.1 MyAnimeList fix is retained. The MyAnimeList lookup uses Tenrai instead of Jikan’s discontinued public API. Show details display Crunchyroll’s community score out of 5, MyAnimeList’s score out of 10 and overall rank, and IMDb’s score out of 10. Sources load independently with caching, unavailable states and retries. MyAnimeList and IMDb matches require a unique title/alias and a consistent release year; ambiguous matches stay unavailable. Demo scores are explicitly labeled sample data. See [rating sources and matching](docs/RATINGS.md).

The 0.6.5 phone landscape behavior is retained. Playback supports either landscape direction and restores the previous orientation setting when it closes. The setting survives activity recreation and episode changes. Tablets and TV retain their existing orientation behavior.

The fullscreen fixes from 0.6.4 are retained. The player and its Tracks, Quality, Upscaler and Display info menus hide the status and navigation bars. Immersive mode is reapplied on resume and window focus, stays active across episode changes, and restores normal system bars when playback closes. Edge swipes temporarily reveal the bars using Android's normal immersive behavior.

The 0.6.3 artwork and Home improvements are retained. Phone detail headers request the portrait poster before landscape fallbacks; Home and tablet/TV headers use wide hero artwork. Home starts with a varied selection, offers previous/next and Refresh controls, and advances when you choose Home. Connected catalogue data refreshes after 15 minutes when Home is reopened or resumed. The selected title loads its own localized logo and artwork; catalogue/watchlist refreshes preserve those assets. Playback and skip controls are retained.

Episodes play inside the app using Media3 and Android Widevine. FSR 1 upscaling and saved sharpness controls from 0.6.0 are retained. The player has an explicit **Quality** menu with Auto and the supported resolutions from the current stream. Home titles on all devices and tablet/TV detail titles load Crunchyroll’s localized title-logo artwork, with a text fallback. Styled subtitle video renditions are preferred when the service offers them; Off and language changes preserve playback position.

Use **Sign in** on the welcome screen, or **Profile → Sign in to Crunchyroll** in demo mode. The 0.3.1 sign-in fix is retained. Version 0.4 adds cloud resume, Continue Watching, account history, working episode-panel watchlist mapping, stable season scrolling and aspect-preserving series banners. Password requests use the app’s sign-in identity; playback keeps its separate compatibility headers. Retries reuse a stable installation ID. Older 0.2 sessions may still require one fresh sign-in. Refresh tokens remain encrypted in Android Keystore-backed storage and excluded from backups. Passwords are not persisted.

The optional demo remains separate: its anime entries, chapter names, seasons and progress are fixtures. Its native Media3 player streams the Blender Foundation’s **Big Buck Bunny** test film. Existing design studies in `design/` are preserved.

## Video upscaling

Open **Player → Upscaler → FSR 1**. Adjust **Sharpness** from 0–100%; both settings are saved per profile. Video is upscaled to fit the active playback display while preserving its aspect ratio, including 3840 × 2160 output on a 4K TV. **Quality** controls the source resolution separately. FSR uses AMD’s EASU and RCAS GPU passes. Zero sharpness keeps upscaling and disables sharpening. Native-resolution and HDR video use standard scaling. See [upscaling behavior and compatibility](docs/UPSCALING.md).

## XREAL / Unbound playback

Install `artifacts/home-logo/sumi-home-logo-0.7.4.apk` over the previous build and play an episode. Phones, tablets and XREAL retain the texture-view/software-Widevine path with reported compatibility for OnePlus 15 / Android 17 / Unbound / XREAL One. Android TV uses native-resolution SurfaceView output while FSR 1 is selected, allowing 4K video independently of UI resolution. There is no display-mode selection. Previously saved Automatic/Standard/Spatial preferences are ignored and removed during subsequent preference writes. Sessions and other profile settings remain intact.

The player requests genuine device-supported Widevine L3. Android obtains and enforces the normal license, including output restrictions; available resolution/HDR still depends on the device and license.

**Profile → Settings → Check this display** retains separate clear and public Widevine test clips. The player’s **Display info** dialog retains **Copy display report** for troubleshooting. Reports exclude account identity, tokens and stream URLs. See [display compatibility details](docs/DISPLAY_COMPATIBILITY.md).

## Build

Requirements: JDK 17, Android SDK platform 37 and build tools 36.0.0. The Gradle wrapper downloads Gradle 9.7.1 and verifies its checksum. Dependency versions are pinned in `gradle/libs.versions.toml`.

```sh
# macOS, if your default Java is newer:
export JAVA_HOME=$(/usr/libexec/java_home -v 17)

# Android Studio can create local.properties for you. Otherwise:
printf 'sdk.dir=%s/Library/Android/sdk\n' "$HOME" > local.properties

./gradlew :app:assembleDebug :app:testDebugUnitTest
```

The APK is `app/build/outputs/apk/debug/app-debug.apk`, displayed as **Sumi** in Android. One APK supports both the standard Android launcher and the Android TV launcher. Minimum Android version: Android 8.0 / API 26. The existing `dev.cinema.crunchyroll` application ID is retained so Sumi updates previous installs and keeps their account sessions and preferences.

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.cinema.crunchyroll/.MainActivity

# Actual UI + sample playback tests on a connected device or emulator:
./scripts/check-android.sh emulator-5554
```

## Implemented

- Account sign-in with cancellation, actionable service errors, secure session restoration, serialized refresh and sign-out.
- Real catalogue/search/artwork and title logos, account profiles, seasons/episodes and service watchlist mutations.
- Native account playback, Android CDM licensing, available-resolution selection, subtitle sidecars, progress sync, session keepalive and cleanup.
- Onboarding with explicit unofficial status; separate optional demo.
- Immersive Home, sample Continue Watching, discovery rows, real collection destinations.
- Debounced search with cancellation, categories, loading, empty and retry states.
- Details, reversible shared artwork, demo seasons and chapters.
- Persistent watchlists with commit-before-success behavior and undo; per-profile sample history.
- Two local profiles, profile-specific language, autoplay, haptic and motion preferences.
- Maximal, Standard and Reduced motion; system animation duration scaling; true black system bars.
- Bottom navigation below 600dp, navigation rail for larger touch windows, distinct TV navigation and TV Compose cards.
- Media3 ExoPlayer + MediaSession, pause/play, seeking, buffering/error/retry, track discovery, resume, lifecycle pause, next-chapter countdown/cancel, media keys and session cleanup.

## Structure

```text
app/src/main/java/dev/cinema/crunchyroll/
  data/       Catalogue interface, fixtures, DataStore, profile models, search pipeline
  design/     OLED theme, bundled fonts, vector icons, shared artwork, motion primitives
  ui/         Saved-state ViewModel, adaptive shell, catalogue and account screens
  playback/   Retained ExoPlayer engine, MediaSession, animated controls
```

`CatalogueRepository` is the boundary for future live catalogue implementations. `UserStore` currently stores **demo-only** state. Connected repositories and the Keystore-backed refresh-token vault are implemented in `data/account/`. Account caches are discarded on profile switch and logout; account preferences have separate keys from demo profiles.

## Remaining work

See [implementation status](docs/IMPLEMENTATION.md), [integration audit](docs/INTEGRATION.md), and [motion audit](docs/MOTION.md). Account sign-in, entitled native Widevine playback, quality switching and session cleanup were exercised using securely supplied credentials. Credentials and tokens are excluded from artifacts and source. Real hardware, TalkBack, comprehensive D-pad focus restoration, font scaling, predictive Back, and frame performance still need dedicated validation. Real native DRM playback, quality changes, title-logo loading, progress and lease cleanup are now tested on emulators. Physical-device and long-session testing remain open.

## Attribution

See [third-party material](docs/THIRD_PARTY.md). This project is independent and unofficial; it is not affiliated with Crunchyroll. Design-reference artwork is not a production catalogue asset pipeline.

The device-check script requires an explicit emulator ID. Avoid an unrestricted `connectedDebugAndroidTest` invocation: Android’s test runner may install/remove debug packages on every connected device.

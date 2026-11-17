<p align="center">
  <img src="design/brand/sumi-spark.svg" width="112" height="112" alt="Sumi Spark logo">
</p>

<h1 align="center">Sumi</h1>

<p align="center">
  An unofficial Crunchyroll client for Android phones, tablets, and Android TV.<br>
  True-black screens, expressive motion, and one APK for every screen size.
</p>

<p align="center">
  <img alt="Version 0.7.4" src="https://img.shields.io/badge/version-0.7.4-FF7A26">
  <img alt="Android 8.0 or newer" src="https://img.shields.io/badge/Android-8.0%2B-000000">
  <img alt="Kotlin and Jetpack Compose" src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-101010">
</p>

> [!NOTE]
> Sumi is an independent project. It is not affiliated with, endorsed by, or sponsored by Crunchyroll. A Crunchyroll account is needed to watch real episodes. Sumi does not bypass subscriptions, regional availability, or DRM.

## Features

- **One app, three layouts.** Phones use bottom navigation, and tablets and other wide windows use a side rail. Android TV has top navigation and orange focus rings, and every screen is built for D-pad, Select, and Back.
- **Your Crunchyroll library.** Sign in to browse the catalogue, add and remove series from your watchlist, and resume episodes from your account's saved position.
- **My Content.** Watched shows, Watchlist, and History are grouped in one place. History shows the latest saved position for each episode.
- **Native playback.** Episodes play in Sumi using Media3 and Android Widevine. You can choose audio and subtitle tracks and the skip interval. Next episode and skip recap, intro, and credits appear when Crunchyroll supplies the data.
- **Pick the resolution, even on TV.** The player's **Quality** menu lists every resolution the stream offers, plus Auto, on phones, tablets, and Android TV. The official Crunchyroll app doesn't let you choose the resolution on TV. Sumi remembers your choice for each profile.
- **Ratings at a glance.** Show pages list Crunchyroll, MyAnimeList, and IMDb scores when a confident title match exists.
- **FSR 1 upscaling.** AMD FidelityFX Super Resolution 1 can upscale lower-resolution SDR video to your display. You can adjust its sharpness.
- **Motion you can tune.** Shared artwork transitions, spring physics, and small celebrations are on by default. Choose **Maximal**, **Standard**, or **Reduced**. Sumi also follows Android's animation settings.
- **Demo mode.** You can try the whole interface without an account using sample series and a freely licensed test film.

## Getting started

### Requirements

- JDK 17
- Android SDK Platform 37, Build Tools 36.0.0, and Platform Tools. You can install them with Android Studio's SDK Manager.
- An Android 8.0 (API 26) or newer phone, tablet, Android TV, or emulator

For command-line builds, set `JAVA_HOME` to the JDK and `ANDROID_HOME` to the Android SDK directory.

### Build

```sh
git clone https://github.com/MarkPronkin/sumi.git
cd sumi
./gradlew :app:assembleDebug
```

On Windows, use `gradlew.bat` instead of `./gradlew`. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

### Install

Enable USB debugging on your device, or start an emulator. Then install the APK:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Open **Sumi** from the launcher. On Android TV, it appears with your other apps.

## Using Sumi

When Sumi opens, choose one of two options:

| Option | What you get |
| --- | --- |
| **Sign in** | Your Crunchyroll catalogue, profiles, watchlist, history, and episode playback |
| **Explore the demo** | Sample series (*Frieren*, *Solo Leveling*, and *DAN DA DAN*) with chapters that play the Blender Foundation's *Big Buck Bunny*. Demo progress stays on the device. |

The app has four main sections:

- **Home:** Continue Watching, a featured spotlight, and rows of series
- **Browse:** search and filters for the catalogue
- **My Content:** Watched shows, Watchlist, and History
- **Profile:** your account and **Settings**

In **Profile → Settings**, you can choose your preferred audio and subtitle languages from 22 options. You can also turn autoplay on or off, set the skip interval (5, 10, 15, or 30 seconds), choose the motion intensity, and turn touch haptics on or off. Settings are saved per profile. **Check this display** plays test clips that help with external-display and Widevine troubleshooting.

On a phone or tablet, double-tap the left or right side of the video to skip back or forward.

### Privacy

Your password is used only to sign in. The refresh token is encrypted with a key stored in Android Keystore. Rating lookups use a separate network client that never sends Crunchyroll credentials. Diagnostic reports leave out account IDs, stream URLs, and license data.

## Testing

Run the JVM unit tests:

```sh
./gradlew :app:testDebugUnitTest
```

To run the UI and playback tests, start a phone or Android TV emulator. Then pass its serial from `adb devices` to the test script:

```sh
./scripts/check-android.sh emulator-5554
```

The script only targets emulators. It builds the app, runs the unit tests, and then runs the instrumentation tests on the emulator you name. Instrumented Gradle tasks also require `ANDROID_SERIAL`, so tests never install on every connected device.

## Project layout

```text
app/src/main/java/dev/cinema/crunchyroll/
├── data/        Catalogue, watchlist, ratings, preferences, and the account client
├── design/      Theme, icons, motion, and haptics
├── playback/    Media3 player, Widevine, FSR 1 upscaling, and display diagnostics
└── ui/          Compose screens for phone, tablet, and TV
app/src/main/assets/shaders/fsr1/   AMD FSR 1 shader sources
design/                             Brand assets and the approved design studies
docs/                               Technical notes for each feature area
scripts/check-android.sh            Emulator test runner
```

Built with Kotlin, Jetpack Compose, Compose for TV, Media3 (ExoPlayer), OkHttp, kotlinx.serialization, Coil, and DataStore.

## Documentation

| Topic | Document |
| --- | --- |
| Product and design specification | [SPEC.md](SPEC.md) |
| Implementation status and release notes | [docs/IMPLEMENTATION.md](docs/IMPLEMENTATION.md) |
| Account sign-in, library, and playback | [docs/INTEGRATION.md](docs/INTEGRATION.md) |
| Motion system | [docs/MOTION.md](docs/MOTION.md) |
| Ratings sources and title matching | [docs/RATINGS.md](docs/RATINGS.md) |
| FSR 1 upscaling | [docs/UPSCALING.md](docs/UPSCALING.md) |
| External displays and Widevine | [docs/DISPLAY_COMPATIBILITY.md](docs/DISPLAY_COMPATIBILITY.md) |
| Third-party fonts, shaders, artwork, and media | [docs/THIRD_PARTY.md](docs/THIRD_PARTY.md) |

## Acknowledgements

Sumi's service integration drew on research from [aarron-lee/crunchyroll-linux](https://github.com/aarron-lee/crunchyroll-linux). Sumi uses its own implementation and copies no upstream application code. The app bundles DM Sans and Barlow Condensed under the SIL Open Font License, and AMD's FidelityFX Super Resolution 1 shaders under the MIT license. Demo playback uses Blender Foundation films under CC BY 3.0. The demo anime artwork is design-reference material only. [THIRD_PARTY.md](docs/THIRD_PARTY.md) lists every source.

Crunchyroll is a trademark of its owner. Other series titles and artwork belong to their respective rights holders.

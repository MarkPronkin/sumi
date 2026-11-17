<img src="design/brand/sumi-spark.svg" width="96" height="96" alt="Sumi Spark logo">

# Sumi

Sumi is an independent, unofficial Crunchyroll client for Android phones, tablets, and Android TV. It uses Kotlin and Jetpack Compose for a true-black interface that adapts to touchscreens and TV remotes. One APK runs on all three.

## First launch

Choose **Sign in** to browse the Crunchyroll catalogue, manage your watchlist, and resume episodes from your account’s saved position. Episodes available to your account play in Sumi using Media3 and Android Widevine. Your password is used only for sign-in. Sumi encrypts the saved refresh token with a key held in Android Keystore.

Choose **Explore the demo** to try Sumi without an account. Open *Frieren*, *Solo Leveling*, or *DAN DA DAN* to browse sample seasons and chapters. Playing any demo chapter streams the Blender Foundation’s *Big Buck Bunny*. Demo watchlists and progress stay on the device.

## Browsing and playback

- **Home** puts Continue Watching alongside a rotating selection of series. Open a series to browse seasons and episodes and see available ratings from Crunchyroll, MyAnimeList, and IMDb.
- **My Content** groups Watched shows, Watchlist, and History. History shows the latest saved position for each episode.
- **Player controls** let you pick an available audio or subtitle track, select a stream resolution, and skip forward or back. On a phone, double-tap either side of the video to seek. The interval defaults to 10 seconds and can be changed in **Profile → Settings**.
- In **Upscaler**, enable AMD FSR 1 to scale lower-resolution SDR video to your display. Set the source resolution in **Quality** and adjust output sharpness in **Upscaler**. Sumi remembers the upscaler settings per profile.

## Build and install

Use JDK 17 and install Android SDK Platform 37, Build Tools 36.0.0, and Platform Tools through Android Studio’s SDK Manager. For a command-line build, set `JAVA_HOME` to the JDK and `ANDROID_HOME` to the SDK directory.

```sh
git clone https://github.com/MarkPronkin/sumi.git
cd sumi
./gradlew :app:assembleDebug
```

On Windows, use `gradlew.bat` in place of `./gradlew`. The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

To install it, connect an Android 8.0 (API 26) or newer device with USB debugging enabled, or start an Android emulator. With `adb` from Platform Tools on your path and one device connected, run the following command, then open **Sumi** from the launcher.

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Tests

Run the JVM unit tests from the repository root.

```sh
./gradlew :app:testDebugUnitTest
```

For UI and playback tests, start a phone or TV emulator and find its serial with `adb devices`. Pass that serial to the test script, replacing `emulator-5554` with the one listed.

```sh
./scripts/check-android.sh emulator-5554
```

## Project notes

- [Account integration and playback](docs/INTEGRATION.md)
- [Rating sources and title matching](docs/RATINGS.md)
- [FSR 1 upscaling](docs/UPSCALING.md)
- [External displays and Widevine compatibility](docs/DISPLAY_COMPATIBILITY.md)
- [Credits for artwork, fonts, shaders, and demo media](docs/THIRD_PARTY.md)

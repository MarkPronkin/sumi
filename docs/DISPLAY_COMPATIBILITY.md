# XREAL / Unbound playback · 0.6.0

Account playback has reported compatibility with OnePlus 15, Android 17, current Unbound and XREAL One using the 0.5.0 Spatial compatibility option. Version 0.5.1 uses that exact playback configuration everywhere by default. Install the update and play; no mode selection is required.

## Playback path

Phones, tablets and spatial windows use TextureView. Android TV switches to a physical-resolution SurfaceView when FSR 1 is selected, so video can reach 4K independently of the UI resolution. Every native account or public test Widevine session requests a real device-supported L3 instance and verifies the returned security level. A device that cannot open L3 gets a specific error. Available quality/HDR remains controlled by the stream, device and license.

Version 0.6.0 initializes Media3's GPU effects graph before playback so FSR can be toggled during playback. The TextureView and L3 choices are retained, but the new graph still needs physical OnePlus/Unbound/XREAL verification. See [upscaling details](UPSCALING.md).

The former Automatic/Standard/Spatial picker, display-capability selection branch and per-profile mode field are removed. Old stored mode keys are ignored immediately on upgrade and removed during later preference writes. Account sessions, language/quality preferences, watchlists and progress are retained. Moving a window between displays no longer causes a mode change or stream reopen.

Android's CDM continues to own provisioning, licenses, decoder security and output enforcement. No HDCP spoofing, insecure-decoder override or protected-content capture is involved. Display flags and HDCP readbacks are used only in the optional report.

## Why this path was added

Unbound's ADB-assisted app windows cannot supply the secure virtual display used by stock Beam Pro. The [Linux reference player](https://github.com/aarron-lee/crunchyroll-linux/blob/master/server/js/core/player.js) requests software-secure Widevine. Native Android TextureView plus a genuine L3 request supplied a compatible licensed path in reported device testing.

References: Android [surface guidance](https://developer.android.com/media/media3/ui/surface) and [MediaDrm API](https://developer.android.com/reference/android/media/MediaDrm).

## Optional diagnostics

**Profile → Settings → Check this display** offers clear and public Widevine test clips without opening an account stream or writing watch history. Compare the visible result inside Unbound with ordinary XREAL mirroring when investigating another issue. The player's **Display info → Copy display report** remains available too.

Reports contain device/model/API, display name/flags/size, actual surface type and L3 request, upscaler/sharpness/input/output dimensions, actual opened DRM level, key loading, decoder, first-frame events, playback status, error code and informational HDCP readbacks. They exclude account/profile IDs, credentials, stream URLs, license bodies and content keys. A rendered-first-frame callback alone cannot establish what is visible in the glasses.

## Verification scope

Phone and TV emulator checks cover default clear and publicly licensed Widevine playback into a real TextureView with actual L3/key loading, without any mode selection. They also cover history isolation and playback position/pause retention through recreation. Reported successful account playback provides physical OnePlus/Unbound/XREAL evidence for this same configuration; no additional physical-device or fresh account-license test was performed locally for 0.5.1. Emulator results and the APK checksum are stored in `artifacts/v0.5.1/`.

Version 0.6.0 passes 32 JVM tests, all 10 phone emulator tests and three targeted TV emulator tests. Public Widevine L3 video passes through FSR on both devices. The TV runs a 3840 × 2160 physical display mode with a 1920 × 1080 logical UI; assertions verify a real 3840 × 2160 SurfaceView buffer. Current results and checksum are in `artifacts/v0.6.0/`. These results do not establish physical HDMI performance or visibility inside XREAL.

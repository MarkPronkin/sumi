# Third-party material

## Fonts

- DM Sans: Google Fonts / its copyright holders, SIL Open Font License 1.1. Bundled at `app/src/main/res/font/dm_sans.ttf`; license at [DM-Sans-OFL.txt](DM-Sans-OFL.txt).
- Barlow Condensed Bold: Jeremy Tribby and its contributors, SIL Open Font License 1.1. Bundled at `app/src/main/res/font/barlow_condensed.ttf`; license at [Barlow-Condensed-OFL.txt](Barlow-Condensed-OFL.txt).

Fonts were downloaded from the [Google Fonts repository](https://github.com/google/fonts). Runtime font downloads are not required.

## AMD FidelityFX Super Resolution 1

The FSR 1 GPU effects use AMD's [FidelityFX-FSR reference headers](https://github.com/GPUOpen-Effects/FidelityFX-FSR/tree/a21ffb8f6c13233ba336352bdff293894c706575/ffx-fsr), Copyright © 2021 Advanced Micro Devices, Inc., under the MIT license. Verbatim `ffx_a.h` and `ffx_fsr1.h`, including the full copyright and permission notices, are bundled in `app/src/main/assets/shaders/fsr1/`. GLES integration details are documented in [UPSCALING.md](UPSCALING.md).

## Demo film

**Big Buck Bunny**, © 2008 Blender Foundation, [bigbuckbunny.org](https://www.bigbuckbunny.org/), [Creative Commons Attribution 3.0](https://creativecommons.org/licenses/by/3.0/). The app streams an ExoPlayer test encoding from `https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4`. No anime episode is bundled or streamed in this demo.

The first candidate sample URL on the `gtv-videos-bucket` host returned HTTP 403 during verification. The selected ExoPlayer test URL returned HTTP 200. Actual decoder testing is documented separately.

The optional Widevine display check streams **Tears of Steel**, © 2012 Blender Foundation / Mango open movie project, [tearsofsteel.org](https://mango.blender.org/), [CC BY 3.0](https://creativecommons.org/licenses/by/3.0/). The manifest and public software-security test license are the samples listed by the [AndroidX Media3 demo](https://github.com/androidx/media/blob/release/demos/main/src/main/assets/media.exolist.json). The test uses Android’s normal CDM and sends no account headers.

## Anime artwork

The four existing files in `design/assets/` were copied into the demo’s drawable resources for fidelity to the approved design. The original files are unchanged. Their source URLs remain recorded in [sources.json](../design/assets/sources.json). These images are **design-reference material**, with no new redistribution rights asserted. Obtain artwork from an authenticated catalogue or another appropriately licensed source before production distribution.

## Integration reference

[aarron-lee/crunchyroll-linux](https://github.com/aarron-lee/crunchyroll-linux) is an integration/behavior reference only. No source code, Widevine certificates, or DRM adapters were copied. Version 0.3.0 uses pinned public Android client protocol identifiers from [crunchy-labs/artifacts](https://github.com/crunchy-labs/artifacts) for the matching native playback route; no user credential is embedded. Android’s own Widevine CDM processes licenses. Only the service’s transport envelope is decoded; content keys and decrypted movie data are not extracted. Its LICENSE/package metadata discrepancy is recorded in [INTEGRATION.md](INTEGRATION.md).

All new Android UI and state code in this workspace was written for this implementation. AndroidX, Kotlin and Gradle remain subject to their respective upstream licenses.

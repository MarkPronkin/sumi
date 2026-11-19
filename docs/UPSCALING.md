# Video upscaling

Version 0.6.0 adds **Player → Upscaler** with **Off** (default) and **FSR 1**. Selection and FSR sharpness are saved per profile, including separate connected-account profile preferences. The picker is an enum-backed registry so additional implementations can be added without changing quality selection.

FSR 1 runs AMD's EASU edge reconstruction and RCAS sharpening on decoded video using Media3's OpenGL effects pipeline. **Quality** controls the source rendition and bandwidth. Auto can change the stream resolution while the upscaler is enabled.

The sharpness slider runs from **0–100%**, with 5% steps and explicit minus/plus buttons for TV remotes. Changes reach the next processed video frame. Zero bypasses RCAS while retaining EASU; 100% applies AMD's maximum RCAS coefficient. The default is 50%. FSR is bypassed entirely when the source already fills or exceeds the output, rather than using RCAS alone.

## Output resolution

The target follows the current playback viewport and the active display mode. A 16:9 720p or 1080p source on a fullscreen **3840 × 2160** display is processed to **3840 × 2160**. Aspect ratios are preserved: 4:3 content on that display becomes 2880 × 2160 and ultrawide video retains its bars. Rotation, viewport size, display attachment and adaptive source-size changes update the target. GPU maximum texture size limits are respected.

Android TV often renders its UI at 1080p even when HDMI output is 4K. With FSR selected on TV, playback uses a SurfaceView with a fixed physical-resolution buffer, allowing actual 4K video output independently of UI density. Off returns to the original TextureView. Phones, tablets and XREAL / Unbound keep the working TextureView output. Android's active display mode is respected; the app does not force a different HDMI mode.

**Display info** and **Copy display report** include the chosen upscaler, sharpness, input/output dimensions, actual surface type, whether the effect is active, and whether a frame completed the FSR passes.

## Compatibility

- Both shaders use OpenGL ES 2.0 and require high-precision fragment floats. The player tries them on the active playback context across GPU vendors. If a shader fails to initialize, the picker identifies the failed pass and playback uses standard scaling.
- FSR 1 processes SDR video in Media3 1.11.1's default display-encoded SDR working space. HDR frames pass through with their original precision.
- The existing Android Widevine L3 licensing path remains in use. Output restrictions and available stream resolutions are still enforced by Android and the license.
- Spatial upscaling cannot recover detail absent from the source. Large scale ratios can exaggerate compression, and 4K processing performance depends on the GPU.
- Physical-device 4K HDMI output, HDR devices, XREAL and long-session thermal performance require hardware validation.

## Source and portability

`app/src/main/assets/shaders/fsr1/ffx_a.h` and `ffx_fsr1.h` are verbatim AMD reference files pinned to [a21ffb8f6c13233ba336352bdff293894c706575](https://github.com/GPUOpen-Effects/FidelityFX-FSR/tree/a21ffb8f6c13233ba336352bdff293894c706575/ffx-fsr). Their MIT copyright and license notices are bundled in the APK.

The player compiles two GLSL ES 1.00 adaptations of the scalar reference kernels. EASU reads its 12 neighboring RGB texels once each using normalized texel-center coordinates and nearest sampling. RCAS reads the center and four neighbors. Both use native floating-point math and clamp sampling at the texture edges. This avoids the bitfield helper names that collided with built-ins on the OnePlus 15 driver.

Validation covers sizing and native-resolution bypass, actual offscreen GPU pixels at 64 × 64 and 3840 × 2160, zero-sharpness identity, effective sharpening, opaque borders, live sample playback, saved settings, recreation, seeking and switching off. See the versioned test results in `artifacts/v0.6.0/`.

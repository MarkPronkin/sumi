// AMD FSR 1 RCAS. Copyright (c) 2021 Advanced Micro Devices, Inc.
// Adapted from the FP32 kernel in ffx_fsr1.h. See that file for the MIT license.
precision highp float;
uniform highp sampler2D uTexSampler;
uniform vec2 uTexelSize;
uniform float uApply;
uniform float uSharpness;
varying highp vec2 vTexCoord;

void main() {
    vec4 center = texture2D(uTexSampler, vTexCoord);
    if (uApply < 0.5 || uSharpness <= 0.0) {
        gl_FragColor = center;
        return;
    }

    vec3 above = texture2D(uTexSampler, vTexCoord - vec2(0.0, uTexelSize.y)).rgb;
    vec3 below = texture2D(uTexSampler, vTexCoord + vec2(0.0, uTexelSize.y)).rgb;
    vec3 left = texture2D(uTexSampler, vTexCoord - vec2(uTexelSize.x, 0.0)).rgb;
    vec3 right = texture2D(uTexSampler, vTexCoord + vec2(uTexelSize.x, 0.0)).rgb;
    vec3 minimum = min(min(above, below), min(left, right));
    vec3 maximum = max(max(above, below), max(left, right));

    // Bound the negative lobe for every channel. Guard solid black and white neighborhoods.
    vec3 hitMin = min(minimum, center.rgb) / max(4.0 * maximum, vec3(1.0e-6));
    vec3 hitMax = (1.0 - max(maximum, center.rgb)) / min(4.0 * minimum - 4.0, vec3(-1.0e-6));
    vec3 limits = max(-hitMin, hitMax);
    float lobe = clamp(max(limits.r, max(limits.g, limits.b)), -0.1875, 0.0) * uSharpness;
    vec3 color = (center.rgb + lobe * (above + below + left + right)) / (1.0 + 4.0 * lobe);
    gl_FragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}

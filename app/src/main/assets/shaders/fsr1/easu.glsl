// AMD FSR 1 EASU. Copyright (c) 2021 Advanced Micro Devices, Inc.
// Adapted from the FP32 kernel in ffx_fsr1.h. See that file for the MIT license.
precision highp float;
uniform highp sampler2D uTexSampler;
uniform vec2 uInputSize;
uniform vec2 uTexelSize;
uniform float uApply;
varying highp vec2 vTexCoord;

float luma(vec3 c) {
    return c.g + 0.5 * (c.r + c.b);
}

// Estimate edge direction and how consistently each axis follows that edge.
void accumulateEdge(inout vec2 direction, inout float lengthSum, float weight,
                    float above, float left, float center, float right, float below) {
    vec2 gradient = vec2(right - left, below - above);
    vec2 steepest = max(abs(vec2(right, below) - center), abs(center - vec2(left, above)));
    vec2 alignment = min(abs(gradient) / max(steepest, vec2(1.0e-6)), 1.0);
    direction += weight * gradient;
    lengthSum += weight * dot(alignment, alignment);
}

// Rotate into the edge's coordinate system and evaluate AMD's Lanczos2 approximation.
void tap(inout vec3 colorSum, inout float weightSum, vec2 offset, vec3 color,
         vec2 direction, vec2 axisScale, float lobe, float cutoff) {
    vec2 v = vec2(dot(offset, direction), dot(offset, vec2(-direction.y, direction.x))) * axisScale;
    float distance2 = min(dot(v, v), cutoff);
    float base = 0.4 * distance2 - 1.0;
    float window = lobe * distance2 - 1.0;
    float weight = (1.5625 * base * base - 0.5625) * window * window;
    colorSum += weight * color;
    weightSum += weight;
}

vec3 sampleTap(vec2 center, vec2 offset) {
    return texture2D(uTexSampler, center + offset * uTexelSize).rgb;
}

void main() {
    if (uApply < 0.5) {
        gl_FragColor = texture2D(uTexSampler, vTexCoord);
        return;
    }

    vec2 position = vTexCoord * uInputSize - 0.5;
    vec2 fraction = fract(position);
    vec2 center = (floor(position) + 0.5) * uTexelSize;

    // Each tap reads RGB together. Media3 binds the input with CLAMP_TO_EDGE.
    //     b c
    //   e f g h
    //   i j k l
    //     n o
    vec3 b = sampleTap(center, vec2( 0.0, -1.0));
    vec3 c = sampleTap(center, vec2( 1.0, -1.0));
    vec3 e = sampleTap(center, vec2(-1.0,  0.0));
    vec3 f = sampleTap(center, vec2( 0.0,  0.0));
    vec3 g = sampleTap(center, vec2( 1.0,  0.0));
    vec3 h = sampleTap(center, vec2( 2.0,  0.0));
    vec3 i = sampleTap(center, vec2(-1.0,  1.0));
    vec3 j = sampleTap(center, vec2( 0.0,  1.0));
    vec3 k = sampleTap(center, vec2( 1.0,  1.0));
    vec3 l = sampleTap(center, vec2( 2.0,  1.0));
    vec3 n = sampleTap(center, vec2( 0.0,  2.0));
    vec3 o = sampleTap(center, vec2( 1.0,  2.0));

    vec2 direction = vec2(0.0);
    float lengthSum = 0.0;
    vec2 opposite = 1.0 - fraction;
    accumulateEdge(direction, lengthSum, opposite.x * opposite.y, luma(b), luma(e), luma(f), luma(g), luma(j));
    accumulateEdge(direction, lengthSum, fraction.x * opposite.y, luma(c), luma(f), luma(g), luma(h), luma(k));
    accumulateEdge(direction, lengthSum, opposite.x * fraction.y, luma(f), luma(i), luma(j), luma(k), luma(n));
    accumulateEdge(direction, lengthSum, fraction.x * fraction.y, luma(g), luma(j), luma(k), luma(l), luma(o));

    float magnitude2 = dot(direction, direction);
    direction = magnitude2 < 1.0 / 32768.0 ? vec2(1.0, 0.0) : direction * inversesqrt(magnitude2);
    float edge = 0.5 * lengthSum;
    edge *= edge;
    float stretch = 1.0 / max(abs(direction.x), abs(direction.y));
    vec2 axisScale = vec2(1.0 + (stretch - 1.0) * edge, 1.0 - 0.5 * edge);
    float lobe = mix(0.5, 0.21, edge);
    float cutoff = 1.0 / lobe;

    vec3 colorSum = vec3(0.0);
    float weightSum = 0.0;
    tap(colorSum, weightSum, vec2( 0.0, -1.0) - fraction, b, direction, axisScale, lobe, cutoff);
    tap(colorSum, weightSum, vec2( 1.0, -1.0) - fraction, c, direction, axisScale, lobe, cutoff);
    tap(colorSum, weightSum, vec2(-1.0,  0.0) - fraction, e, direction, axisScale, lobe, cutoff);
    tap(colorSum, weightSum, vec2( 0.0,  0.0) - fraction, f, direction, axisScale, lobe, cutoff);
    tap(colorSum, weightSum, vec2( 1.0,  0.0) - fraction, g, direction, axisScale, lobe, cutoff);
    tap(colorSum, weightSum, vec2( 2.0,  0.0) - fraction, h, direction, axisScale, lobe, cutoff);
    tap(colorSum, weightSum, vec2(-1.0,  1.0) - fraction, i, direction, axisScale, lobe, cutoff);
    tap(colorSum, weightSum, vec2( 0.0,  1.0) - fraction, j, direction, axisScale, lobe, cutoff);
    tap(colorSum, weightSum, vec2( 1.0,  1.0) - fraction, k, direction, axisScale, lobe, cutoff);
    tap(colorSum, weightSum, vec2( 2.0,  1.0) - fraction, l, direction, axisScale, lobe, cutoff);
    tap(colorSum, weightSum, vec2( 0.0,  2.0) - fraction, n, direction, axisScale, lobe, cutoff);
    tap(colorSum, weightSum, vec2( 1.0,  2.0) - fraction, o, direction, axisScale, lobe, cutoff);

    vec3 minimum = min(min(f, g), min(j, k));
    vec3 maximum = max(max(f, g), max(j, k));
    gl_FragColor = vec4(clamp(colorSum / weightSum, minimum, maximum), 1.0);
}

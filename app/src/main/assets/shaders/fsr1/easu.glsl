// GLES 3.0 callbacks for AMD FSR 1. See the copyright/license in ffx_fsr1.h.
uniform sampler2D uTexSampler;
uniform vec2 uInputSize;
uniform vec2 uOutputSize;
uniform float uApply;
in vec2 vTexCoord;
out vec4 outColor;

// Media3 1.11.1's default SDR working space is display encoded, as EASU requires.
// HDR is bypassed in Fsr1Pipeline; no HDR values are clamped by these kernels.
// Emulate textureGather with clamped texelFetch for GLES 3.0 (gather requires 3.1).
vec4 gatherChannel(vec2 p, int channel) {
    ivec2 base = ivec2(floor(p * uInputSize - 0.5));
    ivec2 edge = ivec2(uInputSize) - 1;
    return vec4(
        texelFetch(uTexSampler, clamp(base + ivec2(0,1), ivec2(0), edge), 0)[channel],
        texelFetch(uTexSampler, clamp(base + ivec2(1,1), ivec2(0), edge), 0)[channel],
        texelFetch(uTexSampler, clamp(base + ivec2(1,0), ivec2(0), edge), 0)[channel],
        texelFetch(uTexSampler, clamp(base, ivec2(0), edge), 0)[channel]);
}
vec4 FsrEasuRF(vec2 p) {return gatherChannel(p,0);}
vec4 FsrEasuGF(vec2 p) {return gatherChannel(p,1);}
vec4 FsrEasuBF(vec2 p) {return gatherChannel(p,2);}
void main() {
    if (uApply < 0.5) {outColor=texture(uTexSampler,vTexCoord); return;}
    uvec4 con0,con1,con2,con3;
    FsrEasuCon(con0,con1,con2,con3,uInputSize.x,uInputSize.y,uInputSize.x,uInputSize.y,uOutputSize.x,uOutputSize.y);
    vec3 color;
    FsrEasuF(color,uvec2(gl_FragCoord.xy),con0,con1,con2,con3);
    outColor=vec4(color,1.0);
}

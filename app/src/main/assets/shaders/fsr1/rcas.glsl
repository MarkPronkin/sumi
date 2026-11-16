// GLES callbacks for AMD FSR 1. See the copyright/license in ffx_fsr1.h.
uniform sampler2D uTexSampler;
uniform float uApply;
uniform float uSharpness;
in vec2 vTexCoord;
out vec4 outColor;
vec4 FsrRcasLoadF(ivec2 p) {
    return texelFetch(uTexSampler,clamp(p,ivec2(0),textureSize(uTexSampler,0)-1),0);
}
void FsrRcasInputF(inout float r,inout float g,inout float b) {}
void main() {
    // Zero is a true sharpening bypass; EASU still runs.
    if (uApply < 0.5 || uSharpness <= 0.0) {outColor=texture(uTexSampler,vTexCoord); return;}
    vec3 color;
    // AMD's RCAS coefficient is exp2(-stops). Expose it as an increasing 0..1 strength.
    uvec4 con=uvec4(floatBitsToUint(uSharpness),0u,0u,0u);
    FsrRcasF(color.r,color.g,color.b,uvec2(gl_FragCoord.xy),con);
    outColor=vec4(clamp(color,0.0,1.0),1.0);
}

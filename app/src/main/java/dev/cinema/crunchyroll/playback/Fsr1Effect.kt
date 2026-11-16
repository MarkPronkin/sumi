@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package dev.cinema.crunchyroll.playback

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

/** One EASU reconstruction pass followed by one RCAS pass, using AMD's unmodified FP32 kernels. */
class Fsr1Pipeline(
    val target: VideoDimensions,
    sharpness: Int,
    private val onStatus: (UpscalingStatus)->Unit,
) {
    @Volatile var sharpness: Int=sharpness.coerceIn(0,100)
    private var status=UpscalingStatus()
    private var failure: String?=null
    val effects: List<GlEffect> = listOf(Pass(true),Pass(false))

    private inner class Pass(private val easu: Boolean): GlEffect {
        override fun toGlShaderProgram(context: Context,useHdr: Boolean): GlShaderProgram {
            if(easu) failure=null
            if(useHdr) failure="FSR 1 supports SDR video. HDR uses standard scaling."
            if(GlUtil.getContextMajorVersion()<3) failure="FSR 1 requires OpenGL ES 3.0. Using standard scaling."
            if(failure==null) {
                try {return Program(context,easu)}
                catch(e: Exception) {
                    android.util.Log.w("CinemaFsr","FSR initialization: ${e.message?.substringBefore(", source:")}")
                    failure="FSR 1 is unavailable on this GPU. Using standard scaling."
                }
            }
            status=status.copy(active=false,rendered=false,notice=failure);onStatus(status)
            return CopyProgram(useHdr) {width,height ->
                if(easu) {status=UpscalingStatus(VideoDimensions(width,height),VideoDimensions(width,height),notice=failure);onStatus(status)}
            }
        }
    }

    private inner class Program(context: Context,private val easu: Boolean): BaseGlShaderProgram(false,1) {
        private val gl=GlProgram(VERTEX_300,buildString {
            append("#version 300 es\nprecision highp float;\nprecision highp int;\n#define A_GPU 1\n#define A_GLSL 1\n")
            append(if(easu) "#define FSR_EASU_F 1\n" else "#define FSR_RCAS_F 1\n")
            append(context.assets.open("shaders/fsr1/gles_compat.glsl").bufferedReader().use {it.readText()})
            append('\n')
            append(context.assets.open("shaders/fsr1/ffx_a.h").bufferedReader().use {it.readText()})
            append('\n')
            // Desktop GLSL permits these implicit signed-to-unsigned assignments; GLES requires 'u'.
            append(context.assets.open("shaders/fsr1/ffx_fsr1.h").bufferedReader().use {it.readText()}
                .replace("con3[2]=con3[3]=0;","con3[2]=con3[3]=0u;")
                .replace("con[2]=0;","con[2]=0u;").replace("con[3]=0;","con[3]=0u;"))
            append('\n')
            append(context.assets.open(if(easu) "shaders/fsr1/easu.glsl" else "shaders/fsr1/rcas.glsl").bufferedReader().use {it.readText()})
        }).apply {setBufferAttribute("aFramePosition",VERTICES,4)}
        private var applied=false

        override fun configure(inputWidth: Int,inputHeight: Int): Size {
            val input=VideoDimensions(inputWidth,inputHeight)
            if(easu) {
                val limit=IntArray(1);GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE,limit,0)
                val output=upscaleDimensions(input,target,limit[0])
                applied=output!=input
                status=UpscalingStatus(input,output,active=applied && failure==null,notice=failure ?: when {
                    !target.valid -> "Waiting for the playback display."
                    !applied -> "Video already meets or exceeds the output resolution."
                    output!=upscaleDimensions(input,target) -> "Output is limited by this GPU’s maximum texture size."
                    else -> null
                })
                onStatus(status)
                gl.setFloatsUniform("uInputSize",floatArrayOf(inputWidth.toFloat(),inputHeight.toFloat()))
                gl.setFloatsUniform("uOutputSize",floatArrayOf(output.width.toFloat(),output.height.toFloat()))
                return Size(output.width,output.height)
            }
            applied=status.active
            return Size(inputWidth,inputHeight)
        }

        override fun drawFrame(inputTexId: Int,presentationTimeUs: Long) {
            try {
                gl.use()
                gl.setSamplerTexIdUniform("uTexSampler",inputTexId,0)
                // ABR can change the source size while EASU keeps the same output dimensions.
                // In that case RCAS isn't reconfigured, so read the current EASU bypass state.
                gl.setFloatUniform("uApply",if((if(easu) applied else status.active) && failure==null) 1f else 0f)
                if(!easu) gl.setFloatUniform("uSharpness",sharpness/100f)
                gl.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4)
                GlUtil.checkGlError()
                if(!easu && !status.rendered) {status=status.copy(rendered=true);onStatus(status)}
            } catch(e: GlUtil.GlException) {throw VideoFrameProcessingException(e,presentationTimeUs)}
        }

        override fun release() {
            try {gl.delete()} catch(e: GlUtil.GlException) {throw VideoFrameProcessingException(e)}
            finally {super.release()}
        }
    }

    /** Preserve playback and HDR values when the FSR kernels cannot run. */
    private class CopyProgram(useHdr: Boolean,private val configured: (Int,Int)->Unit): BaseGlShaderProgram(useHdr,1) {
        private val gl=GlProgram(VERTEX_100,"precision highp float; uniform sampler2D uTexSampler; varying vec2 vTexCoord; void main(){gl_FragColor=texture2D(uTexSampler,vTexCoord);}")
            .apply {setBufferAttribute("aFramePosition",VERTICES,4)}
        override fun configure(inputWidth: Int,inputHeight: Int): Size {configured(inputWidth,inputHeight);return Size(inputWidth,inputHeight)}
        override fun drawFrame(inputTexId: Int,presentationTimeUs: Long) {
            try {
                gl.use();gl.setSamplerTexIdUniform("uTexSampler",inputTexId,0);gl.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);GlUtil.checkGlError()
            } catch(e: GlUtil.GlException) {throw VideoFrameProcessingException(e,presentationTimeUs)}
        }
        override fun release() {
            try {gl.delete()} catch(e: GlUtil.GlException) {throw VideoFrameProcessingException(e)}
            finally {super.release()}
        }
    }

    companion object {
        private val VERTICES=floatArrayOf(-1f,-1f,0f,1f, -1f,1f,0f,1f, 1f,-1f,0f,1f, 1f,1f,0f,1f)
        private const val VERTEX_300="#version 300 es\nin vec4 aFramePosition; out vec2 vTexCoord; void main(){gl_Position=aFramePosition;vTexCoord=aFramePosition.xy*0.5+0.5;}"
        private const val VERTEX_100="attribute vec4 aFramePosition; varying vec2 vTexCoord; void main(){gl_Position=aFramePosition;vTexCoord=aFramePosition.xy*0.5+0.5;}"
    }
}

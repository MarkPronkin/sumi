@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package dev.cinema.crunchyroll.playback

import android.content.Context
import android.opengl.GLES20
import android.util.Log
import androidx.media3.common.GlObjectsProvider
import androidx.media3.common.GlTextureInfo
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import androidx.media3.effect.PassthroughShaderProgram

/** AMD FSR 1 reconstruction and sharpening in high-precision GLES 2.0 fragment shaders. */
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
            if(failure==null) {
                try {return Program(context,easu)}
                catch(e: Exception) {
                    val stage=if(easu) "reconstruction" else "sharpening"
                    val renderer=GLES20.glGetString(GLES20.GL_RENDERER)
                    Log.w("CinemaFsr","FSR $stage on $renderer: ${e.message?.substringBefore(", source:")?.take(600)}")
                    failure="The FSR 1 $stage shader could not start. Using standard scaling."
                }
            }
            status=UpscalingStatus(notice=failure)
            onStatus(status)
            if(!easu) return PassthroughShaderProgram()
            return object: PassthroughShaderProgram() {
                override fun queueInputFrame(
                    glObjectsProvider: GlObjectsProvider,
                    inputTexture: GlTextureInfo,
                    presentationTimeUs: Long,
                ) {
                    if(inputTexture.width!=status.input.width || inputTexture.height!=status.input.height) {
                        val input=VideoDimensions(inputTexture.width,inputTexture.height)
                        status=UpscalingStatus(input,input,notice=failure)
                        onStatus(status)
                    }
                    super.queueInputFrame(glObjectsProvider,inputTexture,presentationTimeUs)
                }
            }
        }
    }

    private inner class Program(context: Context,private val easu: Boolean): BaseGlShaderProgram(false,1) {
        private val gl: GlProgram
        private var applied=false

        init {
            val shader=if(easu) "easu.glsl" else "rcas.glsl"
            val source=context.assets.open("shaders/fsr1/$shader").bufferedReader().use {it.readText()}
            gl=GlProgram(VERTEX_SHADER,source)
            gl.setBufferAttribute("aFramePosition",VERTICES,4)
        }

        override fun configure(inputWidth: Int,inputHeight: Int): Size {
            val input=VideoDimensions(inputWidth,inputHeight)
            gl.setFloatsUniform("uTexelSize",floatArrayOf(1f/inputWidth,1f/inputHeight))
            if(easu) {
                val limit=IntArray(1);GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE,limit,0)
                val output=if(failure==null) upscaleDimensions(input,target,limit[0]) else input
                applied=output!=input
                status=UpscalingStatus(input,output,active=applied && failure==null,notice=failure ?: when {
                    !target.valid -> "Waiting for the playback display."
                    !applied -> "Video already meets or exceeds the output resolution."
                    output!=upscaleDimensions(input,target) -> "Output is limited by this GPU’s maximum texture size."
                    else -> null
                })
                onStatus(status)
                gl.setFloatsUniform("uInputSize",floatArrayOf(inputWidth.toFloat(),inputHeight.toFloat()))
                return Size(output.width,output.height)
            }
            return Size(inputWidth,inputHeight)
        }

        override fun drawFrame(inputTexId: Int,presentationTimeUs: Long) {
            try {
                gl.use()
                gl.setSamplerTexIdUniform("uTexSampler",inputTexId,0,GLES20.GL_NEAREST)
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

    companion object {
        private val VERTICES=floatArrayOf(-1f,-1f,0f,1f, -1f,1f,0f,1f, 1f,-1f,0f,1f, 1f,1f,0f,1f)
        private const val VERTEX_SHADER="attribute vec4 aFramePosition; varying highp vec2 vTexCoord; void main(){gl_Position=aFramePosition;vTexCoord=aFramePosition.xy*0.5+0.5;}"
    }
}

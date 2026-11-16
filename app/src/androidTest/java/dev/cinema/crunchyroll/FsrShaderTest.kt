@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package dev.cinema.crunchyroll

import android.opengl.EGL14
import android.opengl.GLES20
import androidx.media3.common.util.GlUtil
import androidx.media3.effect.BaseGlShaderProgram
import androidx.test.platform.app.InstrumentationRegistry
import dev.cinema.crunchyroll.playback.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

/** Exercise real compiled GLES kernels and read their pixels, independently of video decoding. */
class FsrShaderTest {
    @Test fun easuAndRcasProduceFiniteEdgesAndSharpnessChangesPixels() {
        val display=GlUtil.getDefaultEglDisplay()
        val context=GlUtil.createEglContext(EGL14.EGL_NO_CONTEXT,display,3,GlUtil.EGL_CONFIG_ATTRIBUTES_RGBA_8888)
        val surface=GlUtil.createFocusedPlaceholderEglSurface(context,display)
        val textures=mutableListOf<Int>();val fbos=mutableListOf<Int>();val programs=mutableListOf<BaseGlShaderProgram>()
        try {
            val androidContext=InstrumentationRegistry.getInstrumentation().targetContext
            var status=UpscalingStatus()
            val pipeline=Fsr1Pipeline(VideoDimensions(64,64),0) {status=it}
            val input=GlUtil.createTexture(16,16,false).also {textures+=it}
            // A gray diagonal with solid black/white corners catches NaNs, border loads and flips.
            val pixels=ByteBuffer.allocateDirect(16*16*4)
            for(y in 0 until 16) for(x in 0 until 16) {
                val value=if(x+y<6) 0 else if(x+y>23) 255 else if(x>y) 180 else 65
                repeat(3) {pixels.put(value.toByte())};pixels.put(255.toByte())
            }
            pixels.flip();GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,input)
            GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D,0,0,0,16,16,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,pixels)
            fun draw(program: BaseGlShaderProgram,source: Int,width: Int,height: Int,configure: Boolean=true): Pair<Int,ByteArray> {
                val size=if(configure) program.configure(width,height) else androidx.media3.common.util.Size(width,height)
                val texture=GlUtil.createTexture(size.width,size.height,false).also {textures+=it}
                val fbo=GlUtil.createFboForTexture(texture).also {fbos+=it}
                GlUtil.focusFramebufferUsingCurrentContext(fbo,size.width,size.height)
                program.drawFrame(source,0)
                val output=ByteBuffer.allocateDirect(size.width*size.height*4)
                GLES20.glReadPixels(0,0,size.width,size.height,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,output)
                GlUtil.checkGlError()
                val result=ByteArray(output.capacity());output.rewind();output.get(result)
                return texture to result
            }
            val easu=(pipeline.effects[0].toGlShaderProgram(androidContext,false) as BaseGlShaderProgram).also {programs+=it}
            val first=draw(easu,input,16,16)
            assertTrue(status.notice,status.active)
            assertEquals(VideoDimensions(64,64),status.output)
            val rcas=(pipeline.effects[1].toGlShaderProgram(androidContext,false) as BaseGlShaderProgram).also {programs+=it}
            val soft=draw(rcas,first.first,64,64).second
            assertArrayEquals("Zero sharpening must preserve the EASU frame exactly",first.second,soft)
            pipeline.sharpness=100
            val sharp=draw(rcas,first.first,64,64).second
            assertFalse("RCAS must actually change reconstructed edge pixels",soft.contentEquals(sharp))
            assertEquals(0,sharp[0].toInt() and 255)
            assertTrue("AMD's approximate reciprocal must preserve white within one 8-bit level",(sharp[sharp.size-4].toInt() and 255)>=254)
            for(i in sharp.indices step 4) assertEquals("Every output pixel must be written",255,sharp[i+3].toInt() and 255)
            assertTrue(status.rendered)
            // Native content must bypass both passes, including sharpening.
            val native=draw(easu,input,64,64)
            assertFalse(status.active)
            val unchanged=draw(rcas,native.first,64,64,configure=false)
            assertArrayEquals(native.second,unchanged.second)
            val smaller=draw(easu,input,16,16)
            val sharpenedAgain=draw(rcas,smaller.first,64,64,configure=false)
            assertFalse("ABR returning to a smaller source must restore sharpening",smaller.second.contentEquals(sharpenedAgain.second))
            // Reconfigure the same kernels for a real 4K render target.
            val fourK=Fsr1Pipeline(VideoDimensions(3840,2160),50) {status=it}
            val full=(fourK.effects[0].toGlShaderProgram(androidContext,false) as BaseGlShaderProgram).also {programs+=it}
            val fullFrame=draw(full,input,16,9)
            assertEquals(VideoDimensions(3840,2160),status.output)
            assertTrue(status.active)
            val fullRcas=(fourK.effects[1].toGlShaderProgram(androidContext,false) as BaseGlShaderProgram).also {programs+=it}
            draw(fullRcas,fullFrame.first,3840,2160)
            assertTrue(status.rendered)
            val hdr=(pipeline.effects[0].toGlShaderProgram(androidContext,true) as BaseGlShaderProgram).also {programs+=it}
            draw(hdr,input,16,16)
            assertFalse(status.active)
            assertTrue(status.notice.orEmpty().contains("HDR"))
            // An HDR stream must not permanently disable FSR for the next SDR episode.
            val sdr=(pipeline.effects[0].toGlShaderProgram(androidContext,false) as BaseGlShaderProgram).also {programs+=it}
            draw(sdr,input,16,16)
            assertTrue(status.notice,status.active)
        } finally {
            programs.reversed().forEach {it.release()}
            fbos.forEach {GlUtil.deleteFbo(it)};textures.forEach {GlUtil.deleteTexture(it)}
            GlUtil.destroyEglSurface(display,surface);GlUtil.destroyEglContext(display,context);GlUtil.terminate(display)
        }
    }
}

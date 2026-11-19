@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package dev.cinema.crunchyroll

import android.opengl.EGL14
import android.opengl.GLES20
import androidx.media3.common.GlTextureInfo
import androidx.media3.common.util.GlUtil
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.DefaultGlObjectsProvider
import androidx.media3.effect.GlShaderProgram
import androidx.test.platform.app.InstrumentationRegistry
import dev.cinema.crunchyroll.playback.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

/** Exercise real compiled GLES kernels and read their pixels, independently of video decoding. */
class FsrShaderTest {
    @Test fun easuAndRcasProduceFiniteEdgesAndSharpnessChangesPixels() = checkKernels(3)

    @Test fun es2PlaybackContextRunsBothKernels() = checkKernels(2)

    private fun checkKernels(contextVersion: Int) {
        val display=GlUtil.getDefaultEglDisplay()
        val context=GlUtil.createEglContext(EGL14.EGL_NO_CONTEXT,display,contextVersion,GlUtil.EGL_CONFIG_ATTRIBUTES_RGBA_8888)
        val surface=GlUtil.createFocusedPlaceholderEglSurface(context,display)
        val textures=mutableListOf<Int>();val fbos=mutableListOf<Int>();val programs=mutableListOf<GlShaderProgram>()
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
            // Sample the diagonal before sharpening. Allow six 8-bit levels for the
            // difference between AMD's fast reciprocal approximations and native float math.
            val referenceEdge=intArrayOf(65,89,130,165,180)
            referenceEdge.forEachIndexed {offset,expected ->
                val red=first.second[(25*64+25+offset)*4].toInt() and 255
                assertEquals("EASU diagonal sample $offset",expected.toDouble(),red.toDouble(),6.0)
            }
            val rcas=(pipeline.effects[1].toGlShaderProgram(androidContext,false) as BaseGlShaderProgram).also {programs+=it}
            val soft=draw(rcas,first.first,64,64).second
            assertArrayEquals("Zero sharpening must preserve the EASU frame exactly",first.second,soft)
            pipeline.sharpness=100
            val sharp=draw(rcas,first.first,64,64).second
            assertFalse("RCAS must actually change reconstructed edge pixels",soft.contentEquals(sharp))
            assertEquals(0,sharp[0].toInt() and 255)
            assertTrue("Solid white must stay within one 8-bit level",(sharp[sharp.size-4].toInt() and 255)>=254)
            for(i in sharp.indices step 4) assertEquals("Every output pixel must be written",255,sharp[i+3].toInt() and 255)
            assertTrue(status.rendered)
            // Native content must bypass both passes, including sharpening.
            val native=draw(easu,first.first,64,64)
            assertFalse(status.active)
            assertArrayEquals(first.second,native.second)
            val unchanged=draw(rcas,native.first,64,64,configure=false)
            assertArrayEquals(native.second,unchanged.second)
            val smaller=draw(easu,input,16,16)
            val sharpenedAgain=draw(rcas,smaller.first,64,64,configure=false)
            assertFalse("ABR returning to a smaller source must restore sharpening",smaller.second.contentEquals(sharpenedAgain.second))
            if(contextVersion==3) {
                val limit=IntArray(1)
                GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE,limit,0)
                val target=VideoDimensions(3840,2160)
                val fourK=Fsr1Pipeline(target,50) {status=it}
                val full=(fourK.effects[0].toGlShaderProgram(androidContext,false) as BaseGlShaderProgram).also {programs+=it}
                val wideInput=GlUtil.createTexture(16,9,false).also {textures+=it}
                val fullFrame=draw(full,wideInput,16,9)
                val size=upscaleDimensions(VideoDimensions(16,9),target,limit[0])
                assertEquals(size,status.output)
                assertTrue(status.active)
                val fullRcas=(fourK.effects[1].toGlShaderProgram(androidContext,false) as BaseGlShaderProgram).also {programs+=it}
                draw(fullRcas,fullFrame.first,size.width,size.height)
                assertTrue(status.rendered)
            }
            val hdr=pipeline.effects[0].toGlShaderProgram(androidContext,true).also {programs+=it}
            val frame=GlTextureInfo(input,-1,-1,16,16)
            var forwarded: GlTextureInfo?=null
            hdr.setOutputListener(object: GlShaderProgram.OutputListener {
                override fun onOutputFrameAvailable(outputTexture: GlTextureInfo,presentationTimeUs: Long) {forwarded=outputTexture}
            })
            hdr.queueInputFrame(DefaultGlObjectsProvider(),frame,0)
            assertSame(frame,forwarded)
            hdr.releaseOutputFrame(frame)
            assertFalse(status.active)
            assertTrue(status.notice.orEmpty().contains("HDR"))
            assertEquals(VideoDimensions(16,16),status.output)
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

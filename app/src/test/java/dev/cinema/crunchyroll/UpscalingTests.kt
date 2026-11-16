package dev.cinema.crunchyroll

import dev.cinema.crunchyroll.data.Upscaler
import dev.cinema.crunchyroll.playback.*
import org.junit.Assert.*
import org.junit.Test

class UpscalingTests {
    @Test fun `any stream resolution fits a 4K display without changing aspect ratio`() {
        val tv=VideoDimensions(3840,2160)
        listOf(VideoDimensions(320,180),VideoDimensions(1280,720),VideoDimensions(1920,1080)).forEach {
            assertEquals(tv,upscaleDimensions(it,tv))
        }
        assertEquals(VideoDimensions(2880,2160),upscaleDimensions(VideoDimensions(640,480),tv))
        assertEquals(VideoDimensions(3840,1600),upscaleDimensions(VideoDimensions(1920,800),tv))
        assertEquals(VideoDimensions(1215,2160),upscaleDimensions(VideoDimensions(720,1280),tv))
    }
    @Test fun `native and larger content bypass upscaling and GPU limits preserve proportions`() {
        val native=VideoDimensions(3840,2160)
        assertEquals(native,upscaleDimensions(native,native))
        assertEquals(native,upscaleDimensions(native,VideoDimensions(1920,1080)))
        assertEquals(VideoDimensions(2048,1152),upscaleDimensions(VideoDimensions(1280,720),native,2048))
        assertEquals(VideoDimensions(1280,720),upscaleDimensions(VideoDimensions(1280,720),VideoDimensions()))
    }
    @Test fun `TV UI density and window size map to physical output pixels`() {
        val tv=PlaybackDisplay(width=3840,height=2160,logicalWidth=1920,logicalHeight=1080)
        assertEquals(VideoDimensions(3840,2160),playbackOutputTarget(VideoDimensions(1920,1080),tv))
        assertEquals(VideoDimensions(1920,1080),playbackOutputTarget(VideoDimensions(960,540),tv))
        assertEquals(VideoDimensions(2880,2160),videoSurfaceDimensions(VideoDimensions(640,480),VideoDimensions(3840,2160)))
        assertEquals(VideoDimensions(1920,1080),videoSurfaceDimensions(VideoDimensions(3840,2160),VideoDimensions(1920,1080)))
        val rotated=PlaybackDisplay(width=2400,height=1080,logicalWidth=2400,logicalHeight=1080)
        assertEquals(VideoDimensions(2400,1080),playbackOutputTarget(VideoDimensions(2400,1080),rotated))
    }
    @Test fun `unknown future upscalers safely default to Off`() {
        assertEquals(Upscaler.Off,Upscaler.fromKey("future"))
        assertEquals(Upscaler.Off,Upscaler.fromKey(null))
        assertEquals(Upscaler.Fsr1,Upscaler.fromKey("fsr1"))
    }
}

package dev.cinema.crunchyroll

import dev.cinema.crunchyroll.data.account.PlaybackAuthorization
import dev.cinema.crunchyroll.playback.*
import org.junit.Assert.*
import org.junit.Test

class NativeLanguageSelectionTests {
    private fun stream(
        current: String = "es-419",
        audio: List<StreamAudio> = listOf(StreamAudio("spain", "es-ES"), StreamAudio("english", "en-US")),
        subtitles: List<StreamSubtitle> = listOf(StreamSubtitle("es-419", "https://example.invalid/latin.vtt", "vtt")),
        hardSubtitles: Map<String, String> = mapOf("es-ES" to "https://example.invalid/spain.mpd"),
    ) = NativeStream("current", "https://example.invalid/original.mpd", null, "fixture",
        PlaybackAuthorization("fixture", "account", 1), subtitles, audio, current, 60, false, hardSubtitles)

    @Test fun `regional audio switches editions even when the current edition has the same base language`() {
        assertEquals("spain", stream().preferredAudioVersion("es-ES")?.id)
        assertEquals("current", stream().preferredAudioVersion("es-419")?.id)
    }

    @Test fun `current exact edition is retained ahead of duplicate versions`() {
        val descriptor = stream(current = "pt-PT", audio = listOf(StreamAudio("duplicate", "pt-PT"), StreamAudio("brazil", "pt-BR")))
        assertEquals("current", descriptor.preferredAudioVersion("pt-PT")?.id)
        assertEquals("brazil", descriptor.preferredAudioVersion("pt-BR")?.id)
    }

    @Test fun `legacy audio uses its default region and unavailable languages leave the current stream alone`() {
        assertEquals("english", stream().preferredAudioVersion("en")?.id)
        assertEquals("current", stream().preferredAudioVersion("es-MX")?.id)
        assertNull(stream().preferredAudioVersion("ja"))
    }

    @Test fun `subtitles match exact regional hard or soft tracks before falling back`() {
        val descriptor = stream()
        assertEquals("es-ES", descriptor.preferredSubtitleLanguage("es-ES"))
        assertEquals("https://example.invalid/spain.mpd", descriptor.manifestFor(descriptor.preferredSubtitleLanguage("es-ES")))
        assertEquals("es-419", descriptor.preferredSubtitleLanguage("es-419"))
        assertEquals("es-419", descriptor.preferredSubtitleLanguage("es-MX"))
        assertEquals("off", descriptor.preferredSubtitleLanguage("ja"))
    }

    @Test fun `subtitles Off cannot fall back to any available language or hard subtitle`() {
        val descriptor = stream(hardSubtitles = mapOf("none" to "https://example.invalid/clean.mpd", "en-US" to "https://example.invalid/english.mpd"))
        assertEquals("off", descriptor.preferredSubtitleLanguage("off"))
        assertEquals("https://example.invalid/clean.mpd", descriptor.manifestFor(descriptor.preferredSubtitleLanguage("off")))
        assertTrue(descriptor.subtitleChoices().none { it.language == "none" })
    }
}

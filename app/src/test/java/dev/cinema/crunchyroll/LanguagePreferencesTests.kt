package dev.cinema.crunchyroll

import dev.cinema.crunchyroll.data.LanguagePreferences
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class LanguagePreferencesTests {
    private fun match(preferred: String, vararg available: String) =
        LanguagePreferences.bestMatch(preferred, available.toList()) { it }

    @Test fun `legacy preferences keep their stored codes and readable labels`() {
        assertEquals("ja", LanguagePreferences.optionFor("ja", LanguagePreferences.audioOptions)?.code)
        assertEquals("en", LanguagePreferences.optionFor("en", LanguagePreferences.audioOptions)?.code)
        assertEquals("off", LanguagePreferences.optionFor("off", LanguagePreferences.subtitleOptions)?.code)
        assertEquals("Japanese", LanguagePreferences.label("ja-JP"))
        assertEquals("English", LanguagePreferences.label("en-US"))
        assertEquals("Off", LanguagePreferences.label("off"))
        assertEquals("ja-JP", match("ja", "en-US", "ja-JP"))
        assertEquals("en-US", match("en", "en-GB", "en-US"))
        assertEquals("en", match("en", "en-US", "en"))
    }

    @Test fun `options include distinct service variants and Off belongs only to subtitles`() {
        val codes = LanguagePreferences.audioOptions.map { it.code }
        assertTrue(codes.containsAll(listOf("es-419", "es-ES", "pt-BR", "pt-PT", "fr-FR", "de-DE", "it-IT",
            "ar-ME", "ar-SA", "hi-IN", "ta-IN", "te-IN", "ko-KR", "zh-CN", "zh-TW", "id-ID", "ms-MY")))
        assertEquals(codes.size, codes.distinct().size)
        assertFalse(codes.contains("off"))
        assertEquals(1, LanguagePreferences.subtitleOptions.count { it.code == "off" })
        assertEquals("Spanish (Latin America)", LanguagePreferences.label("es-419"))
        assertEquals("Portuguese (Portugal)", LanguagePreferences.label("pt-PT"))
    }

    @Test fun `exact regions outrank the base language default and the first offered variant`() {
        assertEquals("es-ES", match("es-ES", "es-419", "es", "es-ES"))
        assertEquals("es-419", match("es-419", "es-ES", "es-419"))
        assertEquals("pt-PT", match("pt-PT", "pt-BR", "pt-PT"))
        assertEquals("ar-SA", match("ar-SA", "ar-ME", "ar-SA"))
        assertEquals("zh-TW", match("zh-TW", "zh-CN", "zh-TW"))
    }

    @Test fun `unavailable region prefers a bare track then the language default`() {
        assertEquals("es", match("es-MX", "es-ES", "es-419", "es"))
        assertEquals("es-419", match("es-MX", "es-ES", "es-419"))
        assertEquals("es-419", match("es-MX", "es-419", "es-ES"))
        assertEquals("pt-BR", match("pt-PT", "fr-FR", "pt-BR"))
        assertEquals("en-AU", match("en-CA", "en-GB", "en-AU"))
        assertEquals("en-AU", match("en-CA", "en-AU", "en-GB"))
    }

    @Test fun `Chinese fallback retains the requested script when that script is available`() {
        assertEquals("zh-TW", match("zh-HK", "zh-CN", "zh-TW"))
        assertEquals("zh-TW", match("zh-Hant", "zh-CN", "zh-TW"))
        assertEquals("zh-CN", match("zh-SG", "zh-TW", "zh-CN"))
        assertEquals("zh-CN", match("zh-TW", "zh-CN"))
    }

    @Test fun `missing languages and Off never select an unrelated track`() {
        assertNull(match("fr-FR", "de-DE", "en-US"))
        assertNull(match("off", "off", "en-US"))
        assertNull(match(" OFF ", "en-US"))
        assertNull(match("", "", "en-US"))
        assertNull(match("und", "und", "en-US"))
        assertNull(match("ja"))
        assertTrue(LanguagePreferences.isOff(" OFF "))
    }

    @Test fun `comparison tolerates service casing and underscores without changing the result code`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("HI_in", match("hi-IN", "en-US", "HI_in"))
            assertEquals("es_ES", match(" ES-es ", "es-419", "es_ES"))
            assertEquals("pt-PT", LanguagePreferences.optionFor("PT_pt", LanguagePreferences.audioOptions)?.code)
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test fun `up next normalization expands legacy codes and preserves selected regional variants`() {
        assertEquals("ja-JP", LanguagePreferences.serviceAudioLocale("ja"))
        assertEquals("en-US", LanguagePreferences.serviceAudioLocale("en"))
        listOf("es-419", "es-ES", "pt-BR", "pt-PT", "ar-ME", "ar-SA", "zh-CN", "zh-TW").forEach {
            assertEquals(it, LanguagePreferences.serviceAudioLocale(it))
        }
        assertEquals("pt-PT", LanguagePreferences.serviceAudioLocale("PT_pt"))
        assertEquals("es-419", LanguagePreferences.serviceAudioLocale("es"))
    }
}

package dev.cinema.crunchyroll.data

import java.util.Locale

data class LanguageOption(val code: String, val label: String)

/** Stored codes stay unchanged; only service requests and comparisons normalize them. */
object LanguagePreferences {
    const val OFF = "off"

    val audioOptions = listOf(
        LanguageOption("ja", "Japanese"),
        LanguageOption("en", "English"),
        LanguageOption("es-419", "Spanish (Latin America)"),
        LanguageOption("es-ES", "Spanish (Spain)"),
        LanguageOption("pt-BR", "Portuguese (Brazil)"),
        LanguageOption("pt-PT", "Portuguese (Portugal)"),
        LanguageOption("fr-FR", "French"),
        LanguageOption("de-DE", "German"),
        LanguageOption("it-IT", "Italian"),
        LanguageOption("ar-ME", "Arabic (Middle East)"),
        LanguageOption("ar-SA", "Arabic (Saudi Arabia)"),
        LanguageOption("hi-IN", "Hindi"),
        LanguageOption("ta-IN", "Tamil"),
        LanguageOption("te-IN", "Telugu"),
        LanguageOption("ko-KR", "Korean"),
        LanguageOption("zh-CN", "Chinese (Simplified)"),
        LanguageOption("zh-TW", "Chinese (Traditional)"),
        LanguageOption("ru-RU", "Russian"),
        LanguageOption("th-TH", "Thai"),
        LanguageOption("vi-VN", "Vietnamese"),
        LanguageOption("id-ID", "Indonesian"),
        LanguageOption("ms-MY", "Malay"),
    )
    val subtitleOptions = listOf(LanguageOption(OFF, "Off")) + audioOptions

    private val defaults = audioOptions.associate { it.code.substringBefore('-') to it.code } + mapOf(
        "ja" to "ja-JP", "en" to "en-US", "es" to "es-419", "pt" to "pt-BR",
        "ar" to "ar-ME", "zh" to "zh-CN",
    )

    private fun key(code: String) = code.trim().replace('_', '-').lowercase(Locale.ROOT)
    fun isOff(code: String) = key(code) == OFF

    /** Up-next needs a full service locale, while legacy ja/en remain valid saved preferences. */
    fun serviceAudioLocale(code: String): String {
        val normalized = key(code)
        return defaults[normalized] ?: Locale.forLanguageTag(normalized).toLanguageTag()
    }

    fun optionFor(code: String, options: List<LanguageOption>): LanguageOption? =
        options.firstOrNull { key(it.code) == key(code) }
            ?: options.firstOrNull { key(serviceAudioLocale(it.code)) == key(serviceAudioLocale(code)) }

    fun label(code: String): String = optionFor(code, subtitleOptions)?.label
        ?: Locale.forLanguageTag(code.replace('_', '-')).getDisplayName(Locale.ENGLISH).ifBlank { code }

    /**
     * Exact locale wins even if a different region is already playing. When unavailable, use a
     * language-only track, then a compatible Chinese script, the language's default region, and
     * finally another same-language region in stable order. Never substitute another language.
     * Legacy language-only preferences prefer their default region when no bare track exists.
     * Returns the original option so service locale spelling and track identity are preserved.
     */
    fun <T> bestMatch(preferred: String, options: List<T>, language: (T) -> String): T? {
        val requested = key(preferred)
        if (requested in listOf("", OFF, "none", "und")) return null
        val base = requested.substringBefore('-')
        val default = key(defaults[base].orEmpty())
        val script = chineseScript(requested)
        return options.filter { key(language(it)).substringBefore('-') == base }
            .minWithOrNull(compareBy<T> {
                val candidate = key(language(it))
                when {
                    candidate == requested -> 0
                    requested == base && candidate == default -> 1
                    candidate == base -> 2
                    script != null && chineseScript(candidate) == script -> 3
                    candidate == default -> 4
                    else -> 5
                }
            }.thenBy { key(language(it)) })
    }

    private fun chineseScript(code: String): String? {
        if (code.substringBefore('-') != "zh") return null
        val parts = code.split('-').drop(1)
        return when {
            "hant" in parts -> "hant"
            "hans" in parts -> "hans"
            parts.any { it in listOf("tw", "hk", "mo") } -> "hant"
            parts.any { it in listOf("cn", "sg") } -> "hans"
            else -> null
        }
    }
}

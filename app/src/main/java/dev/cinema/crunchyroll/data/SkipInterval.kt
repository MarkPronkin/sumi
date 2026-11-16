package dev.cinema.crunchyroll.data

object SkipInterval {
    const val DEFAULT_SECONDS = 10
    val choices = listOf(5, 10, 15, 30)

    fun seconds(value: Int?): Int = value?.takeIf { it in choices } ?: DEFAULT_SECONDS
    fun millis(value: Int?): Long = seconds(value) * 1_000L
}

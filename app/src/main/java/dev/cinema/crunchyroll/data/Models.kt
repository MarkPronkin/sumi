package dev.cinema.crunchyroll.data

import androidx.annotation.DrawableRes

enum class MotionLevel { Maximal, Standard, Reduced }

enum class Upscaler(val key: String,val label: String) {
    Off("off","Off"), Fsr1("fsr1","FSR 1");
    companion object {
        fun fromKey(key: String?) = entries.firstOrNull {it.key==key} ?: Off
    }
}

data class Anime(
    val id: String,
    val title: String,
    val subtitle: String,
    val synopsis: String,
    val year: Int,
    val genres: List<String>,
    @DrawableRes val artwork: Int,
    @DrawableRes val poster: Int = artwork,
    val seasons: Int = 1,
    val languages: List<String> = listOf("Japanese", "English"),
    val artworkUrl: String? = null,
    val posterUrl: String? = null,
    val seasonNumbers: List<Int> = emptyList(),
    val logoUrl: String? = null,
    val heroUrl: String? = null,
    val isMovie: Boolean = false,
)

data class Episode(val id: String, val showId: String, val season: Int, val number: Int, val title: String,
    val durationMs: Long = 0, val thumbnailUrl: String? = null)
data class DemoProfile(val id: String, val name: String, val initial: String)
val demoProfiles = listOf(DemoProfile("main", "Anime night", "A"), DemoProfile("guest", "Guest", "G"))

data class Progress(val positionMs: Long, val durationMs: Long, val updatedAt: Long,val needsSync: Boolean=false) {
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val complete: Boolean get() = durationMs > 0 && positionMs >= durationMs
}

data class HistoryEntry(val show: Anime,val episode: Episode,val progress: Progress)
data class HistoryPage(val items: List<HistoryEntry>,val hasMore: Boolean)
data class EpisodeSelection(val showId: String="",val requestedSeason: Int=1,val displayedSeason: Int=1,
    val episodes: List<Episode> = emptyList(),val loading: Boolean=false,val error: String?=null)

fun resumeProgress(local: Progress?,remote: Progress?): Progress? = when {
    local?.needsSync==true -> local
    local!=null && remote!=null && remote.updatedAt>0 && local.updatedAt>remote.updatedAt -> local
    remote!=null -> remote
    else -> local
}

data class ProfileData(
    val watchlist: Set<String> = emptySet(),
    val progress: Map<String, Progress> = emptyMap(),
    val audio: String = "ja",
    val subtitles: String = "en",
    val autoplay: Boolean = true,
    val haptics: Boolean = true,
    val motion: MotionLevel = MotionLevel.Maximal,
    val qualityHeight: Int = 0,
    val upscaler: Upscaler = Upscaler.Off,
    val fsrSharpness: Int = 50,
    val skipIntervalSeconds: Int = SkipInterval.DEFAULT_SECONDS,
)

data class LocalState(
    val enteredDemo: Boolean = false,
    val activeProfile: String = "main",
    val profiles: Map<String, ProfileData> = demoProfiles.associate { it.id to ProfileData() },
) {
    val current: ProfileData get() = profiles[activeProfile] ?: ProfileData()
    fun updateProfile(id: String = activeProfile, update: (ProfileData) -> ProfileData): LocalState =
        copy(profiles = profiles + (id to update(profiles[id] ?: ProfileData())))
}

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Content<T>(val value: T) : LoadState<T>
    data class Error(val message: String) : LoadState<Nothing>
}

data class SearchRequest(val query: String = "", val category: String = "All", val revision: Int = 0)

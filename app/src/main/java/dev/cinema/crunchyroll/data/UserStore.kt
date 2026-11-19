package dev.cinema.crunchyroll.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.cinemaDataStore by preferencesDataStore("cinema_demo_v1")

interface UserStore {
    val state: Flow<LocalState>
    suspend fun update(transform: (LocalState) -> LocalState)
}

class PreferencesUserStore(context: Context) : UserStore {
    private val store = context.applicationContext.cinemaDataStore
    override val state = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map(::decode)
    override suspend fun update(transform: (LocalState) -> LocalState) {
        store.edit { prefs -> encode(transform(decode(prefs)), prefs) }
    }

    private fun decode(prefs: Preferences): LocalState = LocalState(
        enteredDemo = prefs[booleanPreferencesKey("entered")] ?: false,
        activeProfile = (prefs[stringPreferencesKey("active")] ?: "main").takeIf { id -> demoProfiles.any { it.id == id } } ?: "main",
        profiles = ((prefs[stringSetPreferencesKey("profile_ids")] ?: emptySet())+demoProfiles.map {it.id}).associate { p ->
            p to ProfileData(
                watchlist = prefs[stringSetPreferencesKey("${p}_list")] ?: emptySet(),
                progress = (prefs[stringSetPreferencesKey("${p}_progress")] ?: emptySet()).mapNotNull { entry ->
                    val parts = entry.split('|')
                    if (parts.size !in 4..6) null else {
                        val position = parts[1].toLongOrNull(); val duration = parts[2].toLongOrNull(); val time = parts[3].toLongOrNull()
                        val edit = HistoryEdit.entries.find { it.name == parts.getOrNull(5) }
                        if (position == null || duration == null || time == null) null else parts[0] to Progress(position, duration, time,parts.getOrNull(4)=="true",edit)
                    }
                }.toMap(),
                audio = prefs[stringPreferencesKey("${p}_audio")] ?: "ja",
                subtitles = prefs[stringPreferencesKey("${p}_subtitles")] ?: "en",
                autoplay = prefs[booleanPreferencesKey("${p}_autoplay")] ?: true,
                skipIntervalSeconds = SkipInterval.seconds(prefs[intPreferencesKey("${p}_skip_interval_seconds")]),
                haptics = prefs[booleanPreferencesKey("${p}_haptics")] ?: true,
                motion = MotionLevel.entries.find { it.name == prefs[stringPreferencesKey("${p}_motion")] } ?: MotionLevel.Maximal,
                qualityHeight = prefs[intPreferencesKey("${p}_quality")] ?: 0,
                upscaler = Upscaler.fromKey(prefs[stringPreferencesKey("${p}_upscaler")]),
                fsrSharpness = (prefs[intPreferencesKey("${p}_fsr_sharpness")] ?: 50).coerceIn(0,100),
            )
        },
    )

    private fun encode(state: LocalState, prefs: MutablePreferences) {
        prefs[booleanPreferencesKey("entered")] = state.enteredDemo
        prefs[stringPreferencesKey("active")] = state.activeProfile
        prefs[stringSetPreferencesKey("profile_ids")] = state.profiles.keys
        state.profiles.forEach { (p, data) ->
            prefs[stringSetPreferencesKey("${p}_list")] = data.watchlist
            prefs[stringSetPreferencesKey("${p}_progress")] = data.progress.map { (id, v) ->
                "$id|${v.positionMs}|${v.durationMs}|${v.updatedAt}|${v.needsSync}" + (v.historyEdit?.let { "|${it.name}" } ?: "")
            }.toSet()
            prefs[stringPreferencesKey("${p}_audio")] = data.audio
            prefs[stringPreferencesKey("${p}_subtitles")] = data.subtitles
            prefs[booleanPreferencesKey("${p}_autoplay")] = data.autoplay
            prefs[intPreferencesKey("${p}_skip_interval_seconds")] = SkipInterval.seconds(data.skipIntervalSeconds)
            prefs[booleanPreferencesKey("${p}_haptics")] = data.haptics
            prefs[stringPreferencesKey("${p}_motion")] = data.motion.name
            prefs[intPreferencesKey("${p}_quality")] = data.qualityHeight
            prefs[stringPreferencesKey("${p}_upscaler")] = data.upscaler.key
            prefs[intPreferencesKey("${p}_fsr_sharpness")] = data.fsrSharpness.coerceIn(0,100)
            prefs.remove(stringPreferencesKey("${p}_display_mode"))
        }
    }
}

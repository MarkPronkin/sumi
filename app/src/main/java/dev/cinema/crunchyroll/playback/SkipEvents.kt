package dev.cinema.crunchyroll.playback

import dev.cinema.crunchyroll.data.account.CrunchyrollHttp
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import kotlin.math.roundToLong

enum class SkipKind(val label: String) {
    Recap("Skip recap"), Intro("Skip intro"), Credits("Skip titles")
}

data class SkipSegment(val kind: SkipKind,val startMs: Long,val endMs: Long) {
    fun contains(positionMs: Long,durationMs: Long): Boolean =
        startMs>=0 && endMs>startMs && durationMs>0 && startMs<durationMs &&
            positionMs>=startMs && positionMs<minOf(endMs,durationMs)
}

/** Optional metadata failures must never prevent an entitled episode from playing. */
class SkipEventService(private val http: CrunchyrollHttp=CrunchyrollHttp()) {
    suspend fun load(episodeId: String): List<SkipSegment> {
        for(legacy in listOf(false,true)) {
            try {
                val segments=parse(http.skipEvents(episodeId,legacy))
                if(segments.isNotEmpty()) return segments
            } catch(e: CancellationException) {throw e} catch(_: Exception) { /* Missing/invalid markers are common. */ }
        }
        return emptyList()
    }

    companion object {
        internal fun parse(root: JsonElement): List<SkipSegment> {
            val data=root as? JsonObject ?: return emptyList()
            fun segment(kind: SkipKind,node: JsonObject,startKey: String="start",endKey: String="end"): SkipSegment? {
                fun millis(key: String): Long? {
                    val seconds=(node[key] as? JsonPrimitive)?.doubleOrNull ?: return null
                    val value=seconds*1000
                    return value.takeIf {it.isFinite() && it>=0 && it<Long.MAX_VALUE.toDouble()}?.roundToLong()
                }
                val start=millis(startKey) ?: return null
                val end=millis(endKey) ?: return null
                return SkipSegment(kind,start,end).takeIf {end>start}
            }
            val segments=listOf("recap" to SkipKind.Recap,"intro" to SkipKind.Intro,"credits" to SkipKind.Credits)
                .mapNotNull {(key,kind) -> (data[key] as? JsonObject)?.let {segment(kind,it)}}
            return (segments.ifEmpty {listOfNotNull(segment(SkipKind.Intro,data,"startTime","endTime"))}).sortedBy {it.startMs}
        }
    }
}

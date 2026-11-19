package dev.cinema.crunchyroll.playback

import dev.cinema.crunchyroll.data.LanguagePreferences
import dev.cinema.crunchyroll.data.account.*
import java.net.URI
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl

data class StreamSubtitle(val language: String,val url: String,val format: String)
data class StreamAudio(val id: String,val language: String)
data class SubtitleChoice(val language: String,val styled: Boolean)

/** Signed URLs and license/session headers are deliberately excluded from toString and saved state. */
class NativeStream(
    val contentId: String,val manifestUrl: String,val licenseUrl: String?,val videoToken: String,
    val authorization: PlaybackAuthorization,val subtitles: List<StreamSubtitle>,val audio: List<StreamAudio>,
    val audioLocale: String,val renewSeconds: Int,val usesStreamLimits: Boolean,
    val hardSubtitles: Map<String,String> = emptyMap(),
) {override fun toString()="NativeStream([redacted])"}

fun NativeStream.subtitleChoices(): List<SubtitleChoice> = (subtitles.map {it.language}+hardSubtitles.keys.filter {it !in listOf("none","off","")})
    .distinct().map {SubtitleChoice(it,it in hardSubtitles)}

fun NativeStream.manifestFor(language: String): String = hardSubtitles[language] ?: hardSubtitles["none"] ?: manifestUrl

internal fun NativeStream.preferredAudioVersion(preferred: String): StreamAudio? = LanguagePreferences.bestMatch(
    preferred,listOf(StreamAudio(contentId,audioLocale))+audio,
) {it.language}

internal fun NativeStream.preferredSubtitleLanguage(preferred: String): String =
    LanguagePreferences.bestMatch(preferred,subtitleChoices()) {it.language}?.language ?: LanguagePreferences.OFF

class NativePlaybackService(private val session: AccountSession,private val skipEvents: SkipEventService=SkipEventService()) {
    suspend fun skipSegments(episodeId: String,contentId: String): List<SkipSegment> =
        skipEvents.load(contentId).ifEmpty {if(contentId!=episodeId) skipEvents.load(episodeId) else emptyList()}

    suspend fun open(episodeId: String,preferredAudio: String): NativeStream {
        var stream=fetch(episodeId)
        try {
            val desired=stream.preferredAudioVersion(preferredAudio)
            if(desired!=null && desired.id!=stream.contentId) {
                close(stream);stream=fetch(desired.id)
            }
            return stream
        } catch(e: Exception) {
            withContext(NonCancellable) {runCatching {close(stream)}}
            throw e
        }
    }
    private suspend fun fetch(id: String): NativeStream {
        val original=session.playbackAuthorization()
        val body=session.playbackRequest("v3/$id/${NativeClientProtocol.PLAYBACK_PATH}/play",expectedIdentity=original.identity).objectOrEmpty()
        val token=body.text("token")
        try {
            val authorization=session.playbackAuthorization()
            if(authorization.identity!=original.identity) throw ServiceException(409,"The active profile changed. Open the episode again.")
            val stream=parse(id,body,authorization)
            if(stream.manifestUrl.isBlank()) throw ServiceException(200,"Crunchyroll returned no playable stream for this episode.")
            return stream
        } catch(e: Exception) {
            if(token.isNotBlank()) withContext(NonCancellable) {runCatching {session.releasePlayback("v1/token/$id/${encode(token)}",original)}}
            throw e
        }
    }
    suspend fun renew(stream: NativeStream,positionMs: Long=0) {
        if(!stream.usesStreamLimits) return
        session.renewPlayback("v1/token/${stream.contentId}/${encode(stream.videoToken)}/keepAlive",positionMs,stream.authorization.identity)
    }
    suspend fun close(stream: NativeStream) {
        if(stream.videoToken.isNotBlank() && stream.usesStreamLimits)
            session.releasePlayback("v1/token/${stream.contentId}/${encode(stream.videoToken)}",stream.authorization)
    }
    suspend fun saveProgress(stream: NativeStream,episodeId: String,positionMs: Long,capturedAt: Long = System.nanoTime()) =
        session.savePlayhead(stream.authorization, episodeId, positionMs, capturedAt)
    companion object {
        private fun encode(value: String)=URLEncoder.encode(value,"UTF-8").replace("+","%20")
        internal fun parse(id: String,body: JsonObject,authorization: PlaybackAuthorization): NativeStream {
            val token=body.text("token")
            val url=body.text("url").toHttpUrl()
            require(url.isHttps)
            val manifest=url.newBuilder().setQueryParameter("accountid",authorization.accountId).setQueryParameter("playbackGuid",token).build().toString()
            val drm=body["drm"].objectOrEmptyNullable()
            val license=drm.text("drmUrl").takeIf {it.isNotBlank()}?.also {
                val parsed=it.toHttpUrl()
                require(parsed.isHttps && (parsed.host=="crunchyroll.com" || parsed.host.endsWith(".crunchyroll.com") || parsed.host.endsWith(".crunchyrollsvc.com")))
            }
            val sessionInfo=body["session"].objectOrEmptyNullable()
            fun subtitles(node: JsonElement?): List<StreamSubtitle> {
                val values=when(node) {is JsonObject -> node.values.toList();is JsonArray -> node.toList();else -> emptyList()}
                return values.mapNotNull {item ->
                    val s=item.objectOrEmpty();val address=s.text("url");val format=s.text("format").lowercase()
                    if(!address.startsWith("https://") || format !in listOf("ass","vtt","srt")) null
                    else StreamSubtitle(s.text("language"),address,format)
                }
            }
            val hard=(body["hardSubs"] as? JsonObject)?.mapNotNull {(language,value) ->
                val address=(value as? JsonObject)?.text("url") ?: (value as? JsonPrimitive)?.contentOrNull.orEmpty()
                if(!address.startsWith("https://")) null else language to address.toHttpUrl().newBuilder()
                    .setQueryParameter("accountid",authorization.accountId).setQueryParameter("playbackGuid",token).build().toString()
            }?.toMap().orEmpty()
            return NativeStream(id,manifest,license,token,authorization,
                (subtitles(body["subtitles"])+subtitles(body["captions"])).distinctBy {it.language to it.url},
                body.array("versions").mapNotNull {v ->val a=v.objectOrEmpty();val guid=a.text("guid");if(guid.isBlank()) null else StreamAudio(guid,a.text("audio_locale").ifBlank {a.text("audioLocale")})},
                body.text("audioLocale"),sessionInfo.number("renewSeconds").coerceIn(30,600),
                (sessionInfo["usesStreamLimits"] as? JsonPrimitive)?.booleanOrNull ?: true,hard)
        }
        private fun JsonElement?.objectOrEmptyNullable()=this as? JsonObject ?: JsonObject(emptyMap())
    }
}

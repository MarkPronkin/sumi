package dev.cinema.crunchyroll.data

import java.io.IOException
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.coroutines.resumeWithException

/** Public metadata uses its own client: account tokens never go to external rating providers. */
class RatingsRepository(
    private val myAnimeListBase: HttpUrl="https://api.tenrai.org/v1/".toHttpUrl(),
    private val cinemetaBase: HttpUrl="https://v3-cinemeta.strem.io/".toHttpUrl(),
    private val client: OkHttpClient=OkHttpClient.Builder().connectTimeout(5,TimeUnit.SECONDS)
        .readTimeout(8,TimeUnit.SECONDS).callTimeout(10,TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build(),
    private val now: () -> Long=System::currentTimeMillis,
) {
    private data class Key(val id: String,val title: String,val year: Int,val movie: Boolean,val source: RatingSource)
    private data class Cached(val state: RatingState,val expiresAt: Long)
    private val cache=object: LinkedHashMap<Key,Cached>(32,.75f,true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key,Cached>?)=size>120
    }
    private val myAnimeListLock=Mutex()
    private var lastMyAnimeListRequest: Long?=null

    suspend fun load(show: Anime,source: RatingSource,force: Boolean=false,
        crunchyroll: suspend () -> JsonElement? = {null}): RatingState {
        val key=Key(show.id,show.title,show.year,show.isMovie,source)
        if(!force) synchronized(cache) {cache[key]?.takeIf {it.expiresAt>now()}}?.let {return it.state}
        val state=try {
            when(source) {
                RatingSource.Crunchyroll -> crunchyroll()?.let(::parseCrunchyroll) ?: RatingState.Unavailable
                RatingSource.MyAnimeList -> myAnimeList(show)
                RatingSource.Imdb -> imdb(show)
            }
        } catch(e: CancellationException) {throw e}
        catch(_: Exception) {RatingState.Error}
        // Errors remain immediately retryable; missing matches are cached briefly.
        if(state!=RatingState.Error) synchronized(cache) {
            cache[key]=Cached(state,now()+if(state==RatingState.Unavailable) 30*60_000 else 6*60*60_000)
        }
        return state
    }

    private suspend fun myAnimeList(show: Anime): RatingState {
        // Tenrai serves the MAL catalogue with the same schema; Jikan's public API was retired.
        // Stay below Tenrai's public limits of 4 requests/second and 120/minute.
        val root=myAnimeListLock.withLock {
            lastMyAnimeListRequest?.let {delay((2100-(now()-it)).coerceIn(0,2100))}
            lastMyAnimeListRequest=now()
            get(myAnimeListBase.newBuilder().addPathSegment("anime").addQueryParameter("q",show.title)
                .addQueryParameter("limit","25").build())
        }.obj()
        check(root["data"] is JsonArray) {"Unexpected MyAnimeList response"}
        val match=uniqueMatch(root.list("data"),show,ids={it.text("mal_id")},titles={obj ->
            listOf(obj.text("title"),obj.text("title_english"),obj.text("title_japanese"))+
                obj.list("titles").map {it.obj().text("title")}+
                obj.list("title_synonyms").mapNotNull {(it as? JsonPrimitive)?.contentOrNull}
        },year={obj -> obj.integer("year") ?: obj["aired"].obj()["prop"].obj()["from"].obj().integer("year")},
            eligible={obj -> if(show.isMovie) obj.text("type")=="Movie" else obj.text("type") in listOf("TV","ONA","OVA")})
            ?: return RatingState.Unavailable
        // Never use MAL's `rating` field: that is the age classification, not the audience score.
        val score=match.score("score",10)
        val rank=match.integer("rank")?.takeIf {it>0}
        return available(score,rank)
    }

    private suspend fun imdb(show: Anime): RatingState {
        val type=if(show.isMovie) "movie" else "series"
        val root=get(cinemetaBase.newBuilder().addPathSegments("catalog/$type/top")
            .addPathSegment("search=${show.title}.json").build()).obj()
        check(root["metas"] is JsonArray) {"Unexpected IMDb search response"}
        val match=uniqueMatch(root.list("metas"),show,ids={it.text("id")},titles={listOf(it.text("name"))},
            year={it.text("releaseInfo").take(4).toIntOrNull()},eligible={it.text("id").matches(Regex("tt[0-9]+"))})
            ?: return RatingState.Unavailable
        val id=match.text("id")
        val meta=get(cinemetaBase.newBuilder().addPathSegments("meta/$type").addPathSegment("$id.json").build()).obj()["meta"].obj()
        check(meta.isNotEmpty()) {"Unexpected IMDb metadata response"}
        if(meta.text("id")!=id || meta.list("genres").none {(it as? JsonPrimitive)?.contentOrNull=="Animation"}) return RatingState.Unavailable
        return available(meta.score("imdbRating",10))
    }

    private suspend fun get(url: HttpUrl): JsonElement {
        val call=client.newCall(Request.Builder().url(url).header("Accept","application/json")
            .header("User-Agent","Sumi/ratings (unofficial)").build())
        return suspendCancellableCoroutine {continuation ->
            continuation.invokeOnCancellation {call.cancel()}
            call.enqueue(object: Callback {
                override fun onFailure(call: Call,e: IOException) {if(continuation.isActive) continuation.resumeWithException(e)}
                override fun onResponse(call: Call,response: Response) {
                    val result=runCatching {response.use {
                        if(!it.isSuccessful) throw IOException("Rating provider HTTP ${it.code}")
                        Json.parseToJsonElement(it.body.string())
                    }}
                    if(continuation.isActive) result.fold({continuation.resume(it) {_,_,_->}},{continuation.resumeWithException(it)})
                }
            })
        }
    }

    companion object {
        internal fun parseCrunchyroll(root: JsonElement): RatingState {
            val obj=root.obj()
            // `rating` is the signed-in viewer's own vote. Only `average` is the community score.
            return if(obj.integer("total")==0) RatingState.Unavailable else available(obj.score("average",5))
        }
        private fun available(score: Double?,rank: Int?=null): RatingState =
            if(score==null && rank==null) RatingState.Unavailable else RatingState.Available(ShowRating(score,rank))

        private fun normalized(title: String): String = Normalizer.normalize(title,Normalizer.Form.NFKD)
            .replace(Regex("\\p{M}+"),"").lowercase(Locale.ROOT).replace("&","and")
            .replace(Regex("[^\\p{L}\\p{N}]"),"")

        /** An exact alias and consistent release year must identify one unique work. Never pick the first search hit. */
        private fun uniqueMatch(items: List<JsonElement>,show: Anime,ids: (JsonObject)->String,
            titles: (JsonObject)->List<String>,year: (JsonObject)->Int?,eligible: (JsonObject)->Boolean): JsonObject? {
            val aliases=listOf(show.title).map(::normalized).filter {it.isNotEmpty()}.toSet()
            return items.map {it.obj()}.filter {obj ->
                ids(obj).isNotBlank() && eligible(obj) && titles(obj).any {normalized(it) in aliases} &&
                    (show.year<=0 || year(obj)==show.year)
            }.distinctBy(ids).singleOrNull()
        }
        private fun JsonElement?.obj()=this as? JsonObject ?: JsonObject(emptyMap())
        private fun JsonObject.text(key: String)=(this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
        private fun JsonObject.integer(key: String)=(this[key] as? JsonPrimitive)?.intOrNull
        private fun JsonObject.list(key: String)=(this[key] as? JsonArray)?.toList().orEmpty()
        private fun JsonObject.score(key: String,maximum: Int)=(this[key] as? JsonPrimitive)?.doubleOrNull
            ?.takeIf {it.isFinite() && it>0 && it<=maximum}
    }
}

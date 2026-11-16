package dev.cinema.crunchyroll.data.account

import dev.cinema.crunchyroll.R
import dev.cinema.crunchyroll.data.*
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.OffsetDateTime

/** The account cache is owned by one authenticated profile and discarded on every switch/logout. */
class ConnectedCatalogue private constructor(private val session: AccountSession,private val data: Cache) : CatalogueRepository {
    constructor(session: AccountSession): this(session,Cache())
    private class Cache {
        val shows=ConcurrentHashMap<String,Anime>()
        val chapters=ConcurrentHashMap<Pair<String,Int>,List<Episode>>()
        val seasons=ConcurrentHashMap<Pair<String,Int>,String>()
        val episodeIndex=ConcurrentHashMap<String,Episode>()
        val progress=ConcurrentHashMap<String,Progress>()
        val featuredHydrated=ConcurrentHashMap.newKeySet<String>()
        var homeIds=listOf<String>()
    }
    private val cache=data.shows
    private val chapters=data.chapters
    private val seasons=data.seasons
    private fun cacheSeries(show: Anime) {
        cache.compute(show.id) {_,old -> show.copy(artworkUrl=show.artworkUrl ?: old?.artworkUrl,
            posterUrl=show.posterUrl ?: old?.posterUrl,heroUrl=show.heroUrl ?: old?.heroUrl,logoUrl=show.logoUrl ?: old?.logoUrl)}
    }
    fun snapshot()=ConnectedCatalogue(session,data)
    override val shows: List<Anime> get() = data.homeIds.mapNotNull(cache::get)
    override fun show(id: String)=cache[id]
    override fun episode(id: String)=data.episodeIndex[id] ?: chapters.values.asSequence().flatten().firstOrNull {it.id==id}
    fun progress(id: String)=data.progress[id]
    override fun episodes(showId: String,season: Int)=chapters[showId to season].orEmpty()
    /** Continue Watching has an episode panel, but may never have loaded the season list. */
    suspend fun loadNext(episode: Episode,preferredAudio: String): Episode? {
        val audio=LanguagePreferences.serviceAudioLocale(preferredAudio)
        val response=session.request("content/v2/discover/up_next/${episode.id}",locale+mapOf("preferred_audio_language" to audio))
        val next=entries(response).mapNotNull {mapHistory(it)?.episode}
            .firstOrNull {it.id!=episode.id && it.showId==episode.showId}
        next?.let {data.episodeIndex[it.id]=it}
        return next
    }
    private fun accountId(): String = (session.state.value as? AccountState.SignedIn)?.account?.accountId
        ?: throw ServiceException(401,"Please sign in again.",true)
    private val locale get()=mapOf("locale" to "en-US")

    suspend fun rating(show: Anime): JsonElement = session.request(
        "content-reviews/v2/user/${accountId()}/rating/${if(show.isMovie) "movie_listing" else "series"}/${show.id}",locale)

    suspend fun home(): List<Anime> {
        val data=session.request("content/v2/discover/browse",locale+mapOf("sort_by" to "popularity","type" to "series","n" to "30","start" to "0"))
        val shows=entries(data).mapNotNull(::mapSeries).distinctBy {it.id}
        shows.forEach(::cacheSeries);this.data.homeIds=shows.map {it.id};this.data.featuredHydrated.clear()
        return this.shows
    }
    /** Fetch artwork for the selected spotlight, without loading seasons or hydrating every card. */
    suspend fun featured(id: String) {
        if(id in data.featuredHydrated) return
        entries(session.request("content/v2/cms/series/$id",locale)).firstOrNull()?.let(::mapSeries)?.let {
            cacheSeries(it);data.featuredHydrated.add(id)
        }
    }
    override suspend fun search(query: String,category: String): List<Anime> {
        val data=if(query.isBlank()) session.request("content/v2/discover/browse",locale+buildMap {
            put("sort_by","popularity");put("type","series");put("n","50")
            if(category!="All") put("categories",category.lowercase())
        }) else session.request("content/v2/discover/search",locale+mapOf("q" to query.trim(),"type" to "series","n" to "50"))
        val items=entries(data).flatMap { item ->
            val obj=item.objectOrEmpty()
            if(obj["items"] is JsonArray) obj.array("items").toList() else listOf(item)
        }.mapNotNull(::mapSeries).distinctBy {it.id}
        items.forEach(::cacheSeries)
        return items
    }
    suspend fun details(id: String) {
        val data=session.request("content/v2/cms/series/$id",locale)
        entries(data).firstOrNull()?.let(::mapSeries)?.let {updated ->
            cacheSeries(updated);this.data.featuredHydrated.add(id)
        }
        val seasonData=session.request("content/v2/cms/series/$id/seasons",locale)
        val all=entries(seasonData).map {it.objectOrEmpty()}
        // Retain one original/primary edition per season rather than inventing dub seasons.
        val groups=all.groupBy {it.number("season_number").takeIf {n->n>0} ?: 1}
        groups.forEach {(number,versions) ->
            val season=versions.firstOrNull {(it["is_subbed"] as? JsonPrimitive)?.booleanOrNull==true} ?: versions.first()
            season.text("id").takeIf {it.isNotBlank()}?.let {seasons[id to number]=it}
        }
        cache[id]?.let {cache[id]=it.copy(seasons=groups.size.coerceAtLeast(1),seasonNumbers=groups.keys.sorted())}
        groups.keys.minOrNull()?.let {loadEpisodes(id,it)}
    }
    suspend fun loadEpisodes(id: String,season: Int) {
        val seasonId=seasons[id to season] ?: return
        val data=session.request("content/v2/cms/seasons/$seasonId/episodes",locale)
        chapters[id to season]=entries(data).mapNotNull {entry ->
            val e=entry.objectOrEmpty();val episodeId=e.text("id")
            if(episodeId.isBlank()) null else Episode(episodeId,id,season,e.number("episode_number"),e.text("title").ifBlank {"Episode"},
                e.number("duration_ms").toLong(),image(e.objectOrEmpty()["images"],"thumbnail"))
        }.sortedBy {it.number}
        this.data.episodeIndex.putAll(chapters[id to season].orEmpty().associateBy {it.id})
        try {playheads(chapters[id to season].orEmpty())} catch(e: CancellationException) {throw e} catch(_: Exception) { /* Playback retries cloud resume independently. */ }
    }

    suspend fun watchlist(): List<Anime> {
        val all=mutableListOf<JsonElement>()
        while(all.size<10_000) {
            val response=session.request("content/v2/discover/${accountId()}/watchlist",locale+mapOf("n" to "100","start" to all.size.toString(),"order" to "desc"))
            val page=entries(response);all+=page
            if(page.isEmpty() || response.objectOrEmpty().number("total")<=all.size) break
        }
        val panels=all.map {(it.objectOrEmpty()["panel"] ?: it).objectOrEmpty()}
        val ids=panels.mapNotNull {panel ->
            mapSeries(panel)?.let {cacheSeries(it);it.id}
                ?: panel["episode_metadata"].objectOrEmptyNullable().text("series_id").takeIf {it.isNotBlank()}
                ?: panel.text("id").takeIf {it.isNotBlank() && panel.text("type")!="episode"}
        }.distinct()
        hydrate(ids.filter {cache[it]==null})
        return ids.mapNotNull(cache::get)
    }

    private suspend fun hydrate(ids: List<String>) {
        for(group in ids.distinct().chunked(20)) {
            val result=session.request("content/v2/cms/objects/${group.joinToString(",")}",locale)
            entries(result).mapNotNull(::mapSeries).forEach(::cacheSeries)
        }
    }

    /** Provider history/checkpoints, not an append-only log of every rewatch or playback session. */
    suspend fun history(page: Int=1,pageSize: Int=50): HistoryPage {
        val root=session.request("content/v2/${accountId()}/watch-history",locale+mapOf("page" to page.toString(),"page_size" to pageSize.toString()))
        val raw=entries(root)
        val preliminary=raw.mapNotNull {entry -> mapHistory(entry)}
        hydrate(preliminary.map {it.show.id}.filter {cache[it]==null})
        val result=preliminary.map {entry ->
            data.episodeIndex[entry.episode.id]=entry.episode
            data.progress.compute(entry.episode.id) {_,old ->resumeProgress(old,entry.progress)}
            entry.copy(show=cache[entry.show.id] ?: entry.show.also {cache[it.id]=it})
        }
        return HistoryPage(result,historyHasNext(root,page,pageSize,raw.size))
    }

    suspend fun playheads(episodes: List<Episode>): Map<String,Progress> {
        if(episodes.isEmpty()) return emptyMap()
        val result=mutableMapOf<String,Progress>()
        for(group in episodes.distinctBy {it.id}.chunked(50)) {
            val response=session.request("content/v2/${accountId()}/playheads",locale+mapOf("content_ids" to group.joinToString(",") {it.id}))
            val durations=group.associate {it.id to it.durationMs}
            entries(response).forEach {raw ->
                val p=raw.objectOrEmpty();val id=p.text("content_id")
                if(id.isNotBlank()) result[id]=mapProgress(p,durations[id] ?: episode(id)?.durationMs ?: 0)
            }
        }
        data.progress.putAll(result);return result
    }
    suspend fun setWatchlist(id: String,included: Boolean) {
        val path="content/v2/${accountId()}/watchlist"
        if(included) session.request(path,locale,"POST",buildJsonObject {put("content_id",id)})
        else session.request("$path/$id",locale,"DELETE")
    }

    companion object {
        internal fun historyHasNext(root: JsonElement,page: Int,pageSize: Int,rawSize: Int): Boolean {
            val obj=root.objectOrEmpty()
            val meta=obj["meta"].objectOrEmptyNullable()
            if("next_page" in meta) {
                val next=meta["next_page"] as? JsonPrimitive ?: return false
                val value=next.contentOrNull ?: return false
                return when {
                    next.booleanOrNull!=null -> next.booleanOrNull==true
                    next.intOrNull!=null -> next.intOrNull!!>page
                    else -> value.isNotBlank() && value!="null"
                }
            }
            val total=(obj["total"] as? JsonPrimitive)?.intOrNull
            return if(total!=null) page.toLong()*pageSize<total else rawSize>=pageSize
        }
        internal fun entries(root: JsonElement): List<JsonElement> = when(root) {
            is JsonArray -> root.toList()
            is JsonObject -> (root["data"] as? JsonArray ?: root["items"] as? JsonArray)?.toList()
                ?: if(root.text("id").isNotBlank()) listOf(root) else emptyList()
            else -> emptyList()
        }
        internal fun mapSeries(element: JsonElement): Anime? {
            val obj=element.objectOrEmpty();val id=obj.text("id");val title=obj.text("title")
            if(id.isBlank() || title.isBlank()) return null
            val type=obj.text("type")
            if(type.isNotBlank() && type !in listOf("series","movie_listing")) return null
            val metadata=(obj["series_metadata"] ?: obj["movie_listing_metadata"] ?: obj).objectOrEmpty()
            val audio=metadata.array("audio_locales").mapNotNull {(it as? JsonPrimitive)?.contentOrNull}.map {java.util.Locale.forLanguageTag(it).displayLanguage}.distinct()
            return Anime(id,title,"",obj.text("description"),metadata.number("series_launch_year"),
                metadata.array("tenant_categories").mapNotNull {(it as? JsonPrimitive)?.contentOrNull}.ifEmpty {listOf("Anime")},
                R.drawable.ic_cinema,R.drawable.ic_cinema,metadata.number("season_count").coerceAtLeast(1),audio,
                image(obj["images"],"poster_wide") ?: localizedImage(obj["images_localized"],"hero_background",1280),
                image(obj["images"],"poster_tall"),logoUrl=localizedImage(obj["images_localized"],"content_logo_polychromatic",640)
                    ?: localizedImage(obj["images_localized"],"content_logo_monochromatic",640),
                heroUrl=localizedImage(obj["images_localized"],"hero_background",1280),isMovie=type=="movie_listing")
        }
        internal fun mapHistory(element: JsonElement): HistoryEntry? {
            val row=element.objectOrEmpty();val panel=row["panel"].objectOrEmptyNullable()
            val meta=panel["episode_metadata"].objectOrEmptyNullable()
            val seriesId=meta.text("series_id").ifBlank {row.text("parent_id")}
            val id=panel.text("id").ifBlank {row.text("id")}
            if(seriesId.isBlank() || id.isBlank() || panel.text("type") !in listOf("episode","movie")) return null
            val episode=Episode(id,seriesId,meta.number("season_number"),meta.number("episode_number"),panel.text("title"),meta.number("duration_ms").toLong(),image(panel["images"],"thumbnail"))
            val show=Anime(seriesId,meta.text("series_title").ifBlank {"Series"},"","",0,listOf("Anime"),R.drawable.ic_cinema,
                artworkUrl=episode.thumbnailUrl)
            return HistoryEntry(show,episode,mapProgress(row,episode.durationMs))
        }
        internal fun mapProgress(obj: JsonObject,duration: Long): Progress {
            val done=(obj["fully_watched"] as? JsonPrimitive)?.booleanOrNull ?: false
            val position=if(done && duration>0) duration else ((obj["playhead"] as? JsonPrimitive)?.longOrNull ?: 0)*1000
            return Progress(position,duration,timestamp(obj.text("last_modified").ifBlank {obj.text("date_played")}))
        }
        private fun timestamp(value: String): Long = value.toDoubleOrNull()?.let {if(it>1e12) it.toLong() else (it*1000).toLong()}
            ?: runCatching {Instant.parse(value).toEpochMilli()}.getOrElse {runCatching {OffsetDateTime.parse(value).toInstant().toEpochMilli()}.getOrDefault(0)}
        private fun JsonElement?.objectOrEmptyNullable()=this as? JsonObject ?: JsonObject(emptyMap())
        internal fun localizedImage(images: JsonElement?,type: String,width: Int): String? {
            val image=((images as? JsonObject)?.get(type) as? JsonObject) ?: return null
            return image.text("url").takeIf {it.startsWith("https://")}
                ?.replace("{:param}","width=$width,fit=contain,format=webp")
        }
        internal fun image(images: JsonElement?,type: String): String? {
            fun candidates(node: JsonElement?): List<JsonObject> = when(node) {
                is JsonArray -> node.flatMap {candidates(it)}
                is JsonObject -> if(node.text("source").startsWith("https://")) listOf(node) else emptyList()
                else -> emptyList()
            }
            val versions=candidates((images as? JsonObject)?.get(type))
            return versions.filter {it.number("width")<=1920}.maxByOrNull {it.number("width")}?.text("source")
                ?: versions.minByOrNull {it.number("width")}?.text("source")
        }
    }
}

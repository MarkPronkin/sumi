package dev.cinema.crunchyroll.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import dev.cinema.crunchyroll.R
import dev.cinema.crunchyroll.data.*
import dev.cinema.crunchyroll.data.account.*
import dev.cinema.crunchyroll.playback.NativePlaybackService
import dev.cinema.crunchyroll.updates.AppUpdates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class Notice(val text: String, val undo: (() -> Unit)? = null, val actionLabel: String = "Undo", val success: Boolean = true)

class CinemaViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    var catalogue: CatalogueRepository by mutableStateOf(DemoCatalogue()); private set
    private val session=AccountSession(CrunchyrollHttp(),KeystoreTokenVault(application),InstallationDeviceIdentity(application))
    private val nativePlayer=NativePlaybackService(session)
    fun nativePlaybackService(): NativePlaybackService? = if(isConnected) nativePlayer else null
    val account=session.state
    internal val authenticationEvents get()=session.authenticationEvents
    val loginOpen=MutableStateFlow(false)
    val connectedLoading=MutableStateFlow(false)
    val connectedError=MutableStateFlow<String?>(null)
    val detailLoading=MutableStateFlow(false)
    val detailError=MutableStateFlow<String?>(null)
    val connectedWatchlist=MutableStateFlow<List<Anime>>(emptyList())
    val watchlistLoading=MutableStateFlow(false)
    val watchlistError=MutableStateFlow<String?>(null)
    val connectedHistory=MutableStateFlow<List<HistoryEntry>>(emptyList())
    val historyLoading=MutableStateFlow(false)
    val historyError=MutableStateFlow<String?>(null)
    val historyHasMore=MutableStateFlow(false)
    val episodeSelection=MutableStateFlow(EpisodeSelection())
    val ratings=MutableStateFlow(ShowRatings())
    private val ratingsRepository=RatingsRepository()
    private var ratingsJob: Job?=null
    private var authJob: Job?=null
    private var contentJob: Job?=null
    private var featuredJob: Job?=null
    private var homeRefreshedAt: Long?=null
    private var detailsJob: Job?=null
    private var watchlistJob: Job?=null
    private var historyJob: Job?=null
    private var watchlistRevision=0L
    private var historyPages=HistoryPages()
    private var historyRetryLoadMore=false
    private var historyRevision=0L
    private var detailRevision=0L
    private var restartEpisode: String?=null
    private var accountEpoch=0L
    private val searchRepository=MutableStateFlow(catalogue)
    val isConnected get()=account.value is AccountState.SignedIn
    val accountPreferenceKey: String? get()=(account.value as? AccountState.SignedIn)?.account?.let {"account:${it.accountId}:${it.profileId ?: "primary"}"}
    private val store: UserStore = PreferencesUserStore(application)
    private val watchlist = WatchlistActions(store)
    val local = store.state.stateIn(viewModelScope, SharingStarted.Eagerly, LocalState())
    var ready = MutableStateFlow(false); private set
    val destination = saved.getStateFlow("destination", "Home")
    val contentView = saved.getStateFlow("contentView", ContentView.WatchedShows.name)
    val featuredId = saved.getStateFlow("featuredId",catalogue.shows.random().id)
    val detail = saved.getStateFlow<String?>("detail", null)
    val origin = saved.getStateFlow("origin", "home-hero")
    val artworkOriginRounded = saved.getStateFlow("artworkOriginRounded", true)
    val playerEpisode = saved.getStateFlow<String?>("player", null)
    val query = saved.getStateFlow("query", "")
    val category = saved.getStateFlow("category", "All")
    private val retry = MutableStateFlow(0)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val results = searchRepository.flatMapLatest { repo -> searchResults(combine(query, category, retry) { q, c, r -> SearchRequest(q, c, r) }, repo) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LoadState.Loading)
    val busy = MutableStateFlow<Set<String>>(emptySet())
    private val notices = Channel<Notice>(Channel.BUFFERED)
    val messages = notices.receiveAsFlow()
    internal val appUpdates = AppUpdates(application, viewModelScope)
    internal val appUpdatesOpen = saved.getStateFlow("appUpdatesOpen", false)

    init {
        checkForLaunchUpdate()
        ContentView.fromLegacyRoute(destination.value)?.let {
            saved["contentView"]=it.name;saved["destination"]="My Content"
        }
        viewModelScope.launch { store.state.first(); ready.value = true }
        viewModelScope.launch {
            account.collect {state ->
                if(state is AccountState.SignedIn) {
                    loginOpen.value=false
                    activateConnected()
                } else if(state is AccountState.SignedOut && catalogue is ConnectedCatalogue) {
                    accountEpoch++;contentJob?.cancel();featuredJob?.cancel();detailsJob?.cancel();watchlistJob?.cancel();historyJob?.cancel();clearRatings();homeRefreshedAt=null
                    catalogue=DemoCatalogue();searchRepository.value=catalogue
                    advanceFeatured()
                    connectedWatchlist.value=emptyList();connectedHistory.value=emptyList();historyPages=HistoryPages();historyHasMore.value=false;historyLoading.value=false;historyError.value=null;watchlistLoading.value=false;watchlistError.value=null;episodeSelection.value=EpisodeSelection();saved["detail"]=null;saved["player"]=null
                    write {it.copy(enteredDemo=false)}
                }
            }
        }
        authJob=viewModelScope.launch {session.restore()}
    }

    internal fun checkForLaunchUpdate() {
        val application = getApplication<Application>()
        appUpdates.check { release ->
            notices.trySend(Notice(
                application.getString(R.string.update_available, release.version),
                { saved["appUpdatesOpen"] = true },
                application.getString(R.string.app_updates),
            ))
        }
    }

    internal fun closeAppUpdates() { saved["appUpdatesOpen"] = false }

    fun navigate(target: String) {
        val legacyView=ContentView.fromLegacyRoute(target)
        if(legacyView!=null) saved["contentView"]=legacyView.name
        val route=if(legacyView!=null) "My Content" else target
        saved["destination"] = route
        if(route=="Home") {
            if(!refreshHomeIfStale()) advanceFeatured()
            if(isConnected) refreshHistory()
        } else if(route=="My Content") refreshContent()
    }
    fun openContent(view: ContentView) {
        saved["contentView"]=view.name
        navigate("My Content")
    }
    fun selectContentView(view: ContentView) {
        if(contentView.value==view.name) return
        saved["contentView"]=view.name
        // Both played-content views share the same loaded pages and pagination cursor.
        if(view==ContentView.Watchlist) refreshWatchlist()
        else if(historyPages.lastPage==0 && !historyLoading.value) refreshHistory()
    }
    fun refreshContent() {
        if(ContentView.fromKey(contentView.value)==ContentView.Watchlist) refreshWatchlist() else refreshHistory()
    }
    fun advanceFeatured(direction: Int=1) {
        val shows=catalogue.shows
        if(shows.isEmpty()) return
        val current=shows.indexOfFirst {it.id==featuredId.value}
        val selected=if(current<0) shows.random() else shows[Math.floorMod(current+direction,shows.size)]
        saved["featuredId"]=selected.id
        hydrateFeatured(selected.id)
    }
    private fun hydrateFeatured(id: String) {
        val repo=catalogue as? ConnectedCatalogue ?: return
        val epoch=accountEpoch
        featuredJob?.cancel()
        featuredJob=viewModelScope.launch {
            try {
                repo.featured(id)
                if(epoch==accountEpoch && featuredId.value==id) catalogue=repo.snapshot()
            } catch(e: CancellationException) {throw e}
            catch(_: Exception) { /* Keep the catalogue artwork and readable title on a metadata failure. */ }
        }
    }
    fun refreshHome() {
        if(isConnected) {refreshConnected();refreshHistory()} else advanceFeatured()
    }
    fun refreshHomeIfStale(): Boolean {
        if(!isConnected || connectedLoading.value || homeRefreshedAt?.let {SystemClock.elapsedRealtime()-it<15*60*1000}==true) return false
        refreshConnected()
        return true
    }
    fun show(id: String, source: String = "home-hero", roundedArtwork: Boolean = true) {
        if(detail.value==id) return
        clearRatings()
        saved["origin"] = source; saved["artworkOriginRounded"] = roundedArtwork; saved["detail"] = id
        loadDetails(id)
    }
    fun play(id: String,fromBeginning: Boolean=false) {clearRatings();restartEpisode=if(fromBeginning) id else null;saved["player"] = id }
    suspend fun nextEpisode(episode: Episode,preferredAudio: String): Episode? {
        val repo=catalogue as? ConnectedCatalogue ?: return catalogue.next(episode)
        val epoch=accountEpoch
        val next=repo.loadNext(episode,preferredAudio)
        if(epoch!=accountEpoch) throw CancellationException("The active account changed")
        return next
    }
    fun back(): Boolean = when {
        playerEpisode.value != null -> { saved["player"] = null; if(isConnected) refreshHistory();true }
        detail.value != null -> { detailsJob?.cancel();clearRatings();saved["detail"] = null; true }
        destination.value != "Home" -> { navigate("Home"); true }
        else -> false
    }
    fun search(value: String) { saved["query"] = value; if(isConnected && value.isNotBlank()) saved["category"]="All" }
    fun filter(value: String) { saved["category"] = value }
    fun retrySearch() { retry.value++ }
    fun enterDemo() = write { it.copy(enteredDemo = true) }
    fun openLogin() {loginOpen.value=true}
    fun reportExternalLinkFailure() {viewModelScope.launch {notices.send(Notice("No app could open this link. Install Crunchyroll or a browser, then retry.",success=false))}}
    fun closeLogin() {authJob?.cancel();loginOpen.value=false}
    fun login(email: String,password: String) {
        if(account.value is AccountState.SigningIn) return
        authJob?.cancel()
        authJob=viewModelScope.launch {session.login(email,password)}
    }
    fun retrySession() {
        authJob?.cancel();authJob=viewModelScope.launch {account.value=AccountState.Restoring;session.restore()}
    }
    fun logout() {
        authJob?.cancel();contentJob?.cancel();featuredJob?.cancel();detailsJob?.cancel();clearRatings();accountEpoch++
        authJob=viewModelScope.launch {session.logout()}
    }
    fun connectedProfiles(): List<AccountProfile> = (account.value as? AccountState.SignedIn)?.account?.profiles.orEmpty()
    fun switchAccountProfile(id: String) {
        if(connectedLoading.value) return
        authJob=viewModelScope.launch {
            connectedLoading.value=true
            try {session.switchProfile(id)}
            catch(e: CancellationException) {throw e}
            catch(e: Exception) {notices.send(Notice(e.accountMessage(),success=false))}
            finally {connectedLoading.value=false}
        }
    }
    private fun activateConnected() {
        accountEpoch++;contentJob?.cancel();featuredJob?.cancel();detailsJob?.cancel();watchlistJob?.cancel();historyJob?.cancel();clearRatings();homeRefreshedAt=null
        saved["detail"]=null;saved["player"]=null
        if(destination.value!="My Content") saved["destination"]="Home"
        val repo=ConnectedCatalogue(session)
        catalogue=repo;searchRepository.value=repo
        connectedWatchlist.value=emptyList()
        connectedHistory.value=emptyList();historyPages=HistoryPages();historyHasMore.value=false;historyLoading.value=false;historyError.value=null;watchlistError.value=null;episodeSelection.value=EpisodeSelection()
        refreshConnected()
        refreshHistory()
    }
    fun refreshConnected() {
        val repo=catalogue as? ConnectedCatalogue ?: return
        val epoch=accountEpoch
        contentJob?.cancel()
        contentJob=viewModelScope.launch {
            connectedLoading.value=true;connectedError.value=null
            try {
                repo.home()
                if(epoch==accountEpoch) {homeRefreshedAt=SystemClock.elapsedRealtime();catalogue=repo.snapshot();advanceFeatured();refreshWatchlist()}
            } catch(e: CancellationException) {throw e}
            catch(e: Exception) {if(epoch==accountEpoch) connectedError.value=e.accountMessage()}
            finally {if(epoch==accountEpoch) connectedLoading.value=false}
        }
    }
    fun loadDetails(id: String) {
        val repo=catalogue as? ConnectedCatalogue ?: return
        val epoch=accountEpoch
        detailsJob?.cancel()
        val revision=++detailRevision
        episodeSelection.value=EpisodeSelection(showId=id,loading=true)
        detailsJob=viewModelScope.launch {
            detailLoading.value=true;detailError.value=null
            try {
                repo.details(id)
                val available=repo.show(id)?.seasonNumbers.orEmpty()
                val recent=connectedHistory.value.firstOrNull {it.show.id==id && it.progress.positionMs>0 && !it.progress.complete}?.episode?.season
                val selected=(saved.get<Int>("season:$id") ?: recent ?: available.firstOrNull() ?: 1).takeIf {available.isEmpty() || it in available} ?: available.first()
                if(repo.episodes(id,selected).isEmpty()) repo.loadEpisodes(id,selected)
                if(epoch==accountEpoch && revision==detailRevision) {
                    catalogue=repo.snapshot()
                    episodeSelection.value=EpisodeSelection(id,selected,selected,repo.episodes(id,selected))
                }
            }
            catch(e: CancellationException) {throw e}
            catch(e: Exception) {if(epoch==accountEpoch && revision==detailRevision) {detailError.value=e.accountMessage();episodeSelection.value=episodeSelection.value.copy(loading=false,error=e.accountMessage())}}
            finally {if(epoch==accountEpoch && revision==detailRevision) detailLoading.value=false}
        }
    }
    fun loadRatings(id: String,retryErrors: Boolean=false) {
        if(detail.value!=id || playerEpisode.value!=null) return
        val show=catalogue.show(id) ?: return
        val repo=catalogue as? ConnectedCatalogue
        ratingsJob?.cancel()
        if(repo==null) {ratings.value=demoRatings(id);return}
        val epoch=accountEpoch
        val previous=ratings.value.takeIf {it.showId==id}
        val sources=RatingSource.entries.filter {!retryErrors || previous?.get(it) in listOf(RatingState.Error,RatingState.Loading)}
        ratings.value=ShowRatings(id,(previous?.values.orEmpty())+sources.associateWith {RatingState.Loading})
        ratingsJob=viewModelScope.launch {
            sources.forEach {source -> launch {
                val result=withTimeoutOrNull(22_000) {ratingsRepository.load(show,source) {repo.rating(show)}} ?: RatingState.Error
                if(epoch==accountEpoch && detail.value==id) ratings.update {current ->
                    if(current.showId==id) current.copy(values=current.values+(source to result)) else current
                }
            }}
        }
    }
    private fun clearRatings() {ratingsJob?.cancel();ratings.value=ShowRatings()}
    fun loadSeason(id: String,season: Int) {
        val repo=catalogue as? ConnectedCatalogue ?: return
        val epoch=accountEpoch
        detailsJob?.cancel()
        val revision=++detailRevision
        episodeSelection.value=episodeSelection.value.copy(showId=id,requestedSeason=season,loading=true,error=null)
        detailsJob=viewModelScope.launch {
            detailLoading.value=true;detailError.value=null
            try {repo.loadEpisodes(id,season);if(epoch==accountEpoch && revision==detailRevision) {catalogue=repo.snapshot();saved["season:$id"]=season;episodeSelection.value=EpisodeSelection(id,season,season,repo.episodes(id,season))}}
            catch(e: CancellationException) {throw e}
            catch(e: Exception) {if(epoch==accountEpoch && revision==detailRevision) {detailError.value=e.accountMessage();episodeSelection.value=episodeSelection.value.copy(loading=false,error=e.accountMessage())}}
            finally {if(epoch==accountEpoch && revision==detailRevision) detailLoading.value=false}
        }
    }
    fun refreshWatchlist() {
        val repo=catalogue as? ConnectedCatalogue ?: return
        val epoch=accountEpoch
        val revision=++watchlistRevision
        watchlistJob?.cancel()
        watchlistJob=viewModelScope.launch {
            watchlistLoading.value=true;watchlistError.value=null
            try {val items=repo.watchlist();if(epoch==accountEpoch && revision==watchlistRevision) {connectedWatchlist.value=items;catalogue=repo.snapshot()}}
            catch(e: CancellationException) {throw e}
            catch(e: Exception) {if(epoch==accountEpoch && revision==watchlistRevision) watchlistError.value=e.accountMessage()}
            finally {if(epoch==accountEpoch && revision==watchlistRevision) watchlistLoading.value=false}
        }
    }
    fun refreshHistory(loadMore: Boolean=false) {
        val repo=catalogue as? ConnectedCatalogue ?: return
        if(loadMore && (historyLoading.value || !historyHasMore.value)) return
        val epoch=accountEpoch
        historyJob?.cancel()
        val pages=if(loadMore) (historyPages.lastPage+1)..(historyPages.lastPage+1) else 1..historyPages.lastPage.coerceAtLeast(1)
        val revision=++historyRevision
        historyRetryLoadMore=loadMore
        historyJob=viewModelScope.launch {
            historyLoading.value=true;historyError.value=null
            try {
                // Refresh the loaded window so changed page boundaries cannot skip older series.
                // Retain the visible pages until each replacement succeeds, including on a partial failure.
                for(page in pages) {
                    val result=repo.history(page)
                    if(epoch!=accountEpoch || revision!=historyRevision) return@launch
                    historyPages=historyPages.withPage(page,result)
                    connectedHistory.value=historyPages.items
                    historyHasMore.value=historyPages.hasMore;catalogue=repo.snapshot()
                    if(!result.hasMore) break
                }
            } catch(e: CancellationException) {throw e}
            catch(e: Exception) {if(epoch==accountEpoch && revision==historyRevision) historyError.value=e.accountMessage()}
            finally {if(epoch==accountEpoch && revision==historyRevision) historyLoading.value=false}
        }
    }
    fun retryHistory()=refreshHistory(historyRetryLoadMore)

    fun progressFor(episode: Episode): Progress? {
        val profile=accountPreferenceKey ?: local.value.activeProfile
        return resumeProgress(local.value.profiles[profile]?.progress?.get(episode.id),(catalogue as? ConnectedCatalogue)?.progress(episode.id))
    }
    suspend fun playbackPosition(episode: Episode,profileId: String): Long {
        if(restartEpisode==episode.id) return 0
        val current=local.value.profiles[profileId]?.progress?.get(episode.id)
        if(current?.needsSync==true) return if(current.complete) 0 else current.positionMs.coerceAtLeast(0)
        val repo=catalogue as? ConnectedCatalogue
        val remote=repo?.playheads(listOf(episode))?.get(episode.id)
        val progress=resumeProgress(current,remote) ?: return 0
        return if(progress.complete) 0 else progress.positionMs.coerceAtLeast(0)
    }
    fun markProgressSynced(profileId: String,id: String,position: Long) = write {state ->state.updateProfile(profileId) {profile ->
        val previous=profile.progress[id]
        if(previous==null || previous.positionMs!=position) profile else profile.copy(progress=profile.progress+(id to previous.copy(needsSync=false)))
    }}
    fun selectProfile(id: String) {
        if (demoProfiles.none { it.id == id }) return
        saved["detail"] = null; saved["player"] = null
        write { it.copy(activeProfile = id) }
    }
    fun leaveDemo() {
        saved["detail"] = null; saved["player"] = null; navigate("Home")
        write { it.copy(enteredDemo = false) }
    }
    fun preferences(change: (ProfileData) -> ProfileData) {
        val profile = accountPreferenceKey ?: local.value.activeProfile
        write { it.updateProfile(profile, change) }
    }
    fun toggleList(showId: String, desired: Boolean? = null) {
        if(isConnected) {toggleConnectedList(showId,desired);return}
        val profile = local.value.activeProfile
        val key = "$profile/$showId"
        if (key in busy.value) return
        val include = desired ?: (showId !in local.value.current.watchlist)
        busy.value += key
        viewModelScope.launch {
            try {
                watchlist.set(profile, showId, include)
                notices.send(Notice(if (include) "Added to your demo Watchlist" else "Removed from your demo Watchlist", { setListForProfile(profile, showId, !include) }))
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) {
                notices.send(Notice("Couldn’t save your Watchlist. Try again.", { setListForProfile(profile, showId, include) }, "Retry", false))
            } finally { busy.value -= key }
        }
    }
    private fun toggleConnectedList(id: String,desired: Boolean?) {
        val repo=catalogue as? ConnectedCatalogue ?: return
        val epoch=accountEpoch
        if(id in busy.value) return
        watchlistJob?.cancel();watchlistRevision++;watchlistLoading.value=false
        val include=desired ?: connectedWatchlist.value.none {it.id==id}
        busy.value+=id
        viewModelScope.launch {
            try {
                repo.setWatchlist(id,include)
                if(epoch==accountEpoch) {
                    connectedWatchlist.value=if(include) (connectedWatchlist.value+listOfNotNull(repo.show(id))).distinctBy {it.id} else connectedWatchlist.value.filterNot {it.id==id}
                    watchlistError.value=null
                    notices.send(Notice(if(include) "Added to Watchlist" else "Removed from Watchlist"))
                }
            } catch(e: CancellationException) {throw e}
            catch(e: Exception) {if(epoch==accountEpoch) notices.send(Notice(e.accountMessage(),{if(epoch==accountEpoch) toggleConnectedList(id,include)},"Retry",false))}
            finally {busy.value-=id}
        }
    }
    private fun setListForProfile(profile: String, showId: String, included: Boolean) {
        viewModelScope.launch {
            try { watchlist.set(profile, showId, included); notices.send(Notice("Demo Watchlist updated")) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { notices.send(Notice("Couldn’t save your Watchlist. Try again.", success=false)) }
        }
    }
    fun saveProgress(profileId: String, episodeId: String, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0 || positionMs < 0) return
        write { state -> state.updateProfile(profileId) { p ->
            p.copy(progress = p.progress + (episodeId to Progress(positionMs.coerceAtMost(durationMs), durationMs, System.currentTimeMillis(),profileId.startsWith("account:"))))
        } }
    }
    fun resumeEpisode(showId: String): Episode = local.value.current.progress.entries
        .filter { catalogue.episode(it.key)?.showId == showId && !it.value.complete }
        .maxByOrNull { it.value.updatedAt }?.let { catalogue.episode(it.key) }
        ?: catalogue.episodes(showId, 1).first()

    private fun write(transform: (LocalState) -> LocalState) = viewModelScope.launch {
        try { store.update(transform) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { notices.send(Notice("Couldn’t save on this device. Please try again.", success=false)) }
    }
}

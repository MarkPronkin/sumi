package dev.cinema.crunchyroll.ui

import android.content.Intent
import android.net.Uri

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import dev.cinema.crunchyroll.data.*
import dev.cinema.crunchyroll.data.account.ConnectedCatalogue
import dev.cinema.crunchyroll.design.*

@Composable
fun HomeScreen(vm: CinemaViewModel,state: LocalState,origin: String) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {vm.refreshHomeIfStale()}
    LaunchedEffect(vm) {vm.refreshHomeIfStale()}
    Box(Modifier.fillMaxSize().testTag("home-viewport")) {
        if(LocalConnected.current) ConnectedHomeScreen(vm,origin,state) else DemoHomeScreen(vm,state,origin)
    }
}

@Composable
private fun DemoHomeScreen(vm: CinemaViewModel,state: LocalState,origin: String) {
    val list=rememberLazyListState()
    val motion=LocalMotion.current
    val history=remember(state.current.progress,vm.catalogue) { mergedHistory(emptyList(),state.current.progress,vm.catalogue).filter {!it.progress.complete} }
    LazyColumn(Modifier.fillMaxSize(),state=list,contentPadding=PaddingValues(bottom=28.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item(key="hero") {
            FeaturedBanner(vm,state.current.watchlist,
                parallax={if(motion==MotionLevel.Maximal && list.firstVisibleItemIndex==0) list.firstVisibleItemScrollOffset*.2f else 0f})
        }
        item(key="continue") { Reveal(1) { Column(Modifier.padding(horizontal=24.dp)) {
            Heading("Continue Watching",action={vm.openContent(ContentView.History)})
            if(history.isEmpty()) Text("Start a demo chapter. Your place will be saved here.",color=Muted,fontSize=13.sp,modifier=Modifier.padding(bottom=14.dp))
            LazyRow(Modifier.testTag("continue-watching"),horizontalArrangement=Arrangement.spacedBy(16.dp),contentPadding=PaddingValues(vertical=8.dp)) {
                if(history.isEmpty()) items(vm.catalogue.shows.take(2),key={it.id}) { anime ->
                    ShowCard(anime,"continue-${anime.id}",origin,landscape=true,caption="Play sample video · Demo chapter 1",onClick={vm.play(vm.catalogue.episodes(anime.id,1).first().id)})
                } else items(history,key={it.episode.id}) { entry ->
                    val minutesLeft = ((entry.progress.durationMs - entry.progress.positionMs) / 60000).coerceAtLeast(1)
                    ContinueWatchingCard(vm, state.activeProfile, entry, "continue-${entry.episode.id}", origin, "$minutesLeft min left")
                }
            }
        } } }
        item(key="discover") { Reveal(2) { Column(Modifier.padding(horizontal=24.dp)) {
            Heading("Find your next obsession",action={vm.filter("All");vm.navigate("Browse")})
            LazyRow(horizontalArrangement=Arrangement.spacedBy(16.dp),contentPadding=PaddingValues(vertical=8.dp)) {
                items(vm.catalogue.shows,key={it.id}) { anime -> ShowCard(anime,"home-${anime.id}",origin){vm.show(anime.id,"home-${anime.id}")} }
            }
        } } }
        item(key="note") { Text("DEMO CATALOGUE · LOCAL PROGRESS · SAMPLE PLAYBACK",Modifier.padding(24.dp),color=Muted,fontSize=10.sp,letterSpacing=1.sp) }
    }
}

@Composable
private fun FeaturedBanner(vm: CinemaViewModel,watchlist: Set<String>,parallax: () -> Float={0f}) {
    val selected by vm.featuredId.collectAsStateWithLifecycle()
    val loading by vm.connectedLoading.collectAsStateWithLifecycle()
    val shows=vm.catalogue.shows
    val show=shows.firstOrNull {it.id==selected} ?: return
    val source="featured-${show.id}"
    val wide=LocalWide.current
    @Composable fun Header() {
        Hero(show,source,wide,vm.featuredDirection,parallax,inList=show.id in watchlist,
            play={if(vm.isConnected) vm.show(show.id,source,roundedArtwork=false) else vm.play(vm.resumeEpisode(show.id).id)},
            details={vm.show(show.id,source,roundedArtwork=false)},toggle={vm.toggleList(show.id)})
    }
    @Composable fun Controls(modifier: Modifier=Modifier) {
        Row(modifier.padding(horizontal=24.dp,vertical=8.dp).testTag("spotlight-controls"),
            horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
            if(!wide) Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text("THE SPOTLIGHT",color=Muted,fontSize=9.sp,lineHeight=13.sp,letterSpacing=1.3.sp,fontWeight=FontWeight.Bold)
                Text("${shows.indexOf(show)+1} of ${shows.size}",color=Paper,fontSize=12.sp,lineHeight=18.sp)
            }
            if(shows.size>1) {
                IconAction("Previous featured series",Glyph.Back){vm.advanceFeatured(-1)}
                if(wide) Text("${shows.indexOf(show)+1} / ${shows.size}",color=Muted,fontSize=12.sp)
                IconAction("Next featured series",Glyph.Chevron){vm.advanceFeatured()}
            }
            if(loading) CircularProgressIndicator(Modifier.size(24.dp).semantics {contentDescription="Refreshing Home"},color=Orange,strokeWidth=2.dp)
            else IconAction("Refresh Home",Glyph.Refresh){vm.refreshHome()}
        }
    }
    // Recreate the banner's shared drawing layers when the motion setting changes.
    key(LocalMotion.current) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            (vm.catalogue as? ConnectedCatalogue)?.let { repo ->
                PrefetchSpotlight(repo, vm.artworkPrefetcher, spotlightNeighbours(shows, selected, vm.featuredDirection), constraints.maxWidth)
            }
            if(wide && maxWidth>=700.dp) Box(Modifier.fillMaxWidth()) {
                Header()
                Controls(Modifier.align(Alignment.BottomEnd).padding(end=12.dp,bottom=8.dp))
            } else Column {
                Header()
                Controls(Modifier.fillMaxWidth())
            }
        }
    }
}

private val SpotlightFade = Brush.verticalGradient(
    0f to Oled.copy(alpha = .05f), .35f to Color.Transparent, .7f to Oled.copy(alpha = .5f), 1f to Oled)
private val WideBannerFade = Brush.verticalGradient(
    0f to Oled.copy(alpha = .4f), .2f to Color.Transparent, .45f to Color.Transparent, .72f to Oled.copy(alpha = .55f), 1f to Oled)
private val WideBannerSideShade = Brush.horizontalGradient(
    0f to Oled.copy(alpha = .96f), .4f to Oled.copy(alpha = .65f), .75f to Color.Transparent)

// Draw inside sharedArt so the fade always ends at the moving image's lower edge.
private fun Modifier.bannerShade(wide: Boolean) = drawWithContent {
    drawContent()
    drawRect(if (wide) WideBannerFade else SpotlightFade)
    if (wide) drawRect(WideBannerSideShade)
}

@Composable
private fun SpotlightArtwork(show: Anime, direction: Int, modifier: Modifier) {
    val motion = LocalMotion.current
    val forward = (direction > 0) == (LocalLayoutDirection.current == LayoutDirection.Ltr)
    val dissolve = motion.millis(360, 220)
    AnimatedContent(
        targetState = show,
        contentKey = { it.id },
        modifier = modifier.clipToBounds(),
        transitionSpec = {
            val fade = fadeIn(tween(dissolve, easing = LinearEasing))
            val enter = if (motion == MotionLevel.Maximal) {
                fade + scaleIn(
                    tween(540, easing = LinearOutSlowInEasing),
                    initialScale = 1.065f,
                    transformOrigin = TransformOrigin(if (forward) 0f else 1f, .5f),
                )
            } else fade
            // Keep the outgoing image underneath the dissolve so the banner stays luminous.
            val exit = fadeOut(tween(dissolve / 3, delayMillis = dissolve * 2 / 3))
            (enter togetherWith exit).using(null)
        },
        label = "spotlight-artwork",
    ) { series ->
        Art(series, Modifier.fillMaxSize(), hero = true)
    }
}

@Composable
private fun SpotlightCopy(show: Anime, wide: Boolean) {
    val motion = LocalMotion.current
    val short = LocalCompactHeight.current
    val connected = LocalConnected.current
    AnimatedContent(
        targetState = show,
        contentKey = { it.id },
        contentAlignment = Alignment.BottomStart,
        transitionSpec = {
            val delay = if (motion == MotionLevel.Maximal) 90 else 0
            val fade = fadeIn(tween(motion.millis(260, 160), delayMillis = delay))
            val enter = if (motion == MotionLevel.Maximal) {
                fade + slideInVertically(tween(320, delayMillis = delay, easing = LinearOutSlowInEasing)) { it / 8 }
            } else fade
            (enter togetherWith fadeOut(tween(motion.millis(110, 90)))).using(
                SizeTransform { _, _ -> if (motion == MotionLevel.Reduced) snap() else tween(motion.millis(320)) }
            )
        },
        label = "spotlight-title",
    ) { series ->
        val semantics = if (series.id == show.id) Modifier else Modifier.clearAndSetSemantics {}
        Column(semantics, verticalArrangement = Arrangement.spacedBy(if (wide) 8.dp else 6.dp)) {
            Column {
                SeriesTitle(
                    series, fontSize = if (!wide) 32.sp else if (short) 48.sp else 64.sp,
                    lineHeight = if (wide) 58.sp else 34.sp,
                    alignment = if (wide) Alignment.CenterStart else Alignment.BottomStart,
                    logoOnPhone = true, compact = !wide,
                )
                if (wide && series.subtitle.isNotBlank()) Text(series.subtitle, fontSize = 16.sp, letterSpacing = .7.sp)
            }
            val genres = (if (wide) series.genres else series.genres.take(2)).joinToString(" · ")
            val languages = if (connected) series.languages.joinToString(" · ") else "Sub | Dub"
            val metadata = listOfNotNull(
                series.year.takeIf { it > 0 }?.toString(), genres.takeIf { it.isNotBlank() },
                languages.takeIf { wide && it.isNotBlank() },
            )
            Text(metadata.joinToString("  •  "), color = if (wide) Muted else Paper.copy(alpha = .8f),
                fontSize = 12.sp, lineHeight = 18.sp, maxLines = if (wide) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis)
            if (wide && !short) Text(series.synopsis, color = Muted, fontSize = 14.sp, maxLines = 2, lineHeight = 21.sp)
        }
    }
}

@Composable
private fun Hero(show: Anime, source: String, wide: Boolean, direction: Int, parallax: () -> Float, inList: Boolean,
                 play: () -> Unit, details: () -> Unit, toggle: () -> Unit) {
    val tv = LocalTv.current
    val short = LocalCompactHeight.current
    val connected = LocalConnected.current
    @Composable fun Actions(playModifier: Modifier = Modifier) {
        Action(if (connected) "View episodes" else "Play demo", Glyph.Play, filled = true, modifier = playModifier,
            haptic = if (connected) CinemaHaptic.SeriesOpen else CinemaHaptic.Click, onClick = play)
        ListAction(show.id, inList, toggle, compact = !wide)
        IconAction("Details for ${show.title}", Glyph.Info, haptic = CinemaHaptic.SeriesOpen, onClick = details)
    }
    Column(Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val height = when {
                !wide -> maxWidth * 9f / 16f
                tv -> 300.dp
                short -> 236.dp
                else -> 390.dp
            }
            Box(Modifier.fillMaxWidth().heightIn(min = height).clipToBounds().testTag("home-banner")
                .semantics { contentDescription = show.title }) {
                val art = Modifier.matchParentSize().sharedArt(source).bannerShade(wide)
                SpotlightArtwork(show, direction, if (wide) art.graphicsLayer { translationY = parallax() } else art)
                Column(Modifier.align(Alignment.BottomStart).widthIn(max = if (wide) 560.dp else Dp.Infinity)
                    .fillMaxWidth().sharedArtChrome(overlayOnExit = true).padding(start = 24.dp, end = 24.dp,
                        top = if (wide) 24.dp else 16.dp, bottom = if (wide) 12.dp else 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (wide) {
                        Reveal { Eyebrow(if (connected) "THE SPOTLIGHT" else "THE SPOTLIGHT · DEMO FEATURE") }
                        Reveal(1) { SpotlightCopy(show, wide) }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Actions()
                        }
                    } else SpotlightCopy(show, wide)
                }
            }
        }
        if (!wide) Row(Modifier.fillMaxWidth().sharedArtChrome(overlayOnExit = true).padding(horizontal = 24.dp, vertical = 8.dp).testTag("home-banner-copy"),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Actions(Modifier.weight(1f))
        }
    }
}

@Composable
private fun ConnectedHomeScreen(vm: CinemaViewModel,origin: String,state: LocalState) {
    val loading by vm.connectedLoading.collectAsStateWithLifecycle()
    val error by vm.connectedError.collectAsStateWithLifecycle()
    val watchlist by vm.connectedWatchlist.collectAsStateWithLifecycle()
    val history by vm.connectedHistory.collectAsStateWithLifecycle()
    val historyLoading by vm.historyLoading.collectAsStateWithLifecycle()
    val historyError by vm.historyError.collectAsStateWithLifecycle()
    val recent=mergedHistory(history,state.current.progress,vm.catalogue).filter {it.progress.positionMs>0 && !it.progress.complete}.distinctBy {it.show.id}
    val shows=vm.catalogue.shows
    val list=rememberLazyListState()
    LazyColumn(Modifier.fillMaxSize(),state=list,contentPadding=PaddingValues(bottom=32.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        if(shows.isNotEmpty()) item(key="connected-hero") {
            FeaturedBanner(vm,watchlist.map {it.id}.toSet())
        }
        item(key="continue-watching") {Column(Modifier.padding(horizontal=24.dp)) {
            Heading("Continue Watching",action={vm.openContent(ContentView.History)})
            when {
                recent.isNotEmpty() -> LazyRow(Modifier.testTag("continue-watching"),horizontalArrangement=Arrangement.spacedBy(16.dp),contentPadding=PaddingValues(vertical=8.dp)) {
                    items(recent,key={it.episode.id}) {entry ->
                        val position = entry.progress.positionMs
                        ContinueWatchingCard(vm, state.activeProfile, entry, "resume-${entry.episode.id}", origin,
                            "Resume at ${position/60000}:${(position/1000%60).toString().padStart(2,'0')}")
                    }
                }
                historyLoading -> LinearProgressIndicator(Modifier.fillMaxWidth(),color=Orange)
                historyError!=null -> {Text(historyError.orEmpty(),color=Muted);Action("Retry history",Glyph.Refresh){vm.refreshHistory()}}
                else -> Text("Episodes you start will appear here, ready to resume.",color=Muted,fontSize=13.sp)
            }
        }}
        if(loading) item {Column(Modifier.fillMaxWidth().padding(32.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(color=Orange);Text("Loading Crunchyroll catalogue…",color=Muted)
        }}
        error?.let {message -> item {Column(Modifier.padding(24.dp)) {EmptyState("Couldn’t load your catalogue",message,"Retry",vm::refreshConnected)}}}
        if(!loading && error==null && shows.isEmpty()) item {Column(Modifier.padding(24.dp)) {EmptyState("Nothing available yet","The catalogue returned no series for this account or region.","Retry",vm::refreshConnected)}}
        if(watchlist.isNotEmpty()) item {Column(Modifier.padding(horizontal=24.dp)) {
            Heading("Your Watchlist",action={vm.openContent(ContentView.Watchlist)})
            LazyRow(horizontalArrangement=Arrangement.spacedBy(16.dp),contentPadding=PaddingValues(vertical=10.dp)) {
                items(watchlist,key={it.id}) {show->ShowCard(show,"account-list-${show.id}",origin,caption=show.languages.joinToString(" · ")){vm.show(show.id,"account-list-${show.id}")}}
            }
        }}
        if(shows.isNotEmpty()) item {Column(Modifier.padding(horizontal=24.dp)) {
            Heading("Popular on Crunchyroll",action={vm.navigate("Browse")})
            LazyRow(horizontalArrangement=Arrangement.spacedBy(16.dp),contentPadding=PaddingValues(vertical=10.dp)) {
                items(shows,key={it.id}) {show->ShowCard(show,"connected-${show.id}",origin,caption=show.languages.joinToString(" · ")){vm.show(show.id,"connected-${show.id}")}}
            }
        }}
        item {Text("CONNECTED TO CRUNCHYROLL",Modifier.padding(24.dp),color=Muted,fontSize=10.sp,letterSpacing=1.sp)}
    }
}

@Composable
private fun ContinueWatchingCard(vm: CinemaViewModel, profileId: String, entry: HistoryEntry, source: String, origin: String, caption: String) {
    val episode = entry.episode
    val playFocus = remember { FocusRequester() }
    val menuFocus = remember { FocusRequester() }
    Box {
        ShowCard(entry.show, source, origin, true, entry.progress, "S${episode.season} E${episode.number} · $caption",
            modifier = Modifier.focusRequester(playFocus).focusProperties { end = menuFocus }) { vm.play(episode.id) }
        EpisodeHistoryMenu(vm, profileId, episode, entry.progress, inHistory = true,
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).focusRequester(menuFocus).focusProperties { start = playFocus })
    }
}

@Composable
private fun EpisodeRow(vm: CinemaViewModel, profileId: String, episode: Episode, progress: Progress?, inHistory: Boolean,
    playLabel: String, modifier: Modifier, playModifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit) {
    Row(modifier.padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(playModifier.weight(1f).cinemaClick(playLabel, enabled = enabled) { vm.play(episode.id) }.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically, content = content)
        if (enabled) EpisodeHistoryMenu(vm, profileId, episode, progress, inHistory)
    }
}

@Composable
private fun EpisodeHistoryMenu(vm: CinemaViewModel, profileId: String, episode: Episode, progress: Progress?, inHistory: Boolean,
    modifier: Modifier = Modifier) {
    var expanded by remember(profileId, episode.id) { mutableStateOf(false) }
    val editing by vm.editingHistory.collectAsStateWithLifecycle()
    fun select(edit: HistoryEdit) {
        expanded = false
        vm.editEpisodeHistory(profileId, episode, edit)
    }
    Box(modifier) {
        if ((profileId to episode.id) in editing) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(20.dp).semantics { contentDescription = "Updating watch history" }, color = Orange, strokeWidth = 2.dp)
            }
        } else IconAction("Options for season ${episode.season} episode ${episode.number}, ${episode.title}", Glyph.More) { expanded = true }
        val options: @Composable () -> Unit = {
            if (progress?.complete != true) DropdownMenuItem(text = { Text("Mark as watched", color = Paper) },
                leadingIcon = { CinemaIcon(Glyph.Check) }, onClick = { select(HistoryEdit.MarkWatched) })
            if (inHistory) DropdownMenuItem(text = { Text("Remove from watch history", color = Paper) },
                leadingIcon = { CinemaIcon(Glyph.Close) }, onClick = { select(HistoryEdit.Remove) })
        }
        if (LocalQuestPanelSize.current == null) {
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = SurfaceRaised) { options() }
        } else if (expanded) {
            // Android positions popup menus against the host window, which may include letterbox bars.
            CinemaWindowDialog(onDismissRequest = { expanded = false }) {
                Column(Modifier.widthIn(max = 320.dp).background(SurfaceRaised, RoundedCornerShape(12.dp))
                    .verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) { options() }
            }
        }
    }
}

@Composable
private fun ListAction(showId: String,inList: Boolean,onToggle: () -> Unit,compact: Boolean=false) {
    Box(contentAlignment=Alignment.Center) {
        if(compact) IconAction(if(inList) "Remove from Watchlist" else "Add to Watchlist",if(inList) Glyph.Check else Glyph.Plus,selected=inList,onClick=onToggle)
        else Action(if(inList) "In Watchlist" else "Watchlist",if(inList) Glyph.Check else Glyph.Plus,selected=inList,
            accessibilityLabel=if(inList) "Remove from Watchlist" else "Add to Watchlist",onClick=onToggle)
        key(showId) {
            var burst by remember { mutableIntStateOf(0) }
            var previous by remember { mutableStateOf(inList) }
            LaunchedEffect(inList) { if(inList && !previous) burst++;previous=inList }
            Burst(burst,Modifier.matchParentSize())
        }
    }
}

@Composable
fun BrowseScreen(vm: CinemaViewModel,origin: String) {
    val connected=LocalConnected.current
    val query by vm.query.collectAsStateWithLifecycle()
    val category by vm.category.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val wide=LocalWide.current
    val list=rememberLazyListState()
    @Composable fun SearchInput(modifier: Modifier=Modifier) {
        OutlinedTextField(query,vm::search,modifier=modifier.fillMaxWidth().semantics { contentDescription="Search anime" },
            placeholder={Text(if(connected) "Search Crunchyroll" else "Search the demo catalogue",fontSize=14.sp)},singleLine=true,leadingIcon={CinemaIcon(Glyph.Search,Muted,Modifier.size(20.dp))},
            trailingIcon={if(query.isNotEmpty()) IconAction("Clear search",Glyph.Close){vm.search("")}},shape=RoundedCornerShape(16.dp),
            colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=Orange,unfocusedBorderColor=Divider,focusedContainerColor=Surface,unfocusedContainerColor=Surface))
    }
    CatalogueLayout {columns ->
    val sideSearch=wide && columns>=4
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=24.dp),state=list,contentPadding=PaddingValues(bottom=32.dp),verticalArrangement=Arrangement.spacedBy(if(wide) 12.dp else 16.dp)) {
        item(key="heading") {
            if(sideSearch) Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(24.dp)) {
                Box(Modifier.weight(1f)) {ScreenHeader("Discover","FIND YOUR NEXT STORY")}
                SearchInput(Modifier.width(320.dp))
            } else ScreenHeader("Discover","FIND YOUR NEXT STORY")
        }
        if(!sideSearch) item(key="input") {SearchInput()}
        item(key="filters") { LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            items(listOf("All","Fantasy","Action","Adventure","Comedy","Drama","Supernatural")) { label -> ChoiceChip(label,selected=label==category){vm.filter(label)} }
        } }
        when(val result=results) {
            LoadState.Loading -> item(key="loading") { Column(Modifier.fillMaxWidth().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator(color=Orange);Text("Finding your next story…",color=Muted)
            } }
            is LoadState.Error -> item(key="error") { EmptyState("A little interruption",result.message,"Retry",vm::retrySearch) }
            is LoadState.Content -> {
                if(result.value.isEmpty()) item(key="empty") {EmptyState("No stories found","Try another title or explore all categories.","Reset search"){vm.search("");vm.filter("All")}}
                else {
                    item(key="count") { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text(if(query.isNotBlank()) "Search results" else if(category=="All") "All series" else category,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                        Text("${result.value.size} ${if(connected) "series" else "demo series"}",color=Muted,fontSize=12.sp,modifier=Modifier.semantics { liveRegion=LiveRegionMode.Polite })
                    } }
                    items(result.value.chunked(columns),key={it.joinToString {s->s.id}}) { row ->
                        PosterGridRow(row,columns,origin,"browse",onShow={show,source ->vm.show(show.id,source)})
                    }
                }
            }
        }
    }
    }
}

@Composable
fun DetailsScreen(show: Anime,origin: String,vm: CinemaViewModel,state: LocalState) {
    val wide=LocalWide.current
    val short=LocalCompactHeight.current
    val connected=LocalConnected.current
    val ratings by vm.ratings.collectAsStateWithLifecycle()
    LaunchedEffect(show.id,show.title,show.year,connected) {vm.loadRatings(show.id)}
    val selection by vm.episodeSelection.collectAsStateWithLifecycle()
    val watchlist by vm.connectedWatchlist.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val checkingList by vm.watchlistLoading.collectAsStateWithLifecycle()
    val accountHistory by vm.connectedHistory.collectAsStateWithLifecycle()
    var demoSeason by rememberSaveable(show.id) { mutableIntStateOf(1) }
    val active=selection.takeIf {connected && it.showId==show.id}
    val season=active?.requestedSeason ?: demoSeason
    val rows=active?.episodes ?: vm.catalogue.episodes(show.id,demoSeason)
    val loading=active?.loading ?: false
    val error=active?.error
    val list=rememberLazyListState()
    var anchor by rememberSaveable(show.id) {mutableStateOf<Int?>(null)}
    val displayed=active?.displayedSeason ?: demoSeason
    SideEffect {
        if(anchor!=null && !loading) {list.requestScrollToItem(2,-anchor!!);anchor=null}
    }
    val latest=rows.mapNotNull {episode ->vm.progressFor(episode,state.current)?.let {Triple(episode,it,it.updatedAt)}}
        .filter {!it.second.complete && it.second.positionMs>0}.maxByOrNull {it.third}
    val history = remember(accountHistory, state.current.progress, vm.catalogue, show.id) {
        mergedHistory(accountHistory, state.current.progress, vm.catalogue).filter { it.show.id == show.id }.associateBy { it.episode.id }
    }
    val seriesResume=if(connected) history.values.firstOrNull {!it.progress.complete && it.progress.positionMs>0} else null
    val playable=seriesResume?.episode ?: latest?.first ?: rows.firstOrNull {vm.progressFor(it,state.current)?.complete != true} ?: rows.firstOrNull()
    val canResume=seriesResume!=null || latest!=null
    val inList=if(connected) watchlist.any {it.id==show.id} else show.id in state.current.watchlist
    val screenHeight=LocalQuestPanelSize.current?.height ?: androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    LazyColumn(Modifier.fillMaxSize().background(Oled).navigationBarsPadding().testTag("details-list"),state=list,contentPadding=PaddingValues(bottom=32.dp)) {
        item(key="detail-hero") {
            @Composable fun Copy(modifier: Modifier,includeTitle: Boolean=true) {
                Column(modifier.sharedArtChrome().padding(24.dp),horizontalAlignment=if(wide) Alignment.Start else Alignment.CenterHorizontally,
                    verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    if(includeTitle) {
                        Eyebrow("${if(connected) "SERIES" else "DEMO SERIES"} · ${show.genres.first()}")
                        SeriesTitle(show,fontSize=if(short) 42.sp else 50.sp,lineHeight=52.sp)
                        if(show.subtitle.isNotBlank()) Text(show.subtitle,fontSize=18.sp)
                    }
                    Text(listOfNotNull(show.year.takeIf {it>0}?.toString(),"${show.seasons} seasons",show.genres.joinToString(" · ")).joinToString("  •  "),color=Muted,fontSize=13.sp,
                        textAlign=if(wide) TextAlign.Start else TextAlign.Center)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp,if(wide) Alignment.Start else Alignment.CenterHorizontally),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Action(if(!connected) "Play sample video" else if(canResume) "Resume · S${playable?.season} E${playable?.number}" else "Play episode",Glyph.Play,true,
                            enabled=!connected || playable!=null && !loading) {
                            if(connected) playable?.let {vm.play(it.id)} else vm.play(vm.resumeEpisode(show.id).id)
                        }
                        Action(if(inList) "In Watchlist" else "Add to Watchlist",if(inList) Glyph.Check else Glyph.Plus,
                            selected=inList,enabled=show.id !in busy && (!connected || !checkingList),accessibilityLabel=if(inList) "Remove from Watchlist" else "Add to Watchlist"){vm.toggleList(show.id)}
                        if(connected && canResume && playable!=null) Action("Start over",Glyph.Refresh){vm.play(playable.id,true)}
                    }
                }
            }
            if(wide) Box(Modifier.fillMaxWidth().heightIn(min=if(short) 300.dp else 360.dp).clipToBounds().testTag("series-banner")) {
                Art(show,Modifier.matchParentSize().sharedArt(origin).bannerShade(wide),hero=true)
                IconAction("Back to catalogue",Glyph.Back,Modifier.statusBarsPadding().padding(20.dp).sharedArtChrome()){vm.back()}
                Copy(Modifier.align(Alignment.BottomStart).fillMaxWidth(.6f).statusBarsPadding().padding(top=64.dp))
            } else Column {
                Box(Modifier.fillMaxWidth().aspectRatio(2f/3).clipToBounds().testTag("series-banner").semantics {contentDescription=show.title}) {
                    Art(show,Modifier.fillMaxSize().sharedArt(origin,portraitPoster=true),
                        poster=true,hero=true,scale=ContentScale.Fit)
                    IconAction("Back to catalogue",Glyph.Back,Modifier.statusBarsPadding().padding(16.dp).sharedArtChrome()){vm.back()}
                }
                Copy(Modifier.fillMaxWidth().background(Oled),includeTitle=false)
            }
        }
        item(key="synopsis") {Column(Modifier.padding(horizontal=24.dp).widthIn(max=840.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            ShowRatingsSection(ratings.takeIf {it.showId==show.id} ?: ShowRatings(show.id),demo=!connected) {vm.loadRatings(show.id,retryErrors=true)}
            Text(show.synopsis,color=Muted,fontSize=16.sp,lineHeight=25.sp)
            Text(if(connected) show.languages.joinToString(" · ") else "Demo chapters use the Big Buck Bunny sample film.",color=Muted,fontSize=12.sp,lineHeight=20.sp)
        }}
        stickyHeader(key="seasons") {Column(Modifier.fillMaxWidth().background(Oled).padding(horizontal=24.dp,vertical=8.dp).testTag("season-selector")) {
            Text("Episodes",fontSize=22.sp,fontWeight=FontWeight.Bold)
            LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(vertical=10.dp)) {
                items(show.seasonNumbers.ifEmpty {(1..show.seasons).toList()},key={it}) {value ->
                    ChoiceChip("Season $value",selected=season==value) {
                        anchor=list.layoutInfo.visibleItemsInfo.firstOrNull {it.key=="seasons"}?.offset?.coerceAtLeast(0) ?: 0
                        if(connected) vm.loadSeason(show.id,value) else demoSeason=value
                    }
                }
            }
        }}
        item(key="episode-status") {Row(Modifier.heightIn(min=48.dp).fillMaxWidth().padding(horizontal=24.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
            if(loading) {CircularProgressIndicator(Modifier.size(20.dp),color=Orange,strokeWidth=2.dp);Text("Loading season $season…",color=Muted)}
            else Text("${rows.size} episodes",color=Muted,fontSize=13.sp)
        }}
        if(error!=null) item(key="episode-error") {Column(Modifier.padding(24.dp)) {EmptyState("Couldn’t load this season",error,"Retry"){vm.loadSeason(show.id,season)}}}
        items(rows,key={it.id}) {episode ->
            val progress=vm.progressFor(episode,state.current)
            EpisodeRow(vm, state.activeProfile, episode, progress,
                inHistory = episode.id in history || progress?.complete == true || (progress?.positionMs ?: 0) > 0,
                playLabel = "Play ${episode.title}", enabled = !loading,
                modifier = Modifier.padding(horizontal=24.dp,vertical=6.dp).fillMaxWidth().animateItem().clip(RoundedCornerShape(16.dp)).background(Surface).border(1.dp,Divider,RoundedCornerShape(16.dp))) {
                Box(Modifier.width(if(wide) 172.dp else 104.dp).aspectRatio(16f/9).clip(RoundedCornerShape(8.dp))) {
                    if(episode.thumbnailUrl!=null) RemoteArtwork(episode.thumbnailUrl,Modifier.fillMaxSize())
                    else Art(show,Modifier.fillMaxSize())
                    CinemaIcon(Glyph.Play,modifier=Modifier.align(Alignment.Center))
                    if(progress!=null) LinearProgressIndicator({progress.fraction},Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomCenter),color=Orange)
                }
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text("E${episode.number} · ${episode.title}",fontSize=14.sp,fontWeight=FontWeight.Bold)
                    Text(when {progress?.complete==true->"Watched";progress!=null && progress.positionMs>0->"Resume · ${progress.positionMs/60000} min watched";connected->"${episode.durationMs/60000} min";else->"Big Buck Bunny · Sample film"},color=Muted,fontSize=12.sp)
                }
            }
        }
        item(key="episode-footer") {Spacer(Modifier.height((screenHeight-144.dp-(if(wide) 140.dp else 100.dp)*rows.size).coerceAtLeast(32.dp)))}
    }
}

@Composable
fun MyContentScreen(vm: CinemaViewModel,state: LocalState,origin: String) {
    val connected=LocalConnected.current
    val selected by vm.contentView.collectAsStateWithLifecycle()
    val view=ContentView.fromKey(selected)
    val saved by vm.connectedWatchlist.collectAsStateWithLifecycle()
    val remoteHistory by vm.connectedHistory.collectAsStateWithLifecycle()
    val watchLoading by vm.watchlistLoading.collectAsStateWithLifecycle()
    val watchError by vm.watchlistError.collectAsStateWithLifecycle()
    val historyLoading by vm.historyLoading.collectAsStateWithLifecycle()
    val historyError by vm.historyError.collectAsStateWithLifecycle()
    val more by vm.historyHasMore.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val watchlist=view==ContentView.Watchlist
    val loading=connected && (if(watchlist) watchLoading else historyLoading)
    val error=if(!connected) null else if(watchlist) watchError else historyError
    val shows=if(connected) saved else vm.catalogue.shows.filter {it.id in state.current.watchlist}
    val entries=remember(remoteHistory,state.current.progress,vm.catalogue,connected) {
        mergedHistory(if(connected) remoteHistory else emptyList(),state.current.progress,vm.catalogue)
    }
    val watched=remember(entries) {watchedShows(entries)}
    val empty=if(watchlist) shows.isEmpty() else entries.isEmpty()
    val holder=rememberSaveableStateHolder()
    CatalogueLayout {columns ->
    Column(Modifier.fillMaxSize().padding(horizontal=24.dp)) {
        ScreenHeader("My Content",if(LocalCompactHeight.current) null else "YOUR COLLECTION")
        Row(Modifier.widthIn(max=640.dp).fillMaxWidth().padding(top=8.dp,bottom=12.dp).height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            ContentView.entries.forEach {tab ->
                Action(tab.label,selected=view==tab,modifier=Modifier.weight(1f).fillMaxHeight().testTag("content-tab-${tab.name}")
                    .semantics {this.selected=view==tab;role=Role.Tab}) {vm.selectContentView(tab)}
            }
        }
        holder.SaveableStateProvider("${state.activeProfile}:${view.name}") {
        val listTag="${if(connected) "account" else "content"}-${when(view) {ContentView.History->"history";ContentView.Watchlist->"watchlist";ContentView.WatchedShows->"watched"}}"
        LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag(listTag),state=rememberLazyListState(),contentPadding=PaddingValues(bottom=32.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            item(key="description") {Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(when(view) {
                    ContentView.Watchlist -> if(connected) "Saved series synced with your Crunchyroll Watchlist." else "Your demo Watchlist stays on this device."
                    ContentView.WatchedShows -> "Series with played episodes, most recently watched first."
                    ContentView.History -> if(connected) "Latest played episodes returned by Crunchyroll, most recent first." else "Sample episode progress saved on this device, most recent first."
                },color=Muted,fontSize=13.sp)
                if(connected) Action("Refresh",Glyph.Refresh,enabled=!loading){vm.refreshContent()}
            }}
            if(loading) item(key="loading") {LinearProgressIndicator(Modifier.fillMaxWidth(),color=Orange)}
            if(error!=null) item(key="error") {EmptyState("Couldn’t load ${view.label.lowercase()}",error,"Retry") {if(watchlist) vm.refreshWatchlist() else vm.retryHistory()}}
            if(!loading && error==null && empty) item(key="empty") {
                EmptyState(if(watchlist) "Save your next adventure" else "Your story starts here",
                    if(watchlist) "Use Add to Watchlist on a series to save it here." else if(connected) "Play an episode to see it in My Content." else "Play a demo chapter to start your history.","Browse anime"){vm.navigate("Browse")}
            }
            when(view) {
                ContentView.History -> items(entries,key={it.episode.id}) {entry ->HistoryRow(vm,state.activeProfile,entry)}
                ContentView.WatchedShows -> items(watched.chunked(columns),key={it.joinToString {s->s.show.id}}) {row ->
                    WatchedShowsRow(row,columns,origin,vm)
                }
                ContentView.Watchlist -> items(shows.chunked(columns),key={it.joinToString {s->s.id}}) {row ->
                    PosterGridRow(row,columns,origin,"list",onShow={show,source ->vm.show(show.id,source)}) {show ->
                        Action("Remove",Glyph.Close,modifier=Modifier.fillMaxWidth(),enabled=(if(connected) show.id else "${state.activeProfile}/${show.id}") !in busy,
                            accessibilityLabel="Remove ${show.title} from Watchlist"){vm.toggleList(show.id,false)}
                    }
                }
            }
            // Grouping a page can yield no new series; the next page must remain reachable in both views.
            if(connected && !watchlist && more) item(key="more") {Action("Load more",enabled=!loading){vm.refreshHistory(true)}}
        }
        }
    }
    }
}

@Composable
private fun WatchedShowsRow(row: List<WatchedShow>,columns: Int,origin: String,vm: CinemaViewModel) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        row.forEach {watched ->
            val show=watched.show
            val source="watched-${show.id}"
            val resume=watched.resume ?: watched.latest
            Column(Modifier.weight(1f).testTag("watched-show-${show.id}"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                ShowCard(show,source,origin,progress=watched.latest.progress,caption="Last played · S${watched.latest.episode.season} E${watched.latest.episode.number}",fillWidth=true){vm.show(show.id,source)}
                val action=if(resume.progress.complete) "Watch again" else if(resume.progress.positionMs>0) "Resume" else "Play"
                Action("$action · S${resume.episode.season} E${resume.episode.number}",Glyph.Play,modifier=Modifier.fillMaxWidth(),
                    accessibilityLabel="$action ${show.title} season ${resume.episode.season} episode ${resume.episode.number}"){vm.play(resume.episode.id)}
            }
        }
        repeat((columns-row.size).coerceAtLeast(0)) {Spacer(Modifier.weight(1f))}
    }
}

@Composable
private fun HistoryRow(vm: CinemaViewModel,profileId: String,entry: HistoryEntry) {
    EpisodeRow(vm, profileId, entry.episode, entry.progress, inHistory = true,
        playLabel = "${if(entry.progress.complete) "Watch again" else "Resume"} ${entry.show.title} season ${entry.episode.season} episode ${entry.episode.number}",
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surface),
        playModifier = Modifier.testTag("history-episode-${entry.episode.id}")) {
        Box(Modifier.width(if(LocalWide.current) 180.dp else 96.dp).aspectRatio(16f/9).clip(RoundedCornerShape(8.dp))) {
            if(entry.episode.thumbnailUrl!=null) RemoteArtwork(entry.episode.thumbnailUrl,Modifier.fillMaxSize()) else Art(entry.show,Modifier.fillMaxSize())
            LinearProgressIndicator({entry.progress.fraction},Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomCenter),color=Orange)
        }
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Text(entry.show.title,fontWeight=FontWeight.Bold,fontSize=16.sp)
            Text("S${entry.episode.season} E${entry.episode.number} · ${entry.episode.title}",color=Muted,fontSize=13.sp)
            Text(if(entry.progress.complete) "Watched · Play again" else "Resume at ${entry.progress.positionMs/60000}:${(entry.progress.positionMs/1000%60).toString().padStart(2,'0')}",color=Orange,fontSize=12.sp)
        }
    }
}

package dev.cinema.crunchyroll.ui

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cinema.crunchyroll.data.*
import dev.cinema.crunchyroll.data.account.*
import dev.cinema.crunchyroll.design.*
import dev.cinema.crunchyroll.playback.PlayerScreen
import dev.cinema.crunchyroll.playback.PlayerSystemBars
import dev.cinema.crunchyroll.playback.PlayerOrientation
import dev.cinema.crunchyroll.playback.DisplayCheckScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

val LocalWide = staticCompositionLocalOf { false }
val LocalCompactHeight = staticCompositionLocalOf { false }

@Composable
fun CinemaApp(vm: CinemaViewModel) {
    val context = LocalContext.current
    val tv = (context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager).currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    val storedState by vm.local.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    val loginOpen by vm.loginOpen.collectAsStateWithLifecycle()
    val state=vm.accountPreferenceKey?.let {storedState.copy(activeProfile=it)} ?: storedState
    val ready by vm.ready.collectAsStateWithLifecycle()
    val detail by vm.detail.collectAsStateWithLifecycle()
    val playing by vm.playerEpisode.collectAsStateWithLifecycle()
    val destination by vm.destination.collectAsStateWithLifecycle()
    val origin by vm.origin.collectAsStateWithLifecycle()
    val artworkOriginRounded by vm.artworkOriginRounded.collectAsStateWithLifecycle()
    var notice by remember { mutableStateOf<Notice?>(null) }
    LaunchedEffect(vm) { vm.messages.collectLatest { notice=it;delay(4500);notice=null } }
    BackHandler(enabled=loginOpen || playing!=null || detail!=null || destination!="Home") { if(loginOpen) vm.closeLogin() else vm.back() }
    CinemaTheme(state.current.motion,tv,state.current.haptics) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Oled)) {
            CompositionLocalProvider(LocalConnected provides (account is AccountState.SignedIn),LocalWide provides (maxWidth>=600.dp || tv),LocalCompactHeight provides (maxHeight<500.dp && !tv)) {
                val transition = SceneTransition()
                val holder = rememberSaveableStateHolder()
                SharedTransitionLayout {
                    val shared = this
                    val scene = when {
                        !ready || account is AccountState.Restoring -> "loading"
                        loginOpen -> "login"
                        !state.enteredDemo && account !is AccountState.SignedIn -> "welcome"
                        playing!=null -> "player:$playing"
                        detail!=null -> "detail:$detail"
                        else -> "shell"
                    }
                    PlayerSystemBars(enabled=scene.startsWith("player:"))
                    PlayerOrientation(enabled=scene.startsWith("player:"))
                    val motion=LocalMotion.current
                    AnimatedContent(scene,modifier=Modifier.fillMaxSize(),transitionSpec={
                        // Shared artwork owns its geometry. Translating/scaling its parent caused jumps and double transforms.
                        if(targetState.startsWith("player:")) {
                            // Reveal playback promptly; its feedback animates independently of the native surface.
                            fadeIn(tween(motion.millis(180,140))) togetherWith fadeOut(tween(motion.millis(120,100)))
                        } else if(initialState.startsWith("detail:") || targetState.startsWith("detail:") ||
                            initialState.startsWith("player:")) {
                            fadeIn(tween(motion.millis(460))) togetherWith fadeOut(tween(motion.millis(340)))
                        } else transition
                    },label="app-scenes") { route ->
                        CompositionLocalProvider(LocalSharedScope provides shared,LocalSceneScope provides this,LocalArtworkOriginRounded provides artworkOriginRounded) {
                            when {
                                route=="loading" -> Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp,Alignment.CenterVertically),horizontalAlignment=Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color=Orange)
                                    Text("Restoring your session…",color=Muted)
                                    Action("Continue without signing in"){vm.closeLogin()}
                                }
                                route=="welcome" -> WelcomeScreen(vm::enterDemo,vm::openLogin)
                                route=="login" -> LoginScreen(vm)
                                route.startsWith("player:") -> vm.catalogue.episode(route.removePrefix("player:"))?.let { episode ->
                                    PlayerScreen(episode,vm,state.activeProfile,state.current,onBack={vm.back()})
                                }
                                route.startsWith("detail:") -> vm.catalogue.show(route.removePrefix("detail:"))?.let { show ->
                                    holder.SaveableStateProvider("detail-${show.id}") { DetailsScreen(show,origin,vm,state) }
                                }
                                else -> holder.SaveableStateProvider("shell") { AppShell(vm,state,destination,origin) }
                            }
                        }
                    }
                }
                AnimatedVisibility(notice!=null,modifier=Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal=20.dp,vertical=if(tv) 24.dp else 92.dp),
                    enter=slideInVertically(spring(dampingRatio=.55f)){it}+fadeIn(),exit=slideOutVertically{it}+fadeOut()) {
                    Row(Modifier.widthIn(max=560.dp).clip(RoundedCornerShape(16.dp)).background(Surface).border(1.dp,Divider,RoundedCornerShape(16.dp)).padding(start=16.dp,end=8.dp,top=8.dp,bottom=8.dp),
                        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        CinemaIcon(if(notice?.success==false) Glyph.Info else Glyph.Check,Orange)
                        Text(notice?.text.orEmpty(),Modifier.weight(1f).semantics { liveRegion=LiveRegionMode.Polite },fontSize=14.sp)
                        notice?.undo?.let { undo -> Action(notice?.actionLabel ?: "Undo",onClick={undo();notice=null}) }
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeScreen(onEnter: () -> Unit,onLogin: () -> Unit) {
    val wide = LocalWide.current
    Box(Modifier.fillMaxSize().systemBarsPadding()) {
        Art(DemoCatalogue().shows.first(),Modifier.fillMaxSize().graphicsLayer { alpha=.48f })
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Oled.copy(alpha=.15f),Oled,Oled))))
        Column(Modifier.align(Alignment.Center).widthIn(max=620.dp).verticalScroll(rememberScrollState()).padding(if(wide) 48.dp else 24.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
            Reveal { Wordmark(label="UNOFFICIAL") }
            Reveal(1) { Eyebrow("YOUR NEXT WORLD AWAITS") }
            Reveal(2) { Text("A LITTLE MORE\nEXTRAORDINARY.",fontFamily=DisplayFont,fontWeight=FontWeight.Bold,fontSize=if(wide) 64.sp else 52.sp,lineHeight=if(wide) 60.sp else 50.sp) }
            Reveal(3) { Text("Big stories. True black. A little extra motion.",fontSize=18.sp) }
            Reveal(4) { Text("Sumi is an unofficial Crunchyroll client. Sign in to your Crunchyroll account, or explore the local demo.",color=Muted,fontSize=15.sp,lineHeight=23.sp) }
            Reveal(5) { Action("Sign in",Glyph.Profile,filled=true,modifier=Modifier.fillMaxWidth(),onClick=onLogin) }
            Reveal(6) { Action("Explore the demo",Glyph.Play,modifier=Modifier.fillMaxWidth(),onClick=onEnter) }
        }
    }
}

private val destinations = listOf("Home" to Glyph.Home,"Browse" to Glyph.Search,"My Content" to Glyph.List,"Profile" to Glyph.Profile)

private fun navigationSection(destination: String) = when(destination) {
    "My List", "Watchlist", "History" -> "My Content"
    "Settings", "Display check" -> "Profile"
    else -> destination
}

@Composable
private fun AppShell(vm: CinemaViewModel,state: LocalState,destination: String,origin: String) {
    val account by vm.account.collectAsStateWithLifecycle()
    val tv = LocalTv.current
    val wide = LocalWide.current
    val short = LocalCompactHeight.current
    val section = navigationSection(destination)
    Column(Modifier.fillMaxSize().statusBarsPadding().then(if(tv) Modifier.padding(horizontal=24.dp,vertical=8.dp) else Modifier)) {
        Row(Modifier.fillMaxWidth().heightIn(min=if(tv) 72.dp else 68.dp).padding(horizontal=24.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            Wordmark()
            if(tv) Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) { destinations.forEach { (label,glyph) ->
                Action(label,glyph,selected=label==section,modifier=Modifier.semantics {selected=label==section},onClick={vm.navigate(label)})
            } }
            if(!tv) Box(Modifier.size(48.dp).clip(CircleShape).cinemaClick("Open profile",onClick={vm.navigate("Profile")}),contentAlignment=Alignment.Center) {
                Box(Modifier.size(36.dp).background(AccentSurface,CircleShape).border(1.dp,Orange.copy(alpha=.4f),CircleShape),contentAlignment=Alignment.Center) {
                    Text(if(vm.isConnected) (account as? AccountState.SignedIn)?.account?.name?.take(1).orEmpty() else demoProfiles.first {it.id==state.activeProfile}.initial,color=Orange,fontWeight=FontWeight.Bold,fontSize=14.sp)
                }
            }
        }
        Row(Modifier.weight(1f)) {
            if(wide && !tv) Column(Modifier.width(104.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical=if(short) 4.dp else 12.dp),verticalArrangement=Arrangement.spacedBy(if(short) 4.dp else 16.dp)) {
                destinations.forEach { (label,glyph) -> NavItem(label,glyph,section==label,Modifier.fillMaxWidth()){vm.navigate(label)} }
            }
            val holder=rememberSaveableStateHolder()
            val transition=SceneTransition()
            AnimatedContent(destination,modifier=Modifier.weight(1f).fillMaxHeight(),transitionSpec={transition},label="destinations") { route ->
                holder.SaveableStateProvider(route) {
                    when(route) {
                        "Home" -> HomeScreen(vm,state,origin)
                        "Browse" -> BrowseScreen(vm,origin)
                        "My Content", "My List", "Watchlist", "History" -> MyContentScreen(vm,state,origin)
                        "Settings" -> SettingsScreen(vm,state.current)
                        "Display check" -> DisplayCheckScreen(vm)
                        else -> ProfileScreen(vm,state)
                    }
                }
            }
        }
        if(!wide) BottomNav(section,vm::navigate) else Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun BottomNav(destination: String,onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Oled).navigationBarsPadding()) {
        HorizontalDivider(color=Divider.copy(alpha=.7f))
        Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp)) {
            destinations.forEach { (label,glyph) -> NavItem(label,glyph,destination==label,Modifier.weight(1f)){onSelect(label)} }
        }
    }
}

@Composable
private fun NavItem(label: String,glyph: Glyph,selected: Boolean,modifier: Modifier,onClick: () -> Unit) {
    val color by animateColorAsState(if(selected) Orange else Muted,label="nav-color")
    val background by animateColorAsState(if(selected) AccentSurface else Oled,label="nav-background")
    Column(modifier.heightIn(min=64.dp).clip(RoundedCornerShape(12.dp)).cinemaClick(label,onClick=onClick).semantics { this.selected=selected;role=Role.Tab }.padding(vertical=4.dp),
        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(56.dp,32.dp).background(background,RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center) {
            CinemaIcon(glyph,color,Modifier.size(22.dp))
        }
        Text(label,color=if(selected) Paper else Muted,fontSize=11.sp,lineHeight=16.sp,fontWeight=if(selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

package dev.cinema.crunchyroll.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import dev.cinema.crunchyroll.BuildConfig
import dev.cinema.crunchyroll.R
import dev.cinema.crunchyroll.data.*
import dev.cinema.crunchyroll.design.*
import dev.cinema.crunchyroll.data.account.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ProfileScreen(vm: CinemaViewModel,state: LocalState) {
    if(LocalConnected.current) {ConnectedProfileScreen(vm);return}
    var confirmExit by rememberSaveable { mutableStateOf(false) }
    var about by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=24.dp),contentPadding=PaddingValues(bottom=32.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        item {ScreenHeader("Who’s watching?","YOUR SPACE","Demo profiles · Saved on this device")}
        item {Row(horizontalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.widthIn(max=480.dp).fillMaxWidth()) {
            demoProfiles.forEach { p ->
                ProfileTile(p.name,p.initial,p.id==state.activeProfile,"Select ${p.name} profile",Modifier.weight(1f)) {vm.selectProfile(p.id)}
            }
        } }
        item { Column(Modifier.widthIn(max=800.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            AccountRow("Sign in to Crunchyroll","Connect your real account",Glyph.Profile){vm.openLogin()}
            AccountRow("History","Played episodes in My Content",Glyph.History){vm.openContent(ContentView.History)}
            AccountRow("Settings","Motion, languages & playback",Glyph.Settings){vm.navigate("Settings")}
            AccountRow("About this app","An unofficial, independent project",Glyph.Info){about=true}
            AccountRow("Leave demo","Return to the welcome screen",Glyph.Back){confirmExit=true}
        } }
        item {Text("SUMI · ${BuildConfig.VERSION_NAME}",color=Muted,fontSize=11.sp,letterSpacing=1.sp)}
    }
    if(confirmExit) CinemaDialog("Leave the demo?","Your local profiles, list, preferences, and sample playback progress will be kept on this device.","Leave demo",{confirmExit=false;vm.leaveDemo()},{confirmExit=false})
    if(about) CinemaDialog("About Sumi","Sumi is an independent, unofficial Crunchyroll client. Not affiliated with or endorsed by Crunchyroll. This demo uses fixture data. Anime artwork is included for design reference only. Demo playback uses the Blender Foundation’s Big Buck Bunny (CC BY 3.0). Fonts: DM Sans and Barlow Condensed (SIL OFL).", "Got it",{about=false},{about=false})
}

@Composable
private fun ConnectedProfileScreen(vm: CinemaViewModel) {
    val accountState by vm.account.collectAsStateWithLifecycle()
    val summary=(accountState as? AccountState.SignedIn)?.account ?: return
    val busy by vm.connectedLoading.collectAsStateWithLifecycle()
    var confirmLogout by rememberSaveable {mutableStateOf(false)}
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=24.dp),contentPadding=PaddingValues(bottom=32.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        item {ScreenHeader("${summary.name}’s anime night","YOUR SPACE","Connected to Crunchyroll · Your account, your collection")}
        if(summary.profiles.isNotEmpty()) item {FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            summary.profiles.forEach {profile ->
                val selected=profile.id==summary.profileId
                ProfileTile(profile.name,profile.name.take(1),selected,"Switch to ${profile.name}",Modifier.width(156.dp),enabled=!busy) {
                    if(!selected) vm.switchAccountProfile(profile.id)
                }
            }
        }}
        item {Column(Modifier.widthIn(max=800.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            AccountRow("History","Played episodes in My Content",Glyph.History){vm.openContent(ContentView.History)}
            AccountRow("Watchlist","Saved series in My Content",Glyph.List){vm.openContent(ContentView.Watchlist)}
            AccountRow("Settings","Motion, haptics & account",Glyph.Settings){vm.navigate("Settings")}
            AccountRow("Refresh catalogue","Get the latest available series",Glyph.Refresh){vm.refreshConnected();vm.navigate("Home")}
            AccountRow("Sign out","Remove the account session from this device",Glyph.Back){confirmLogout=true}
        }}
        item {Text("SUMI · ${BuildConfig.VERSION_NAME}\nUnofficial Crunchyroll client. Native Android playback with the stream’s available tracks and quality options.",color=Muted,fontSize=12.sp,lineHeight=20.sp)}
    }
    if(confirmLogout) CinemaDialog("Sign out of Crunchyroll?","Your encrypted session will be removed from this device. Your Crunchyroll watchlist stays on your account.","Sign out",{confirmLogout=false;vm.logout()},{confirmLogout=false})
}

@Composable
private fun ProfileTile(name: String,initial: String,selected: Boolean,label: String,modifier: Modifier=Modifier,
                        enabled: Boolean=true,onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Surface)
        .border(1.dp,if(selected) Orange.copy(alpha=.65f) else Divider,RoundedCornerShape(16.dp))
        .cinemaClick(label,enabled,onClick).semantics {this.selected=selected}.padding(16.dp),
        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(56.dp).background(if(selected) AccentSurface else SurfaceRaised,CircleShape),contentAlignment=Alignment.Center) {
            Text(initial,color=if(selected) Orange else Muted,fontSize=24.sp,fontWeight=FontWeight.Bold)
        }
        Text(name,fontWeight=FontWeight.SemiBold,fontSize=14.sp,lineHeight=20.sp)
        Row(horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically) {
            if(selected) CinemaIcon(Glyph.Check,Orange,Modifier.size(12.dp))
            Text(if(selected) "Watching now" else "Switch profile",color=if(selected) Orange else Muted,fontSize=11.sp,lineHeight=16.sp)
        }
    }
}

@Composable
private fun AccountRow(title: String,subtitle: String,glyph: Glyph,onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface).border(1.dp,Divider,RoundedCornerShape(16.dp))
        .cinemaClick(title,onClick=onClick).padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(40.dp).background(SurfaceRaised,RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center) {CinemaIcon(glyph,Orange,Modifier.size(20.dp))}
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {Text(title,fontWeight=FontWeight.SemiBold,fontSize=15.sp);Text(subtitle,color=Muted,fontSize=12.sp,lineHeight=17.sp)}
        CinemaIcon(Glyph.Chevron,Muted,Modifier.size(18.dp))
    }
}

@Composable
fun SettingsScreen(vm: CinemaViewModel,settings: ProfileData) {
    val tv=LocalTv.current
    val connected=LocalConnected.current
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=24.dp),contentPadding=PaddingValues(top=8.dp,bottom=40.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Action("Back to profile",Glyph.Back){vm.navigate("Profile")}
            ScreenHeader("Settings","MAKE IT YOURS",if(connected) "Preferences for your account profile." else "Preferences for your demo profile.")
        } }
        item {
            SettingsGroup(stringResource(R.string.app_updates), stringResource(R.string.update_installed_version, BuildConfig.VERSION_NAME)) {
                AppUpdateControls(vm.appUpdates)
            }
        }
        item {SettingsGroup("Motion intensity","Set the pace, from expressive to subtle.") {
            FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                MotionLevel.entries.forEach { m -> ChoiceChip(m.name,selected=settings.motion==m){vm.preferences {it.copy(motion=m)}} }
            }
        } }
        item {SettingsGroup("Playback","Choose your preferred languages. Available tracks depend on the episode.") {
            LanguagePreferenceSelector("Preferred audio",settings.audio,LanguagePreferences.audioOptions) {code ->
                vm.preferences {it.copy(audio=code)}
            }
            LanguagePreferenceSelector("Preferred subtitles",settings.subtitles,LanguagePreferences.subtitleOptions) {code ->
                vm.preferences {it.copy(subtitles=code)}
            }
            SettingToggle(if(connected) "Autoplay next episode" else "Autoplay next demo chapter",settings.autoplay){vm.preferences {it.copy(autoplay=!it.autoplay)}}
            SkipIntervalSetting(settings.skipIntervalSeconds) {seconds -> vm.preferences {it.copy(skipIntervalSeconds=seconds)}}
        } }
        item {SettingsGroup("Display check","Play test clips and copy display information to help diagnose playback issues.") {
            Action("Check this display",Glyph.Info){vm.navigate("Display check")}
        }}
        if(!tv) item {SettingsGroup("Feel every action","Light haptic feedback on supported touch devices.") {
            SettingToggle("Touch haptics",settings.haptics){vm.preferences {it.copy(haptics=!it.haptics)}}
        } }
        item {SettingsGroup("Account",if(connected) "Connected to Crunchyroll. Episodes play here using Android’s native media player." else "Connect Crunchyroll to browse its catalogue and manage your real watchlist.") {
            Text(if(connected) "Mode: Connected" else "Mode: Demo · local storage only",color=Orange,fontSize=14.sp)
            if(!connected) Action("Sign in to Crunchyroll",Glyph.Profile,filled=true){vm.openLogin()}
        } }
    }
}

@Composable
private fun SettingsGroup(title: String,body: String,content: @Composable ColumnScope.() -> Unit) {
    Reveal {Column(Modifier.widthIn(max=800.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface).border(1.dp,Divider,RoundedCornerShape(16.dp)).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(title,fontSize=18.sp,fontWeight=FontWeight.Bold)
        Text(body,color=Muted,style=MaterialTheme.typography.bodyMedium)
        content()
    } }
}

@Composable
private fun SettingToggle(label: String,enabled: Boolean,onToggle: () -> Unit) {
    val color by animateColorAsState(if(enabled) Orange else Divider,tween(LocalMotion.current.millis(240)),label="toggle-track")
    val x by animateDpAsState(if(enabled) 24.dp else 4.dp,spring(if(LocalMotion.current==MotionLevel.Maximal) .45f else 1f,400f),label="toggle-thumb")
    Row(Modifier.fillMaxWidth().heightIn(min=56.dp).clip(RoundedCornerShape(12.dp)).cinemaClick(label,onClick=onToggle)
        .semantics {role=Role.Switch;toggleableState=if(enabled) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off;stateDescription=if(enabled) "On" else "Off"}.padding(vertical=8.dp,horizontal=4.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        Text(label,Modifier.weight(1f),fontSize=15.sp)
        Box(Modifier.width(52.dp).height(32.dp).background(color,CircleShape)) {
            Box(Modifier.offset(x=x,y=4.dp).size(24.dp).background(if(enabled) Ink else Paper,CircleShape))
        }
    }
}

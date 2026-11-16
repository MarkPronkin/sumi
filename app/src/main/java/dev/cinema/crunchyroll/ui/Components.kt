package dev.cinema.crunchyroll.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import dev.cinema.crunchyroll.data.*
import dev.cinema.crunchyroll.design.*
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration

@Composable
fun SeriesTitle(show: Anime,fontSize: TextUnit,modifier: Modifier=Modifier,lineHeight: TextUnit=fontSize,
                alignment: Alignment=Alignment.CenterStart,logoOnPhone: Boolean=false,compact: Boolean=false) {
    val centered=alignment==Alignment.Center
    val fallback: @Composable (Modifier) -> Unit = {textModifier -> Text(show.title,textModifier,fontFamily=DisplayFont,fontWeight=FontWeight.Bold,fontSize=fontSize,lineHeight=lineHeight,letterSpacing=1.sp,
        textAlign=if(centered) TextAlign.Center else TextAlign.Start,
        maxLines=if(compact) 2 else Int.MAX_VALUE,overflow=TextOverflow.Ellipsis)}
    val phone=!LocalTv.current && LocalConfiguration.current.smallestScreenWidthDp<600
    val logo=show.logoUrl.takeUnless {phone && !logoOnPhone}
    if(logo.isNullOrBlank()) fallback(modifier)
    else Box(modifier,contentAlignment=alignment) {
        var loaded by remember(logo) {mutableStateOf(false)}
        var failed by remember(logo) {mutableStateOf(false)}
        SubcomposeAsyncImage(model=logo,contentDescription=show.title,
            modifier=Modifier.widthIn(max=if(compact) 260.dp else if(centered) 280.dp else 420.dp).fillMaxWidth()
                .height(if(compact || LocalCompactHeight.current) 64.dp else if(centered) 80.dp else 96.dp).testTag("series-title-logo")
                .semantics {stateDescription=if(loaded) "Title artwork loaded" else if(failed) "Title artwork unavailable" else "Loading title artwork"},
            contentScale=ContentScale.Fit,alignment=alignment,
            onSuccess={loaded=true;failed=false},onError={failed=true;loaded=false},
            loading={fallback(Modifier.fillMaxWidth())},error={fallback(Modifier.fillMaxWidth())})
    }
}

@Composable
fun Action(text: String, glyph: Glyph? = null, filled: Boolean = false, selected: Boolean = false,
           modifier: Modifier = Modifier, enabled: Boolean = true,accessibilityLabel: String=text, onClick: () -> Unit) {
    Action(text,glyph,filled,selected,modifier,enabled,accessibilityLabel,CinemaHaptic.Click,onClick)
}

@Composable
fun Action(text: String, glyph: Glyph? = null, filled: Boolean = false, selected: Boolean = false,
           modifier: Modifier = Modifier, enabled: Boolean = true,accessibilityLabel: String=text,
           haptic: CinemaHaptic, onClick: () -> Unit) {
    val color by animateColorAsState(when {filled -> Orange; selected -> AccentSurface; else -> Surface}, tween(LocalMotion.current.millis(240)),label="action-color")
    val ink = when {filled -> Ink; selected -> Orange; else -> Paper}
    Row(modifier.defaultMinSize(minWidth=48.dp,minHeight=48.dp)
        .clip(RoundedCornerShape(12.dp)).background(color)
        .border(1.dp,when {filled -> color; selected -> Orange.copy(alpha=.45f); else -> Divider},RoundedCornerShape(12.dp))
        .cinemaClick(accessibilityLabel,enabled,haptic,onClick)
        .padding(horizontal=16.dp,vertical=12.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp,Alignment.CenterHorizontally)) {
        if(glyph!=null) AnimatedContent(glyph, transitionSpec={scaleIn() + fadeIn() togetherWith scaleOut() + fadeOut()}, label="icon-morph") {
            CinemaIcon(it,ink,Modifier.size(20.dp))
        }
        Text(text,color=ink,style=MaterialTheme.typography.labelLarge,textAlign=TextAlign.Center)
    }
}

@Composable
fun IconAction(label: String, glyph: Glyph, modifier: Modifier = Modifier, selected: Boolean = false, onClick: () -> Unit) {
    IconAction(label,glyph,modifier,selected,CinemaHaptic.Click,onClick)
}

@Composable
fun IconAction(label: String, glyph: Glyph, modifier: Modifier = Modifier, selected: Boolean = false,
               haptic: CinemaHaptic, onClick: () -> Unit) {
    val background by animateColorAsState(if(selected) AccentSurface else Surface.copy(alpha=.9f),label="icon-action-color")
    Box(modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(background)
        .border(1.dp,if(selected) Orange.copy(alpha=.45f) else Divider,RoundedCornerShape(12.dp))
        .cinemaClick(label,haptic=haptic,onClick=onClick),contentAlignment=Alignment.Center) { CinemaIcon(glyph,if(selected) Orange else Paper,Modifier.size(20.dp)) }
}

/** Selection controls share one treatment, distinct from a primary playback action. */
@Composable
fun ChoiceChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val background by animateColorAsState(if(selected) AccentSurface else SurfaceRaised,label="choice-background")
    Box(modifier.heightIn(min=48.dp).clip(RoundedCornerShape(12.dp)).cinemaClick(text,onClick=onClick)
        .semantics { this.selected=selected; role=Role.RadioButton }.padding(vertical=4.dp),contentAlignment=Alignment.Center) {
        Box(Modifier.heightIn(min=40.dp).clip(RoundedCornerShape(10.dp)).background(background)
            .border(1.dp,if(selected) Orange.copy(alpha=.55f) else Divider,RoundedCornerShape(10.dp))
            .padding(horizontal=14.dp,vertical=10.dp),contentAlignment=Alignment.Center) {
            Text(text,color=if(selected) Orange else Muted,fontSize=13.sp,lineHeight=18.sp,fontWeight=FontWeight.SemiBold)
        }
    }
}

@Composable
fun ScreenHeader(title: String, eyebrow: String? = null, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(top=12.dp,bottom=4.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        eyebrow?.let { Eyebrow(it) }
        Text(title,style=MaterialTheme.typography.headlineLarge)
        subtitle?.let { Text(it,color=Muted,style=MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
fun Wordmark(modifier: Modifier = Modifier,label: String? = null) {
    Row(modifier,verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
        Image(painterResource(dev.cinema.crunchyroll.R.drawable.ic_cinema),null,Modifier.size(32.dp))
        Column(verticalArrangement=Arrangement.spacedBy(2.dp)) { Text(stringResource(dev.cinema.crunchyroll.R.string.app_name),fontWeight=FontWeight.Bold,fontSize=22.sp,letterSpacing=(-1).sp)
            Text(label ?: if(LocalConnected.current) "UNOFFICIAL" else "UNOFFICIAL · DEMO",color=Muted,fontSize=8.sp,lineHeight=12.sp,letterSpacing=1.4.sp) }
    }
}

@Composable
fun Eyebrow(text: String) { Text(text,color=Orange,fontSize=10.sp,lineHeight=14.sp,fontWeight=FontWeight.Bold,letterSpacing=1.6.sp) }

@Composable
fun Heading(title: String, label: String? = null, action: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top=12.dp,bottom=16.dp),verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.SpaceBetween) {
        Text(title,Modifier.weight(1f),fontSize=if(LocalTv.current) 22.sp else 20.sp,fontWeight=FontWeight.Bold)
        if(action!=null) Row(Modifier.heightIn(min=48.dp).clip(RoundedCornerShape(12.dp)).cinemaClick("View all $title",onClick=action).padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(label ?: "View all",color=Muted,fontSize=12.sp); CinemaIcon(Glyph.Chevron,Muted,Modifier.size(16.dp))
        }
    }
}

@Composable
fun Art(show: Anime, modifier: Modifier = Modifier, poster: Boolean = false,hero: Boolean=false,scale: ContentScale=ContentScale.Crop) {
    val candidates=show.imageUrls(poster,hero)
    var attempt by remember(show.id,candidates) {mutableIntStateOf(0)}
    val remote=candidates.getOrNull(attempt)
    if(remote!=null) AsyncImage(model=remote,contentDescription=null,modifier=modifier.background(Surface),
        placeholder=painterResource(dev.cinema.crunchyroll.R.drawable.ic_cinema),contentScale=scale,onError={attempt++})
    else Image(painterResource(if(poster) show.poster else show.artwork),null,modifier,
        contentScale=scale,alignment=if(show.id=="frieren") BiasAlignment(0f,-.1f) else Alignment.TopCenter)
}

@Composable
fun ShowCard(show: Anime, source: String, restore: String, landscape: Boolean = false, progress: Progress? = null,
             caption: String? = null, modifier: Modifier = Modifier, fillWidth: Boolean = false, onClick: () -> Unit) {
    val tv = LocalTv.current
    val request = remember { FocusRequester() }
    LaunchedEffect(restore) { if(tv && restore==source) runCatching { request.requestFocus() } }
    val content: @Composable () -> Unit = {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(if(landscape) 16f/9 else 2f/3).clip(RoundedCornerShape(ArtworkCardCorner)).border(1.dp,Paper.copy(alpha=.08f),RoundedCornerShape(ArtworkCardCorner))) {
                Art(show,Modifier.fillMaxSize().sharedArt(source,rounded=true),poster=!landscape)
                if(landscape) {
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.Transparent,Oled.copy(alpha=.6f)))))
                    CinemaIcon(Glyph.Play,Paper,Modifier.align(Alignment.Center).size(28.dp))
                }
                if(progress!=null) LinearProgressIndicator(progress={progress.fraction},modifier=Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomCenter),color=Orange,trackColor=Paper.copy(alpha=.25f))
            }
            Text(show.title,Modifier.padding(top=10.dp,start=2.dp,end=2.dp),fontSize=14.sp,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis,lineHeight=19.sp)
            Text(caption ?: "${show.genres.firstOrNull().orEmpty()} · Sub | Dub",Modifier.padding(top=4.dp,start=2.dp,end=2.dp,bottom=4.dp),color=Muted,fontSize=12.sp,maxLines=2,overflow=TextOverflow.Ellipsis,lineHeight=17.sp)
        }
    }
    val width = if(landscape) {if(tv) 240.dp else 232.dp} else {if(tv) 150.dp else 142.dp}
    val sizing=modifier.then(if(fillWidth) Modifier.fillMaxWidth() else Modifier.width(width))
    if(tv) {
        androidx.tv.material3.Card(onClick=onClick,modifier=sizing.focusRequester(request),
            colors=androidx.tv.material3.CardDefaults.colors(containerColor=Oled,focusedContainerColor=Oled,contentColor=Paper,focusedContentColor=Paper),
            border=androidx.tv.material3.CardDefaults.border(focusedBorder=androidx.tv.material3.Border(BorderStroke(2.dp,Orange))),
            scale=androidx.tv.material3.CardDefaults.scale(focusedScale=if(LocalMotion.current==MotionLevel.Reduced) 1f else 1.065f)) { content() }
    } else Box(sizing.clip(RoundedCornerShape(12.dp)).cinemaClick("Open ${show.title}",
        haptic=if(landscape) CinemaHaptic.Click else CinemaHaptic.SeriesOpen,onClick=onClick)) {content()}
}

@Composable
fun CatalogueLayout(content: @Composable (columns: Int) -> Unit) {
    val fontScale=androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val minimum=(if(LocalTv.current) 128.dp else 148.dp)*fontScale
        val columns=((maxWidth-48.dp+16.dp)/(minimum+16.dp)).toInt().coerceIn(1,6)
        content(columns)
    }
}

@Composable
fun PosterGridRow(shows: List<Anime>,columns: Int,origin: String,sourcePrefix: String,
                  onShow: (Anime,String) -> Unit,footer: (@Composable (Anime) -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        shows.forEach {show ->
            val source="$sourcePrefix-${show.id}"
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                ShowCard(show,source,origin,fillWidth=true){onShow(show,source)}
                footer?.invoke(show)
            }
        }
        repeat((columns-shows.size).coerceAtLeast(0)) {Spacer(Modifier.weight(1f))}
    }
}

@Composable
fun EmptyState(title: String, body: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Reveal {
        Column(Modifier.fillMaxWidth().padding(vertical=40.dp),verticalArrangement=Arrangement.spacedBy(16.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Box(Modifier.size(64.dp).background(Surface,CircleShape),contentAlignment=Alignment.Center) {CinemaIcon(Glyph.Search,Orange,Modifier.size(28.dp))}
            Text(title,fontSize=22.sp,fontWeight=FontWeight.Bold)
            Text(body,color=Muted,modifier=Modifier.widthIn(max=380.dp),textAlign=androidx.compose.ui.text.style.TextAlign.Center)
            if(action!=null && onAction!=null) Action(action,filled=true,onClick=onAction)
        }
    }
}

@Composable
fun CinemaDialog(title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest=onDismiss) {
        Reveal {
            Column(Modifier.widthIn(max=440.dp).clip(RoundedCornerShape(24.dp)).background(Surface).padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                Eyebrow("SUMI · UNOFFICIAL")
                Text(title,fontSize=24.sp,fontWeight=FontWeight.Bold)
                Text(body,color=Muted)
                Action(confirm,filled=true,modifier=Modifier.fillMaxWidth(),onClick=onConfirm)
                Action("Close",modifier=Modifier.fillMaxWidth(),onClick=onDismiss)
            }
        }
    }
}

package dev.cinema.crunchyroll.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.cinema.crunchyroll.BuildConfig
import dev.cinema.crunchyroll.R
import dev.cinema.crunchyroll.data.MotionLevel
import dev.cinema.crunchyroll.design.*

@Composable
internal fun WelcomeScreen(vm: CinemaViewModel, showLogin: Boolean) {
    var aboutOpen by rememberSaveable { mutableStateOf(false) }
    val motion = LocalMotion.current
    val tv = LocalTv.current
    BoxWithConstraints(Modifier.fillMaxSize().background(Oled)) {
        val landscape = maxWidth > maxHeight
        val sidePadding = when {
            tv -> 52.dp
            maxWidth >= 600.dp -> 48.dp
            else -> 26.dp
        }
        val contentWidth = if (landscape) (maxWidth * .53f).coerceAtMost(480.dp) else 480.dp
        val compact = maxHeight < 500.dp
        WelcomeBackdrop(landscape, showLogin)

        Column(Modifier.fillMaxSize().safeDrawingPadding()
            .padding(horizontal = sidePadding, vertical = if (compact) 8.dp else 16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Wordmark(label = "UNOFFICIAL")
                if (showLogin) IconAction("Back", Glyph.Back, onClick = vm::closeLogin)
                else IconAction(stringResource(R.string.welcome_about), Glyph.Info) { aboutOpen = true }
            }
            AnimatedContent(
                targetState = showLogin,
                modifier = Modifier.fillMaxWidth().weight(1f),
                transitionSpec = {
                    val duration = motion.millis(520, 300)
                    val direction = if (targetState) 1 else -1
                    val arrive = fadeIn(tween(duration))
                    val leave = fadeOut(tween(motion.millis(180, 120)))
                    when {
                        motion == MotionLevel.Reduced -> arrive togetherWith leave
                        landscape -> (arrive + slideInHorizontally(tween(duration)) { direction * it / 9 }) togetherWith
                            (leave + slideOutHorizontally(tween(duration)) { -direction * it / 14 })
                        else -> (arrive + slideInVertically(tween(duration)) { direction * it / 9 }) togetherWith
                            (leave + slideOutVertically(tween(duration)) { -direction * it / 14 })
                    }
                },
                label = "welcome-to-login",
            ) { login ->
                Box(Modifier.fillMaxSize(), contentAlignment = if (landscape) Alignment.CenterStart else Alignment.BottomCenter) {
                    Column(Modifier.widthIn(max = contentWidth).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(top = 12.dp, bottom = if (compact) 8.dp else 20.dp)) {
                        if (login) LoginForm(vm)
                        else WelcomeIntro(compact = compact, landscape = landscape, onLogin = vm::openLogin)
                    }
                }
            }
        }
    }
    if (aboutOpen && !showLogin) WelcomeInfo(
        onDismiss = { aboutOpen = false },
        onDemo = { aboutOpen = false; vm.enterDemo() },
    )
}

@Composable
private fun WelcomeBackdrop(landscape: Boolean, showLogin: Boolean) {
    val motion = LocalMotion.current
    val progress = animateFloatAsState(if (showLogin) 1f else 0f, tween(motion.millis(600, 300)), label = "welcome-art")
    Box(Modifier.fillMaxWidth().fillMaxHeight(if (landscape) 1f else .81f).clipToBounds().drawWithCache {
        val shade = if (landscape) Brush.horizontalGradient(
            0f to Oled.copy(alpha = .98f), .24f to Oled.copy(alpha = .94f),
            .46f to Oled.copy(alpha = .78f), .7f to Oled.copy(alpha = .12f), 1f to Color.Transparent,
        ) else Brush.verticalGradient(
            0f to Oled.copy(alpha = .65f), .20f to Color.Transparent, .56f to Color.Transparent,
            .80f to Oled.copy(alpha = .62f), 1f to Oled,
        )
        onDrawWithContent {
            drawContent()
            drawRect(shade)
        }
    }) {
        AsyncImage(
            model = R.drawable.welcome_coast, contentDescription = null, contentScale = ContentScale.Crop,
            alignment = BiasAbsoluteAlignment(.45f, 0f),
            modifier = Modifier.fillMaxSize().graphicsLayer {
                val zoom = if (motion == MotionLevel.Reduced) 1f else 1f + .055f * progress.value
                scaleX = zoom
                scaleY = zoom
                alpha = 1f - (if (landscape) .23f else .70f) * progress.value
            },
        )
    }
}

@Composable
private fun WelcomeIntro(compact: Boolean, landscape: Boolean, onLogin: () -> Unit) {
    val signIn = stringResource(R.string.welcome_sign_in)
    val titleSize = if (compact) 54.sp else 76.sp
    Text(buildAnnotatedString {
        append(stringResource(R.string.welcome_heading))
        append('\n')
        withStyle(SpanStyle(color = Orange)) { append(stringResource(R.string.app_name)); append('.') }
    }, modifier = Modifier.semantics { heading() }, fontFamily = DisplayFont, fontWeight = FontWeight.Bold,
        fontSize = titleSize, lineHeight = if (compact) 49.sp else 68.sp, letterSpacing = (-1.5).sp)
    Spacer(Modifier.height(if (compact) 10.dp else 16.dp))
    Text(stringResource(R.string.welcome_description), color = Paper.copy(alpha = .82f),
        fontSize = 16.sp, lineHeight = 24.sp, modifier = Modifier.widthIn(max = 330.dp))
    Spacer(Modifier.height(if (compact) 16.dp else 28.dp))
    val focus = remember { FocusRequester() }
    val tv = LocalTv.current
    val buttonWidth = if (landscape) Modifier.widthIn(max = 320.dp) else Modifier
    Row(buttonWidth.fillMaxWidth().heightIn(min = 56.dp).focusRequester(focus).cinemaClick(signIn, onClick = onLogin)
        .clip(RoundedCornerShape(14.dp)).background(Orange).padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(signIn, color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        CinemaIcon(Glyph.Back, Ink, Modifier.size(20.dp).scale(-1f, 1f))
    }
    LaunchedEffect(tv) { if (tv) focus.requestFocus() }
}

@Composable
private fun WelcomeInfo(onDismiss: () -> Unit, onDemo: () -> Unit) {
    CinemaWindowDialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(24.dp)
        Column(Modifier.widthIn(max = 400.dp).fillMaxWidth().clip(shape).background(Surface)
            .border(1.dp, Divider, shape).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.welcome_about), Modifier.weight(1f).semantics { heading() }, fontFamily = DisplayFont,
                    fontSize = 34.sp, fontWeight = FontWeight.Bold)
                IconAction("Close", Glyph.Close, onClick = onDismiss)
            }
            Text(stringResource(R.string.welcome_about_description), color = Muted, style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider(color = Divider)
            VersionDetail(stringResource(R.string.welcome_version), BuildConfig.VERSION_NAME)
            VersionDetail(stringResource(R.string.welcome_build), BuildConfig.VERSION_CODE.toString())
            HorizontalDivider(color = Divider)
            Text(stringResource(R.string.welcome_demo_description),
                color = Muted, style = MaterialTheme.typography.bodyMedium)
            Action(stringResource(R.string.welcome_demo), Glyph.Play, modifier = Modifier.fillMaxWidth(), onClick = onDemo)
        }
    }
}

@Composable
private fun VersionDetail(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Muted)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

package dev.cinema.crunchyroll.design

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.cinema.crunchyroll.data.MotionLevel
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

val LocalSharedScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
val LocalSceneScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }
val LocalArtworkOriginRounded = staticCompositionLocalOf { true }
val ArtworkCardCorner = 12.dp

@Composable
fun Modifier.sharedArt(key: String, rounded: Boolean = false): Modifier {
    val shared = LocalSharedScope.current
    val scene = LocalSceneScope.current
    val motion = LocalMotion.current
    val restingCorner = if(rounded) ArtworkCardCorner else 0.dp
    return if (shared != null && scene != null && motion == MotionLevel.Maximal) with(shared) {
        val content = rememberSharedContentState(key)
        val otherCorner = if(!rounded && LocalArtworkOriginRounded.current) ArtworkCardCorner else 0.dp
        val corner by scene.transition.animateDp(
            transitionSpec={spring(dampingRatio=1f,stiffness=180f,visibilityThreshold=.01.dp)},
            label="artwork-corner-$key",
        ) { if(it==EnterExitState.Visible) restingCorner else otherCorner }
        this@sharedArt.sharedBounds(content, scene,
            enter=fadeIn(tween(200,delayMillis=80)),exit=fadeOut(tween(160)),
            resizeMode=SharedTransitionScope.ResizeMode.RemeasureToBounds,
            boundsTransform = { _, _ -> spring(dampingRatio = 1f, stiffness = 180f) })
            // Parent clips are left behind in the overlay. Both images must carry the
            // same animated shape, including when the transition reverses mid-flight.
            .clip(RoundedCornerShape(if(content.isMatchFound) corner else restingCorner))
    } else clip(RoundedCornerShape(restingCorner))
}

/** Header chrome must share the overlay layer; otherwise lifted artwork covers its gradient and controls. */
@Composable
fun Modifier.sharedArtChrome(): Modifier {
    val shared=LocalSharedScope.current ?: return this
    val scene=LocalSceneScope.current ?: return this
    val motion=LocalMotion.current
    if(motion!=MotionLevel.Maximal) return this
    val opacity=scene.transition.animateFloat(
        transitionSpec={tween(180,delayMillis=if(targetState==EnterExitState.Visible) 60 else 0)},label="artwork-chrome-opacity",
    ) {if(it==EnterExitState.Visible) 1f else 0f}
    return with(shared) {
        this@sharedArtChrome.renderInSharedTransitionScopeOverlay(zIndexInOverlay=1f)
            .graphicsLayer {alpha=opacity.value}
    }
}

@Composable
fun Modifier.cinemaClick(label: String, enabled: Boolean = true, onClick: () -> Unit): Modifier {
    return cinemaClick(label, enabled, CinemaHaptic.Click, onClick)
}

/** Keep the original positional/trailing-lambda API while allowing one specific effect per action. */
@Composable
fun Modifier.cinemaClick(label: String, enabled: Boolean = true, haptic: CinemaHaptic, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    val tv = LocalTv.current
    val motion = LocalMotion.current
    val feedback = rememberCinemaHaptics()
    val scale by animateFloatAsState(
        if (motion == MotionLevel.Reduced) 1f else if (pressed) .95f else if (focused && tv) 1.065f else 1f,
        if (motion == MotionLevel.Reduced) tween(90) else spring(if (motion == MotionLevel.Maximal) .42f else .8f, 420f), label = "press-focus")
    val ring by animateFloatAsState(if(focused) 1f else 0f, tween(motion.millis(160)), label = "focus-ring")
    // Reduced motion needs no transform layer; keep these controls in the normal draw pass.
    return then(if(motion==MotionLevel.Reduced && enabled) Modifier else Modifier.graphicsLayer {
            scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else .45f
        })
        .border(if(focused) 2.dp else 0.dp, Orange.copy(alpha=ring), RoundedCornerShape(12.dp))
        .onFocusChanged { focused = it.isFocused }
        .semantics { contentDescription = label }
        .clickable(enabled = enabled, role = Role.Button, interactionSource = source, indication = LocalIndication.current) {
            feedback.perform(haptic)
            onClick()
        }
}

@Composable
fun Reveal(index: Int = 0, content: @Composable () -> Unit) {
    val motion = LocalMotion.current
    var visible by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { if(motion==MotionLevel.Maximal) delay((index * 45L).coerceAtMost(180));visible=true }
    AnimatedVisibility(visible,
        enter = if(motion==MotionLevel.Reduced) fadeIn(tween(90)) else fadeIn(tween(motion.millis(380))) +
            slideInVertically(tween(motion.millis(460))) { if(motion==MotionLevel.Maximal) 44 else 0 },
        exit = fadeOut(tween(90))) { content() }
}

@Composable
fun Burst(trigger: Int, modifier: Modifier = Modifier) {
    val motion = LocalMotion.current
    val amount = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if(trigger>0 && motion==MotionLevel.Maximal) {amount.snapTo(0f);amount.animateTo(1f,tween(720,easing=FastOutSlowInEasing))}
    }
    Canvas(modifier.size(64.dp)) {
        val p = amount.value
        if(p < 1f) {
            drawCircle(Orange.copy(alpha=(1-p)*.7f),radius=size.minDimension*.5f*p,style=androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
            repeat(10) { i ->
                val angle = i*Math.PI/5
                val radius = size.width*.48f*p
                drawCircle(Orange.copy(alpha=1-p),2.dp.toPx()*(1-p),center+androidx.compose.ui.geometry.Offset(cos(angle).toFloat()*radius,sin(angle).toFloat()*radius))
            }
        }
    }
}

@Composable
fun SceneTransition(): ContentTransform {
    val m = LocalMotion.current
    return (fadeIn(tween(m.millis(420))) + slideInHorizontally(tween(m.millis(500))) { if(m==MotionLevel.Maximal) it/12 else 0 }) togetherWith
        (fadeOut(tween(m.millis(230))) + scaleOut(tween(m.millis(420)), targetScale=if(m==MotionLevel.Maximal) .94f else 1f))
}

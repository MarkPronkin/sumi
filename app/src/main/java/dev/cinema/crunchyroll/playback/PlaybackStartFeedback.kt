package dev.cinema.crunchyroll.playback

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import dev.cinema.crunchyroll.data.MotionLevel
import dev.cinema.crunchyroll.design.LocalMotion
import dev.cinema.crunchyroll.design.LocalTv
import dev.cinema.crunchyroll.design.Oled
import dev.cinema.crunchyroll.design.Orange

private data class PlaybackCue(val serial: Int, val entry: Boolean)

/** A draw-only sibling of the native surface; never owns input, focus, or playback timing. */
@Composable
internal fun PlaybackStartFeedback(
    sessionKey: String,
    episodeId: String,
    player: Player,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val enabledNow by rememberUpdatedState(enabled)
    var entryConsumed by rememberSaveable(sessionKey) { mutableStateOf(false) }
    var serial by remember(sessionKey) { mutableIntStateOf(0) }
    var cue by remember(sessionKey) { mutableStateOf<PlaybackCue?>(null) }
    val gate = remember(player, sessionKey) {
        PlaybackFeedbackGate(initiallyPaused = entryConsumed && player.currentMediaItem?.mediaId == episodeId &&
            !player.playWhenReady && player.playbackState == Player.STATE_READY)
    }

    LaunchedEffect(sessionKey, enabled) {
        if (!enabled) {
            cue = null
            gate.reset()
        } else if (!entryConsumed) {
            // Save before starting: rotation/restoration never replays a consumed entry.
            entryConsumed = true
            cue = PlaybackCue(++serial, entry = true)
        }
    }
    DisposableEffect(player, sessionKey, lifecycle) {
        val listener = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (gate.onPlayIntent(playWhenReady,
                        userRequest = reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
                        foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED),
                        matchingEpisode = player.currentMediaItem?.mediaId == episodeId,
                        enabled = enabledNow)) {
                    cue = PlaybackCue(++serial, entry = false)
                } else if (!playWhenReady) cue = null
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) cue = null
        }
        player.addListener(listener)
        lifecycle.addObserver(observer)
        onDispose {
            player.removeListener(listener)
            lifecycle.removeObserver(observer)
        }
    }
    key(sessionKey) {
        PlaybackStartAnimation(cue?.serial ?: 0, cue?.entry == true, modifier, enabled)
    }
}

/** Finite progress also scales the fade/hold with Android's animator duration setting. */
@Composable
internal fun PlaybackStartAnimation(
    trigger: Int,
    entry: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val motion = LocalMotion.current
    val tv = LocalTv.current
    val progress = remember { Animatable(1f) }
    var visible by remember { mutableStateOf(false) }
    var consumedTrigger by remember { mutableIntStateOf(0) }
    LaunchedEffect(trigger, enabled, motion) {
        visible = false
        if (trigger <= consumedTrigger || !enabled) return@LaunchedEffect
        consumedTrigger = trigger
        if (coroutineContext[MotionDurationScale]?.scaleFactor == 0f) return@LaunchedEffect
        progress.snapTo(0f)
        visible = true
        try {
            val duration = when (motion) {
                MotionLevel.Maximal -> if (entry) 560 else 440
                MotionLevel.Standard -> 320
                MotionLevel.Reduced -> 160
            }
            progress.animateTo(1f, tween(duration, easing = LinearEasing))
        } finally { visible = false }
    }
    if (!visible || !enabled) return
    // Entry sits above the loading indicator; a later resume confirms at the center.
    // Reduced uses that same fixed position with an opacity change only.
    Canvas(modifier.offset(y = if (entry) (-76).dp else 0.dp).size(if (tv) 112.dp else 96.dp)
        .clearAndSetSemantics { testTag = "playback-start-feedback" }) {
        val p = progress.value
        val opacity = minOf(p / .10f, (1f - p) / .46f, 1f).coerceIn(0f, 1f)
        val settle = FastOutSlowInEasing.transform((p / .36f).coerceIn(0f, 1f))
        val badgeScale = when (motion) {
            MotionLevel.Maximal -> .80f + .20f * settle
            MotionLevel.Standard -> .94f + .06f * settle
            MotionLevel.Reduced -> 1f
        }
        if (motion == MotionLevel.Maximal) {
            val expansion = FastOutSlowInEasing.transform(p)
            drawCircle(Orange.copy(alpha = opacity * (1f - expansion) * .55f),
                radius = size.minDimension * (.31f + .18f * expansion), style = Stroke(1.5.dp.toPx()))
        }
        scale(badgeScale) {
            val radius = size.minDimension * .30f
            drawCircle(Oled.copy(alpha = opacity * .88f), radius)
            drawCircle(Orange.copy(alpha = opacity * .65f), radius, style = Stroke(1.dp.toPx()))
            val unit = size.minDimension
            val triangle = Path().apply {
                moveTo(center.x - unit * .065f, center.y - unit * .115f)
                lineTo(center.x + unit * .125f, center.y)
                lineTo(center.x - unit * .065f, center.y + unit * .115f)
                close()
            }
            drawPath(triangle, Orange.copy(alpha = opacity))
        }
    }
}

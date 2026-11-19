package dev.cinema.crunchyroll.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.decode.DataSource
import coil3.request.ImageRequest
import dev.cinema.crunchyroll.data.MotionLevel
import dev.cinema.crunchyroll.design.*

private val PlaceholderSurface = Brush.linearGradient(listOf(SurfaceRaised, Surface, Oled))

@Composable
internal fun RemoteArtwork(
    url: String,
    modifier: Modifier = Modifier,
    scale: ContentScale = ContentScale.Crop,
    reuseCachedPoster: Boolean = false,
    onError: () -> Unit = {},
) {
    val model = if (reuseCachedPoster) {
        val context = LocalContext.current
        remember(context, url) {
            ImageRequest.Builder(context).data(url).placeholderMemoryCacheKey(url).build()
        }
    } else url
    var state by remember(url) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
    val motion = LocalMotion.current
    val success = state as? AsyncImagePainter.State.Success
    val fadeMillis = when {
        success == null || success.result.dataSource == DataSource.MEMORY_CACHE -> 0
        motion == MotionLevel.Reduced -> 0
        else -> motion.millis(240, 160)
    }

    Box(modifier.background(Surface), propagateMinConstraints = true) {
        AsyncImage(model = model, contentDescription = null, contentScale = scale, onState = {
            state = it
            if (it is AsyncImagePainter.State.Error) onError()
        })
        AnimatedVisibility(
            visible = state is AsyncImagePainter.State.Loading && state.painter == null,
            modifier = Modifier.matchParentSize(),
            enter = EnterTransition.None,
            exit = fadeOut(tween(fadeMillis)),
        ) {
            Spacer(Modifier.fillMaxSize().loadingSheen().background(PlaceholderSurface))
        }
    }
}

@Composable
internal fun Modifier.loadingSheen(onText: Boolean = false): Modifier {
    val motion = LocalMotion.current
    if (motion == MotionLevel.Reduced) return this
    val phase = rememberInfiniteTransition(label = "artwork-loading").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1450, delayMillis = 220, easing = LinearEasing)),
        label = "highlight-position",
    )
    val mask = if (onText) Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen } else Modifier
    return then(mask).drawWithCache {
        val bandWidth = size.width * .55f
        val slope = .22f
        val band = Brush.linearGradient(
            0f to Orange.copy(alpha = 0f),
            .34f to Paper.copy(alpha = .06f),
            .58f to Orange.copy(alpha = if (onText) .65f else .18f),
            1f to Orange.copy(alpha = 0f),
            end = Offset(bandWidth, bandWidth * slope),
        )
        val start = -bandWidth * (1f + slope * slope)
        val end = size.width + size.height * slope
        val blend = if (onText) BlendMode.SrcAtop else BlendMode.SrcOver
        onDrawWithContent {
            drawContent()
            val x = start + (end - start) * phase.value
            // Move the cached gradient through the original drawing bounds.
            translate(left = x) {
                drawRect(band, topLeft = Offset(-x, 0f), size = size, blendMode = blend)
            }
        }
    }
}

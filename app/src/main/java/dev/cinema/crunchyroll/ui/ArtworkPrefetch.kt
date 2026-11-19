package dev.cinema.crunchyroll.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.decode.BlackholeDecoder
import coil3.network.HttpException
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Scale
import dev.cinema.crunchyroll.data.*
import dev.cinema.crunchyroll.data.account.ConnectedCatalogue
import dev.cinema.crunchyroll.data.account.ServiceException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext
import java.io.IOException

private const val LIBRARY_REQUEST_LIMIT = 24

/** One budget per signed-in profile session. All scheduling decisions run on the main thread. */
internal class ArtworkPrefetcher(private val context: Context,
                                 private val networkAllowed: (Boolean) -> Boolean = context::allowsArtworkPrefetch) {
    private val loader by lazy { SingletonImageLoader.get(context) }
    private val libraryAttempts = mutableSetOf<String>()
    private var retryAfter = 0L

    private fun canPrefetch(allowMetered: Boolean) =
        SystemClock.elapsedRealtime() >= retryAfter && networkAllowed(allowMetered)

    private fun backOffOnFailure(error: Throwable?) {
        val status = when (error) {
            is HttpException -> error.response.code
            is ServiceException -> error.status
            else -> null
        }
        if (status == 429 || (status != null && status >= 500) || (status == null && error is IOException)) {
            retryAfter = SystemClock.elapsedRealtime() + 60_000
        }
    }

    private suspend fun execute(request: ImageRequest) {
        val result = loader.execute(request)
        if (result is ErrorResult) backOffOnFailure(result.throwable)
    }

    suspend fun spotlight(repo: ConnectedCatalogue, id: String, width: Int, allowMetered: Boolean,
                          stillWanted: () -> Boolean) {
        fun wanted() = stillWanted() && canPrefetch(allowMetered)
        if (!wanted()) return
        try {
            repo.featured(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            backOffOnFailure(e)
        }
        val show = repo.show(id) ?: return
        val banner = show.imageUrls(hero = true).firstOrNull()
        if (banner != null && wanted()) {
            val pixels = width.coerceIn(2, 1920)
            execute(ImageRequest.Builder(context).data(banner).size(pixels, pixels * 9 / 16)
                .precision(Precision.INEXACT).scale(Scale.FILL).build())
        }
        if (!show.logoUrl.isNullOrBlank() && wanted()) {
            execute(ImageRequest.Builder(context).data(show.logoUrl).size(640, 360)
                .precision(Precision.INEXACT).scale(Scale.FIT).build())
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    suspend fun library(urls: List<String>) {
        val remaining = LIBRARY_REQUEST_LIMIT - libraryAttempts.size
        if (urls.isEmpty() || remaining == 0) return
        delay(2_000) // Give visible images and the adjacent spotlight first use of the connection.
        if (!canPrefetch(allowMetered = false)) return
        val attempted = libraryAttempts.toSet()
        val missing = try {
            withContext(Dispatchers.IO) {
                val cache = loader.diskCache ?: return@withContext emptyList()
                urls.asSequence().filter { url ->
                    ensureActive()
                    url !in attempted && cache.openSnapshot(url)?.use { true } != true
                }.take(remaining).toList()
            }
        } catch (_: IOException) {
            return
        }
        for (url in missing) {
            delay(750)
            if (libraryAttempts.size >= LIBRARY_REQUEST_LIMIT || !canPrefetch(allowMetered = false)) return
            if (!libraryAttempts.add(url)) continue
            execute(ImageRequest.Builder(context).data(url).memoryCachePolicy(CachePolicy.DISABLED)
                .decoderFactory(BlackholeDecoder.Factory()).build())
        }
    }
}

private fun Context.allowsArtworkPrefetch(allowMetered: Boolean): Boolean {
    val connectivity = getSystemService(ConnectivityManager::class.java)
    val network = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
    return network.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
        (allowMetered || network.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
}

@Composable
internal fun PrefetchSpotlight(repo: ConnectedCatalogue, prefetcher: ArtworkPrefetcher, targets: List<String>, width: Int) {
    val neighbours by rememberUpdatedState(targets)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(prefetcher, lifecycle, width) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // Finish an in-flight fetch so a slide becoming visible can join it. Drop obsolete queued work.
            snapshotFlow { neighbours }.conflate().collect { window ->
                delay(200)
                for ((index, id) in window.withIndex()) {
                    if (window != neighbours) break
                    prefetcher.spotlight(repo, id, width, allowMetered = index == 0) { window == neighbours }
                }
            }
        }
    }
}

@Composable
internal fun PrefetchLibrary(vm: CinemaViewModel, profile: ProfileData) {
    val repo = vm.catalogue as? ConnectedCatalogue ?: return
    val history by vm.connectedHistory.collectAsStateWithLifecycle()
    val watchlist by vm.connectedWatchlist.collectAsStateWithLifecycle()
    val urls = remember(repo, history, profile.progress, watchlist) {
        val watched = mergedHistory(history, profile.progress, repo).distinctBy { it.show.id }
        libraryArtworkUrls(watched.map { repo.show(it.show.id) ?: it.show }, watchlist.map { repo.show(it.id) ?: it })
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val prefetcher = vm.artworkPrefetcher
    LaunchedEffect(prefetcher, urls, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { prefetcher.library(urls) }
    }
}

package dev.cinema.crunchyroll

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.network.DeDupeConcurrentRequestStrategy
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.Call
import okhttp3.OkHttpClient

class CinemaApplication : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: Context) = artworkImageLoader(context)
}

@OptIn(ExperimentalCoilApi::class)
internal fun artworkImageLoader(context: Context, client: () -> Call.Factory = ::OkHttpClient): ImageLoader =
    ImageLoader.Builder(context).components {
        add(OkHttpNetworkFetcherFactory(callFactory = client, concurrentRequestStrategy = ::DeDupeConcurrentRequestStrategy))
    }.build()

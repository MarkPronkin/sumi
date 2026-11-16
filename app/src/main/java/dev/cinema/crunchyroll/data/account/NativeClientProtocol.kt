package dev.cinema.crunchyroll.data.account

import dev.cinema.crunchyroll.BuildConfig

/** Pinned public Android-client protocol identifiers. No user account material belongs here. */
object NativeClientProtocol {
    const val AUTHORIZATION="Basic c3pybGY4eHJsd2dlMjJteW0xemc6RzBJRHB6aFN3SUNiVFlEaVMyZEdNd2ljVHc4ZVVNSTA="
    val USER_AGENT: String by lazy {"Crunchyroll/ANDROIDTV/3.81.0_22375 (Android ${android.os.Build.VERSION.RELEASE ?: "unknown"}; en-US; Sumi/${BuildConfig.VERSION_NAME} unofficial)"}
    // Password grants use the application's OAuth identity, independently of the playback compatibility profile.
    val SIGN_IN_USER_AGENT="Sumi/${BuildConfig.VERSION_NAME} (Unofficial Android client)"
    const val PLAYBACK_PATH="tv/android_tv"
}

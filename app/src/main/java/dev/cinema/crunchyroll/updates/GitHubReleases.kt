package dev.cinema.crunchyroll.updates

import dev.cinema.crunchyroll.R
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

private const val REPOSITORY = "MarkPronkin/sumi"
internal class UpdateException(val messageId: Int) : IOException()

internal data class AppRelease(
    val version: String,
    val url: HttpUrl,
    val size: Long,
    val sha256: String?,
)

internal class GitHubReleases(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.MINUTES)
        .followSslRedirects(false)
        .build(),
) {
    suspend fun latestUpdate(installedVersion: String): AppRelease? = runInterruptible(Dispatchers.IO) {
        val request = Request.Builder().url("https://api.github.com/repos/$REPOSITORY/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2026-03-10")
            .header("User-Agent", "Sumi-Android")
            .build()
        val call = client.newCall(request)
        call.timeout().timeout(12, TimeUnit.SECONDS)
        val release = call.execute().use { response ->
            if (response.code == 404) throw UpdateException(R.string.update_no_public_release)
            if (!response.isSuccessful) throw IOException("GitHub releases HTTP ${response.code}")
            try {
                json.decodeFromString<Release>(response.body.string())
            } catch (_: SerializationException) {
                throw UpdateException(R.string.update_invalid_release)
            }
        }
        val installed = Version.parse(installedVersion) ?: throw UpdateException(R.string.update_invalid_release)
        val version = Version.parse(release.tag)?.takeIf { it.stable }
            ?: throw UpdateException(R.string.update_invalid_release)
        if (version <= installed) return@runInterruptible null

        val apks = release.assets.filter {
            it.state == "uploaded" && it.size > 0 && it.name.endsWith(".apk", ignoreCase = true)
        }
        val apk = apks.singleOrNull { it.name == "sumi.apk" } ?: apks.singleOrNull()
            ?: throw UpdateException(R.string.update_no_apk)
        val url = "https://github.com/$REPOSITORY/releases/download/".toHttpUrl().newBuilder()
            .addPathSegment(release.tag).addPathSegment(apk.name).build()
        val sha256 = apk.digest?.let {
            if (!sha256Pattern.matches(it)) throw UpdateException(R.string.update_invalid_release)
            it.removePrefix("sha256:")
        }
        AppRelease(release.tag.removePrefix("v"), url, apk.size, sha256)
    }

    suspend fun download(release: AppRelease, destination: File, progress: (Int) -> Unit) {
        runInterruptible(Dispatchers.IO) {
            val request = Request.Builder().url(release.url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("GitHub download HTTP ${response.code}")
                val digest = release.sha256?.let { MessageDigest.getInstance("SHA-256") }
                var total = 0L
                var lastPercent = 0
                response.body.byteStream().use { input ->
                    destination.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count == -1) break
                            total += count
                            if (total > release.size) throw UpdateException(R.string.update_corrupt_download)
                            output.write(buffer, 0, count)
                            digest?.update(buffer, 0, count)
                            val percent = (100.0 * total / release.size).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                progress(percent)
                            }
                        }
                    }
                }
                val sha256 = digest?.digest()?.joinToString("") { "%02x".format(it) }
                if (total != release.size || !sha256.equals(release.sha256, ignoreCase = true)) {
                    throw UpdateException(R.string.update_corrupt_download)
                }
            }
        }
    }

    @Serializable
    private data class Release(@SerialName("tag_name") val tag: String, val assets: List<Asset>)

    @Serializable
    private data class Asset(val name: String, val state: String, val size: Long, val digest: String? = null)

    private data class Version(val major: Int, val minor: Int, val patch: Int, val stable: Boolean) : Comparable<Version> {
        override fun compareTo(other: Version): Int =
            compareValuesBy(this, other, Version::major, Version::minor, Version::patch, Version::stable)

        companion object {
            private val pattern = Regex("""v?([0-9]+)\.([0-9]+)\.([0-9]+)(-[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?""")

            fun parse(value: String): Version? {
                val match = pattern.matchEntire(value) ?: return null
                val (major, minor, patch, prerelease) = match.destructured
                return Version(
                    major.toIntOrNull() ?: return null,
                    minor.toIntOrNull() ?: return null,
                    patch.toIntOrNull() ?: return null,
                    stable = prerelease.isEmpty(),
                )
            }
        }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        val sha256Pattern = Regex("sha256:[0-9a-fA-F]{64}")
    }
}

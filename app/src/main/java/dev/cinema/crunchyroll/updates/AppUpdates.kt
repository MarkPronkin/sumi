package dev.cinema.crunchyroll.updates

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import dev.cinema.crunchyroll.BuildConfig
import dev.cinema.crunchyroll.R
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal sealed interface UpdateState {
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class CheckFailed(val messageId: Int) : UpdateState
    data class Available(val release: AppRelease, val error: Int? = null) : UpdateState
    data class Downloading(val release: AppRelease, val percent: Int) : UpdateState
    data class Ready(val release: AppRelease, val apk: File, val error: Int? = null) : UpdateState
}

internal class AppUpdates(
    private val application: Application,
    private val scope: CoroutineScope,
) {
    private val releases = GitHubReleases()
    private val status = MutableStateFlow<UpdateState>(UpdateState.Checking)
    val state = status.asStateFlow()
    private var job: Job? = null

    fun check(onAvailable: (AppRelease) -> Unit = {}) {
        if (job?.isActive == true) return
        val knownRelease = when (val current = status.value) {
            is UpdateState.Available -> current.release
            is UpdateState.Ready -> current.release
            else -> null
        }
        if (knownRelease == null) status.value = UpdateState.Checking
        job = scope.launch {
            val release = try {
                releases.latestUpdate(BuildConfig.VERSION_NAME)
            } catch (error: IOException) {
                if (knownRelease == null || error is UpdateException) {
                    status.value = UpdateState.CheckFailed((error as? UpdateException)?.messageId ?: R.string.update_check_failed)
                }
                return@launch
            }
            if (release != null && release == knownRelease) return@launch
            status.value = release?.let { UpdateState.Available(it) } ?: UpdateState.UpToDate
            release?.let(onAvailable)
        }
    }

    fun download() {
        val release = (status.value as? UpdateState.Available)?.release ?: return
        job?.cancel()
        status.value = UpdateState.Downloading(release, 0)
        job = scope.launch {
            try {
                val apk = withContext(Dispatchers.IO) { stageApk(release) }
                status.value = UpdateState.Ready(release, apk)
            } catch (error: IOException) {
                status.value = UpdateState.Available(release, (error as? UpdateException)?.messageId ?: R.string.update_download_failed)
            }
        }
    }

    private suspend fun stageApk(release: AppRelease): File {
        val directory = File(application.cacheDir, "app_updates")
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Could not create update directory")
        val temporary = File.createTempFile("download-", ".apk", directory)
        try {
            releases.download(release, temporary) { percent -> status.value = UpdateState.Downloading(release, percent) }
            @Suppress("DEPRECATION")
            val info = application.packageManager.getPackageArchiveInfo(temporary.path, 0)
            if (info == null || info.packageName != application.packageName ||
                info.versionName?.removePrefix("v") != release.version ||
                PackageInfoCompat.getLongVersionCode(info) <= BuildConfig.VERSION_CODE
            ) {
                throw UpdateException(R.string.update_incompatible_apk)
            }
            val apk = File(directory, "sumi-update.apk")
            if (!temporary.renameTo(apk)) throw IOException("Could not save downloaded update")
            return apk
        } finally {
            temporary.delete()
        }
    }

    fun install(context: Context) {
        val ready = status.value as? UpdateState.Ready ?: return
        if (!ready.apk.isFile) {
            status.value = UpdateState.Available(ready.release, R.string.update_expired)
            return
        }
        val uri = FileProvider.getUriForFile(context, "${application.packageName}.updates", ready.apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(intent)
            status.value = ready.copy(error = null)
        } catch (_: ActivityNotFoundException) {
            status.value = ready.copy(error = R.string.update_installer_unavailable)
        } catch (_: SecurityException) {
            status.value = ready.copy(error = R.string.update_installer_unavailable)
        }
    }
}

class UpdateFileProvider : FileProvider()

package dev.cinema.crunchyroll.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.cinema.crunchyroll.BuildConfig
import dev.cinema.crunchyroll.R
import dev.cinema.crunchyroll.design.Glyph
import dev.cinema.crunchyroll.design.Muted
import dev.cinema.crunchyroll.design.Orange
import dev.cinema.crunchyroll.design.Surface
import dev.cinema.crunchyroll.updates.AppUpdates
import dev.cinema.crunchyroll.updates.UpdateState

@Composable
internal fun AppUpdateControls(updates: AppUpdates) {
    val context = LocalContext.current
    val state by updates.state.collectAsStateWithLifecycle()
    val current = state
    val description = when (current) {
        UpdateState.Checking -> stringResource(R.string.update_checking)
        UpdateState.UpToDate -> stringResource(R.string.update_current)
        is UpdateState.CheckFailed -> stringResource(current.messageId)
        is UpdateState.Available -> stringResource(R.string.update_available, current.release.version)
        is UpdateState.Downloading -> stringResource(R.string.update_downloading, current.release.version, current.percent)
        is UpdateState.Ready -> stringResource(R.string.update_ready, current.release.version)
    }
    Text(description, color = Muted, style = MaterialTheme.typography.bodyMedium)
    when (current) {
        UpdateState.Checking -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = Orange)
        UpdateState.UpToDate, is UpdateState.CheckFailed ->
            Action(stringResource(R.string.update_check), Glyph.Refresh) { updates.check() }
        is UpdateState.Available -> {
            current.error?.let { Text(stringResource(it), color = Muted, style = MaterialTheme.typography.bodyMedium) }
            Action(stringResource(R.string.update_download), filled = true, onClick = updates::download)
        }
        is UpdateState.Downloading ->
            LinearProgressIndicator(progress = { current.percent / 100f }, modifier = Modifier.fillMaxWidth(), color = Orange)
        is UpdateState.Ready -> {
            current.error?.let { Text(stringResource(it), color = Muted, style = MaterialTheme.typography.bodyMedium) }
            Action(stringResource(R.string.update_install), filled = true) { updates.install(context) }
        }
    }
}

@Composable
internal fun AppUpdatesDialog(updates: AppUpdates, onDismiss: () -> Unit) {
    CinemaWindowDialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.widthIn(max = 440.dp).clip(RoundedCornerShape(24.dp)).background(Surface).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.app_updates), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.update_installed_version, BuildConfig.VERSION_NAME), color = Muted)
            AppUpdateControls(updates)
            Action("Close", onClick = onDismiss)
        }
    }
}

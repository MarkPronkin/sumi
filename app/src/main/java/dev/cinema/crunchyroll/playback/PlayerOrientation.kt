package dev.cinema.crunchyroll.playback

import android.content.pm.ActivityInfo
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalConfiguration
import dev.cinema.crunchyroll.design.LocalTv
import dev.cinema.crunchyroll.ui.LocalQuestPanelSize

/** Keep the request outside episode crossfades and retain the original policy through rotation. */
@Composable
fun PlayerOrientation(enabled: Boolean) {
    val activity=LocalActivity.current ?: return
    val phone=!LocalTv.current && LocalConfiguration.current.smallestScreenWidthDp<600
    if(!enabled || !phone || LocalQuestPanelSize.current != null) return
    val previousOrientation=rememberSaveable {activity.requestedOrientation}
    DisposableEffect(activity) {
        activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            // A new activity inherits the landscape request; its saved state retains the original policy.
            if(!activity.isChangingConfigurations) activity.requestedOrientation=previousOrientation
        }
    }
}

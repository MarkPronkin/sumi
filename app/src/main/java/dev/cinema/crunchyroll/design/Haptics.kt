package dev.cinema.crunchyroll.design

import android.content.res.Configuration
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalView

@Composable
fun rememberCinemaHaptics(enabled: Boolean = true): CinemaHaptics {
    val view = LocalView.current
    val profileEnabled by rememberUpdatedState(enabled && LocalHaptics.current)
    val television by rememberUpdatedState(LocalTv.current ||
        view.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION)
    return remember(view) {
        CinemaHaptics({ profileEnabled }, { television }) { effect ->
            // No ignore-setting flags or direct Vibrator calls: Android owns system preferences,
            // hardware support and effect fallback. A declined soft tick must stay silent.
            view.performHapticFeedback(platformHapticEffect(effect, Build.VERSION.SDK_INT))
        }
    }
}

// The explicit SDK argument keeps all fallback recipes testable without changing the device SDK.
@android.annotation.SuppressLint("InlinedApi")
internal fun platformHapticEffect(effect: CinemaHaptic, sdk: Int): Int = when (effect) {
    CinemaHaptic.Click -> if (sdk >= 27) HapticFeedbackConstants.TEXT_HANDLE_MOVE else HapticFeedbackConstants.CLOCK_TICK
    CinemaHaptic.SeriesOpen -> if (sdk >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK
    CinemaHaptic.TimelineTick -> if (sdk >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else HapticFeedbackConstants.CLOCK_TICK
}

package dev.cinema.crunchyroll

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dev.cinema.crunchyroll.ui.CinemaApp
import dev.cinema.crunchyroll.ui.CinemaViewModel
import dev.cinema.crunchyroll.ui.QuestPanel

open class MainActivity : ComponentActivity() {
    private val cinema by viewModels<CinemaViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val headset = isQuestHeadset(Build.MANUFACTURER, Build.MODEL, Build.DEVICE)
        if (headset) {
            useQuestLauncher()
            // A first launch or a cached shortcut can still point at the mobile activity.
            // Stay in the task the shell is hosting, including when it recreates the display.
            if (this !is QuestPanelActivity) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        enableEdgeToEdge()
        setContent {
            if (headset || this is QuestPanelActivity) QuestPanel { CinemaApp(cinema) }
            else CinemaApp(cinema)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) cinema.checkForLaunchUpdate()
    }
}

package dev.cinema.crunchyroll

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dev.cinema.crunchyroll.ui.CinemaApp
import dev.cinema.crunchyroll.ui.CinemaViewModel

class MainActivity : ComponentActivity() {
    private val cinema by viewModels<CinemaViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CinemaApp(cinema) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) cinema.checkForLaunchUpdate()
    }
}

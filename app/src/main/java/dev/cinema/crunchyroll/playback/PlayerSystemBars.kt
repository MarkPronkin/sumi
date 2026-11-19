package dev.cinema.crunchyroll.playback

import android.view.ViewTreeObserver
import android.view.Window
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.cinema.crunchyroll.ui.CinemaWindowDialog

/** Owned by the player route, so episode crossfades cannot restore the bars mid-playback. */
@Composable
fun PlayerSystemBars(enabled: Boolean) {
    if(enabled) ImmersivePlayerWindow(LocalActivity.current?.window,restoreOnDispose=true)
}

/** Dialogs have their own window and must request immersive mode independently. */
@Composable
internal fun PlayerDialog(onDismissRequest: ()->Unit,content: @Composable ()->Unit) {
    CinemaWindowDialog(onDismissRequest=onDismissRequest,properties=DialogProperties(decorFitsSystemWindows=false)) {
        ImmersivePlayerWindow((LocalView.current.parent as? DialogWindowProvider)?.window,restoreOnDispose=false)
        content()
    }
}

@Composable
private fun ImmersivePlayerWindow(window: Window?,restoreOnDispose: Boolean) {
    if(window==null) return
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(window,lifecycle,restoreOnDispose) {
        val decor=window.decorView
        val controller=WindowCompat.getInsetsController(window,decor)
        val previousBehavior=controller.systemBarsBehavior
        fun hideSystemBars() {
            controller.systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        val focusListener=ViewTreeObserver.OnWindowFocusChangeListener {focused ->if(focused) hideSystemBars()}
        val viewTreeObserver=decor.viewTreeObserver
        viewTreeObserver.addOnWindowFocusChangeListener(focusListener)
        val lifecycleObserver=LifecycleEventObserver {_,event ->
            if(event==Lifecycle.Event.ON_RESUME) hideSystemBars()
        }
        lifecycle.addObserver(lifecycleObserver)
        hideSystemBars()
        onDispose {
            lifecycle.removeObserver(lifecycleObserver)
            if(viewTreeObserver.isAlive) viewTreeObserver.removeOnWindowFocusChangeListener(focusListener)
            else decor.viewTreeObserver.removeOnWindowFocusChangeListener(focusListener)
            if(restoreOnDispose) {
                controller.systemBarsBehavior=previousBehavior
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

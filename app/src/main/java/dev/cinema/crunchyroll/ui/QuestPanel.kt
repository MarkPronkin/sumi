package dev.cinema.crunchyroll.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.cinema.crunchyroll.design.Oled

internal val LocalQuestPanelSize = compositionLocalOf<DpSize?> { null }

@Composable
internal fun QuestPanel(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Oled), contentAlignment = Alignment.Center) {
        BoxWithConstraints(Modifier.aspectRatio(16f / 9f).clipToBounds(), propagateMinConstraints = true) {
            CompositionLocalProvider(LocalQuestPanelSize provides DpSize(maxWidth, maxHeight), content = content)
        }
    }
}

/** Android dialogs have their own window and don't inherit the content area's layout constraints. */
@Composable
internal fun CinemaWindowDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    val panel = LocalQuestPanelSize.current
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        if (panel == null) content()
        else Box(Modifier.sizeIn(maxWidth = panel.width, maxHeight = panel.height).clipToBounds()) { content() }
    }
}

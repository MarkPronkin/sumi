package dev.cinema.crunchyroll.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.cinema.crunchyroll.data.LanguageOption
import dev.cinema.crunchyroll.data.LanguagePreferences
import dev.cinema.crunchyroll.design.*

/** A bounded list keeps the settings compact and every option reachable by touch or D-pad. */
@Composable
internal fun LanguagePreferenceSelector(
    title: String,
    value: String,
    options: List<LanguageOption>,
    onSelect: (String) -> Unit,
) {
    var expanded by rememberSaveable(value) { mutableStateOf(false) }
    val selected = LanguagePreferences.optionFor(value, options)
        ?: LanguageOption(value, LanguagePreferences.label(value))
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(12.dp))
            .background(SurfaceRaised).cinemaClick("$title: ${selected.label}", onClick = { expanded = true })
            .semantics { stateDescription = selected.label }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = Muted, fontSize = 13.sp)
            Text(selected.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        CinemaIcon(Glyph.Chevron, Muted, Modifier.size(18.dp))
    }
    if (expanded) {
        val choices = if (selected in options) options else listOf(selected) + options
        val index = choices.indexOf(selected).coerceAtLeast(0)
        val list = rememberLazyListState(initialFirstVisibleItemIndex = index)
        val focus = remember { FocusRequester() }
        val tv = LocalTv.current
        Dialog(onDismissRequest = { expanded = false }) {
            val inputMode = LocalInputModeManager.current
            val windowFocused = LocalWindowInfo.current.isWindowFocused
            Column(
                Modifier.widthIn(max = 480.dp).fillMaxWidth().heightIn(max = 560.dp)
                    .clip(RoundedCornerShape(24.dp)).background(Surface).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(title, Modifier.semantics { heading() }, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Available languages vary by episode.", color = Muted, fontSize = 13.sp)
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f, fill = false).selectableGroup().testTag("language-options"),
                    state = list, contentPadding = PaddingValues(4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(choices, key = { it.code }) { option ->
                        val checked = option == selected
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                .then(if (checked) Modifier.focusRequester(focus) else Modifier)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (checked) AccentSurface else SurfaceRaised)
                                .border(1.dp, if (checked) Orange.copy(alpha = .55f) else Divider, RoundedCornerShape(12.dp))
                                .cinemaClick(option.label, onClick = { onSelect(option.code); expanded = false })
                                .semantics { this.selected = checked; role = Role.RadioButton }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(option.label, Modifier.weight(1f), color = if (checked) Orange else Paper,
                                fontSize = 15.sp, lineHeight = 21.sp)
                            if (checked) CinemaIcon(Glyph.Check, Orange, Modifier.size(18.dp))
                        }
                    }
                }
                Action("Close", modifier = Modifier.fillMaxWidth(), onClick = { expanded = false })
            }
            LaunchedEffect(tv, windowFocused) {
                if (tv && windowFocused) {
                    inputMode.requestInputMode(InputMode.Keyboard)
                    withFrameNanos { }
                    focus.requestFocus()
                }
            }
        }
    }
}

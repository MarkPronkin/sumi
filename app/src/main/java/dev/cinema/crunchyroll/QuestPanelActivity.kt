package dev.cinema.crunchyroll

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import java.util.Locale

class QuestPanelActivity : MainActivity()

/** The legacy Quest shell reads the Android launcher component before it creates the outer panel. */
internal fun Context.useQuestLauncher() {
    val quest = ComponentName(this, "${packageName}.QuestLauncher")
    val mobile = ComponentName(this, "${packageName}.AndroidLauncher")
    fun setLauncher(component: ComponentName, state: Int) {
        if (packageManager.getComponentEnabledSetting(component) != state) {
            packageManager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
        }
    }
    // Enable first so the package always has a launcher, even if interrupted between the two writes.
    setLauncher(quest, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
    setLauncher(mobile, PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
}

internal fun isQuestHeadset(manufacturer: String, model: String, device: String): Boolean {
    if (!manufacturer.equals("Oculus", ignoreCase = true) && !manufacturer.equals("Meta", ignoreCase = true)) return false
    val name = model.lowercase(Locale.ROOT).removePrefix("oculus ").removePrefix("meta ")
    return name in setOf("quest", "quest 1", "quest 2", "quest 3") ||
        device.lowercase(Locale.ROOT) in setOf("monterey", "hollywood", "eureka")
}

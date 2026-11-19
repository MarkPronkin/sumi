package dev.cinema.crunchyroll

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class QuestLauncherTest {
    @Test fun androidLauncherResolvesDirectlyToTheLandscapeActivityAfterQuestDetection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pm = context.packageManager
        val mobile = ComponentName(context, "${context.packageName}.AndroidLauncher")
        val quest = ComponentName(context, "${context.packageName}.QuestLauncher")
        val original = listOf(mobile, quest).associateWith(pm::getComponentEnabledSetting)
        val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName)
        fun launcher() = pm.queryIntentActivities(launch, 0).single().activityInfo
        try {
            for (component in original.keys) {
                pm.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP)
            }
            assertEquals(MainActivity::class.java.name, launcher().targetActivity)
            context.useQuestLauncher()
            context.useQuestLauncher()
            assertEquals(QuestPanelActivity::class.java.name, launcher().targetActivity)
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, launcher().screenOrientation)
        } finally {
            for ((component, state) in original) pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
        }
    }
}

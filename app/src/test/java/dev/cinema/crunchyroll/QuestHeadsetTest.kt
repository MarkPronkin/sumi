package dev.cinema.crunchyroll

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestHeadsetTest {
    @Test fun recognizesQuestLaunchersByModelOrHardwareName() {
        assertTrue(isQuestHeadset("Oculus", "Quest", "monterey"))
        assertTrue(isQuestHeadset("Oculus", "Oculus Quest 2", "unknown"))
        assertTrue(isQuestHeadset("Meta", "Meta Quest 3", "unknown"))
        assertTrue(isQuestHeadset("Oculus", "VR Headset", "hollywood"))
        assertTrue(isQuestHeadset("Oculus", "VR Headset", "eureka"))
        assertFalse(isQuestHeadset("OnePlus", "Quest 3", "eureka"))
        assertFalse(isQuestHeadset("Google", "Android TV", "emulator"))
        assertFalse(isQuestHeadset("Oculus", "Go", "pacific"))
    }
}

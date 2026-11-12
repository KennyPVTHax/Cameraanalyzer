package tech.cameraanalyzer

import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.annotation.Config

class AnalyzerSmokeTest {
    @Test @Config(sdk = [35])
    fun launchesOfflineAnalyzer() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertTrue(Shadows.shadowOf(activity).nextStartedActivity == null)
    }
}

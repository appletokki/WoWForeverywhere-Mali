package app.gamenative.ui.screen.wow

import app.gamenative.utils.AppUpdater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaliSupportTest {
    @Test
    fun maliRenderer_isMali() {
        assertTrue(isMaliGpu("Mali-G720-Immortalis MC12", "mt6989"))
        assertTrue(isMaliGpu("", "mt6989"))
        assertFalse(isMaliGpu("Adreno (TM) 750", "qcom"))
        assertFalse(isMaliGpu("", "qcom"))
    }

    @Test
    fun maliProfile_usesSystemDriverThroughWrapper() {
        assertTrue(MALI_PROFILE.isMali)
        assertEquals(MALI_GRAPHICS_DRIVER_CONFIG, graphicsDriverConfig(MALI_PROFILE))
        assertFalse(GpuProfile.ADRENO_740.isMali)
        assertTrue(graphicsDriverConfig(GpuProfile.ADRENO_740).startsWith("version=${GpuProfile.ADRENO_740.driver},"))
    }

    @Test
    fun maliEnv_dropsTurnipHintAndTurnsOffPatch() {
        val env = "A=1 PULSE_LATENCY_MSEC=144 FD_DEV_FEATURES=enable_tp_ubwc_flag_hint=1"
        assertEquals("A=1 PULSE_LATENCY_MSEC=144 WRAPPER_NO_PATCH_OPCONSTCOMP=1", maliEnvVars(MALI_PROFILE, env))
        assertEquals(env, maliEnvVars(GpuProfile.ADRENO_740, env))
    }

    @Test
    fun maliIgnoresDriverPickedInSettings() {
        val picked = GpuProfile.ADRENO_740.copy(driver = "Turnip-V32-RP6sched")
        assertEquals(MALI_PROFILE, maliLaunchGpu(MALI_PROFILE) { MALI_PROFILE.copy(driver = "Turnip-V32-RP6sched") })
        assertEquals(picked, maliLaunchGpu(GpuProfile.ADRENO_740) { picked })
    }

    @Test
    fun maliReleaseTag_comparesByVersion() {
        assertTrue(AppUpdater.isNewer("v1.3.0-mali", "1.1.3"))
        assertFalse(AppUpdater.isNewer("v1.3.0-mali", "1.3.0"))
    }
}

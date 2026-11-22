package app.gamenative.ui.screen.wow

import app.gamenative.utils.AppUpdater
import app.gamenative.wow.GraphicsDrivers
import app.gamenative.wow.MaliPanVk
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
    fun stockDriver_decodesBcnOnTheCpuIntoAstc4x4() {
        val config = graphicsDriverConfig(MALI_PROFILE)
        assertTrue(MALI_PROFILE.isMali)
        assertTrue(config.startsWith("version=System,"))
        assertTrue("bcnEmulationType=software" in config)
        assertTrue(config.endsWith("quality=high"))
        assertFalse(GpuProfile.ADRENO_740.isMali)
        assertTrue(graphicsDriverConfig(GpuProfile.ADRENO_740).startsWith("version=${GpuProfile.ADRENO_740.driver},"))
    }

    @Test
    fun lightTextures_useAstc8x8() {
        val light = MALI_PROFILE.copy(maliOptions = MaliOptions(lightTextures = true))
        assertTrue(graphicsDriverConfig(light).endsWith("quality=low"))
    }

    @Test
    fun panvk_isLoadedThroughAdrenotoolsAndDecodesBcnItself() {
        val panvk = maliLaunchProfile(MALI_PROFILE, MaliPanVk.DRIVER_NAME, MaliOptions())
        assertTrue(panvk.isMaliAddedDriver)
        assertEquals(MALI_GRAPHICS_DRIVER, panvk.graphicsDriver)
        val config = graphicsDriverConfig(panvk)
        assertTrue(config.startsWith("version=${MaliPanVk.DRIVER_NAME},"))
        assertTrue("bcnEmulation=none" in config)
    }

    @Test
    fun turnipPickedOnMali_fallsBackToStockDriver() {
        GraphicsDrivers.BUILT_IN.forEach { builtIn ->
            val profile = maliLaunchProfile(MALI_PROFILE, builtIn, MaliOptions())
            assertEquals(GraphicsDrivers.SYSTEM, profile.driver)
            assertFalse(profile.isMaliAddedDriver)
        }
    }

    @Test
    fun maliEnv_dropsTurnipHintAndTurnsOffPatch() {
        val env = "A=1 PULSE_LATENCY_MSEC=144 FD_DEV_FEATURES=enable_tp_ubwc_flag_hint=1"
        assertEquals("A=1 PULSE_LATENCY_MSEC=144 WRAPPER_NO_PATCH_OPCONSTCOMP=1", maliEnvVars(MALI_PROFILE, env))
        assertEquals(env, maliEnvVars(GpuProfile.ADRENO_740, env))
    }

    @Test
    fun lowLatency_writesDxvkMaxFrameLatency() {
        assertEquals("dxgi.maxFrameLatency = 1\n", maliDxvkConf(MaliOptions(lowLatency = true)))
        assertEquals("", maliDxvkConf(MaliOptions()))
    }

    @Test
    fun maliReleaseTag_comparesByVersion() {
        assertTrue(AppUpdater.isNewer("v1.3.0-mali", "1.1.3"))
        assertFalse(AppUpdater.isNewer("v1.3.0-mali", "1.3.0"))
    }
}

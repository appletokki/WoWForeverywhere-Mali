package app.gamenative.ui.screen.wow

import android.content.Context
import android.os.Build
import app.gamenative.wow.GraphicsDrivers
import com.winlator.core.GPUInformation
import com.winlator.xenvironment.ImageFs
import java.io.File
import timber.log.Timber

/*
 * Mali (MediaTek Dimensity) support for the Mali build.
 *
 * Everything Mali-specific lives here so that merging a new upstream release touches as few upstream lines as
 * possible: WoWForeverScreen.kt only carries one-line hooks that call into this file (search for "Mali hook").
 */

/** GameNative's wrapper over the device's own Vulkan driver. Turnip ("Wrapper") is Adreno-only. */
internal const val MALI_GRAPHICS_DRIVER = "Wrapper-gamenative"

/**
 * MediaTek Dimensity / Mali: runs on the device's own Vulkan driver through GameNative's wrapper. Tested on a
 * Galaxy Tab S10 Ultra (Dimensity 9300+, Mali-G720): BCn textures are decoded on the CPU and the wrapper's
 * OpConstantComposite SPIR-V patch is off, because either one makes vkCreateShaderModule fail on Mali and WoW quits
 * right after creating its device. 720p to start.
 */
internal val MALI_PROFILE = GpuProfile(
    "System", "", "1280x720", "Mali", "MediaTek Dimensity / Mali (실험적)",
    graphicsDriver = MALI_GRAPHICS_DRIVER,
)

/**
 * The player's Mali options (Settings > Mali graphics).
 *
 * [lowLatency]: DXVK's dxgi.maxFrameLatency = 1, so the game runs at most one frame ahead of the GPU. MaliBridge
 * measured it on Mali at about 1% fps for a frame less of input lag when the GPU is the limit.
 * [lightTextures]: BCn textures the stock driver can't sample are transcoded to ASTC 8x8 instead of 4x4: less
 * memory, bandwidth and conversion time, blurrier textures. PanVK decodes BCn itself, so it doesn't apply there.
 */
data class MaliOptions(val lowLatency: Boolean = false, val lightTextures: Boolean = false)

internal object MaliSettings {
    private const val PREFS = "wow_settings"
    private const val LOW_LATENCY = "mali_low_latency"
    private const val LIGHT_TEXTURES = "mali_light_textures"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun options(context: Context) = MaliOptions(
        lowLatency = prefs(context).getBoolean(LOW_LATENCY, false),
        lightTextures = prefs(context).getBoolean(LIGHT_TEXTURES, false),
    )

    fun setLowLatency(context: Context, on: Boolean) = prefs(context).edit().putBoolean(LOW_LATENCY, on).apply()

    fun setLightTextures(context: Context, on: Boolean) = prefs(context).edit().putBoolean(LIGHT_TEXTURES, on).apply()
}

/** A Mali GPU by its GL renderer name, or a MediaTek board ("mt6989") when the renderer can't be read. */
internal fun isMaliGpu(renderer: String, hardware: String): Boolean =
    renderer.contains("mali", ignoreCase = true) ||
        (renderer.isBlank() && hardware.startsWith("mt", ignoreCase = true))

internal fun isMaliDevice(context: Context): Boolean =
    isMaliGpu(runCatching { GPUInformation.getRenderer(context) }.getOrNull().orEmpty(), Build.HARDWARE)

/** GPUs the game's graphics stack runs on: Adreno through Turnip, Mali through the system driver. */
internal fun isSupportedGpu(context: Context): Boolean {
    val renderer = runCatching { GPUInformation.getRenderer(context) }.getOrNull().orEmpty()
    return renderer.contains("adreno", ignoreCase = true) || isMaliGpu(renderer, Build.HARDWARE) ||
        renderer.isBlank() // unknown: don't block
}

/** A Mali profile running an added Vulkan driver (PanVK) instead of the device's own. */
internal val GpuProfile.isMaliAddedDriver get() = isMali && driver != GraphicsDrivers.SYSTEM

/**
 * The wrapper's settings on Mali.
 *
 * Stock driver: BCn decoded on the CPU into ASTC (4x4, or 8x8 with [MaliOptions.lightTextures]), with a cache.
 * Added driver (PanVK): loaded through adrenotools like a Turnip build. It samples BCn itself (decoded on the GPU), so
 * the wrapper leaves BCn alone.
 */
internal fun maliGraphicsDriverConfig(gpu: GpuProfile): String = if (gpu.isMaliAddedDriver) {
    "version=${gpu.driver},adrenotoolsTurnip=0,resourceType=auto,bcnEmulation=none,quality=high"
} else {
    "version=System,adrenotoolsTurnip=0,resourceType=auto,bcnEmulation=auto,bcnEmulationType=software,bcnEmulationCache=1," +
        "quality=${if (gpu.maliOptions.lightTextures) "low" else "high"}"
}

/**
 * The container's environment for [gpu]: on Mali the Samsung UBWC hint (a Turnip setting) is dropped and the
 * wrapper's OpConstantComposite patch, which breaks shader creation on Mali's own compiler, is turned off.
 */
internal fun maliEnvVars(gpu: GpuProfile, envVars: String): String =
    if (!gpu.isMali) envVars
    else envVars.replace(" FD_DEV_FEATURES=enable_tp_ubwc_flag_hint=1", "") + " WRAPPER_NO_PATCH_OPCONSTCOMP=1"

/**
 * The profile a Mali device launches with. Settings > Graphics driver's Turnip builds don't run on Mali, so only
 * the device's own driver or an added one (PanVK) is used; anything else falls back to the device's own.
 */
internal fun maliLaunchProfile(detected: GpuProfile, pickedDriver: String, options: MaliOptions): GpuProfile {
    val driver = if (pickedDriver in GraphicsDrivers.BUILT_IN) GraphicsDrivers.SYSTEM else pickedDriver
    return detected.copy(driver = driver, maliOptions = options)
}

/** dxvk.conf lines for [options]. */
internal fun maliDxvkConf(options: MaliOptions): String =
    if (options.lowLatency) "dxgi.maxFrameLatency = 1\n" else ""

/**
 * Prepares a launch on Mali: picks the driver and options, and writes DXVK's config file (DXVK_CONFIG_FILE, which
 * DXVKHelper points at but nothing else writes). Other GPUs get [picked] unchanged.
 */
internal fun maliLaunchGpu(context: Context, detected: GpuProfile, picked: () -> GpuProfile): GpuProfile {
    if (!detected.isMali) return picked()
    val profile = maliLaunchProfile(detected, picked().driver, MaliSettings.options(context))
    runCatching {
        val conf = File(ImageFs.find(context).rootDir, ImageFs.CONFIG_PATH + "/dxvk.conf")
        val text = maliDxvkConf(profile.maliOptions)
        if (text.isEmpty()) conf.delete() else conf.apply { parentFile?.mkdirs() }.writeText(text)
    }.onFailure { Timber.w(it, "Couldn't write dxvk.conf") }
    Timber.i("Mali: driver ${profile.driver}, ${profile.maliOptions}")
    return profile
}

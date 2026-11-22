package app.gamenative.ui.screen.wow

import android.content.Context
import android.os.Build
import com.winlator.core.GPUInformation

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

internal const val MALI_GRAPHICS_DRIVER_CONFIG =
    "version=System,adrenotoolsTurnip=0,resourceType=auto,bcnEmulation=auto,bcnEmulationType=software,bcnEmulationCache=1,quality=high"

/**
 * The container's environment for [gpu]: on Mali the Samsung UBWC hint (a Turnip setting) is dropped and the
 * wrapper's OpConstantComposite patch, which breaks shader creation on Mali, is turned off.
 */
internal fun maliEnvVars(gpu: GpuProfile, envVars: String): String =
    if (!gpu.isMali) envVars
    else envVars.replace(" FD_DEV_FEATURES=enable_tp_ubwc_flag_hint=1", "") + " WRAPPER_NO_PATCH_OPCONSTCOMP=1"

/**
 * Settings > Graphics driver lists Turnip builds and added adrenotools drivers, none of which run on Mali, so a
 * Mali device keeps its own driver whatever is picked there.
 */
internal fun maliLaunchGpu(detected: GpuProfile, picked: () -> GpuProfile): GpuProfile =
    if (detected.isMali) detected else picked()

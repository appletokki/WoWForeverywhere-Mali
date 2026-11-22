package app.gamenative.wow

import android.content.Context
import app.gamenative.utils.Net
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Mali build, experimental: Mesa's PanVK Vulkan driver for Mali on the stock mali_kbase kernel driver, from
 * zenithblue-oss/panvk-kbase-android (tested there on the Galaxy Tab S10 Ultra, Mali-G720 MC12).
 *
 * The app doesn't ship the driver: on request it downloads one pinned release package from the project's GitHub
 * release, checks its SHA-256 and installs it as an added graphics driver (contents/adrenotools/<name>), where the
 * graphics wrapper loads it like a Turnip build. A newer release means a new pin here.
 */
object MaliPanVk {
    const val PROJECT_URL = "https://github.com/zenithblue-oss/panvk-kbase-android"
    const val RELEASE = "panvk-kbase-v26.3.1"
    const val PACKAGE_URL = "$PROJECT_URL/releases/download/$RELEASE/Mesa-PanVK-Android-v26.3.1-795ca7ff-adrenotools.zip"
    const val PACKAGE_SHA256 = "7e2e9987bbebefb06f52b2570e61e6208503d4d3a10a395b97aea9b6e1fad9e4"

    /** The name in the package's meta.json, which becomes the driver's folder and its name in the list. */
    const val DRIVER_NAME = "Mesa-PanVK-26.3.0-devel-795ca7ff"

    fun isInstalled(context: Context) = GraphicsDrivers.list(context).any { it.added && it.name == DRIVER_NAME }

    /** Downloads, checks and installs the pinned package. Returns the installed driver's name. */
    suspend fun install(context: Context, onProgress: (Float) -> Unit): String = withContext(Dispatchers.IO) {
        val zip = File(context.cacheDir, "panvk/$RELEASE-adrenotools.zip").apply { parentFile?.mkdirs() }
        try {
            Net.fetchFile(PACKAGE_URL, zip, onProgress)
            val sha = sha256(zip)
            check(sha.equals(PACKAGE_SHA256, ignoreCase = true)) { "checksum mismatch ($sha)" }
            val (result, name) = zip.inputStream().use { GraphicsDrivers.import(context, it) }
            check(result == GraphicsDrivers.ImportResult.OK && name != null) { "install failed: $result" }
            name
        } finally {
            zip.delete()
        }
    }

    internal fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

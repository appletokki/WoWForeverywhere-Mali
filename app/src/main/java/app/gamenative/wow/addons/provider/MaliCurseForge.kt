package app.gamenative.wow.addons.provider

import android.content.Context
import android.util.Base64
import app.gamenative.Crypto
import app.gamenative.utils.Net
import app.gamenative.wow.addons.AddonCategory
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

/**
 * Mali build: the player's own CurseForge API key. The project's CurseForge proxy is for the official release builds,
 * so this build calls CurseForge directly with a key the player gets from console.curseforge.com. The key is stored on
 * this device only, encrypted with an Android Keystore key, and sent only to api.curseforge.com.
 */
object MaliCurseForge {
    const val CONSOLE_URL = "https://console.curseforge.com/"
    const val API_BASE = "https://api.curseforge.com"
    private const val PREFS = "mali_curseforge"
    private const val SEALED = "sealed"

    enum class Check { OK, REJECTED, FAILED }

    /** Never forwards the key to another host through a redirect. */
    val http: OkHttpClient by lazy { Net.http.newBuilder().followRedirects(false).build() }

    @Volatile private var cached: String? = null

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The saved key, or "" when there is none or it can't be read. */
    fun key(context: Context): String = cached ?: synchronized(this) {
        cached ?: runCatching {
            prefs(context).getString(SEALED, null)?.let { Crypto.decrypt(Base64.decode(it, Base64.NO_WRAP)).decodeToString() }
        }.onFailure { Timber.w("The saved CurseForge key couldn't be read (${it.javaClass.simpleName})") }
            .getOrNull().orEmpty().also { cached = it }
    }

    fun save(context: Context, key: String): Unit = synchronized(this) {
        prefs(context).edit().putString(SEALED, Base64.encodeToString(Crypto.encrypt(key.encodeToByteArray()), Base64.NO_WRAP)).apply()
        cached = key
    }

    fun clear(context: Context): Unit = synchronized(this) {
        prefs(context).edit().clear().apply()
        cached = ""
    }

    /** [typed] without spaces and line breaks from pasting, or null when it can't be a key (or an HTTP header). */
    fun clean(typed: String): String? =
        typed.filterNot { it.isWhitespace() }.takeIf { key -> key.length in 20..200 && key.all { it in '!'..'~' } }

    /** Asks CurseForge whether it accepts [key]. */
    suspend fun check(key: String, client: OkHttpClient = http, apiBase: String = API_BASE): Check = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("${apiBase.trimEnd('/')}/v1/categories?gameId=${CurseForgeProvider.GAME_ID}")
            .header("Accept", "application/json").header("x-api-key", key).build()
        try {
            client.newCall(request).execute().use {
                when {
                    it.isSuccessful -> Check.OK
                    it.code == 401 || it.code == 403 -> Check.REJECTED
                    else -> Check.FAILED
                }
            }
        } catch (e: IOException) {
            Check.FAILED
        }
    }
}

/** [withKey] for the player's key while there is one, otherwise [fallback]. Follows changes to the key at once. */
internal class MaliCurseForgeProvider(
    private val key: () -> String,
    private val fallback: AddonProvider,
    private val withKey: (String) -> AddonProvider,
) : AddonProvider {
    override val id: String = fallback.id
    override val enabled: Boolean get() = now().enabled

    private var current: Pair<String, AddonProvider>? = null

    @Synchronized
    private fun now(): AddonProvider {
        val k = key()
        if (k.isBlank()) return fallback
        return current?.takeIf { it.first == k }?.second ?: withKey(k).also { current = k to it }
    }

    override suspend fun search(query: String, page: Int) = now().search(query, page)
    override suspend fun popular(page: Int) = now().popular(page)
    override suspend fun popular(page: Int, category: AddonCategory?) = now().popular(page, category)
    override suspend fun get(projectId: String) = now().get(projectId)
    override suspend fun getMany(ids: List<String>) = now().getMany(ids)
    override suspend fun description(projectId: String) = now().description(projectId)
}

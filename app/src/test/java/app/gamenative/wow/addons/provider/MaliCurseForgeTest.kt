package app.gamenative.wow.addons.provider

import app.gamenative.wow.addons.provider.Fixtures.base
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MaliCurseForgeTest {
    private val server = MockWebServer()

    @After
    fun tearDown() = server.close()

    @Test
    fun clean_dropsPastedWhitespaceAndRefusesWhatCantBeAHeader() {
        val key = "${'$'}2a${'$'}10${'$'}abcdefghijklmnopqrstuv"
        assertEquals(key, MaliCurseForge.clean(" $key\n"))
        assertNull(MaliCurseForge.clean("short"))
        assertNull(MaliCurseForge.clean("한".repeat(30)))
    }

    @Test
    fun provider_followsTheKey() = runBlocking {
        server.dispatcher = Fixtures.dispatcher { Fixtures.json(Fixtures.load("cf-search.schema.json", server.base())) }
        server.start()
        var key = ""
        val provider = MaliCurseForgeProvider(
            key = { key },
            fallback = CurseForgeProvider("", OkHttpClient(), apiBase = server.base()),
            withKey = { CurseForgeProvider(it, OkHttpClient(), apiBase = server.base()) },
        )
        assertTrue(provider.search("questie", 0).isEmpty())
        key = "first-key"
        assertTrue(provider.search("questie", 0).isNotEmpty())
        assertEquals("first-key", server.takeRequest().headers["x-api-key"])
        key = "second-key"
        provider.search("questie", 0)
        assertEquals("second-key", server.takeRequest().headers["x-api-key"])
    }

    @Test
    fun oneUnreadableMod_doesNotEmptyTheList() = runBlocking {
        val page = JSONObject(Fixtures.load("cf-search.schema.json", "http://localhost"))
        page.getJSONArray("data").put(JSONObject().put("name", "no id"))
        server.dispatcher = Fixtures.dispatcher { Fixtures.json(page.toString()) }
        server.start()
        val found = CurseForgeProvider("key", OkHttpClient(), apiBase = server.base()).search("questie", 0)
        assertEquals(listOf("334372", "4444"), found.map { it.projectId })
    }

    @Test
    fun failedListing_notesWhy() = runBlocking {
        server.dispatcher = Fixtures.dispatcher { MockResponse(code = 503) }
        server.start()
        val direct = CurseForgeProvider("key", OkHttpClient(), apiBase = server.base())
        val provider = MaliCurseForgeProvider({ "" }, direct) { direct }
        assertTrue(runCatching { provider.search("x", 0) }.isFailure)
        assertEquals("IOException: HTTP 503 for /v1/mods/search", MaliCurseForge.lastProblem)
    }

    @Test
    fun check_tellsRejectedFromUnreachable() = runBlocking {
        server.dispatcher = Fixtures.dispatcher { req ->
            when (req.headers["x-api-key"]) {
                "good" -> Fixtures.json("""{"data":[]}""")
                "bad" -> MockResponse(code = 403)
                else -> MockResponse(code = 500)
            }
        }
        server.start()
        assertEquals(MaliCurseForge.Check.OK, MaliCurseForge.check("good", OkHttpClient(), server.base()))
        assertEquals(MaliCurseForge.Check.REJECTED, MaliCurseForge.check("bad", OkHttpClient(), server.base()))
        assertEquals(MaliCurseForge.Check.FAILED, MaliCurseForge.check("other", OkHttpClient(), server.base()))
    }
}

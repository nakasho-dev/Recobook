package org.ukky.recobook.network

import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HttpClientFactoryTest {

    private val testJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun createRecobookHttpClient_debugDisabled_doesNotEmitHttpLogs() = runTest {
        val logs = mutableListOf<String>()
        val client = createRecobookHttpClient(
            json = testJson,
            engine = mockEngine(),
            enableNetworkLogging = false,
            logger = object : Logger {
                override fun log(message: String) {
                    logs += message
                }
            },
        )

        client.get("https://example.com/books")
        client.close()

        assertTrue(logs.isEmpty())
    }

    @Test
    fun createRecobookHttpClient_debugEnabled_emitsRequestAndResponseLogs() = runTest {
        val logs = mutableListOf<String>()
        val client = createRecobookHttpClient(
            json = testJson,
            engine = mockEngine(),
            enableNetworkLogging = true,
            logger = object : Logger {
                override fun log(message: String) {
                    logs += message
                }
            },
        )

        client.get("https://example.com/books")
        client.close()

        val joinedLogs = logs.joinToString("\n")
        assertFalse(logs.isEmpty())
        assertContains(joinedLogs, "https://example.com/books")
        assertContains(joinedLogs, "200 OK")
    }

    private fun mockEngine(): MockEngine = MockEngine {
        respond(
            content = """{"items":[]}""",
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )
    }
}

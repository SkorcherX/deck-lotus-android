package com.decklotus.companion

import com.decklotus.companion.network.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class IngestContractTest {

    private val mockServer = MockIngestServer()
    private val ingestApi = IngestApi()
    private var baseUrl: String = ""

    @Before
    fun setUp() {
        baseUrl = mockServer.start(0) // Port 0 = choose free port
    }

    @After
    fun tearDown() {
        mockServer.stop()
    }

    @Test
    fun testMockIngestRoundTrip() = runBlocking {
        val request = IngestRequest(
            artHash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            frameHash = "0123456789abcdef",
            ocr = IngestOcrData(
                setCode = "ECC",
                collector = "0001",
                language = "EN",
                rawLines = listOf("ECC \u2022 EN", "0001"),
                confidence = 0.95f
            ),
            capture = IngestCaptureMetadata(
                exposureNs = 2_000_000L,
                iso = 100,
                focusDist = 4.2f,
                device = "pixel-10-pro",
                rig = "card-slinger-3.0"
            ),
            commit = IngestCommitOptions(
                mode = "inventory",
                deckId = null,
                isFoil = false
            )
        )

        val result = ingestApi.postIngest(baseUrl, "test-token", request)

        assertEquals(200, result.httpCode)
        assertNotNull(result.response)
        assertEquals("confident", result.response?.tier)
        assertEquals("ECC", result.response?.printing?.setCode)
        assertEquals("0001", result.response?.printing?.collector)
        assertEquals("Cultivate", result.response?.printing?.name)
        assertTrue(result.latencyMs >= 0)
    }
}
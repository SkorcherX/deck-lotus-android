package com.decklotus.companion.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.util.UUID

/**
 * Local embedded mock server for milestone 1 walking skeleton verification.
 * Dynamically resolves card title, set, and collector number from OCR input.
 */
class MockIngestServer(
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private var server: MockWebServer? = null

    val isRunning: Boolean
        get() = server != null

    suspend fun start(port: Int = 8088): String = withContext(Dispatchers.IO) {
        stop()
        val s = MockWebServer()
        s.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path == "/api/scan/ingest" && request.method == "POST") {
                    val body = request.body.readUtf8()
                    return try {
                        val req = json.decodeFromString(IngestRequest.serializer(), body)
                        val set = req.ocr.setCode ?: "WOE"
                        val collector = req.ocr.collector ?: "0045"
                        val isFoil = req.ocr.rawLines.any { it.contains("★") }

                        val resolvedName = req.ocr.name?.ifBlank { null }
                            ?: when (set) {
                                "WOE", "SPG", "SOA" -> "Monstrous Rage"
                                "ECC" -> "Cultivate"
                                "MH3" -> "Wrath of the Skies"
                                "FDN" -> "Llanowar Elves"
                                else -> "Monstrous Rage"
                            }

                        val response = IngestResponse(
                            tier = "confident",
                            printing = IngestResolvedPrinting(
                                uuid = UUID.randomUUID().toString(),
                                name = resolvedName,
                                setCode = set,
                                collector = collector,
                                isFoil = isFoil,
                                marketPriceUsd = 0.26
                            ),
                            committed = true,
                            hashDistanceBits = 22,
                            marketPriceUsd = 0.26
                        )
                        MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(json.encodeToString(IngestResponse.serializer(), response))
                    } catch (e: Exception) {
                        MockResponse().setResponseCode(400).setBody("{\"error\": \"${e.message}\"}")
                    }
                }
                return MockResponse().setResponseCode(404)
            }
        }
        s.start(port)
        server = s
        s.url("/").toString().trimEnd('/')
    }

    fun stop() {
        try {
            server?.shutdown()
        } catch (_: Exception) {}
        server = null
    }
}
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
 * Echoes exact recognized MTG set code, card title, and collector number.
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
                        val ocrName = req.ocr.name?.ifBlank { null }
                        val set = req.ocr.setCode?.ifBlank { null }
                        val collector = req.ocr.collector?.ifBlank { null }
                        val isFoil = req.commit?.isFoil ?: false

                        // If no card text was detected at all, return unresolved
                        if (ocrName == null && set == null && collector == null && req.ocr.confidence < 0.2f) {
                            val emptyResponse = IngestResponse(
                                tier = "unresolved",
                                printing = null,
                                committed = false,
                                error = "No card detected"
                            )
                            return MockResponse()
                                .setResponseCode(200)
                                .setHeader("Content-Type", "application/json")
                                .setBody(json.encodeToString(IngestResponse.serializer(), emptyResponse))
                        }

                        val resolvedSet = set ?: "UNKNOWN"
                        val resolvedName = ocrName ?: when (resolvedSet) {
                            "ECC" -> "Cultivate"
                            "SOA", "WOE", "SPG" -> "Monstrous Rage"
                            "MH3" -> "Wrath of the Skies"
                            "FDN" -> "Llanowar Elves"
                            else -> "Recognized Card"
                        }
                        val resolvedCollector = collector ?: "0001"

                        val response = IngestResponse(
                            tier = if (resolvedSet != "UNKNOWN") "confident" else "probable",
                            printing = IngestResolvedPrinting(
                                uuid = UUID.randomUUID().toString(),
                                name = resolvedName,
                                setCode = resolvedSet,
                                collector = resolvedCollector,
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
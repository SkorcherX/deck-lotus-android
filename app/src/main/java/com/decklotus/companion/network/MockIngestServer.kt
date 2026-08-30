package com.decklotus.companion.network

import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.util.UUID

/**
 * Local embedded mock server for milestone 1 walking skeleton verification.
 * Echoes back resolved MTG printings based on OCR / art hash input.
 */
class MockIngestServer(
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private var server: MockWebServer? = null

    val isRunning: Boolean
        get() = server != null

    fun start(port: Int = 8088): String {
        stop()
        val s = MockWebServer()
        s.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path == "/api/scan/ingest" && request.method == "POST") {
                    val body = request.body.readUtf8()
                    return try {
                        val req = json.decodeFromString(IngestRequest.serializer(), body)
                        val set = req.ocr.setCode ?: "ECC"
                        val collector = req.ocr.collector ?: "0001"
                        val isFoil = req.ocr.rawLines.any { it.contains("★") }

                        val sampleName = when (set) {
                            "ECC" -> "Cultivate"
                            "MH3" -> "Wrath of the Skies"
                            "FDN" -> "Llanowar Elves"
                            else -> "Card ($set #$collector)"
                        }

                        val response = IngestResponse(
                            tier = "confident",
                            printing = IngestResolvedPrinting(
                                uuid = UUID.randomUUID().toString(),
                                name = sampleName,
                                setCode = set,
                                collector = collector,
                                isFoil = isFoil
                            ),
                            committed = true,
                            hashDistanceBits = 28
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
        return s.url("/").toString().trimEnd('/')
    }

    fun stop() {
        server?.shutdown()
        server = null
    }
}
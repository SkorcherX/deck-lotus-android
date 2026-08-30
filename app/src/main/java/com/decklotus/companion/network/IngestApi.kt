package com.decklotus.companion.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class IngestApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
) {
    data class IngestResult(
        val response: IngestResponse?,
        val latencyMs: Long,
        val httpCode: Int,
        val errorMessage: String? = null
    )

    suspend fun postIngest(
        baseUrl: String,
        token: String?,
        payload: IngestRequest
    ): IngestResult = withContext(Dispatchers.IO) {
        val startNs = System.nanoTime()
        val cleanBaseUrl = baseUrl.trimEnd('/')
        val url = "$cleanBaseUrl/api/scan/ingest"

        val bodyJson = json.encodeToString(IngestRequest.serializer(), payload)
        val requestBody = bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType())

        val requestBuilder = Request.Builder()
            .url(url)
            .post(requestBody)

        if (!token.isNullOrBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        try {
            client.newCall(requestBuilder.build()).execute().use { httpResponse ->
                val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
                val respBody = httpResponse.body?.string().orEmpty()

                if (httpResponse.isSuccessful) {
                    val parsed = json.decodeFromString(IngestResponse.serializer(), respBody)
                    IngestResult(
                        response = parsed,
                        latencyMs = elapsedMs,
                        httpCode = httpResponse.code
                    )
                } else {
                    IngestResult(
                        response = null,
                        latencyMs = elapsedMs,
                        httpCode = httpResponse.code,
                        errorMessage = "HTTP ${httpResponse.code}: $respBody"
                    )
                }
            }
        } catch (e: Exception) {
            val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
            IngestResult(
                response = null,
                latencyMs = elapsedMs,
                httpCode = 0,
                errorMessage = e.localizedMessage ?: e.javaClass.simpleName
            )
        }
    }
}
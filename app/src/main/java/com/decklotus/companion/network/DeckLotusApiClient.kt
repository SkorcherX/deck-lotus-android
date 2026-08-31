package com.decklotus.companion.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

sealed class ServerConnectionStatus {
    object Idle : ServerConnectionStatus()
    object Checking : ServerConnectionStatus()
    data class Connected(val username: String?, val latencyMs: Long) : ServerConnectionStatus()
    data class CloudflareAuthRequired(val message: String = "Cloudflare Access session required. Tap CF Portal to log in.") : ServerConnectionStatus()
    data class DeckLotusAuthRequired(val message: String = "API Key / Token rejected by Deck Lotus.") : ServerConnectionStatus()
    data class Unreachable(val errorMessage: String) : ServerConnectionStatus()
}

@Serializable
data class InventoryBulkAddItem(
    val cardName: String,
    val setCode: String? = null,
    val collectorNumber: String? = null,
    val quantity: Int = 1,
    val isFoil: Boolean = false
)

@Serializable
data class InventoryBulkAddRequest(
    val source: String = "scanner",
    val items: List<InventoryBulkAddItem>
)

@Serializable
data class InventoryBulkAddResponse(
    val added: Int = 0,
    val failed: Int = 0,
    val errors: List<String> = emptyList()
)

class DeckLotusApiClient(
    val cookieJar: CloudflareCookieJar = CloudflareCookieJar()
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun testConnection(baseUrl: String, token: String?): ServerConnectionStatus = withContext(Dispatchers.IO) {
        val cleanBase = baseUrl.trim().trimEnd('/')
        if (cleanBase.isBlank() || !cleanBase.startsWith("http")) {
            return@withContext ServerConnectionStatus.Unreachable("Invalid server URL")
        }

        val startNs = System.nanoTime()
        val url = "$cleanBase/api/auth/me"

        val requestBuilder = Request.Builder()
            .url(url)
            .get()

        if (!token.isNullOrBlank()) {
            val clean = token.trim()
            requestBuilder.addHeader("X-API-Key", clean)
            if (!clean.startsWith("Bearer ", ignoreCase = true)) {
                requestBuilder.addHeader("Authorization", "Bearer $clean")
            } else {
                requestBuilder.addHeader("Authorization", clean)
            }
        }

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
                val code = response.code
                val contentType = response.header("Content-Type").orEmpty().lowercase()
                val cfRay = response.header("CF-Ray")
                val location = response.header("Location").orEmpty()
                val bodyText = response.body?.string().orEmpty()

                Log.d("DeckLotusApiClient", "Ping $url -> Code: $code, Content-Type: $contentType, CF-Ray: $cfRay, Body: $bodyText")

                // 1. Cloudflare Access challenge detection
                if (code == 302 || code == 307) {
                    if (location.contains("cloudflareaccess.com") || location.contains("/cdn-cgi/access/")) {
                        return@withContext ServerConnectionStatus.CloudflareAuthRequired()
                    }
                }

                if (code == 403 || contentType.contains("text/html")) {
                    if (cfRay != null || bodyText.contains("Cloudflare", ignoreCase = true) || bodyText.contains("cloudflareaccess.com", ignoreCase = true)) {
                        return@withContext ServerConnectionStatus.CloudflareAuthRequired()
                    }
                }

                // 2. Deck Lotus API Auth rejection
                if (code == 401) {
                    return@withContext ServerConnectionStatus.DeckLotusAuthRequired()
                }

                // 3. Success
                if (response.isSuccessful) {
                    var username: String? = null
                    try {
                        val parsed = json.parseToJsonElement(bodyText).jsonObject
                        val userObj = parsed["user"]?.jsonObject
                        val uName = userObj?.get("username")?.jsonPrimitive?.content
                        if (uName != null) username = uName
                    } catch (_: Exception) {}

                    return@withContext ServerConnectionStatus.Connected(username = username, latencyMs = elapsedMs)
                }

                ServerConnectionStatus.Unreachable("Server returned HTTP $code")
            }
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: e.javaClass.simpleName
            Log.e("DeckLotusApiClient", "Connection test failed: $msg")
            ServerConnectionStatus.Unreachable(msg)
        }
    }

    suspend fun commitBatchToCollection(
        baseUrl: String,
        token: String?,
        items: List<InventoryBulkAddItem>
    ): Result<InventoryBulkAddResponse> = withContext(Dispatchers.IO) {
        val cleanBase = baseUrl.trim().trimEnd('/')
        val url = "$cleanBase/api/inventory/bulk-add"

        val payload = InventoryBulkAddRequest(
            source = "scanner",
            items = items
        )

        val bodyJson = json.encodeToString(InventoryBulkAddRequest.serializer(), payload)
        Log.d("DeckLotusApiClient", "POST $url Payload: $bodyJson")

        val requestBody = bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType())

        val requestBuilder = Request.Builder()
            .url(url)
            .post(requestBody)

        if (!token.isNullOrBlank()) {
            val clean = token.trim()
            requestBuilder.addHeader("X-API-Key", clean)
            if (!clean.startsWith("Bearer ", ignoreCase = true)) {
                requestBuilder.addHeader("Authorization", "Bearer $clean")
            } else {
                requestBuilder.addHeader("Authorization", clean)
            }
        }

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                Log.d("DeckLotusApiClient", "POST $url Response (${response.code}): $bodyText")
                if (response.isSuccessful) {
                    val parsed = json.decodeFromString(InventoryBulkAddResponse.serializer(), bodyText)
                    Result.success(parsed)
                } else {
                    Result.failure(Exception("HTTP ${response.code}: $bodyText"))
                }
            }
        } catch (e: Exception) {
            Log.e("DeckLotusApiClient", "POST $url Exception: ${e.message}")
            Result.failure(e)
        }
    }
}
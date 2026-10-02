package com.decklotus.companion.network

import android.content.Context
import android.database.sqlite.SQLiteDatabase
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
import java.io.File
import java.io.FileOutputStream
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

/**
 * One line the server could not resolve.
 *
 * cardName is nullable because the server has two error paths and only one of
 * them fills it: a line given as set code and collector number with no name
 * comes back with cardName absent. Declaring it non-null makes an otherwise
 * fine response fail to parse.
 */
@Serializable
data class InventoryBulkAddError(
    val cardName: String? = null,
    val setCode: String? = null,
    val collectorNumber: String? = null,
    val error: String
)

/**
 * What POST /api/inventory/bulk-add actually returns.
 *
 * `errors` is a list of objects, not of strings — it was declared as
 * List<String> here, which parsed fine for as long as every card resolved and
 * threw the moment one did not. The throw surfaced as "the commit failed"
 * *after* the server had already written every line that did resolve, so
 * re-scanning the batch added those cards a second time.
 *
 * `added` counts copies, not lines: a line of quantity 4 adds 4.
 */
@Serializable
data class InventoryBulkAddResponse(
    val added: Int = 0,
    val failed: Int = 0,
    val errors: List<InventoryBulkAddError> = emptyList(),
    /** Ties one import together in the server's audit log, for undoing it. */
    val batchId: String? = null
)

/**
 * The outcome of a whole commit, which may have been several requests.
 *
 * The distinction that matters is [uncommitted] versus [rejected]. A rejected
 * line reached the server and was refused: re-sending it will fail again, and
 * it needs a human. An uncommitted line is one whose fate is unknown — the
 * chunk carrying it never got a reply — and re-sending it may duplicate what
 * is already there. Collapsing the two into "failed" is what made the old
 * failure path dangerous.
 */
data class CommitOutcome(
    val addedCopies: Int,
    val rejected: List<InventoryBulkAddError>,
    val uncommitted: List<InventoryBulkAddItem>,
    val batchIds: List<String>,
    val transportError: String? = null,
    val isCloudflareAuthRequired: Boolean = false
) {
    val isCleanSuccess: Boolean
        get() = rejected.isEmpty() && uncommitted.isEmpty() && transportError == null && !isCloudflareAuthRequired
}

/** Whether the server is mid-rebuild. See GET /api/system/maintenance. */
@Serializable
data class MaintenanceStatus(
    val state: String = "idle",
    val label: String? = null,
    val percent: Int? = null
) {
    /**
     * The states in which card lookups cannot be trusted.
     *
     * scripts/import-mtgjson.js empties `printings` for the minutes it runs, so
     * a commit landing in that window resolves nothing and comes back as a
     * batch of "No printing found" — a whole box reported as unreadable when
     * the scans were fine.
     */
    val blocksWrites: Boolean
        get() = state == "running" || state == "scheduled"
}

/**
 * How many lines go in one bulk-add.
 *
 * The server caps this at 1000 and answers over the limit with a 400, so a big
 * box is split rather than refused. Each chunk is its own commit with its own
 * result: chunk three failing says nothing about chunks one and two, which are
 * already written.
 */
private const val BULK_CHUNK_SIZE = 1000

/**
 * Attach credentials.
 *
 * One header, not two. This used to send X-API-Key *and* an Authorization
 * bearer holding the same value; the server tries the JWT branch first, fails
 * to verify an API key as a token, and falls through — so it worked, at the
 * cost of a wasted verify per request and a 401 that could have come from
 * either header. A token that really is a JWT still goes in Authorization.
 */
private fun Request.Builder.authenticate(token: String?): Request.Builder {
    val clean = token?.trim().orEmpty()
    if (clean.isBlank()) return this

    return if (clean.startsWith("Bearer ", ignoreCase = true)) {
        addHeader("Authorization", clean)
    } else if (clean.count { it == '.' } == 2) {
        addHeader("Authorization", "Bearer $clean")
    } else {
        addHeader("X-API-Key", clean)
    }
}

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

    companion object {
        fun isCloudflareChallenge(
            code: Int,
            location: String,
            contentType: String,
            cfRay: String?,
            wwwAuth: String?,
            bodyText: String
        ): Boolean {
            if (code == 302 || code == 307) {
                if (location.contains("cloudflareaccess.com") ||
                    location.contains("/cdn-cgi/access/") ||
                    wwwAuth?.contains("Cloudflare-Access", ignoreCase = true) == true
                ) {
                    return true
                }
            }
            if (code == 403 || contentType.contains("text/html")) {
                if (cfRay != null ||
                    wwwAuth?.contains("Cloudflare-Access", ignoreCase = true) == true ||
                    bodyText.contains("Cloudflare", ignoreCase = true) ||
                    bodyText.contains("cloudflareaccess.com", ignoreCase = true)
                ) {
                    return true
                }
            }
            return false
        }
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

        requestBuilder.authenticate(token)

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
                val code = response.code
                val contentType = response.header("Content-Type").orEmpty().lowercase()
                val cfRay = response.header("CF-Ray")
                val location = response.header("Location").orEmpty()
                val wwwAuth = response.header("Www-Authenticate")
                val bodyText = response.body?.string().orEmpty()

                Log.d("DeckLotusApiClient", "Ping $url -> Code: $code, Content-Type: $contentType, CF-Ray: $cfRay, Body: $bodyText")

                // 1. Cloudflare Access challenge detection
                if (isCloudflareChallenge(code, location, contentType, cfRay, wwwAuth, bodyText)) {
                    return@withContext ServerConnectionStatus.CloudflareAuthRequired()
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

    /**
     * Is the server safe to write to right now?
     *
     * Unauthenticated on purpose server-side — it has to be answerable while
     * the API-key check itself cannot read the database. A failure to reach it
     * is reported as null and treated as "go ahead": this is a guard against a
     * known window, not a reason to block a commit on one extra request.
     */
    suspend fun maintenanceStatus(baseUrl: String): MaintenanceStatus? = withContext(Dispatchers.IO) {
        val url = "${baseUrl.trim().trimEnd('/')}/api/system/maintenance"
        try {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                json.decodeFromString(MaintenanceStatus.serializer(), response.body?.string().orEmpty())
            }
        } catch (e: Exception) {
            Log.d("DeckLotusApiClient", "Maintenance probe failed, proceeding: ${e.message}")
            null
        }
    }

    /**
     * Send a scanned session to the collection.
     *
     * Split into chunks the server will accept, and reported as a
     * [CommitOutcome] rather than a Result, because "it failed" is not a
     * useful answer here: the endpoint writes line by line with no transaction,
     * so a response can carry both cards that went in and cards that did not,
     * and a request that times out may still have written everything. Whatever
     * the caller does next has to distinguish those.
     */
    suspend fun commitBatchToCollection(
        baseUrl: String,
        token: String?,
        items: List<InventoryBulkAddItem>
    ): CommitOutcome = withContext(Dispatchers.IO) {
        val cleanBase = baseUrl.trim().trimEnd('/')
        val url = "$cleanBase/api/inventory/bulk-add"

        var addedCopies = 0
        val rejected = mutableListOf<InventoryBulkAddError>()
        val uncommitted = mutableListOf<InventoryBulkAddItem>()
        val batchIds = mutableListOf<String>()
        var transportError: String? = null
        var isCloudflareAuthRequired = false

        for (chunk in items.chunked(BULK_CHUNK_SIZE)) {
            // Once a chunk has failed for a reason that will not change — no
            // network, a rejected key — the rest are not worth sending. They
            // are recorded as uncommitted so the session keeps them.
            if (transportError != null) {
                uncommitted += chunk
                continue
            }

            val payload = InventoryBulkAddRequest(source = "scanner", items = chunk)
            val bodyJson = json.encodeToString(InventoryBulkAddRequest.serializer(), payload)
            val requestBody = bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .authenticate(token)
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val code = response.code
                    val location = response.header("Location").orEmpty()
                    val contentType = response.header("Content-Type").orEmpty().lowercase()
                    val cfRay = response.header("CF-Ray")
                    val wwwAuth = response.header("Www-Authenticate")
                    val bodyText = response.body?.string().orEmpty()
                    Log.d("DeckLotusApiClient", "POST $url (${chunk.size} items) -> $code")

                    if (response.isSuccessful) {
                        val parsed = json.decodeFromString(InventoryBulkAddResponse.serializer(), bodyText)
                        addedCopies += parsed.added
                        rejected += parsed.errors
                        parsed.batchId?.let { batchIds += it }
                    } else if (isCloudflareChallenge(code, location, contentType, cfRay, wwwAuth, bodyText)) {
                        transportError = "Cloudflare Access session expired. Log in via CF Portal."
                        isCloudflareAuthRequired = true
                        uncommitted += chunk
                    } else {
                        transportError = "HTTP $code: ${bodyText.take(300)}"
                        uncommitted += chunk
                    }
                }
            } catch (e: Exception) {
                // Includes a response that would not parse. The write may well
                // have happened, so these are uncommitted, never rejected.
                transportError = e.localizedMessage ?: e.javaClass.simpleName
                Log.e("DeckLotusApiClient", "POST $url failed: $transportError")
                uncommitted += chunk
            }
        }

        CommitOutcome(
            addedCopies = addedCopies,
            rejected = rejected,
            uncommitted = uncommitted,
            batchIds = batchIds,
            transportError = transportError,
            isCloudflareAuthRequired = isCloudflareAuthRequired
        )
    }

    /**
     * Fetch the user's decks from GET /api/decks.
     */
    suspend fun fetchUserDecks(
        baseUrl: String,
        token: String?
    ): Result<List<DeckSummary>> = withContext(Dispatchers.IO) {
        val cleanBase = baseUrl.trim().trimEnd('/')
        if (cleanBase.isBlank() || !cleanBase.startsWith("http")) {
            return@withContext Result.failure(IllegalArgumentException("Invalid server URL"))
        }

        val url = "$cleanBase/api/decks"
        val request = Request.Builder()
            .url(url)
            .get()
            .authenticate(token)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val code = response.code
                val location = response.header("Location").orEmpty()
                val contentType = response.header("Content-Type").orEmpty().lowercase()
                val cfRay = response.header("CF-Ray")
                val wwwAuth = response.header("Www-Authenticate")
                val bodyText = response.body?.string().orEmpty()

                if (isCloudflareChallenge(code, location, contentType, cfRay, wwwAuth, bodyText)) {
                    return@withContext Result.failure(IllegalStateException("Cloudflare Access session required. Log in via CF Portal."))
                }

                if (!response.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("HTTP $code: ${bodyText.take(200)}"))
                }

                val parsed = json.decodeFromString(DeckListResponse.serializer(), bodyText)
                Result.success(parsed.decks)
            }
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: e.javaClass.simpleName
            Log.e("DeckLotusApiClient", "GET $url failed: $msg")
            Result.failure(e)
        }
    }

    /**
     * Create a new deck via POST /api/decks.
     */
    suspend fun createDeck(
        baseUrl: String,
        token: String?,
        name: String,
        format: String = "commander",
        description: String? = null,
        status: String = "building"
    ): Result<DeckSummary> = withContext(Dispatchers.IO) {
        val cleanBase = baseUrl.trim().trimEnd('/')
        if (cleanBase.isBlank() || !cleanBase.startsWith("http")) {
            return@withContext Result.failure(IllegalArgumentException("Invalid server URL"))
        }

        val url = "$cleanBase/api/decks"
        val payload = CreateDeckRequest(
            name = name.trim(),
            format = format,
            description = description?.trim()?.ifEmpty { null },
            status = status
        )
        val bodyJson = json.encodeToString(CreateDeckRequest.serializer(), payload)
        val requestBody = bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .authenticate(token)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val code = response.code
                val location = response.header("Location").orEmpty()
                val contentType = response.header("Content-Type").orEmpty().lowercase()
                val cfRay = response.header("CF-Ray")
                val wwwAuth = response.header("Www-Authenticate")
                val bodyText = response.body?.string().orEmpty()

                if (isCloudflareChallenge(code, location, contentType, cfRay, wwwAuth, bodyText)) {
                    return@withContext Result.failure(IllegalStateException("Cloudflare Access session required. Log in via CF Portal."))
                }

                if (!response.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("HTTP $code: ${bodyText.take(200)}"))
                }

                val parsed = json.decodeFromString(CreateDeckResponse.serializer(), bodyText)
                Result.success(parsed.deck)
            }
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: e.javaClass.simpleName
            Log.e("DeckLotusApiClient", "POST $url failed: $msg")
            Result.failure(e)
        }
    }

    /**
     * Check which cards are not in the user's collection via POST /api/scan/shortfall.
     */
    suspend fun checkShortfall(
        baseUrl: String,
        token: String?,
        items: List<ScanShortfallItem>
    ): Result<List<OwnershipShortfall>> = withContext(Dispatchers.IO) {
        val cleanBase = baseUrl.trim().trimEnd('/')
        if (cleanBase.isBlank() || !cleanBase.startsWith("http")) {
            return@withContext Result.failure(IllegalArgumentException("Invalid server URL"))
        }
        if (items.isEmpty()) {
            return@withContext Result.success(emptyList())
        }

        val url = "$cleanBase/api/scan/shortfall"
        val payload = ScanShortfallRequest(items = items)
        val bodyJson = json.encodeToString(ScanShortfallRequest.serializer(), payload)
        val requestBody = bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .authenticate(token)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val code = response.code
                val location = response.header("Location").orEmpty()
                val contentType = response.header("Content-Type").orEmpty().lowercase()
                val cfRay = response.header("CF-Ray")
                val wwwAuth = response.header("Www-Authenticate")
                val bodyText = response.body?.string().orEmpty()

                if (isCloudflareChallenge(code, location, contentType, cfRay, wwwAuth, bodyText)) {
                    return@withContext Result.failure(IllegalStateException("Cloudflare Access session required. Log in via CF Portal."))
                }

                if (!response.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("HTTP $code: ${bodyText.take(200)}"))
                }

                val parsed = json.decodeFromString(ScanShortfallResponse.serializer(), bodyText)
                Result.success(parsed.shortfalls)
            }
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: e.javaClass.simpleName
            Log.e("DeckLotusApiClient", "POST $url failed: $msg")
            Result.failure(e)
        }
    }

    /**
     * Commit a scanned session directly to a deck via POST /api/scan/commit.
     */
    suspend fun commitBatchToDeck(
        baseUrl: String,
        token: String?,
        deckId: Int,
        items: List<ScanCommitItem>,
        alsoAddToCollection: Boolean = false
    ): CommitDeckOutcome = withContext(Dispatchers.IO) {
        val cleanBase = baseUrl.trim().trimEnd('/')
        if (cleanBase.isBlank() || !cleanBase.startsWith("http")) {
            return@withContext CommitDeckOutcome(
                committedCards = 0,
                totalCopies = 0,
                deckId = deckId,
                batchId = null,
                transportError = "Invalid server URL"
            )
        }

        val url = "$cleanBase/api/scan/commit"
        val payload = ScanCommitRequest(
            destination = "deck",
            deckId = deckId,
            items = items,
            alsoAddToCollection = alsoAddToCollection
        )
        val bodyJson = json.encodeToString(ScanCommitRequest.serializer(), payload)
        val requestBody = bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .authenticate(token)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val code = response.code
                val location = response.header("Location").orEmpty()
                val contentType = response.header("Content-Type").orEmpty().lowercase()
                val cfRay = response.header("CF-Ray")
                val wwwAuth = response.header("Www-Authenticate")
                val bodyText = response.body?.string().orEmpty()
                Log.d("DeckLotusApiClient", "POST $url (Deck $deckId, ${items.size} items) -> $code")

                if (response.isSuccessful) {
                    val parsed = json.decodeFromString(ScanCommitResponse.serializer(), bodyText)
                    CommitDeckOutcome(
                        committedCards = parsed.cards,
                        totalCopies = parsed.committed,
                        deckId = parsed.deckId ?: deckId,
                        batchId = parsed.batchId,
                        addedToCollectionCopies = parsed.addedToCollection?.committed ?: 0
                    )
                } else if (isCloudflareChallenge(code, location, contentType, cfRay, wwwAuth, bodyText)) {
                    CommitDeckOutcome(
                        committedCards = 0,
                        totalCopies = 0,
                        deckId = deckId,
                        batchId = null,
                        transportError = "Cloudflare Access session expired. Log in via CF Portal.",
                        isCloudflareAuthRequired = true
                    )
                } else {
                    CommitDeckOutcome(
                        committedCards = 0,
                        totalCopies = 0,
                        deckId = deckId,
                        batchId = null,
                        transportError = "HTTP $code: ${bodyText.take(300)}"
                    )
                }
            }
        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: e.javaClass.simpleName
            Log.e("DeckLotusApiClient", "POST $url failed: $errorMsg")
            CommitDeckOutcome(
                committedCards = 0,
                totalCopies = 0,
                deckId = deckId,
                batchId = null,
                transportError = errorMsg
            )
        }
    }

    /**
     * Resolve a batch of scans into live server printing IDs via POST /api/scan/resolve.
     * Returns a map of ScanItem ID -> live server printing ID.
     */
    suspend fun resolveBatchScans(
        baseUrl: String,
        token: String?,
        items: List<BatchResolveScanItem>
    ): Result<Map<String, Int>> = withContext(Dispatchers.IO) {
        val cleanBase = baseUrl.trim().trimEnd('/')
        if (cleanBase.isBlank() || !cleanBase.startsWith("http")) {
            return@withContext Result.failure(IllegalArgumentException("Invalid server URL"))
        }
        if (items.isEmpty()) {
            return@withContext Result.success(emptyMap())
        }

        val url = "$cleanBase/api/scan/resolve"
        val resultMap = mutableMapOf<String, Int>()

        // Server limits scans array to at most 200 items per request
        for (chunk in items.chunked(200)) {
            val payload = BatchResolveRequest(scans = chunk, limit = 5)
            val bodyJson = json.encodeToString(BatchResolveRequest.serializer(), payload)
            val requestBody = bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .authenticate(token)
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val code = response.code
                    val location = response.header("Location").orEmpty()
                    val contentType = response.header("Content-Type").orEmpty().lowercase()
                    val cfRay = response.header("CF-Ray")
                    val wwwAuth = response.header("Www-Authenticate")
                    val bodyText = response.body?.string().orEmpty()

                    if (isCloudflareChallenge(code, location, contentType, cfRay, wwwAuth, bodyText)) {
                        return@withContext Result.failure(IllegalStateException("Cloudflare Access session required. Log in via CF Portal."))
                    }

                    if (!response.isSuccessful) {
                        return@withContext Result.failure(IllegalStateException("HTTP $code: ${bodyText.take(200)}"))
                    }

                    val parsed = json.decodeFromString(BatchResolveResponse.serializer(), bodyText)
                    for (result in parsed.results) {
                        val topCandidate = result.candidates.firstOrNull()
                        if (topCandidate != null) {
                            resultMap[result.id] = topCandidate.printingId
                        }
                    }
                }
            } catch (e: Exception) {
                val errorMsg = e.localizedMessage ?: e.javaClass.simpleName
                Log.e("DeckLotusApiClient", "POST $url failed: $errorMsg")
                return@withContext Result.failure(e)
            }
        }

        Result.success(resultMap)
    }

    /**
     * Download the latest 256-bit perceptual hash binary from GET /api/scan/hash-index.
     */
    suspend fun downloadHashIndex(
        baseUrl: String,
        token: String?,
        targetFile: File,
        onProgress: (Float) -> Unit = {}
    ): Result<Int> = withContext(Dispatchers.IO) {
        val cleanBase = baseUrl.trim().trimEnd('/')
        if (cleanBase.isBlank() || !cleanBase.startsWith("http")) {
            return@withContext Result.failure(IllegalArgumentException("Invalid server URL"))
        }

        val url = "$cleanBase/api/scan/hash-index"
        val request = Request.Builder()
            .url(url)
            .get()
            .authenticate(token)
            .build()

        val tmpFile = File(targetFile.parentFile ?: File("."), "${targetFile.name}.tmp")
        try {
            client.newCall(request).execute().use { response ->
                val code = response.code
                val location = response.header("Location").orEmpty()
                val contentType = response.header("Content-Type").orEmpty().lowercase()
                val cfRay = response.header("CF-Ray")
                val wwwAuth = response.header("Www-Authenticate")

                if (isCloudflareChallenge(code, location, contentType, cfRay, wwwAuth, "")) {
                    return@withContext Result.failure(IllegalStateException("Cloudflare Access session required. Log in via CF Portal."))
                }

                if (!response.isSuccessful) {
                    val bodySnippet = response.body?.string().orEmpty().take(200)
                    return@withContext Result.failure(IllegalStateException("HTTP $code: $bodySnippet"))
                }

                val body = response.body ?: return@withContext Result.failure(IllegalStateException("Empty response body from $url"))
                val contentLength = body.contentLength()
                var totalRead = 0L

                if (tmpFile.exists()) tmpFile.delete()
                FileOutputStream(tmpFile).use { output ->
                    val source = body.byteStream()
                    val buffer = ByteArray(8192)
                    var read: Int

                    while (source.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        totalRead += read
                        if (contentLength > 0) {
                            onProgress((totalRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f))
                        }
                    }
                    output.flush()
                }

                // Verify file header: magic 0x444c4348
                val headerBytes = ByteArray(16)
                java.io.FileInputStream(tmpFile).use { stream ->
                    val r = stream.read(headerBytes)
                    if (r < 16) throw IllegalArgumentException("Downloaded hash file is truncated")
                }
                val headerBuf = java.nio.ByteBuffer.wrap(headerBytes).order(java.nio.ByteOrder.BIG_ENDIAN)
                val magic = headerBuf.int
                if (magic != 0x444c4348) {
                    tmpFile.delete()
                    return@withContext Result.failure(IllegalArgumentException("Invalid hash index magic: $magic"))
                }
                val version = headerBuf.short
                val artBytes = headerBuf.get().toInt() and 0xFF
                val frameBytes = headerBuf.get().toInt() and 0xFF
                val count = headerBuf.int

                // Atomic replace
                if (targetFile.exists()) targetFile.delete()
                if (!tmpFile.renameTo(targetFile)) {
                    tmpFile.copyTo(targetFile, overwrite = true)
                    tmpFile.delete()
                }

                Log.i("DeckLotusApiClient", "✓ Downloaded hash index: $count card hashes ($totalRead bytes)")
                Result.success(count)
            }
        } catch (e: Exception) {
            if (tmpFile.exists()) tmpFile.delete()
            val msg = e.localizedMessage ?: e.javaClass.simpleName
            Log.e("DeckLotusApiClient", "Failed to download hash index: $msg")
            Result.failure(e)
        }
    }

    /**
     * Fetch identity payload from GET /api/scan/identity.
     */
    suspend fun fetchIdentityPayload(
        baseUrl: String,
        token: String?
    ): Result<ScanIdentityPayload> = withContext(Dispatchers.IO) {
        val cleanBase = baseUrl.trim().trimEnd('/')
        if (cleanBase.isBlank() || !cleanBase.startsWith("http")) {
            return@withContext Result.failure(IllegalArgumentException("Invalid server URL"))
        }

        val url = "$cleanBase/api/scan/identity"
        val request = Request.Builder()
            .url(url)
            .get()
            .authenticate(token)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val code = response.code
                val location = response.header("Location").orEmpty()
                val contentType = response.header("Content-Type").orEmpty().lowercase()
                val cfRay = response.header("CF-Ray")
                val wwwAuth = response.header("Www-Authenticate")

                if (isCloudflareChallenge(code, location, contentType, cfRay, wwwAuth, "")) {
                    return@withContext Result.failure(IllegalStateException("Cloudflare Access session required. Log in via CF Portal."))
                }

                if (!response.isSuccessful) {
                    val bodySnippet = response.body?.string().orEmpty().take(200)
                    return@withContext Result.failure(IllegalStateException("HTTP $code: $bodySnippet"))
                }

                val bodyText = response.body?.string().orEmpty()
                if (bodyText.isBlank()) {
                    return@withContext Result.failure(IllegalStateException("Empty identity payload from $url"))
                }

                val payload = json.decodeFromString(ScanIdentityPayload.serializer(), bodyText)
                if (payload.count <= 0) {
                    return@withContext Result.failure(IllegalStateException("Identity payload contained 0 cards"))
                }
                Result.success(payload)
            }
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: e.javaClass.simpleName
            Log.e("DeckLotusApiClient", "Failed to fetch identity payload: $msg")
            Result.failure(e)
        }
    }

    /**
     * Download and rebuild local SQLite card identity database from GET /api/scan/identity.
     */
    suspend fun syncIdentityDatabase(
        baseUrl: String,
        token: String?,
        targetDbFile: File,
        onProgress: (Float) -> Unit = {}
    ): Result<Int> = withContext(Dispatchers.IO) {
        onProgress(0.05f)
        val payloadResult = fetchIdentityPayload(baseUrl, token)
        if (payloadResult.isFailure) {
            return@withContext Result.failure(payloadResult.exceptionOrNull() ?: Exception("Failed to fetch identity payload"))
        }

        val payload = payloadResult.getOrThrow()
        val count = payload.count
        onProgress(0.3f)

        val tmpDbFile = File(targetDbFile.parentFile ?: File("."), "${targetDbFile.name}.tmp")
        try {
            // Build SQLite database
            if (tmpDbFile.exists()) tmpDbFile.delete()
            val db = SQLiteDatabase.openOrCreateDatabase(tmpDbFile, null)
            try {
                try {
                    db.rawQuery("PRAGMA synchronous = OFF", null)?.use { it.moveToFirst() }
                    db.rawQuery("PRAGMA journal_mode = MEMORY", null)?.use { it.moveToFirst() }
                } catch (_: Exception) {
                    // Non-critical performance optimizations
                }
                db.execSQL(
                    """
                    CREATE TABLE printings (
                            row_id INTEGER PRIMARY KEY,
                            printing_id INTEGER,
                            name TEXT,
                            set_code TEXT,
                            collector_number TEXT,
                            price_cents INTEGER,
                            price_type TEXT
                        );
                        """.trimIndent()
                    )

                    val stmt = db.compileStatement(
                        "INSERT INTO printings (row_id, printing_id, name, set_code, collector_number, price_cents, price_type) VALUES (?, ?, ?, ?, ?, ?, ?)"
                    )

                    val foilSet = payload.foilPriced.toHashSet()

                    db.beginTransaction()
                    try {
                        for (row in 0 until count) {
                            val printingId = payload.printingIds.getOrNull(row) ?: 0
                            val name = payload.names.getOrNull(row)
                            val setCode = payload.sets.getOrNull(row)
                            val collectorNumber = payload.collectors.getOrNull(row)
                            val priceCents = payload.prices.getOrNull(row)
                            val isFoilPriced = foilSet.contains(row)
                            val priceType = if (isFoilPriced) "foil" else if (priceCents != null) "normal" else null

                            stmt.clearBindings()
                            stmt.bindLong(1, row.toLong())
                            stmt.bindLong(2, printingId.toLong())
                            if (name != null) stmt.bindString(3, name) else stmt.bindNull(3)
                            if (setCode != null) stmt.bindString(4, setCode) else stmt.bindNull(4)
                            if (collectorNumber != null) stmt.bindString(5, collectorNumber) else stmt.bindNull(5)
                            if (priceCents != null) stmt.bindLong(6, priceCents.toLong()) else stmt.bindNull(6)
                            if (priceType != null) stmt.bindString(7, priceType) else stmt.bindNull(7)

                            stmt.executeInsert()

                            if (row % 10000 == 0) {
                                val prog = 0.3f + 0.5f * (row.toFloat() / count.toFloat())
                                onProgress(prog)
                            }
                        }
                        db.setTransactionSuccessful()
                    } finally {
                        db.endTransaction()
                    }

                    onProgress(0.85f)
                    db.execSQL("CREATE INDEX idx_name ON printings(name);")
                    db.execSQL("CREATE INDEX idx_set_collector ON printings(set_code, collector_number);")
                    onProgress(0.95f)
                } finally {
                    db.close()
                }

                // Atomic replace
                if (targetDbFile.exists()) targetDbFile.delete()
                if (!tmpDbFile.renameTo(targetDbFile)) {
                    tmpDbFile.copyTo(targetDbFile, overwrite = true)
                    tmpDbFile.delete()
                }

                onProgress(1.0f)
                Log.i("DeckLotusApiClient", "✓ Built local SQLite card database: $count printings indexed")
                Result.success(count)
        } catch (e: Exception) {
            if (tmpDbFile.exists()) tmpDbFile.delete()
            val msg = e.localizedMessage ?: e.javaClass.simpleName
            Log.e("DeckLotusApiClient", "Failed to sync identity database: $msg")
            Result.failure(e)
        }
    }

    /**
     * Complete full sync of both card hashes binary and card identities SQLite database.
     */
    suspend fun syncCardResources(
        baseUrl: String,
        token: String?,
        context: Context,
        onProgress: (step: String, progress: Float) -> Unit = { _, _ -> }
    ): Result<SyncSummary> = withContext(Dispatchers.IO) {
        val cleanBase = baseUrl.trim().trimEnd('/')
        if (cleanBase.isBlank() || !cleanBase.startsWith("http")) {
            return@withContext Result.failure(IllegalArgumentException("Set a valid Server Base URL first"))
        }

        // 1. Check server maintenance
        val maintenance = maintenanceStatus(cleanBase)
        if (maintenance?.blocksWrites == true) {
            val what = maintenance.label ?: "A card database update"
            return@withContext Result.failure(IllegalStateException("$what is currently running on the server. Please retry once server maintenance finishes."))
        }

        // 2. Download card-hashes.bin
        onProgress("Downloading art hash index...", 0.05f)
        val hashFile = File(context.filesDir, "card-hashes.bin")
        val hashResult = downloadHashIndex(cleanBase, token, hashFile) { p ->
            onProgress("Downloading art hash index (${(p * 100).toInt()}%)...", p * 0.4f)
        }

        if (hashResult.isFailure) {
            return@withContext Result.failure(hashResult.exceptionOrNull() ?: Exception("Failed to download hash index"))
        }
        val hashesCount = hashResult.getOrThrow()

        // 3. Download & Build card-identities.db
        onProgress("Downloading card identities...", 0.45f)
        val dbFile = File(context.filesDir, "card-identities.db")
        val dbResult = syncIdentityDatabase(cleanBase, token, dbFile) { p ->
            val stepLabel = if (p < 0.3f) "Downloading identities..." else if (p < 0.85f) "Indexing card database..." else "Finalizing indexes..."
            onProgress(stepLabel, 0.4f + p * 0.6f)
        }

        if (dbResult.isFailure) {
            return@withContext Result.failure(dbResult.exceptionOrNull() ?: Exception("Failed to sync identity database"))
        }
        val printingsCount = dbResult.getOrThrow()

        // 4. Save metadata in SharedPreferences
        val prefs = context.getSharedPreferences("card_db_prefs", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        prefs.edit()
            .putLong("last_sync_timestamp", now)
            .putInt("synced_card_count", printingsCount)
            .putInt("installed_version", 4)
            .apply()

        val totalBytes = (if (hashFile.exists()) hashFile.length() else 0L) + (if (dbFile.exists()) dbFile.length() else 0L)
        Log.i("DeckLotusApiClient", "✓ Resource sync completed: $hashesCount hashes, $printingsCount printings, ${totalBytes / 1024} KB total")

        Result.success(
            SyncSummary(
                hashesCount = hashesCount,
                printingsCount = printingsCount,
                totalBytes = totalBytes
            )
        )
    }
}

@Serializable
data class ScanIdentityPayload(
    val version: Int = 2,
    val count: Int = 0,
    val printingIds: List<Int?> = emptyList(),
    val cardIds: List<Int?> = emptyList(),
    val names: List<String?> = emptyList(),
    val sets: List<String?> = emptyList(),
    val collectors: List<String?> = emptyList(),
    val promos: List<Int> = emptyList(),
    val prices: List<Int?> = emptyList(),
    val foilPriced: List<Int> = emptyList()
)

data class SyncSummary(
    val hashesCount: Int,
    val printingsCount: Int,
    val totalBytes: Long
)
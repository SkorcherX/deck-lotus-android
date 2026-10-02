package com.decklotus.companion.network

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DeckLotusApiClientTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var apiClient: DeckLotusApiClient

    @Before
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()
        apiClient = DeckLotusApiClient()
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
    }

    @Test
    fun testCloudflareChallengeOn302RedirectToAccessDomain() {
        val isChallenge = DeckLotusApiClient.isCloudflareChallenge(
            code = 302,
            location = "https://skorcherx.cloudflareaccess.com/cdn-cgi/access/login/mtg.skorcherx.com?kid=123",
            contentType = "text/html; charset=UTF-8",
            cfRay = "a35fe11e5f5cc74a-SEA",
            wwwAuth = "Cloudflare-Access resource_metadata=\"https://mtg.skorcherx.com/...\"",
            bodyText = "<html><head><title>302 Found</title></head><body>cloudflare</body></html>"
        )
        assertTrue(isChallenge)
    }

    @Test
    fun testCloudflareChallengeOn302WithWwwAuthenticate() {
        val isChallenge = DeckLotusApiClient.isCloudflareChallenge(
            code = 302,
            location = "",
            contentType = "text/html",
            cfRay = null,
            wwwAuth = "Cloudflare-Access resource_metadata=\"...\"",
            bodyText = ""
        )
        assertTrue(isChallenge)
    }

    @Test
    fun testCloudflareChallengeOn403WithCfRay() {
        val isChallenge = DeckLotusApiClient.isCloudflareChallenge(
            code = 403,
            location = "",
            contentType = "text/html",
            cfRay = "a35fe11e5f5cc74a-SEA",
            wwwAuth = null,
            bodyText = "Access Denied"
        )
        assertTrue(isChallenge)
    }

    @Test
    fun testCloudflareChallengeOnHtmlWithCloudflareBody() {
        val isChallenge = DeckLotusApiClient.isCloudflareChallenge(
            code = 403,
            location = "",
            contentType = "text/html",
            cfRay = null,
            wwwAuth = null,
            bodyText = "Please log in via cloudflareaccess.com"
        )
        assertTrue(isChallenge)
    }

    @Test
    fun testNonCloudflareErrorsReturnFalse() {
        val isChallenge400 = DeckLotusApiClient.isCloudflareChallenge(
            code = 400,
            location = "",
            contentType = "application/json",
            cfRay = null,
            wwwAuth = null,
            bodyText = "{\"error\":\"Invalid payload\"}"
        )
        assertFalse(isChallenge400)

        val isChallenge500 = DeckLotusApiClient.isCloudflareChallenge(
            code = 500,
            location = "",
            contentType = "application/json",
            cfRay = null,
            wwwAuth = null,
            bodyText = "{\"error\":\"Internal error\"}"
        )
        assertFalse(isChallenge500)
    }

    @Test
    fun testCommitOutcomeCleanSuccessWithCloudflareFlag() {
        val successOutcome = CommitOutcome(
            addedCopies = 10,
            rejected = emptyList(),
            uncommitted = emptyList(),
            batchIds = listOf("batch-123"),
            transportError = null,
            isCloudflareAuthRequired = false
        )
        assertTrue(successOutcome.isCleanSuccess)

        val cfBlockedOutcome = CommitOutcome(
            addedCopies = 0,
            rejected = emptyList(),
            uncommitted = listOf(InventoryBulkAddItem(cardName = "Black Lotus")),
            batchIds = emptyList(),
            transportError = "Cloudflare Access session expired",
            isCloudflareAuthRequired = true
        )
        assertFalse(cfBlockedOutcome.isCleanSuccess)
    }

    @Test
    fun testFetchUserDecksSuccess() = runBlocking {
        val jsonResponse = """
            {
              "decks": [
                {
                  "id": 1,
                  "name": "Atraxa Superfriends",
                  "format": "commander",
                  "description": "Proliferate planeswalkers",
                  "status": "building",
                  "mainboard_count": 99,
                  "sideboard_count": 0,
                  "maybeboard_count": 5
                },
                {
                  "id": 2,
                  "name": "Burn",
                  "format": "modern",
                  "description": null,
                  "status": "ready",
                  "mainboard_count": 60,
                  "sideboard_count": 15,
                  "maybeboard_count": 0
                }
              ]
            }
        """.trimIndent()

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(jsonResponse)
        )

        val baseUrl = mockServer.url("/").toString()
        val result = apiClient.fetchUserDecks(baseUrl, "test-token")

        assertTrue(result.isSuccess)
        val decks = result.getOrNull()
        assertNotNull(decks)
        assertEquals(2, decks?.size)
        assertEquals("Atraxa Superfriends", decks?.get(0)?.name)
        assertEquals(99, decks?.get(0)?.mainboardCount)
        assertEquals("Commander", decks?.get(0)?.formatDisplayName)
        assertEquals("Burn", decks?.get(1)?.name)
        assertEquals(75, decks?.get(1)?.totalCount)

        val recordedRequest = mockServer.takeRequest()
        assertEquals("/api/decks", recordedRequest.path)
        assertEquals("GET", recordedRequest.method)
        assertEquals("test-token", recordedRequest.getHeader("X-API-Key"))
    }

    @Test
    fun testCreateDeckSuccess() = runBlocking {
        val jsonResponse = """
            {
              "deck": {
                "id": 42,
                "name": "Urza High Lord Artificer",
                "format": "commander",
                "description": "Artifact combo",
                "status": "building",
                "mainboard_count": 0,
                "sideboard_count": 0,
                "maybeboard_count": 0
              }
            }
        """.trimIndent()

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody(jsonResponse)
        )

        val baseUrl = mockServer.url("/").toString()
        val result = apiClient.createDeck(baseUrl, "test-token", "Urza High Lord Artificer", "commander", "Artifact combo")

        assertTrue(result.isSuccess)
        val deck = result.getOrNull()
        assertNotNull(deck)
        assertEquals(42, deck?.id)
        assertEquals("Urza High Lord Artificer", deck?.name)

        val recordedRequest = mockServer.takeRequest()
        assertEquals("/api/decks", recordedRequest.path)
        assertEquals("POST", recordedRequest.method)
        assertTrue(recordedRequest.body.readUtf8().contains("Urza High Lord Artificer"))
    }

    @Test
    fun testCheckShortfallSuccess() = runBlocking {
        val jsonResponse = """
            {
              "shortfalls": [
                {
                  "printingId": 1234,
                  "isFoil": false,
                  "needed": 2,
                  "owned": 1,
                  "short": 1
                }
              ]
            }
        """.trimIndent()

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(jsonResponse)
        )

        val baseUrl = mockServer.url("/").toString()
        val items = listOf(ScanShortfallItem(printingId = 1234, quantity = 2, isFoil = false))
        val result = apiClient.checkShortfall(baseUrl, "test-token", items)

        assertTrue(result.isSuccess)
        val shortfalls = result.getOrNull()
        assertNotNull(shortfalls)
        assertEquals(1, shortfalls?.size)
        assertEquals(1234, shortfalls?.get(0)?.printingId)
        assertEquals(1, shortfalls?.get(0)?.short)

        val recordedRequest = mockServer.takeRequest()
        assertEquals("/api/scan/shortfall", recordedRequest.path)
        assertEquals("POST", recordedRequest.method)
    }

    @Test
    fun testCommitBatchToDeckSuccess() = runBlocking {
        val jsonResponse = """
            {
              "batchId": "scan-1727451234-abc",
              "deckId": 42,
              "cards": 3,
              "committed": 3,
              "addedToCollection": {
                "batchId": "scan-1727451234-abc",
                "cards": 1,
                "committed": 1
              }
            }
        """.trimIndent()

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(jsonResponse)
        )

        val baseUrl = mockServer.url("/").toString()
        val items = listOf(
            ScanCommitItem(printingId = 101, quantity = 1, isFoil = false, boardType = "mainboard", isCommander = true),
            ScanCommitItem(printingId = 102, quantity = 2, isFoil = true, boardType = "sideboard", isCommander = false)
        )
        val outcome = apiClient.commitBatchToDeck(baseUrl, "test-token", deckId = 42, items = items, alsoAddToCollection = true)

        assertTrue(outcome.isCleanSuccess)
        assertEquals(3, outcome.committedCards)
        assertEquals(3, outcome.totalCopies)
        assertEquals(42, outcome.deckId)
        assertEquals("scan-1727451234-abc", outcome.batchId)
        assertEquals(1, outcome.addedToCollectionCopies)

        val recordedRequest = mockServer.takeRequest()
        assertEquals("/api/scan/commit", recordedRequest.path)
        assertEquals("POST", recordedRequest.method)
        val bodyText = recordedRequest.body.readUtf8()
        assertTrue(bodyText.contains("\"destination\":\"deck\""))
        assertTrue(bodyText.contains("\"deckId\":42"))
        assertTrue(bodyText.contains("\"alsoAddToCollection\":true"))
        assertTrue(bodyText.contains("\"isCommander\":true"))
    }

    @Test
    fun testResolveBatchScansSuccess() = runBlocking {
        val jsonResponse = """
            {
              "results": [
                {
                  "id": "item-1",
                  "tier": "confident",
                  "candidates": [
                    {
                      "printingId": 9999,
                      "cardId": 123,
                      "name": "Sol Ring",
                      "setCode": "C18",
                      "collectorNumber": "222"
                    }
                  ]
                },
                {
                  "id": "item-2",
                  "tier": "probable",
                  "candidates": [
                    {
                      "printingId": 8888,
                      "cardId": 456,
                      "name": "Command Tower",
                      "setCode": "CMR",
                      "collectorNumber": "350"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(jsonResponse)
        )

        val baseUrl = mockServer.url("/").toString()
        val items = listOf(
            BatchResolveScanItem(id = "item-1", name = "Sol Ring", setCode = "C18", collectorNumber = "222"),
            BatchResolveScanItem(id = "item-2", name = "Command Tower", setCode = "CMR", collectorNumber = "350")
        )
        val result = apiClient.resolveBatchScans(baseUrl, "test-token", items)

        assertTrue(result.isSuccess)
        val map = result.getOrNull()
        assertNotNull(map)
        assertEquals(2, map?.size)
        assertEquals(9999, map?.get("item-1"))
        assertEquals(8888, map?.get("item-2"))

        val recordedRequest = mockServer.takeRequest()
        assertEquals("/api/scan/resolve", recordedRequest.path)
        assertEquals("POST", recordedRequest.method)
        val bodyText = recordedRequest.body.readUtf8()
        assertTrue(bodyText.contains("Sol Ring"))
        assertTrue(bodyText.contains("Command Tower"))
    }

    @Test
    fun testDownloadHashIndexSuccess() = runBlocking {
        // Construct valid 16-byte binary header: magic 0x444c4348, version 1, art 32, frame 8, count 1
        val headerBytes = ByteArray(16)
        val buf = java.nio.ByteBuffer.wrap(headerBytes).order(java.nio.ByteOrder.BIG_ENDIAN)
        buf.putInt(0x444c4348)
        buf.putShort(1.toShort())
        buf.put(32.toByte())
        buf.put(8.toByte())
        buf.putInt(1) // 1 row

        // 1 row: 16 bytes UUID + 32 bytes Art Hash + 8 bytes Frame Hash = 56 bytes
        val rowBytes = ByteArray(56)
        val totalPayload = headerBytes + rowBytes

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/octet-stream")
                .setBody(okio.Buffer().write(totalPayload))
        )

        val baseUrl = mockServer.url("/").toString()
        val tempFile = java.io.File.createTempFile("test-hashes", ".bin")
        tempFile.deleteOnExit()

        var progressReported = 0f
        val result = apiClient.downloadHashIndex(baseUrl, "test-token", tempFile) { p ->
            progressReported = p
        }

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())
        assertTrue(tempFile.exists())
        assertEquals(72, tempFile.length()) // 16 + 56 = 72

        val recordedRequest = mockServer.takeRequest()
        assertEquals("/api/scan/hash-index", recordedRequest.path)
        assertEquals("GET", recordedRequest.method)
    }

    @Test
    fun testFetchIdentityPayloadSuccess() = runBlocking {
        val identityJson = """
            {
              "version": 2,
              "count": 2,
              "printingIds": [101, 102],
              "cardIds": [1, 2],
              "names": ["Lightning Bolt", "Counterspell"],
              "sets": ["FRA", "FRA"],
              "collectors": ["1", "2"],
              "promos": [],
              "prices": [199, 250],
              "foilPriced": []
            }
        """.trimIndent()

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(identityJson)
        )

        val baseUrl = mockServer.url("/").toString()
        val result = apiClient.fetchIdentityPayload(baseUrl, "test-token")

        assertTrue(result.isSuccess)
        val payload = result.getOrNull()
        assertNotNull(payload)
        assertEquals(2, payload?.count)
        assertEquals(2, payload?.version)
        assertEquals(listOf(101, 102), payload?.printingIds)
        assertEquals(listOf("Lightning Bolt", "Counterspell"), payload?.names)
        assertEquals(listOf("FRA", "FRA"), payload?.sets)
        assertEquals(listOf("1", "2"), payload?.collectors)
        assertEquals(listOf(199, 250), payload?.prices)

        val recordedRequest = mockServer.takeRequest()
        assertEquals("/api/scan/identity", recordedRequest.path)
        assertEquals("GET", recordedRequest.method)
    }
}

package com.decklotus.companion.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckLotusApiClientTest {

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
}

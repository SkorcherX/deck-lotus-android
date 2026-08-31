package com.decklotus.companion

import com.decklotus.companion.vision.CollectorOcr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectorOcrTest {

    @Test
    fun testParseStandardCollectorBlock() {
        val lines = listOf("ECC \u2022 EN", "0001")
        val parsed = CollectorOcr.parseRawCollectorLines(lines)

        assertEquals("ECC", parsed.setCode)
        assertEquals("0001", parsed.collectorNumber)
        assertEquals("EN", parsed.language)
        assertEquals(false, parsed.isFoil)
    }

    @Test
    fun testParseCollectorWithTotalAndFoilStar() {
        val lines = listOf("FDN \u2022 EN \u2605", "0015/0280 R")
        val parsed = CollectorOcr.parseRawCollectorLines(lines)

        assertEquals("FDN", parsed.setCode)
        assertEquals("0015", parsed.collectorNumber)
        assertEquals("EN", parsed.language)
        assertEquals(true, parsed.isFoil)
    }

    @Test
    fun testParseModernHorizons3() {
        val lines = listOf("MH3 \u00B7 EN", "0123")
        val parsed = CollectorOcr.parseRawCollectorLines(lines)

        assertEquals("MH3", parsed.setCode)
        assertEquals("0123", parsed.collectorNumber)
        assertEquals("EN", parsed.language)
    }

    @Test
    fun testParseCombinedSetNumber() {
        val lines = listOf("WOE 0045")
        val parsed = CollectorOcr.parseRawCollectorLines(lines)

        assertEquals("WOE", parsed.setCode)
        assertEquals("0045", parsed.collectorNumber)
    }
}
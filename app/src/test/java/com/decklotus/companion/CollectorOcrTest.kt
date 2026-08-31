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
    fun testParseSOA() {
        val lines = listOf("U 0045", "SOA \u2022 EN LORENZO GAGGIOTTI", "\u2122 & \u00A9 2024 Wizards of the Coast")
        val parsed = CollectorOcr.parseRawCollectorLines(lines)

        assertEquals("SOA", parsed.setCode)
        assertEquals("0045", parsed.collectorNumber)
        assertEquals("EN", parsed.language)
    }

    @Test
    fun testParseBloomburrowPipeSeparator() {
        val lines = listOf("BLB | EN", "0123/0281")
        val parsed = CollectorOcr.parseRawCollectorLines(lines)

        assertEquals("BLB", parsed.setCode)
        assertEquals("0123", parsed.collectorNumber)
        assertEquals("EN", parsed.language)
    }

    @Test
    fun testParseOutlawsSpaceSeparator() {
        val lines = listOf("OTJ EN", "0245")
        val parsed = CollectorOcr.parseRawCollectorLines(lines)

        assertEquals("OTJ", parsed.setCode)
        assertEquals("0245", parsed.collectorNumber)
        assertEquals("EN", parsed.language)
    }
}
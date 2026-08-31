package com.decklotus.companion

import com.decklotus.companion.vision.CardSettleDetector
import com.decklotus.companion.vision.SettleState
import org.junit.Assert.*
import org.junit.Test

class CardSettleDetectorTest {

    @Test
    fun testSettleDetectorStateTransitions() {
        val detector = CardSettleDetector(
            motionThreshold = 10.0f,
            settleThreshold = 3.0f,
            settleDurationMs = 100L
        )

        assertEquals(SettleState.WAITING_FOR_CARD, detector.state)

        val emptyGrid = FloatArray(256) { 50.0f }
        val cardMotionGrid = FloatArray(256) { 180.0f }
        val cardSettledGrid = FloatArray(256) { 181.0f } // Low variance between stationary frames

        // Frame 1: empty cradle baseline
        var triggered = detector.processLumaGrid(emptyGrid, 1000L)
        assertFalse(triggered)
        assertEquals(SettleState.WAITING_FOR_CARD, detector.state)

        // Frame 2: card drops into cradle (motion!)
        triggered = detector.processLumaGrid(cardMotionGrid, 1030L)
        assertFalse(triggered)
        assertEquals(SettleState.CARD_MOVING, detector.state)

        // Frame 3: card stops moving (settling begins)
        triggered = detector.processLumaGrid(cardSettledGrid, 1060L)
        assertFalse(triggered)
        assertEquals(SettleState.CARD_SETTLING, detector.state)

        // Frame 4: card remains stationary before duration expires
        triggered = detector.processLumaGrid(cardSettledGrid, 1120L)
        assertFalse(triggered)
        assertEquals(SettleState.CARD_SETTLING, detector.state)

        // Frame 5: card stationary >= 100ms -> SETTLED!
        triggered = detector.processLumaGrid(cardSettledGrid, 1180L)
        assertTrue(triggered)
        assertEquals(SettleState.CARD_SETTLED, detector.state)

        // Mark captured -> LOCKED
        detector.markCaptured()
        assertEquals(SettleState.LOCKED_AFTER_SCAN, detector.state)

        // Same stationary card produces no re-trigger
        triggered = detector.processLumaGrid(cardSettledGrid, 1200L)
        assertFalse(triggered)
        assertEquals(SettleState.LOCKED_AFTER_SCAN, detector.state)

        // Frame 6: new card dropped (motion delta again!)
        val nextCardMotionGrid = FloatArray(256) { 90.0f }
        triggered = detector.processLumaGrid(nextCardMotionGrid, 1300L)
        assertFalse(triggered)
        assertEquals(SettleState.CARD_MOVING, detector.state)
    }
}
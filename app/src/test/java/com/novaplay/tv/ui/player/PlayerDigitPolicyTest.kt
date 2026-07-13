package com.novaplay.tv.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerDigitPolicyTest {

    @Test
    fun loneZeroRecallsThePreviousChannel() {
        assertEquals(PlayerDigitAction.Recall, PlayerDigitPolicy.interpret("0"))
    }

    @Test
    fun numbersJumpToTheChannel() {
        assertEquals(PlayerDigitAction.JumpToNumber(7), PlayerDigitPolicy.interpret("7"))
        assertEquals(PlayerDigitAction.JumpToNumber(1042), PlayerDigitPolicy.interpret("1042"))
        // Leading zeros still parse to the channel number, not a recall.
        assertEquals(PlayerDigitAction.JumpToNumber(12), PlayerDigitPolicy.interpret("012"))
    }

    @Test
    fun uselessBuffersDoNothing() {
        assertEquals(PlayerDigitAction.None, PlayerDigitPolicy.interpret(""))
        assertEquals(PlayerDigitAction.None, PlayerDigitPolicy.interpret("00"))
        assertEquals(PlayerDigitAction.None, PlayerDigitPolicy.interpret("x1"))
    }
}

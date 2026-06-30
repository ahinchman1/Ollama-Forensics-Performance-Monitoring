package com.codingkinetics.com.ollama_perf_monitor_desktop.util

import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals

class ExtensionUtilsTest {

    @Test
    fun `nanosToSeconds should correctly convert nanoseconds to seconds`() {
        assertEquals(0.0, 0L.nanosToSeconds(), 0.0001)
        assertEquals(1.0, 1_000_000_000L.nanosToSeconds(), 0.0001)
        assertEquals(2.5, 2_500_000_000L.nanosToSeconds(), 0.0001)
        assertEquals(0.000000001, 1L.nanosToSeconds(), 0.0000000001)
    }

    @Test
    fun `roverage should return average for non-empty lists`() {
        val numbers = listOf(1, 2, 3, 4, 5)
        assertEquals(3.0, numbers.roverage(), 0.0001)
    }

    @Test
    fun `roverage should return exact decimal average when result is a fraction`() {
        val numbers = listOf(1, 2)
        assertEquals(1.5, numbers.roverage(), 0.0001)
    }

    @Test
    fun `roverage should return 0 point 0 when list is empty`() {
        val emptyList = emptyList<Int>()
        assertEquals(0.0, emptyList.roverage(), 0.0001)
    }

    @Test
    fun `isBraille should return true for characters within the Braille range`() {
        // U+2800 is the blank Braille pattern (start of block)
        assertTrue('\u2800'.isBraille())

        // U+2813 is an arbitrary pattern inside the block
        assertTrue('\u2813'.isBraille())

        // U+28FF is the full 8-dot pattern (end of block)
        assertTrue('\u28FF'.isBraille())
    }

    @Test
    fun `isBraille should return false for characters outside the Braille range`() {
        // Standard ASCII characters
        assertFalse('A'.isBraille())
        assertFalse('1'.isBraille())
        assertFalse(' '.isBraille())

        // Borderline characters just outside the block boundaries
        assertFalse(('\u2800'.code - 1).toChar().isBraille()) // U+27FF
        assertFalse(('\u28FF'.code + 1).toChar().isBraille()) // U+2900
    }
}
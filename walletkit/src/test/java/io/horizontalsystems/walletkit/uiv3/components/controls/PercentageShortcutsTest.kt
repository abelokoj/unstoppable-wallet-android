package io.horizontalsystems.walletkit.uiv3.components.controls

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PercentageShortcutsTest {
    @Test
    fun `custom percentage accepts editable decimal values up to 100`() {
        listOf("", ".", "0", "0.01", ".5", "25", "99.99", "100").forEach {
            assertTrue(it, isAcceptablePercentInput(it))
        }
    }

    @Test
    fun `custom percentage rejects out of range and malformed values`() {
        listOf("-1", "100.0", "100.01", "101", "1000", "1.234", "1e2", "1,5", "abc").forEach {
            assertFalse(it, isAcceptablePercentInput(it))
        }
    }
}

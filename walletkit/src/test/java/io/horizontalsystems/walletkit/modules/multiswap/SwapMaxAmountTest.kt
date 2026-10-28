package io.horizontalsystems.walletkit.modules.multiswap

import io.horizontalsystems.marketkit.models.TokenType
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class SwapMaxAmountTest {
    @Test fun `native full balance is reduced to the fee-aware amount and requoted before ready`() {
        val maximum = SwapMaxAmount(BigDecimal.ONE, 18)
        assertEquals(BigDecimal("0.997"), maximum.reconcile(BigDecimal("0.997"), false))
        assertFalse(maximum.ready)
        assertNull(maximum.reconcile(BigDecimal("0.997"), true))
        assertTrue(maximum.ready)
        // A later gas increase must invalidate the previous signable amount.
        assertEquals(BigDecimal("0.996"), maximum.reconcile(BigDecimal("0.996"), false))
        assertFalse(maximum.ready)
        assertNull(maximum.reconcile(BigDecimal("0.996"), true))
        assertTrue(maximum.ready)
    }

    @Test fun `missing estimates use bounded unsigned probes and never guess a fee`() {
        val maximum = SwapMaxAmount(BigDecimal.ONE, 18)
        assertEquals(BigDecimal("0.5"), maximum.reconcile(null, false))
        assertFalse(maximum.ready)
        assertEquals(BigDecimal("0.998"), maximum.reconcile(BigDecimal("0.998"), true))
        assertFalse(maximum.ready)
        assertNull(maximum.reconcile(BigDecimal("0.998"), true))
        assertTrue(maximum.ready)
        val matchedProbe = SwapMaxAmount(BigDecimal.ONE, 18)
        matchedProbe.reconcile(null, false)
        assertNull(matchedProbe.reconcile(BigDecimal("0.5"), true))
        assertTrue(matchedProbe.ready)
        val unavailable = SwapMaxAmount(BigDecimal.ONE, 18)
        repeat(4) { assertNotNull(unavailable.reconcile(null, false)) }
        assertNull(unavailable.reconcile(null, false))
        assertTrue(unavailable.failed)
        assertFalse(unavailable.ready)
    }

    @Test fun `unaffordable or unsendable routes fail closed with amounts rounded down`() {
        val maximum = SwapMaxAmount(BigDecimal("1"), 6)
        assertEquals(BigDecimal("0.999999"), maximum.reconcile(BigDecimal("0.9999999"), false))
        assertNull(maximum.reconcile(BigDecimal.ZERO, false))
        assertTrue(maximum.failed)
        maximum.reset()
        assertNull(maximum.reconcile(BigDecimal.ONE, false))
        assertFalse(maximum.ready)
        assertTrue(maximum.failed)
        val tiny = SwapMaxAmount(BigDecimal("0.000001"), 6)
        assertNull(tiny.reconcile(null, false))
        assertTrue(tiny.failed)
    }

    @Test fun `maximum never spends newly received funds or changes nonnative approval behavior`() {
        val maximum = SwapMaxAmount(BigDecimal.ONE, 18)
        assertNull(maximum.reconcile(BigDecimal("2"), true))
        assertEquals(0, maximum.amount.compareTo(BigDecimal.ONE))
        assertTrue(swapFeePaidFromAsset(TokenType.Native))
        assertFalse(swapFeePaidFromAsset(TokenType.Eip20("0x" + "a".repeat(40))))
    }
}

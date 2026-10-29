package io.horizontalsystems.walletkit.modules.multiswap

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class SwapRouteAmountTest {
    @Test fun `large route amounts retain four decimals to distinguish quotes`() {
        for ((value, mantissa) in listOf(
            "11490524519035.5023" to "11.4905",
            "11470077839410.4633" to "11.4700",
            "11375619273845.1473" to "11.3756",
        )) {
            assertEquals(BigDecimal(mantissa).stripTrailingZeros() to "T", compactSwapRouteAmount(BigDecimal(value)))
        }
    }

    @Test fun `compact amounts never round up the quoted output`() {
        val source = BigDecimal("11470077839410.4633")
        val compact = compactSwapRouteAmount(source)!!
        assertTrue(compact.first.movePointRight(12) <= source)
        assertEquals("11470077839410.4633", source.toPlainString())
        assertNotEquals(compact, compactSwapRouteAmount(BigDecimal("11470177839410.4633")))
    }

    @Test fun `magnitude boundaries retain their actual unit`() {
        for ((value, mantissa, suffix) in listOf(
            Triple("1000", "1", "K"),
            Triple("999999.99999", "999.9999", "K"),
            Triple("1000000", "1", "M"),
            Triple("1000000000", "1", "B"),
            Triple("1000000000000", "1", "T"),
            Triple("1000000000000000", "1", "Q"),
        )) {
            assertEquals(BigDecimal(mantissa).stripTrailingZeros() to suffix, compactSwapRouteAmount(BigDecimal(value)))
        }
    }

    @Test fun `ordinary and tiny amounts keep the existing full formatter`() {
        for (value in listOf("0", "0.000000000000000001", "0.123456", "999.999999", "-1000")) {
            assertNull(compactSwapRouteAmount(BigDecimal(value)))
        }
    }

    @Test fun `amounts beyond quadrillion use bounded scientific notation`() {
        assertEquals(BigDecimal("1.2345") to "E32", compactSwapRouteAmount(BigDecimal("1.23456789E32")))
        assertEquals(BigDecimal.ONE to "E400", compactSwapRouteAmount(BigDecimal("1E400")))
    }
}

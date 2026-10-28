package io.horizontalsystems.walletkit.modules.privatesend

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class PrivateSendMaximumTest {
    @Test fun `maximum reserves provider fee and refundable buffer before committing an order`() {
        val output = privateSendMaximumOutput(BigDecimal("1"), BigDecimal("1.025"), BigDecimal("1"), 18)
        assertEquals(BigDecimal("0.975"), output)
        // A smaller exact-output quote fits; its output is kept rather than treating the buffer as free balance.
        assertEquals(output, privateSendMaximumOutput(output, BigDecimal("0.99975"), BigDecimal.ONE, 18))
    }

    @Test fun `network fee reserve is deducted from the source budget and token units round down`() {
        assertEquals(BigDecimal("0.973"), privateSendMaximumOutput(BigDecimal.ONE, BigDecimal("1.025"), BigDecimal("0.998"), 18))
        assertEquals(BigDecimal("0.999999"), privateSendMaximumOutput(BigDecimal.ONE, BigDecimal("1.0000001"), BigDecimal.ONE, 6))
    }

    @Test fun `missing unaffordable or pathological quote costs cannot create a maximum`() {
        listOf(BigDecimal.ZERO, BigDecimal("-1"), BigDecimal("1E100000"), BigDecimal("1E-100000")).forEach { required ->
            assertThrows(PrivateSendError.MaxUnavailable::class.java) {
                privateSendMaximumOutput(BigDecimal.ONE, required, BigDecimal.ONE, 18)
            }
        }
        assertThrows(PrivateSendError.MaxUnavailable::class.java) {
            privateSendMaximumOutput(BigDecimal.ONE, BigDecimal("3"), BigDecimal.ONE, 18)
        }
        assertThrows(PrivateSendError.MaxUnavailable::class.java) {
            privateSendMaximumOutput(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, 18)
        }
    }
}

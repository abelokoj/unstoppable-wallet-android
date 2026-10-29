package io.horizontalsystems.walletkit.modules.coin

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

class TokenSupplyRpcTest {
    @Test fun `EVM supply accepts one exact unsigned ABI word without floating point conversion`() {
        val maximum = BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE)
        assertEquals(BigDecimal(maximum, 18), TokenSupplyRpc.evmAmount("0x" + maximum.toString(16), 18))
        assertEquals(BigDecimal.ZERO, TokenSupplyRpc.evmAmount("0x" + "0".repeat(64), 0))
        for (invalid in listOf("0x", "0x1", "0x" + "0".repeat(63), "0x" + "f".repeat(66), "0x" + "z".repeat(64))) {
            assertThrows(IllegalArgumentException::class.java) { TokenSupplyRpc.evmAmount(invalid, 18) }
        }
        assertThrows(IllegalArgumentException::class.java) { TokenSupplyRpc.evmAmount("0x" + "0".repeat(64), 256) }
    }

    @Test fun `SPL supply uses exact amount and rejects mismatched decimals and invalid integers`() {
        fun response(amount: String, decimals: String = "6") = JsonParser.parseString("""{"value":{"amount":"$amount","decimals":$decimals,"uiAmount":0}}""").asJsonObject
        assertEquals(BigDecimal("1.234567"), TokenSupplyRpc.splAmount(response("1234567"), 6))
        assertEquals(BigDecimal(BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE), 6),
            TokenSupplyRpc.splAmount(response("18446744073709551615"), 6))
        for (invalid in listOf("-1", "1.5", "1e4", "", "١٢٣", "18446744073709551616")) {
            assertThrows(IllegalArgumentException::class.java) { TokenSupplyRpc.splAmount(response(invalid), 6) }
        }
        assertThrows(IllegalArgumentException::class.java) { TokenSupplyRpc.splAmount(response("1", "9"), 6) }
        assertThrows(IllegalArgumentException::class.java) { TokenSupplyRpc.splAmount(response("1", "6.5"), 6) }
    }
}

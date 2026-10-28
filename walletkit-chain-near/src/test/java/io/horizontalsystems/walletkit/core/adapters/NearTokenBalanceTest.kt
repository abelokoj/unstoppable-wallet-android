package io.horizontalsystems.walletkit.core.adapters

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigInteger

class NearTokenBalanceTest {
    @Test
    fun `accepts exact unsigned contract balances through U128 maximum`() {
        for (amount in listOf("0", "1000000", "340282366920938463463374607431768211455")) {
            assertEquals(BigInteger(amount), verifiedNearTokenBalance(JsonParser.parseString("\"$amount\"")))
        }
    }

    @Test
    fun `rejects malformed numeric and out of range balances rather than using a fallback`() {
        for (json in listOf("null", "{}", "[]", "123", "true", "\"-1\"", "\"1.5\"", "\"1e6\"", "\"01\"",
            "\"340282366920938463463374607431768211456\"")) {
            assertThrows(IllegalArgumentException::class.java) { verifiedNearTokenBalance(JsonParser.parseString(json)) }
        }
    }
}

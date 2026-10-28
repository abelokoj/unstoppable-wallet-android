package io.horizontalsystems.walletkit.modules.multiswap.providers

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Test
import org.web3j.utils.Numeric
import java.math.BigInteger

class PancakeNonEvmQuoteTest {
    private val gson = Gson()
    private fun fixture(name: String) = gson.fromJson(javaClass.classLoader!!.getResource("pancake-relay-$name.json")!!.readText(), JsonObject::class.java)
    private fun parse(q: JsonObject) = PancakeNonEvmQuote.parse(q, q["originChainId"].asLong, q["destinationChainId"].asLong,
        q["inputToken"].asString, q["outputToken"].asString, q["amount"].asString.toBigInteger(), 100)

    @Test fun `native SOL and ERC20 routes accept captured zero wallet fee quotes`() {
        for (name in listOf("nativeSOL_to_EVM", "ERC20_to_SOL")) {
            val f = fixture(name)
            val q = f.getAsJsonObject("quote")
            val quote = parse(q)
            assertTrue(quote.minimumOut > BigInteger.ZERO)
            val committed = PancakeNonEvmQuote.parse(f.getAsJsonObject("response"), q["originChainId"].asLong,
                q["destinationChainId"].asLong, q["inputToken"].asString, q["outputToken"].asString,
                q["amount"].asString.toBigInteger(), 100, quote)
            assertEquals(quote.requestId, committed.requestId)
            val bad = q.deepCopy()
            bad.getAsJsonObject("bridgeTransactionData").addProperty("minimumOutputAmount", "1")
            assertThrows(IllegalArgumentException::class.java) { parse(bad) }
        }
    }

    @Test fun `ERC20 Relay deposit is confined to the canonical contract exact amount and depositor`() {
        val f = fixture("ERC20_to_SOL")
        val q = f.getAsJsonObject("quote")
        val response = f.getAsJsonObject("response")
        val amount = q["amount"].asString.toBigInteger()
        val tx = PancakeRelayEvmTransaction.build(response, 1, q["inputToken"].asString, amount, f["owner"].asString)
        assertEquals(PancakeRelayEvmTransaction.DEPOSITORY, tx.to)
        assertEquals(BigInteger.ZERO, tx.value)
        assertTrue(Numeric.toHexString(tx.input).startsWith("0xe8017952"))
        assertThrows(IllegalArgumentException::class.java) { PancakeRelayEvmTransaction.build(response, 1, q["inputToken"].asString, amount + BigInteger.ONE, f["owner"].asString) }
        val bad = response.deepCopy()
        bad.getAsJsonArray("steps").single { it.asJsonObject["id"].asString == "deposit" }.asJsonObject
            .getAsJsonArray("items").single().asJsonObject.getAsJsonObject("data").addProperty("to", f["owner"].asString)
        assertThrows(IllegalArgumentException::class.java) { PancakeRelayEvmTransaction.build(bad, 1, q["inputToken"].asString, amount, f["owner"].asString) }
    }
}

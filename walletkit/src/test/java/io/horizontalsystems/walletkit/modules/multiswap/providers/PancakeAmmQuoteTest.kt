package io.horizontalsystems.walletkit.modules.multiswap.providers

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

class PancakeAmmQuoteTest {
    private val gson = Gson()
    private fun fixture(kind: String) = gson.fromJson(javaClass.classLoader!!.getResource("pancake-amm-$kind.json")!!.readText(), JsonObject::class.java)
    private fun parse(raw: JsonObject) = PancakeAmmQuote.parse(raw, 56, raw["srcToken"].asString,
        raw["dstToken"].asString, raw["inputAmount"].asString.toBigInteger())
    private fun states(f: JsonObject) = f.getAsJsonArray("states").associate { e -> e.asJsonObject.let {
        PancakePoolCall(it["address"].asString.lowercase(), it["data"].asString.lowercase()) to it["result"].asString
    } }

    @Test fun `real V2 V3 StableSwap and Infinity pool states become executable bounded trades`() {
        for ((kind, type) in listOf("v2" to 0, "v3" to 1, "stableswap" to 2, "infinityCl" to 3)) {
            val f = fixture(kind)
            val quote = parse(f.getAsJsonObject("quote"))
            val minimum = quote.amountOut * BigInteger.valueOf(99) / BigInteger.valueOf(100)
            val trade = quote.asTrade(minimum, states(f))
            assertEquals(minimum.toString(), trade.getAsJsonObject("outputAmount")["numerator"].asString)
            assertEquals(quote.amountIn.toString(), trade.getAsJsonObject("inputAmount")["numerator"].asString)
            val pool = trade.getAsJsonArray("routes").single().asJsonObject.getAsJsonArray("pools").single().asJsonObject
            assertEquals(type, pool["type"].asInt)
            assertTrue(quote.additionalStateCalls(states(f)).isEmpty())
            assertThrows(IllegalArgumentException::class.java) { quote.asTrade(minimum, emptyMap()) }
        }
    }

    @Test fun `rejects changed intent non PCS pools and added application fees`() {
        val raw = fixture("v3").getAsJsonObject("quote")
        val amount = raw["inputAmount"].asString.toBigInteger()
        assertThrows(IllegalArgumentException::class.java) { PancakeAmmQuote.parse(raw, 1, raw["srcToken"].asString, raw["dstToken"].asString, amount) }
        assertThrows(IllegalArgumentException::class.java) { PancakeAmmQuote.parse(raw, 56, raw["srcToken"].asString, raw["dstToken"].asString, amount + BigInteger.ONE) }
        for (key in listOf("swapFee", "swapFeeBps")) {
            val bad = raw.deepCopy().apply { addProperty(key, "1") }
            assertThrows(IllegalArgumentException::class.java) { parse(bad) }
        }
        val bad = raw.deepCopy()
        bad.getAsJsonArray("routes").single().asJsonObject.getAsJsonArray("pools").single().asJsonObject.addProperty("provider", "other")
        assertThrows(IllegalArgumentException::class.java) { parse(bad) }
    }

    @Test fun `rejects changed pool fee token identity and forged Infinity ID`() {
        val f = fixture("v3")
        val quote = parse(f.getAsJsonObject("quote"))
        val calls = quote.stateCalls
        for (selector in listOf("0xddca3f43", "0x0dfe1681")) {
            val broken = states(f).toMutableMap()
            broken[calls.single { it.data == selector }] = "0x" + "0".repeat(64)
            assertThrows(IllegalArgumentException::class.java) { quote.asTrade(quote.amountOut, broken) }
        }
        val raw = fixture("infinityCl").getAsJsonObject("quote")
        raw.getAsJsonArray("routes").single().asJsonObject.getAsJsonArray("pools").single().asJsonObject.addProperty("address", "0x" + "1".repeat(64))
        assertThrows(IllegalArgumentException::class.java) { parse(raw) }
    }
}

package io.horizontalsystems.walletkit.modules.multiswap.providers

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

class PancakeInfinityStableQuoteTest {
    @Test fun `official factory membership and real pool state produce a bounded stable hook trade`() = runBlocking {
        val f = Gson().fromJson(javaClass.classLoader!!.getResource("pancake-amm-infinityStable-state.json")!!.readText(), JsonObject::class.java)
        val states = f.getAsJsonArray("states").associate { e -> e.asJsonObject.let {
            PancakePoolCall(it["address"].asString.lowercase(), it["data"].asString.lowercase()) to it["result"].asString
        } }
        val amount = f["amount"].asString.toBigInteger()
        val raw = PancakeInfinityStableQuote.quote(56, f["input"].asString, f["output"].asString, amount,
            { call -> requireNotNull(states[call]) })
        assertNotNull(raw)
        val quote = PancakeAmmQuote.parse(raw!!, 56, f["input"].asString, f["output"].asString, amount)
        assertEquals(f["expectedOutput"].asString.toBigInteger(), quote.amountOut)
        val min = quote.amountOut * BigInteger.valueOf(99) / BigInteger.valueOf(100)
        val trade = quote.asTrade(min, states)
        val pool = trade.getAsJsonArray("routes").single().asJsonObject.getAsJsonArray("pools").single().asJsonObject
        assertEquals(5, pool["type"].asInt)
        assertEquals(f["expectedHook"].asString.lowercase(), pool["hooks"].asString)
        assertEquals(min.toString(), trade.getAsJsonObject("outputAmount")["numerator"].asString)
        assertNull(PancakeInfinityStableQuote.quote(1, f["input"].asString, f["output"].asString, amount, { error("No RPC permitted") }))
    }
}

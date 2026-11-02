package io.horizontalsystems.walletkit.modules.multiswap.providers

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

class PancakeNativeRouterTest {
    private fun fixture(name: String) = JsonParser.parseString(javaClass.classLoader!!.getResource(name)!!.readText()).asJsonObject
    @Test fun frozenDappVectorsMatchEveryIntegerOutputSplitAndGasEstimate() {
        val snapshot = fixture("pancake-native-pools-bnb.json")
        val before = snapshot.toString()
        val pools = snapshot.getAsJsonArray("data").map { PancakeNativePool(56, it.asJsonObject) }
        val vectors = fixture("pancake-native-solver-vectors.json").getAsJsonArray("vectors")
        for (item in vectors) {
            val v = item.asJsonObject
            fun token(key: String) = v[key].asString.let { if (it == "native") PancakeAmmQuote.NATIVE else it }
            val actual = PancakeNativeRouter().findBestTrade(56, token("input"), token("output"),
                v["amount"].asString.toBigInteger(), v["gas"].asString.toBigInteger(), pools)
            val expected = v.getAsJsonObject("expected")
            assertEquals(v["amount"].asString, expected["output"].asString, actual.amountOut.toString())
            assertEquals(expected["gas"].asString, actual.gas.toString())
            val routes = expected.getAsJsonArray("routes")
            assertEquals(routes.size(), actual.routes.size)
            routes.forEachIndexed { i, element ->
                val route = element.asJsonObject
                assertEquals(route["input"].asString, actual.routes[i].amountIn.toString())
                assertEquals(route["output"].asString, actual.routes[i].amountOut.toString())
                assertEquals(route.getAsJsonArray("pools").map { it.asString.lowercase() },
                    actual.routes[i].pools.map { it.id.lowercase() })
            }
        }
        assertEquals(before, snapshot.toString())
    }

    @Test fun invalidInputsFailAndCancellationStopsTheNativeSearch() {
        val pools = fixture("pancake-native-pools-bnb.json").getAsJsonArray("data").map { PancakeNativePool(56, it.asJsonObject) }
        val input = PancakeAmmQuote.NATIVE
        val output = "0x2170ed0880ac9a755fd29b2688956bd959f933f8"
        assertThrows(IllegalArgumentException::class.java) {
            PancakeNativeRouter().findBestTrade(56,input,output,BigInteger.ZERO,BigInteger.ONE,pools)
        }
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            PancakeNativeRouter { throw java.util.concurrent.CancellationException() }
                .findBestTrade(56,input,output,BigInteger.TEN.pow(18),BigInteger.ONE,pools)
        }
    }
}

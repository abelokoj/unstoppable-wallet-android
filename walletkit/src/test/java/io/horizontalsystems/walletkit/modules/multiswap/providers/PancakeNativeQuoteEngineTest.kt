package io.horizontalsystems.walletkit.modules.multiswap.providers

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.Bool
import org.web3j.abi.datatypes.DynamicArray
import org.web3j.abi.datatypes.DynamicBytes
import org.web3j.abi.datatypes.DynamicStruct
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.utils.Numeric
import java.math.BigInteger

class PancakeNativeQuoteEngineTest {
    private val fixtures by lazy { JsonParser.parseString(javaClass.classLoader!!.getResource("pancake-native-quote-replays.json")!!.readText()).asJsonArray }
    private fun word(data: String, offset: Int) = data.substring(2 + offset * 2, 66 + offset * 2).toBigInteger(16).intValueExact()
    private fun bytes(data: String, offset: Int): String = "0x" + data.substring(2 + (offset + 32) * 2, 2 + (offset + 32 + word(data, offset)) * 2)
    private class Result(success: Boolean, data: String) : DynamicStruct(Bool(success), Uint256(100_000L), DynamicBytes(Numeric.hexStringToByteArray(data)))
    private fun wrapper(results: List<Result>) = "0x" + FunctionEncoder.encodeConstructor(listOf(Uint256(1L), DynamicArray(Result::class.java, results), Uint256((results.size - 1).toLong())))

    @Test fun `native engine reproduces all five captured SDK quotes with identical routes and exact output`() = runBlocking {
        assertEquals(5, fixtures.size())
        for (element in fixtures) {
            val fixture = element.asJsonObject
            val request = fixture.getAsJsonObject("request")
            val calls = fixture.getAsJsonArray("calls").map { it.asJsonObject }.associateBy { it["address"].asString.lowercase() to it["data"].asString.lowercase() }
            var count = 0
            val quote = PancakeNativeQuoteEngine { outer ->
                count++
                assertTrue(outer.data.startsWith("0xd8f95843"))
                val input = "0x" + outer.data.drop(10)
                val size = word(input, 64)
                wrapper((0 until size).map { index ->
                    val start = 96 + word(input, 96 + index * 32)
                    val target = "0x" + input.substring(2 + start * 2 + 24, 2 + (start + 32) * 2)
                    val data = bytes(input, start + word(input, start + 64))
                    val captured = calls[target.lowercase() to data.lowercase()]
                        ?: throw AssertionError("Unrecorded quote on chain " + request["chain"] + " target " + target + " data " + data)
                    Result(captured["success"].asBoolean, captured["result"].asString)
                })
            }.quote(request["chain"].asLong, request["input"].asString, request["output"].asString,
                request["amount"].asString.toBigInteger(), request["gasPrice"].asString.removePrefix("0x").toBigInteger(16), request.getAsJsonArray("pools"))
            assertEquals("chain " + request["chain"], fixture["expected"], quote.raw)
            assertTrue(count > 0)
        }
    }

    @Test fun `full fallback matches the public SDK split allocation when primary verification fails`() = runBlocking {
        val fixture = JsonParser.parseString(javaClass.classLoader!!.getResource("pancake-native-full-fallback.json")!!.readText()).asJsonObject
        var reads = 0
        val quote = PancakeNativeQuoteEngine { reads++; throw IllegalArgumentException("Primary verification unavailable") }
            .quote(56, fixture["input"].asString, fixture["output"].asString, fixture["amount"].asString.toBigInteger(), BigInteger.ZERO, fixture.getAsJsonArray("pools"))
        assertTrue(reads >= 2)
        val expected = fixture.getAsJsonObject("expected")
        assertEquals(expected["amountOut"].asString.toBigInteger(), quote.amountOut)
        val routes = quote.raw.getAsJsonArray("routes")
        assertEquals(expected.getAsJsonArray("routes").size(), routes.size())
        routes.forEachIndexed { index, element ->
            val actual = element.asJsonObject
            val reference = expected.getAsJsonArray("routes")[index].asJsonObject
            assertEquals(reference["amountIn"].asString, actual["inputAmount"].asString)
            assertEquals(reference["amountOut"].asString, actual["outputAmount"].asString)
            assertEquals(reference["percent"].asDouble, actual["percent"].asDouble, 0.0)
            assertEquals(reference.getAsJsonArray("pools").map { it.asString }, actual.getAsJsonArray("pools").map { it.asJsonObject["address"].asString })
        }
    }

    @Test fun `cancellation and invalid amounts fail without RPC`() {
        val request = fixtures[0].asJsonObject.getAsJsonObject("request")
        val engine = PancakeNativeQuoteEngine { throw CancellationException("cancelled") }
        assertThrows(CancellationException::class.java) { runBlocking {
            engine.quote(56, request["input"].asString, request["output"].asString, request["amount"].asString.toBigInteger(), BigInteger.ONE, request.getAsJsonArray("pools"))
        } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking {
            engine.quote(56, request["input"].asString, request["output"].asString, BigInteger.ZERO, BigInteger.ONE, request.getAsJsonArray("pools"))
        } }
    }
}

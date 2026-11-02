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

class PancakeNativeQuoterTest {
    private val fixture by lazy { JsonParser.parseString(javaClass.classLoader!!.getResource("pancake-native-quoter-vectors.json")!!.readText()).asJsonObject }
    private val cases get() = fixture.getAsJsonArray("cases").map { it.asJsonObject }
    private val encodings get() = cases.filter { it["routeProviderEncoding"].asBoolean && !it.has("securityRejected") }
    private fun route(case: JsonObject) = PancakeNativeRoute(PancakeFraction(case["amount"].asString.toBigInteger()), BigInteger.ONE,
        case.getAsJsonArray("path").map { it.asString },
        case.getAsJsonArray("pools").map { PancakeNativePool(case["chainId"].asLong, it.asJsonObject) }, BigInteger.valueOf(12345), 27.5)
    private fun call(case: JsonObject) = PancakePoolCall(case["address"].asString, case["data"].asString)
    private fun synthetic(type: Int) = cases.first { it["name"].asString == "synthetic-pure-type-$type" }
    private fun word(result: String, byte: Int) = result.substring(2 + byte * 2, 66 + byte * 2).toBigInteger(16)
    private fun replaceWord(result: String, byte: Int, value: BigInteger) = result.replaceRange(2 + byte * 2, 66 + byte * 2, value.toString(16).padStart(64, '0'))

    @Test fun `direct call journal is reproducibly normalized from SDK multicall envelopes`() {
        val direct = fixture.getAsJsonArray("calls").map { it.asJsonObject }.associateBy {
            Triple(it["chainId"].asLong, it["address"].asString, it["data"].asString)
        }
        val observed = mutableSetOf<Triple<Long, String, String>>()
        fun bytes(data: String, start: Int): String {
            val length = word(data, start).intValueExact()
            return "0x" + data.substring(2 + (start + 32) * 2, 2 + (start + 32 + length) * 2)
        }
        for (entry in fixture.getAsJsonArray("batches")) {
            val batch = entry.asJsonObject
            val input = "0x" + batch["data"].asString.drop(10)
            val output = batch["result"].asString
            val count = word(input, 64).intValueExact()
            val last = word(output, 64).intValueExact()
            repeat(count) { index ->
                val callStart = 96 + word(input, 96 + index * 32).intValueExact()
                val target = "0x" + word(input, callStart).toString(16).padStart(40, '0')
                val calldata = bytes(input, callStart + word(input, callStart + 64).intValueExact())
                val key = Triple(batch["chainId"].asLong, target, calldata)
                val original = direct.getValue(key)
                val resultStart = 128 + word(output, 128 + index * 32).intValueExact()
                val returned = bytes(output, resultStart + word(output, resultStart + 64).intValueExact())
                assertEquals(original["result"].asString.take(66), returned.take(66))
                assertEquals(original["success"].asBoolean, index <= last && word(output, resultStart) == BigInteger.ONE)
                observed.add(key)
            }
        }
        assertEquals(28, direct.size)
        assertEquals(direct.keys, observed)
    }
    @Test fun `all route calldata matches the pinned official SDK byte for byte`() = runBlocking {
        assertEquals(setOf(1L, 56L, 8453L, 42161L, 324L), encodings.map { it["chainId"].asLong }.toSet())
        assertEquals((0..4).toSet(), encodings.flatMap { it.getAsJsonArray("pools").map { p -> p.asJsonObject["type"].asInt } }.toSet())
        for (case in encodings) {
            val r = route(case)
            val quoter = PancakeNativeQuoter { error("Encoding must not make network requests") }
            assertEquals(case["name"].asString, call(case), quoter.encode(case["chainId"].asLong, r))
        }
        val quoter = PancakeNativeQuoter { error("Unexpected read") }
        val targets = mapOf(0 to "0x678aa4bf4e210cf2166753e054d5b7c31cc7fa86", 1 to "0xb048bbc1ee6b733fffcfb9e9cef7375518e25997",
            2 to "0x678aa4bf4e210cf2166753e054d5b7c31cc7fa86", 3 to "0xd0737c9762912dd34c3271197e362aa736df0926", 4 to "0xc631f4b0fc2dd68ad45f74b2942628db117dd359")
        for (type in 0..4) {
            val encoded = quoter.encode(56, route(synthetic(type)))
            assertEquals(targets[type], encoded.address)
            assertEquals(when (type) { 0, 2 -> "0x558b66fb"; 1 -> "0xcdca1753"; else -> "0x00eb2222" }, encoded.data.take(10))
        }
    }

    @Test fun `recorded and synthetic quote responses preserve exact route allocation and decoded tick count`() = runBlocking {
        for (case in encodings.filter { it["success"].asBoolean && it["expectedAmountOut"].asString.toBigInteger() > BigInteger.ZERO }) {
            val original = route(case)
            var reads = 0
            val result = PancakeNativeQuoter { requested ->
                reads++
                assertEquals(call(case), requested)
                case["result"].asString
            }.verify(case["chainId"].asLong, original)
            assertEquals(1, reads)
            assertEquals(case["expectedAmountOut"].asString.toBigInteger(), result.amountOut)
            assertEquals(case["expectedInitializedTicks"].asInt, result.initializedTicks)
            assertSame(original.amount, result.amount)
            assertSame(original.path, result.path)
            assertSame(original.pools, result.pools)
            assertEquals(original.gas, result.gas)
            assertEquals(original.percent, result.percent, 0.0)
        }
    }

    @Test fun `SDK multicall wrappers replay their exact inner calls and discard unsuccessful routes`() = runBlocking {
        val byName = cases.associateBy { it["name"].asString }
        var replayed = 0
        for (entry in fixture.getAsJsonArray("batches")) {
            val batch = entry.asJsonObject
            val vectors = batch.getAsJsonArray("cases").map { byName.getValue(it.asString) }
            if (vectors.any { !it["routeProviderEncoding"].asBoolean }) continue
            val originals = vectors.map(::route)
            val results = PancakeNativeQuoter { requested ->
                assertEquals(PancakePoolCall(batch["address"].asString, batch["data"].asString), requested)
                batch["result"].asString
            }.verify(batch["chainId"].asLong, originals)
            val successful = vectors.indices.filter { vectors[it]["success"].asBoolean && vectors[it]["expectedAmountOut"].asString.toBigInteger() > BigInteger.ZERO }
            assertEquals(successful.size, results.size)
            successful.forEachIndexed { i, original ->
                assertSame(originals[original].amount, results[i].amount)
                assertSame(originals[original].pools, results[i].pools)
                assertEquals(vectors[original]["expectedAmountOut"].asString.toBigInteger(), results[i].amountOut)
                assertEquals(vectors[original]["expectedInitializedTicks"].asInt, results[i].initializedTicks)
            }
            replayed++
        }
        assertEquals(18, replayed)
    }

    @Test fun `primary Infinity screening uses the mixed quoter including unsuccessful bin candidates`() = runBlocking {
        val byName = cases.associateBy { it["name"].asString }
        val batch = fixture.getAsJsonArray("batches").map { it.asJsonObject }.first {
            it.getAsJsonArray("cases").any { c -> !byName.getValue(c.asString)["routeProviderEncoding"].asBoolean }
        }
        val vectors = batch.getAsJsonArray("cases").map { byName.getValue(it.asString) }
        val result = PancakeNativeQuoter { request ->
            assertEquals(PancakePoolCall(batch["address"].asString, batch["data"].asString), request)
            batch["result"].asString
        }.candidates(batch["chainId"].asLong, vectors.map(::route))
        assertEquals(vectors.count { it["success"].asBoolean && it["expectedAmountOut"].asString.toBigInteger().signum() > 0 }, result.size)
    }

    @Test fun `route batches are bounded and retain order across chunk boundaries`() = runBlocking {
        val case = synthetic(1)
        val original = route(case)
        val counts = mutableListOf<Int>()
        val routes = (1..33).map { original.copy(amount = PancakeFraction(BigInteger.valueOf(it.toLong()))) }
        val results = PancakeNativeQuoter { request ->
            assertEquals("0x39eecae833c944ebb94942fa44cae46e87a8da17", request.address)
            assertEquals("0xd8f95843", request.data.take(10))
            val count = word("0x" + request.data.drop(10), 64).toInt()
            counts.add(count)
            assertTrue(count in 1..16)
            assertTrue(request.data.length < 131072 * 2)
            batchOutput(List(count) { true to case["result"].asString })
        }.verify(56, routes)
        assertEquals(listOf(16, 16, 1), counts)
        assertEquals(routes.map { it.amountIn }, results.map { it.amountIn })
        assertEquals(33, results.size)
    }

    @Test fun `empty reverts zero quotes invalid arrays and malformed replies are rejected`(): Unit = runBlocking {
        val case = synthetic(1)
        val encoded = case["result"].asString
        val invalid = listOf("0x", "0x" + "0".repeat(64), "0x08c379a0" + "0".repeat(120), encoded.dropLast(2), encoded + "0".repeat(64),
            replaceWord(encoded, 0, BigInteger.ZERO), replaceWord(encoded, 32, BigInteger.valueOf(160)),
            replaceWord(encoded, 128, BigInteger.valueOf(Int.MAX_VALUE.toLong())), replaceWord(encoded, 224, BigInteger.ONE.shiftLeft(32)))
        for (result in invalid) {
            assertThrows(IllegalArgumentException::class.java) { runBlocking { PancakeNativeQuoter { result }.verify(56, route(case)) } }
        }
        for (type in listOf(3, 4)) {
            val infinity = synthetic(type)
            assertThrows(IllegalArgumentException::class.java) { runBlocking { PancakeNativeQuoter { infinity["result"].asString.take(66) }.verify(56, route(infinity)) } }
        }
        val failed = cases.first { it["routeProviderEncoding"].asBoolean && !it["success"].asBoolean }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { PancakeNativeQuoter { failed["result"].asString }.verify(failed["chainId"].asLong, route(failed)) } }
        assertTrue(PancakeNativeQuoter { batchOutput(listOf(false to "0x08c379a0", true to "0x")) }.verify(56, listOf(route(case), route(case))).isEmpty())
        assertThrows(IllegalArgumentException::class.java) { runBlocking { PancakeNativeQuoter { "0x" }.verify(56, listOf(route(case))) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { PancakeNativeQuoter { batchOutput(listOf(true to encoded)) }.verify(56, listOf(route(case), route(case))) } }
    }

    @Test fun `unsupported chains hooks managers paths and overflowing amounts never reach RPC`() = runBlocking {
        var reads = 0
        val q = PancakeNativeQuoter { reads++; error("Unsafe quote reached RPC") }
        val v3 = route(synthetic(1))
        val infinity = route(synthetic(3))
        val badPool = infinity.pools.single().raw.apply { addProperty("hooks", "0x0000000000000000000000000000000000000001") }
        val badManager = infinity.pools.single().raw.apply { addProperty("poolManager", "0x0000000000000000000000000000000000000001") }
        val badBitmap = infinity.pools.single().raw.apply { addProperty("hooksRegistrationBitmap", "0x1") }
        val invalid = listOf(v3.copy(amount = PancakeFraction(BigInteger.ZERO)), v3.copy(amount = PancakeFraction(BigInteger.ONE.shiftLeft(256))),
            v3.copy(path = listOf(v3.path.first())), v3.copy(path = listOf(v3.path.first(), "https://untrusted.invalid")),
            infinity.copy(amount = PancakeFraction(BigInteger.ONE.shiftLeft(128))), infinity.copy(pools = listOf(PancakeNativePool(56, badPool))),
            infinity.copy(pools = listOf(PancakeNativePool(56, badManager))), infinity.copy(pools = listOf(PancakeNativePool(56, badBitmap))))
        for (r in invalid) assertThrows(IllegalArgumentException::class.java) { runBlocking { q.verify(56, r) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { q.verify(10, v3) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { q.verify(1, infinity) } }
        for (case in cases.filter { it.has("securityRejected") }) {
            assertThrows(IllegalArgumentException::class.java) { runBlocking { q.verify(case["chainId"].asLong, route(case)) } }
        }
        assertEquals(0, reads)
    }

    @Test fun `cancellation propagates through individual and batched verification`() {
        val q = PancakeNativeQuoter { throw CancellationException("cancelled") }
        val r = route(synthetic(1))
        assertThrows(CancellationException::class.java) { runBlocking { q.verify(56, r) } }
        assertThrows(CancellationException::class.java) { runBlocking { q.verify(56, listOf(r)) } }
    }

    private class SubcallResult(success: Boolean, data: String) : DynamicStruct(Bool(success), Uint256(100_000L), DynamicBytes(Numeric.hexStringToByteArray(data)))
    private fun batchOutput(results: List<Pair<Boolean, String>>) = "0x" + FunctionEncoder.encodeConstructor(listOf(Uint256(1L),
        DynamicArray(SubcallResult::class.java, results.map { SubcallResult(it.first, it.second) }), Uint256((results.size - 1).toLong())))
}

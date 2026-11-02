package io.horizontalsystems.walletkit.modules.multiswap.providers

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

class PancakePoolMathTest {
    private val fixture: JsonObject by lazy {
        Gson().fromJson(javaClass.classLoader!!.getResource("pancake-native-pool-math.json")!!.readText(), JsonObject::class.java)
    }
    private fun JsonObject.fraction() = PancakeFraction(get("numerator").asString.toBigInteger(), get("denominator").asString.toBigInteger())
    private fun cases() = fixture.getAsJsonArray("cases").map { it.asJsonObject }

    @Test fun `all five pool kinds match exact official SDK quotes prices repeated quotes and post-swap state in both directions`() {
        assertEquals((0..4).toSet(), cases().map { it.getAsJsonObject("pool")["type"].asInt }.toSet())
        for (case in cases()) {
            val label = case["name"].asString
            val original = case.getAsJsonObject("pool")
            val before = original.deepCopy()
            val pool = PancakeNativePool(case["chain"].asLong, original)
            val input = case["input"].asString; val output = case["output"].asString
            val amount = case.getAsJsonObject("amount").fraction()
            val result = pool.quote(input, output, amount)
            case.getAsJsonObject("price")?.let { assertEquals("$label price", 0, it.fraction().compareTo(pool.price(input, output))) }
            if (case.get("priceError")?.asBoolean == true) assertThrows("$label price", RuntimeException::class.java) { pool.price(input, output) }
            assertEquals(label, before, original)
            assertEquals(label, before, pool.raw)
            if (case["expected"].isJsonNull) { assertNull(label, result); continue }
            assertNotNull(label, result)
            val expected = case.getAsJsonObject("expected")
            assertEquals(label, 0, expected.getAsJsonObject("quote").fraction().compareTo(result!!.quote))
            assertEquals(label, expected["gas"].asString.toBigInteger(), result.gas)
            expected.getAsJsonObject("state")?.entrySet()?.forEach { (field, value) ->
                assertEquals("$label $field", value.asString, result.poolAfter.raw[field].asString)
            }
            expected.getAsJsonArray("reserves")?.forEachIndexed { index, reserve ->
                assertEquals("$label reserve $index", reserve.asJsonObject["quotient"].asString.toBigInteger(), result.poolAfter.reserve(pool.currencies[index].address))
            }
            if (expected.get("unchanged")?.asBoolean == true) assertEquals(label, before, result.poolAfter.raw)
            val repeated = result.poolAfter.quote(input, output, amount)
            if (expected["repeat"].isJsonNull) assertNull("$label repeat", repeated)
            else {
                assertNotNull("$label repeat", repeated)
                assertEquals("$label repeat", 0, expected.getAsJsonObject("repeat").fraction().compareTo(repeated!!.quote))
            }
        }
    }

    @Test fun `tick extremes negative ticks and word edges retain official round-up convention`() {
        for (element in fixture.getAsJsonArray("ticks")) {
            val vector = element.asJsonObject
            val tick = vector["tick"].asInt
            val sqrt = vector["sqrt"].asString.toBigInteger()
            assertEquals("tick $tick", sqrt, PancakePoolMath.sqrtAtTick(tick))
            if (tick < PancakePoolMath.MAX_TICK) assertEquals(tick, PancakePoolMath.tickAtSqrt(sqrt))
            if (tick > PancakePoolMath.MIN_TICK) assertEquals(tick - 1, PancakePoolMath.tickAtSqrt(sqrt - BigInteger.ONE))
        }
        assertThrows(IllegalArgumentException::class.java) { PancakePoolMath.sqrtAtTick(PancakePoolMath.MAX_TICK + 1) }
        assertThrows(IllegalArgumentException::class.java) { PancakePoolMath.tickAtSqrt(PancakePoolMath.MAX_SQRT) }
    }

    @Test fun `pool construction getters and swap results isolate mutable JSON`() {
        for (case in cases().filter { !it["expected"].isJsonNull }.take(10)) {
            val source = case.getAsJsonObject("pool").deepCopy()
            val saved = source.deepCopy()
            val pool = PancakeNativePool(case["chain"].asLong, source)
            source.addProperty("fee", -999)
            pool.raw.addProperty("type", 99)
            assertEquals(saved, pool.raw)
            val amount = case.getAsJsonObject("amount").fraction()
            val first = pool.quote(case["input"].asString, case["output"].asString, amount)!!
            first.poolAfter.raw.addProperty("type", 99)
            assertEquals(saved, pool.raw)
            assertEquals(0, first.quote.compareTo(pool.quote(case["input"].asString, case["output"].asString, amount)!!.quote))
        }
    }

    @Test fun `descriptors preserve actual native order and routed stable pair`() {
        for (case in cases()) {
            val pool = PancakeNativePool(case["chain"].asLong, case.getAsJsonObject("pool"))
            val input = case["input"].asString; val output = case["output"].asString
            val descriptor = pool.descriptor(input, output)
            assertEquals("pancakeswap", descriptor["provider"].asString)
            assertEquals(pool.id, descriptor["address"].asString)
            assertTrue(descriptor["token0"].asString < descriptor["token1"].asString)
            assertEquals(setOf(PancakeNativePool.canonical(pool.chainId, input), PancakeNativePool.canonical(pool.chainId, output)),
                setOf(PancakeNativePool.canonical(pool.chainId, descriptor["token0"].asString), PancakeNativePool.canonical(pool.chainId, descriptor["token1"].asString)))
            if (pool.type >= 3) {
                val stride = descriptor[if (pool.type == 3) "tickSpacing" else "binStep"].asLong
                val parameters = descriptor["parameters"].asString.removePrefix("0x").toBigInteger(16)
                assertEquals(BigInteger.valueOf(stride), parameters.shiftRight(16))
                assertEquals(66, descriptor["parameters"].asString.length)
                assertEquals(pool.currencies[0].address, descriptor["token0"].asString)
            }
        }
        val case = cases().first { it["name"].asString == "cl-crossing-forward" }
        val pool = PancakeNativePool(56, case.getAsJsonObject("pool"))
        val wrapped = PancakeNativePool.canonical(56, pool.currencies[0].address)
        assertEquals(BigInteger.ZERO, pool.reserve(wrapped))
        assertEquals(pool.quote(pool.currencies[0].address, pool.currencies[1].address, BigInteger.TEN.pow(18))?.amountOut,
            pool.quote(wrapped, pool.currencies[1].address, BigInteger.TEN.pow(18))?.amountOut)
    }

    @Test fun `invalid amounts pairs missing liquidity and unsupported bin high words fail closed`() {
        for (case in cases().take(10)) {
            val pool = PancakeNativePool(case["chain"].asLong, case.getAsJsonObject("pool"))
            val input = case["input"].asString; val output = case["output"].asString
            assertNull(pool.quote(input, output, BigInteger.ZERO))
            assertNull(pool.quote(input, output, -BigInteger.ONE))
            assertNull(pool.quote(input, output, BigInteger.ONE.shiftLeft(256)))
            assertNull(pool.quote(input, input, BigInteger.TEN))
            assertNull(pool.quote(input, "0x00000000000000000000000000000000000000ff", BigInteger.TEN))
        }
        val cl = cases().first { it.getAsJsonObject("pool")["type"].asInt == 3 }
        val noTicks = cl.getAsJsonObject("pool").deepCopy().apply { remove("ticks") }
        assertNull(PancakeNativePool(56, noTicks).quote(cl["input"].asString, cl["output"].asString, BigInteger.TEN))
        val bin = cases().first { it["name"].asString == "bin-reverse-1000" }
        val overflow = bin.getAsJsonObject("pool").deepCopy().apply {
            addProperty("activeId", 8388609)
            getAsJsonObject("reserveOfBin").getAsJsonObject("8388609").addProperty("reserveX", (BigInteger.ONE.shiftLeft(128) - BigInteger.ONE).toString())
        }
        assertNull(PancakeNativePool(56, overflow).quote(bin["input"].asString, bin["output"].asString, BigInteger.ONE))
    }
    @Test fun `untrusted stable decimals fee exponents and oversized signed ticks are bounded`() {
        val stable = cases().first { it.getAsJsonObject("pool")["type"].asInt == 2 }
        val malformed = stable.getAsJsonObject("pool").deepCopy().apply { addProperty("fee", "1e999999999") }
        assertNull(PancakeNativePool(56, malformed).quote(stable["input"].asString, stable["output"].asString, BigInteger.TEN.pow(18)))
        val excess = stable.getAsJsonObject("pool").deepCopy().apply {
            val balances = getAsJsonArray("balances")
            while (balances.size() < 4) balances.add(balances[0].deepCopy())
        }
        assertThrows(IllegalArgumentException::class.java) { PancakeNativePool(56, excess) }
        val cl = cases().first { it.getAsJsonObject("pool")["type"].asInt == 3 }
        val invalidTick = cl.getAsJsonObject("pool").deepCopy().apply {
            getAsJsonArray("ticks")[0].asJsonObject.addProperty("liquidityNet", "9".repeat(1000))
        }
        assertNull(PancakeNativePool(56, invalidTick).quote(cl["input"].asString, cl["output"].asString, BigInteger.TEN.pow(18)))
    }

}

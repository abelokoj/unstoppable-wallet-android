package io.horizontalsystems.walletkit.modules.multiswap.providers

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

class PancakeEvmPlannerTest {
    private val native = PancakeCrossChainTransaction.NATIVE
    private val eth = PancakeCrossChainTransaction.chain(1).wrappedNative.lowercase()
    private val bscEth = "0x2170ed0880ac9a755fd29b2688956bd959f933f8"
    private val bscUsdc = "0x8ac76a51cc950d9822d68b83fe1ad97b32cd580d"
    private val ethUsdc = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"
    private val other = "0x1111111111111111111111111111111111111111"
    private val now = 1800000000L
    private val one = BigInteger.TEN.pow(18)
    private val calls = mutableListOf<String>()

    private fun decimals(chain: Long, token: String) = if (chain == 1L && token.equals(ethUsdc, true)) 6 else 18
    private fun swap(chain: Long, input: String, output: String, amount: BigInteger): PancakeAmmQuote {
        calls += "swap:$chain:$input:$output:$amount"
        val from = PancakeRoutePlanner.wrapped(chain, input).lowercase()
        val to = PancakeRoutePlanner.wrapped(chain, output).lowercase()
        val rate = when {
            chain == 56L && output == bscUsdc -> BigDecimal("100")
            chain == 56L && output == bscEth -> BigDecimal("2")
            chain == 1L && input == ethUsdc -> BigDecimal("0.03")
            else -> BigDecimal.ONE
        }
        val out = amount.toBigDecimal().movePointLeft(decimals(chain, input)).multiply(rate)
            .movePointRight(decimals(chain, output)).toBigInteger()
        fun token(address: String) = JsonObject().apply {
            addProperty("address", address); addProperty("decimals", decimals(chain, address)); addProperty("symbol", "T")
        }
        val raw = JsonObject().apply {
            addProperty("chainId", chain); addProperty("srcToken", input); addProperty("dstToken", output)
            addProperty("inputAmount", amount.toString()); addProperty("outputAmount", out.toString())
            addProperty("tradeType", "exactIn"); addProperty("swapFeeBps", "0"); addProperty("swapFee", "0")
            add("routes", JsonArray().apply { add(JsonObject().apply {
                addProperty("percent", 100); addProperty("inputAmount", amount.toString()); addProperty("outputAmount", out.toString())
                add("path", JsonArray().apply { add(token(from)); add(token(to)) })
                add("pools", JsonArray().apply { add(JsonObject().apply {
                    addProperty("address", "0x2222222222222222222222222222222222222222")
                    addProperty("type", "v2"); addProperty("provider", "pancakeswap"); addProperty("fee", "2500")
                    addProperty("token0", minOf(from, to)); addProperty("token1", maxOf(from, to))
                }) })
            }) })
        }
        return PancakeAmmQuote.parse(raw, chain, input, output, amount)
    }

    private fun planner(routes: List<PancakeCrossChainApi.Route>, alter: (PancakeBridgeQuote) -> PancakeBridgeQuote = { it }) =
        PancakeEvmPlanner(routes, ::swap, { origin, destination, input, output, amount, body ->
            calls += "bridge:$origin:$destination:$input:$output:$amount:${body.has("commands")}"
            val out = PancakeEvmPlanner.convertUnits(amount, decimals(origin, input), decimals(destination, output)) * BigInteger.valueOf(99) / BigInteger.valueOf(100)
            alter(PancakeBridgeQuote(true, amount.toString(), input, output, origin, destination, "9", false,
                PancakeBridgeQuote.Limits("1", BigInteger.ONE.shiftLeft(255).toString()),
                PancakeBridgeQuote.BridgeData(out.toString(), (amount / BigInteger.valueOf(100)).toString(), native, 0, now, now + 7200)))
        }, { leg, _ -> JsonObject().apply { addProperty("command", "SWAP"); addProperty("minimum", leg.minimum.toString()) } },
            { chain, token -> decimals(chain, token) }, { now })

    @Test fun `source swap expected quote is independent of slippage and minimum is protected`() = runBlocking {
        val p = planner(listOf(PancakeCrossChainApi.Route(56, 1, bscEth, eth)))
        val a = p.quote(56, 1, native, native, one, BigDecimal("0.5"), other)
        val b = p.quote(56, 1, native, native, one, BigDecimal("2"), other)
        assertEquals(one * BigInteger.valueOf(198) / BigInteger.valueOf(100), a.expectedOutput)
        assertEquals(a.expectedOutput, b.expectedOutput)
        assertTrue(a.minimumOutput < a.expectedOutput)
        assertTrue(b.minimumOutput < a.minimumOutput)
        assertEquals(a.source!!.minimum, a.bridgeAmount)
        assertNull(a.destination)
        assertEquals(a.minimumOutput, a.bridge.bridgeTransactionData!!.outputAmount!!.toBigInteger())
    }

    @Test fun `direct bridge has one quote and no AMM haircut`() = runBlocking {
        val p = planner(listOf(PancakeCrossChainApi.Route(56, 1, bscEth, eth)))
        val q = p.quote(56, 1, bscEth, native, one, BigDecimal.ONE, other)
        assertEquals(q.expectedOutput, q.minimumOutput)
        assertNull(q.source); assertNull(q.destination)
        assertEquals(1, calls.size)
    }

    @Test fun `bridge then swap includes destination command in bridge fee quote`() = runBlocking {
        val p = planner(listOf(PancakeCrossChainApi.Route(1, 56, eth, bscEth)))
        val q = p.quote(1, 56, native, native, one, BigDecimal.ONE, other)
        assertNull(q.source); assertNotNull(q.destination)
        assertEquals(one, q.bridgeAmount)
        assertTrue(calls.filter { it.startsWith("bridge:") }.all { it.endsWith(":true") })
        assertTrue(q.expectedOutput > q.minimumOutput)
    }

    @Test fun `both-swap pattern compares every permitted source path using destination outputs`() = runBlocking {
        val p = planner(listOf(PancakeCrossChainApi.Route(56, 1, bscEth, eth),
            PancakeCrossChainApi.Route(56, 1, bscUsdc, ethUsdc)))
        val q = p.quote(56, 1, other, other, one, BigDecimal.ONE, other)
        assertEquals(bscUsdc, q.bridgeInput)
        assertEquals(ethUsdc, q.bridgeOutput)
        assertNotNull(q.source); assertNotNull(q.destination)
        assertEquals(BigInteger("100000000"), PancakeEvmPlanner.convertUnits(q.source!!.quote.amountOut, 18, 6))
        assertTrue(q.expectedOutput > q.minimumOutput)
        assertEquals(2, calls.count { it.startsWith("bridge:") })
    }

    @Test fun `changed bridge identity and expired metadata cannot become a plan`() = runBlocking {
        val routes = listOf(PancakeCrossChainApi.Route(56, 1, bscEth, eth))
        for (change in listOf<(PancakeBridgeQuote) -> PancakeBridgeQuote>(
            { it.copy(amount = "1") }, { it.copy(destinationChainId = 8453) },
            { it.copy(bridgeTransactionData = it.bridgeTransactionData!!.copy(fillDeadline = now)) })) {
            try { planner(routes, change).quote(56, 1, native, native, one, BigDecimal.ONE, other); fail("Accepted changed metadata") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun `bridge unit conversion floors precision and rejects invalid decimals`() {
        assertEquals(BigInteger("1234567"), PancakeEvmPlanner.convertUnits(BigInteger("1234567891234567890"), 18, 6))
        assertEquals(one, PancakeEvmPlanner.convertUnits(BigInteger("1000000"), 6, 18))
        assertThrows(IllegalArgumentException::class.java) { PancakeEvmPlanner.convertUnits(one, -1, 6) }
        assertThrows(IllegalArgumentException::class.java) { PancakeEvmPlanner.convertUnits(BigInteger.ZERO, 18, 6) }
    }
}

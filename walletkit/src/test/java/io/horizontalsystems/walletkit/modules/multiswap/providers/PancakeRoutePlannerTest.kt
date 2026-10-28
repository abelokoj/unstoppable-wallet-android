package io.horizontalsystems.walletkit.modules.multiswap.providers

import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

class PancakeRoutePlannerTest {
    private val native = PancakeCrossChainTransaction.NATIVE
    private val eth = PancakeCrossChainTransaction.chain(1).wrappedNative
    private val bscEth = "0x2170ed0880ac9a755fd29b2688956bd959f933f8"
    private val usdc = "0x8ac76a51cc950d9822d68b83fe1ad97b32cd580d"

    @Test fun `ETH to BNB finds bridge then swap and reverse finds swap then bridge`() {
        val forward = PancakeCrossChainApi.Route(1, 56, eth, bscEth)
        val reverse = PancakeCrossChainApi.Route(56, 1, bscEth, eth)
        val f = PancakeRoutePlanner.candidates(listOf(forward, reverse), 1, 56, native, native).single()
        assertEquals(1, f.swapCount)
        assertFalse(f.sourceSwap)
        assertTrue(f.destinationSwap)
        val r = PancakeRoutePlanner.candidates(listOf(forward, reverse), 56, 1, native, native).single()
        assertEquals(1, r.swapCount)
        assertTrue(r.sourceSwap)
        assertFalse(r.destinationSwap)
    }

    @Test fun `arbitrary token pair can swap on both sides without matching bridge tokens`() {
        val route = PancakeCrossChainApi.Route(56, 1, usdc, eth)
        val result = PancakeRoutePlanner.candidates(listOf(route), 56, 1, native, usdc).single()
        assertEquals(2, result.swapCount)
        assertTrue(result.sourceSwap && result.destinationSwap)
    }

    @Test fun `direct bridge preferred while fallback routes remain available and directed`() {
        val routes = listOf(PancakeCrossChainApi.Route(1, 56, usdc, usdc),
            PancakeCrossChainApi.Route(1, 56, eth, bscEth), PancakeCrossChainApi.Route(1, 56, eth, bscEth),
            PancakeCrossChainApi.Route(56, 1, bscEth, eth))
        val result = PancakeRoutePlanner.candidates(routes, 1, 56, native, bscEth)
        assertEquals(2, result.size)
        assertEquals(0, result.first().swapCount)
        assertTrue(PancakeRoutePlanner.candidates(routes, 1, 1, native, native).isEmpty())
    }

    @Test fun `slippage rounds down and divides the allowance between swap legs`() {
        val out = BigInteger("1000000")
        assertEquals(out, PancakeRoutePlanner.minimum(out, "0".toBigDecimal(), 2))
        assertEquals(BigInteger("995024"), PancakeRoutePlanner.minimum(out, "1".toBigDecimal(), 2))
        val twice = PancakeRoutePlanner.minimum(PancakeRoutePlanner.minimum(out, "1".toBigDecimal(), 2), "1".toBigDecimal(), 2)
        assertTrue(twice >= BigInteger("990000"))
        assertEquals(50, PancakeRoutePlanner.slippageBps("1".toBigDecimal(), 2))
        assertThrows(IllegalArgumentException::class.java) { PancakeRoutePlanner.minimum(out, "-1".toBigDecimal(), 1) }
    }
}

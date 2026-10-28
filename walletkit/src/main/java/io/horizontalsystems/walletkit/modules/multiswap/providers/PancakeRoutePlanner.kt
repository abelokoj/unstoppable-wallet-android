package io.horizontalsystems.walletkit.modules.multiswap.providers

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

internal object PancakeRoutePlanner {
    data class Candidate(val route: PancakeCrossChainApi.Route, val sourceSwap: Boolean, val destinationSwap: Boolean) {
        val swapCount get() = (if (sourceSwap) 1 else 0) + (if (destinationSwap) 1 else 0)
    }

    fun candidates(routes: List<PancakeCrossChainApi.Route>, origin: Long, destination: Long,
                   input: String, output: String): List<Candidate> {
        if (origin == destination) return emptyList()
        val from = wrapped(origin, input)
        val to = wrapped(destination, output)
        return routes.asSequence().filter { it.originChainId == origin && it.destinationChainId == destination }
            .distinctBy { it.originToken.lowercase() to it.destinationToken.lowercase() }
            .map { Candidate(it, !it.originToken.equals(from, true), !it.destinationToken.equals(to, true)) }
            .sortedWith(compareBy<Candidate> { it.swapCount }.thenBy { if (it.sourceSwap) 1 else 0 })
            .toList()
    }

    fun wrapped(chain: Long, token: String): String = if (token.equals(PancakeCrossChainTransaction.NATIVE, true))
        PancakeCrossChainTransaction.chain(chain).wrappedNative else token

    fun slippageBps(percent: BigDecimal, legs: Int): Int {
        require(percent >= BigDecimal.ZERO && percent <= BigDecimal("50") && legs in 0..2)
        return percent.movePointRight(2).divide(maxOf(1, legs).toBigDecimal(), 0, RoundingMode.DOWN).intValueExact()
    }

    fun minimum(output: BigInteger, percent: BigDecimal, legs: Int): BigInteger {
        require(output.signum() > 0)
        val denominator = BigInteger.valueOf(10000L + slippageBps(percent, legs))
        return output * BigInteger.valueOf(10000) / denominator
    }
}

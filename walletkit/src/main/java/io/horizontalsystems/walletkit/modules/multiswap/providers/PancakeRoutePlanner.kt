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

    /** Match the dapp's bridge-pattern selection before pricing AMM legs. */
    fun dappCandidates(routes: List<PancakeCrossChainApi.Route>, origin: Long, destination: Long,
                       input: String, output: String): List<Candidate> {
        if (origin == destination) return emptyList()
        val directed = routes.filter { it.originChainId == origin && it.destinationChainId == destination }
            .distinctBy { it.originToken.lowercase() to it.destinationToken.lowercase() }
        val from = wrapped(origin, input)
        val to = wrapped(destination, output)
        val source = directed.firstOrNull { it.originToken.equals(from, true) }
        val target = directed.firstOrNull { it.destinationToken.equals(to, true) }
        return when {
            source != null && target != null && source.originToken.equals(target.originToken, true) ->
                listOf(Candidate(target, false, false))
            source != null -> listOf(Candidate(source, false, true))
            target != null -> listOf(Candidate(target, true, false))
            else -> directed.filter { it.originToken.lowercase() in originBridgeTokens[origin].orEmpty() }
                .distinctBy { it.originToken.lowercase() }.map { Candidate(it, true, true) }
        }
    }

    // The frontend permits this source-token set for its swap/bridge/swap strategy.
    private val originBridgeTokens = mapOf(
        1L to setOf("0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2", "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48",
            "0xdac17f958d2ee523a2206206994597c13d831ec7", "0x152649ea73beab28c5b49b26eb48f7ead6d4c898"),
        56L to setOf("0x2170ed0880ac9a755fd29b2688956bd959f933f8", "0x55d398326f99059ff775485246999027b3197955",
            "0x8ac76a51cc950d9822d68b83fe1ad97b32cd580d"),
        42161L to setOf("0xaf88d065e77c8cc2239327c5edb3a432268e5831", "0x82af49447d8a07e3bd95bd0d56f35241523fbab1"),
        8453L to setOf("0x4200000000000000000000000000000000000006", "0xfde4c96c8593536e31f229ea8f37b2ada2699bb2",
            "0x833589fcd6edb6e08f4c7c32d4f71b54bda02913"),
        324L to setOf("0x3355df6d4c9c3035724fd0e3914de96a5a83aaf4", "0x493257fd37edb34451f62edf8d2a0c418852ba4c",
            "0x4b9eb6c0b6ea15176bbf62841c6b2a8a398cb656", "0x5aea5775959fbc2557cc8789bc1bf90a239d9a91",
            "0xbbeb516fb02a01611cbbe0453fe3c580d7281011"),
    )

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

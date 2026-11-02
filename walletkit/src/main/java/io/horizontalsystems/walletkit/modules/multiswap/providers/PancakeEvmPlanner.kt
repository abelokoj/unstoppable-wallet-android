package io.horizontalsystems.walletkit.modules.multiswap.providers

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.math.BigDecimal
import java.math.BigInteger

/** The dapp quotes expected output separately from the amounts its commands protect. */
internal class PancakeEvmPlanner(
    private val routes: List<PancakeCrossChainApi.Route>,
    private val swapQuote: suspend (Long, String, String, BigInteger) -> PancakeAmmQuote,
    private val bridgeQuote: suspend (Long, Long, String, String, BigInteger, JsonObject) -> PancakeBridgeQuote,
    private val swapCommand: suspend (Leg, Int) -> JsonObject,
    private val tokenDecimals: suspend (Long, String) -> Int,
    private val now: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    data class Leg(val quote: PancakeAmmQuote, val minimum: BigInteger)
    data class Plan(
        val bridge: PancakeBridgeQuote,
        val bridgeInput: String,
        val bridgeOutput: String,
        val bridgeAmount: BigInteger,
        val source: Leg?,
        val destination: Leg?,
        val slippageBps: Int,
        val expectedOutput: BigInteger,
        val minimumOutput: BigInteger,
    )
    private data class Path(val candidate: PancakeRoutePlanner.Candidate, val source: Leg?, val destinationProbe: Leg?)

    suspend fun quote(origin: Long, destination: Long, input: String, output: String,
                      amount: BigInteger, slippage: BigDecimal, recipient: String): Plan {
        PancakeCrossChainTransaction.chain(origin); PancakeCrossChainTransaction.chain(destination)
        require(amount.signum() > 0 && amount.bitLength() <= 256)
        require(PancakeCrossChainTransaction.address(recipient).drop(2).toBigInteger(16) > BigInteger.valueOf(2))
        val bps = PancakeRoutePlanner.slippageBps(slippage, 1)
        val candidates = PancakeRoutePlanner.dappCandidates(routes, origin, destination, input, output)
        require(candidates.isNotEmpty()) { "PancakeSwap found no cross-chain route" }

        suspend fun leg(chain: Long, from: String, to: String, value: BigInteger): Leg {
            val quote = swapQuote(chain, from, to, value)
            require(quote.chainId == chain && quote.inputToken.equals(from, true) && quote.outputToken.equals(to, true) && quote.amountIn == value)
            return Leg(quote, PancakeRoutePlanner.minimum(quote.amountOut, slippage, 1))
        }
        suspend fun path(candidate: PancakeRoutePlanner.Candidate): Path {
            val source = if (candidate.sourceSwap) leg(origin, input, candidate.route.originToken, amount) else null
            val probe = if (candidate.destinationSwap) {
                val converted = convertUnits(source?.quote?.amountOut ?: amount,
                    tokenDecimals(origin, candidate.route.originToken), tokenDecimals(destination, candidate.route.destinationToken))
                leg(destination, candidate.route.destinationToken, output, converted)
            } else null
            return Path(candidate, source, probe)
        }
        val selected = if (candidates.size == 1) path(candidates.single()) else {
            val paths = mutableListOf<Path>()
            for (batch in candidates.chunked(3)) coroutineScope {
                batch.map { candidate -> async {
                    try { path(candidate) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                } }.awaitAll().filterNotNull().forEach(paths::add)
            }
            paths.maxByOrNull { requireNotNull(it.destinationProbe).quote.amountOut }
                ?: error("PancakeSwap found no executable AMM path")
        }
        val bridgeInput = if (selected.source == null) input else selected.candidate.route.originToken
        val bridgeOutput = if (selected.destinationProbe == null) output else selected.candidate.route.destinationToken
        val expectedInput = selected.source?.quote?.amountOut ?: amount
        val protectedInput = selected.source?.minimum ?: amount
        val body = JsonObject().apply {
            selected.destinationProbe?.let {
                addProperty("recipientOnDestChain", recipient)
                add("commands", JsonArray().apply { add(swapCommand(it, bps)) })
            }
        }
        suspend fun bridge(value: BigInteger): PancakeBridgeQuote =
            bridgeQuote(origin, destination, bridgeInput, bridgeOutput, value, body).also {
                it.validated(origin, destination, bridgeInput, bridgeOutput, value, now())
            }
        val expectedBridge = bridge(expectedInput)
        val protectedBridge = if (protectedInput == expectedInput) expectedBridge else bridge(protectedInput)
        fun amountOut(quote: PancakeBridgeQuote) = PancakeCrossChainTransaction.uint(quote.bridgeTransactionData!!.outputAmount)
        val expectedDestination = selected.destinationProbe?.let {
            leg(destination, bridgeOutput, output, amountOut(expectedBridge))
        }
        val protectedDestination = expectedDestination?.let {
            if (amountOut(expectedBridge) == amountOut(protectedBridge)) it
            else leg(destination, bridgeOutput, output, amountOut(protectedBridge))
        }
        val expected = expectedDestination?.quote?.amountOut ?: amountOut(expectedBridge)
        val minimum = protectedDestination?.minimum ?: amountOut(protectedBridge)
        require(minimum > BigInteger.ZERO && minimum <= expected) { "PancakeSwap quote changed while planning" }
        return Plan(protectedBridge, bridgeInput, bridgeOutput, protectedInput,
            selected.source, protectedDestination, bps, expected, minimum)
    }

    companion object {
        fun convertUnits(value: BigInteger, fromDecimals: Int, toDecimals: Int): BigInteger {
            require(value.signum() > 0 && value.bitLength() <= 256 && fromDecimals in 0..255 && toDecimals in 0..255)
            val difference = toDecimals - fromDecimals
            val converted = if (difference >= 0) value * BigInteger.TEN.pow(difference)
                else value / BigInteger.TEN.pow(-difference)
            require(converted.signum() > 0 && converted.bitLength() <= 256) { "Bridge amount has no representable precision" }
            return converted
        }
    }
}

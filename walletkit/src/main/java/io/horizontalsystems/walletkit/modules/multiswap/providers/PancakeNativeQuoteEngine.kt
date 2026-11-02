package io.horizontalsystems.walletkit.modules.multiswap.providers

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.math.BigInteger
import java.util.ArrayDeque

/** Native counterpart of the pinned dapp's primary single-hop, adaptive and full-router strategies. */
internal class PancakeNativeQuoteEngine(private val read: suspend (PancakePoolCall) -> String) {
    private val quoter = PancakeNativeQuoter(read)

    suspend fun quote(chain: Long, input: String, output: String, amount: BigInteger, gasPrice: BigInteger,
                      snapshot: JsonArray): PancakeAmmQuote = coroutineScope {
        require(chain in setOf(1L, 56L, 8453L, 42161L, 324L))
        require(amount.signum() > 0 && amount.bitLength() <= 256 && gasPrice.signum() >= 0 && gasPrice.bitLength() <= 128)
        require(snapshot.size() in 1..512)
        val pools = snapshot.map { it.asJsonObject }.filter { raw ->
            val type = raw["type"].asInt
            type in 0..4 && (type < 3 || chain == 56L &&
                raw["fee"].asString.toBigInteger() < BigInteger.valueOf(1_000_000) &&
                raw["hooks"]?.asString.orEmpty().ifEmpty { NATIVE }.equals(NATIVE, true) &&
                raw["poolManager"].asString.equals(if (type == 3) CL_MANAGER else BIN_MANAGER, true))
        }.map { PancakeNativePool(chain, it) }
        require(pools.isNotEmpty())
        require(PancakeNativePool.canonical(chain, input) != PancakeNativePool.canonical(chain, output))
        val context = currentCoroutineContext()
        val router = PancakeNativeRouter { context.ensureActive() }
        val single = async {
            attempt {
                val choices = router.routes(chain, input, output, PancakeFraction(amount), pools, 1)
                val quoted = candidates(chain, choices)
                val best = quoted.maxWithOrNull { a, b -> adjusted(chain, a, output, gasPrice, pools).compareTo(adjusted(chain, b, output, gasPrice, pools)) }
                    ?: error("No native single-hop quote")
                verify(PancakeNativeTrade(chain, input, output, amount, listOf(best)))
            }
        }
        val adaptive = async {
            attempt {
                val trade = withContext(Dispatchers.Default) { router.findBestTrade(chain, input, output, amount, gasPrice, pools) }
                verify(trade)
            }
        }
        val primary = listOfNotNull(single.await(), adaptive.await()).maxByOrNull { it.amountOut }
        val trade = primary ?: full(chain, input, output, amount, gasPrice, pools, router)
        normalize(trade, input, output, pools)
    }

    private suspend fun <T> attempt(block: suspend () -> T): T? = try { block() }
        catch (e: CancellationException) { throw e } catch (_: Exception) { null }

    private suspend fun candidates(chain: Long, routes: List<PancakeNativeRoute>): List<PancakeNativeRoute> {
        val offchain = routes.filter { it.amountIn.signum() > 0 && it.pools.all { pool -> pool.type == 0 || pool.type == 2 } }.mapNotNull { route ->
            try {
                var amount = route.amount
                for (i in route.pools.indices) {
                    amount = requireNotNull(route.pools[i].quote(route.path[i], route.path[i + 1], amount)).quote
                }
                route.copy(amountOut = amount.quotient).takeIf { it.amountOut.signum() > 0 }
            } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        }
        val onchain = routes.filter { it.amountIn.signum() > 0 && it.pools.any { pool -> pool.type != 0 && pool.type != 2 } }
        val verified = if (onchain.isEmpty()) emptyList() else attempt { quoter.candidates(chain, onchain) }.orEmpty()
        // SDK groups off-chain results first; ties preserve that deterministic preference.
        return offchain + verified
    }

    private suspend fun verify(trade: PancakeNativeTrade): PancakeNativeTrade {
        val checked = quoter.verify(trade.chainId, trade.routes)
        require(checked.size == trade.routes.size) { "A selected route could not be verified" }
        checked.forEachIndexed { i, route ->
            val old = trade.routes[i]
            require(route.amount == old.amount || route.amount.compareTo(old.amount) == 0)
            require(route.path == old.path && route.pools.map { it.id } == old.pools.map { it.id })
            require(route.amountOut.signum() > 0)
        }
        return trade.copy(routes = checked)
    }

    private fun sdkGas(chain: Long, route: PancakeNativeRoute): BigInteger {
        val seen = mutableSetOf<Int>()
        var gas = 0L
        val charged = chain == 1L || chain == 56L || chain == 324L
        for (pool in route.pools) when (pool.type) {
            0 -> gas += if (seen.add(0)) 135000 else 50000
            1 -> {
                if (seen.add(1) && charged) gas += 2000
                if (charged) gas += 80000
            }
            2 -> gas += if (seen.add(2)) 180000 else 70000
        }
        if (charged) gas += 31000L * maxOf(1, route.initializedTicks)
        return BigInteger.valueOf(gas)
    }

    private fun adjusted(chain: Long, route: PancakeNativeRoute, output: String, gasPrice: BigInteger,
                         pools: List<PancakeNativePool>): PancakeFraction {
        val native = PancakeNativePool.canonical(chain, NATIVE)
        val quote = PancakeNativePool.canonical(chain, output)
        val gas = gasPrice * sdkGas(chain, route)
        val cost = if (quote == native) PancakeFraction(gas) else {
            val pricePool = pools.firstOrNull { it.type < 3 && native in it.tokens && quote in it.tokens }
            try { pricePool?.price(native, quote)?.times(gas) ?: PancakeFraction(0) }
            catch (_: Exception) { PancakeFraction(0) }
        }
        return PancakeFraction(route.amountOut) - cost
    }

    private suspend fun full(chain: Long, input: String, output: String, amount: BigInteger, gasPrice: BigInteger,
                             pools: List<PancakeNativePool>, router: PancakeNativeRouter): PancakeNativeTrade {
        val base = router.routes(chain, input, output, PancakeFraction(amount), pools)
        require(base.isNotEmpty()) { "No native cross-chain AMM path" }
        val step = when (chain) { 324L -> 20; 8453L -> 10; else -> 5 }
        val percents = (step..100 step step).toList()
        val byPercent = linkedMapOf<Int, MutableList<PancakeNativeRoute>>()
        for (batch in base.chunked(maxOf(1, 4096 / percents.size))) {
            currentCoroutineContext().ensureActive()
            val requests = batch.flatMap { route -> percents.map { percent ->
                route.copy(amount = PancakeFraction(amount * percent.toBigInteger(), BigInteger.valueOf(100)), percent = percent.toDouble())
            } }
            for (route in candidates(chain, requests)) byPercent.getOrPut(route.percent.toInt()) { mutableListOf() }.add(route)
        }
        val sorted = byPercent.mapValues { (_, routes) -> routes.sortedWith { a, b ->
            adjusted(chain, b, output, gasPrice, pools).compareTo(adjusted(chain, a, output, gasPrice, pools))
        } }
        var best = sorted[100]?.firstOrNull()?.let(::listOf)
        fun score(routes: List<PancakeNativeRoute>) = routes.fold(PancakeFraction(0)) { sum, route -> sum + adjusted(chain, route, output, gasPrice, pools) }
        var bestScore = best?.let(::score)
        data class Search(val routes: List<PancakeNativeRoute>, val index: Int, val remaining: Int)
        val queue = ArrayDeque<Search>()
        for (index in percents.indices.reversed()) {
            val percent = percents[index]
            sorted[percent].orEmpty().take(2).forEach { queue.add(Search(listOf(it), index, 100 - percent)) }
        }
        var splits = 1
        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            splits++
            if (splits > 4 || splits >= 3 && best != null && best.size < splits - 1) break
            repeat(queue.size) {
                val state = queue.removeFirst()
                val used = state.routes.flatMap { it.pools }.map { it.id }.toSet()
                for (index in state.index downTo 0) {
                    val percent = percents[index]
                    if (percent > state.remaining) continue
                    val route = sorted[percent]?.firstOrNull { candidate -> candidate.pools.none { it.id in used } } ?: continue
                    val chosen = state.routes + route
                    val remaining = state.remaining - percent
                    if (remaining == 0) {
                        val value = score(chosen)
                        if (bestScore == null || value > bestScore!!) { best = chosen; bestScore = value }
                    } else queue.add(Search(chosen, index, remaining))
                }
            }
        }
        val chosen = requireNotNull(best) { "No complete split allocation" }.sortedWith { a, b -> b.amount.compareTo(a.amount) }
        return PancakeNativeTrade(chain, input, output, amount, chosen)
    }

    private fun normalize(trade: PancakeNativeTrade, originalInput: String, originalOutput: String,
                          pools: List<PancakeNativePool>): PancakeAmmQuote {
        val currencies = linkedMapOf<String, PancakeNativePool.Currency>()
        pools.flatMap { it.currencies }.forEach { currency ->
            val token = PancakeNativePool.canonical(trade.chainId, currency.address)
            require(currencies[token]?.decimals?.let { it == currency.decimals } != false) { "Inconsistent pool token decimals" }
            currencies.putIfAbsent(token, currency)
        }
        var assigned = BigInteger.ZERO
        val raw = JsonObject().apply {
            addProperty("chainId", trade.chainId); addProperty("srcToken", originalInput.lowercase()); addProperty("dstToken", originalOutput.lowercase())
            addProperty("tradeType", "exactIn"); addProperty("inputAmount", trade.amountIn.toString()); addProperty("outputAmount", trade.amountOut.toString())
            addProperty("swapFeeBps", "0"); addProperty("swapFee", "0")
            add("routes", JsonArray().apply {
                trade.routes.forEachIndexed { index, route ->
                    val value = if (index == trade.routes.lastIndex) trade.amountIn - assigned else route.amountIn
                    require(value.signum() > 0 && value >= route.amountIn && value - route.amountIn < trade.routes.size.toBigInteger())
                    assigned += value
                    add(JsonObject().apply {
                        addProperty("percent", minOf(100.0, route.percent)); addProperty("inputAmount", value.toString()); addProperty("outputAmount", route.amountOut.toString())
                        add("path", JsonArray().apply { route.path.forEachIndexed { i, address ->
                            val token = PancakeNativePool.canonical(trade.chainId, address)
                            val currency = requireNotNull(currencies[token])
                            val isInputNative = i == 0 && originalInput.equals(NATIVE, true)
                            val isOutputNative = i == route.path.lastIndex && originalOutput.equals(NATIVE, true)
                            add(JsonObject().apply {
                                addProperty("address", token); addProperty("decimals", currency.decimals)
                                addProperty("symbol", if (isInputNative || isOutputNative) if (trade.chainId == 56L) "BNB" else "ETH" else currency.symbol)
                            })
                        } })
                        add("pools", JsonArray().apply { route.pools.forEachIndexed { i, pool -> add(pool.descriptor(route.path[i], route.path[i + 1])) } })
                    })
                }
            })
        }
        require(assigned == trade.amountIn)
        return PancakeAmmQuote.parse(raw, trade.chainId, originalInput, originalOutput, trade.amountIn)
    }

    companion object {
        private const val NATIVE = "0x0000000000000000000000000000000000000000"
        private const val CL_MANAGER = "0xa0ffb9c1ce1fe56963b0321b32e7a0302114058b"
        private const val BIN_MANAGER = "0xc697d2898e0d09264376196696c51d7abbbaa4a9"
    }
}

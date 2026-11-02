package io.horizontalsystems.walletkit.modules.multiswap.providers

import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.CancellationException
import kotlin.math.floor
import kotlin.math.sqrt

/** Exact raw-unit prices and stream allocations; floating point is only the dapp's split-count heuristic. */
internal class PancakeNativeRouter(private val checkActive: () -> Unit = {}) {
    private class Vertex(val token: String) { val edges = mutableListOf<Edge>() }
    private class Edge(val a: Vertex, val b: Vertex, var pool: PancakeNativePool) {
        fun other(vertex: Vertex) = if (vertex === a) b else a
    }
    private class Graph(pools: List<PancakeNativePool>) {
        val vertices = linkedMapOf<String, Vertex>()
        val edges = linkedMapOf<String, Edge>()
        private val reachable = mutableMapOf<Triple<String, String, Int>, Boolean>()
        private fun key(pool: PancakeNativePool, a: String, b: String) = listOf(a, b).sorted().joinToString("-") + "-" + pool.id.lowercase()
        init {
            for (pool in pools) for (i in pool.tokens.indices) for (j in i + 1 until pool.tokens.size) {
                val a = vertices.getOrPut(pool.tokens[i]) { Vertex(pool.tokens[i]) }
                val b = vertices.getOrPut(pool.tokens[j]) { Vertex(pool.tokens[j]) }
                if (a === b) continue
                val id = key(pool, a.token, b.token)
                if (edges.containsKey(id)) continue
                val edge = Edge(a, b, pool)
                edges[id] = edge; a.edges.add(edge); b.edges.add(edge)
            }
        }
        fun canReach(from: Vertex, target: Vertex, hops: Int, visited: MutableSet<Vertex> = mutableSetOf()): Boolean {
            val key = Triple(from.token, target.token, hops)
            reachable[key]?.let { return it }
            if (from === target) return true
            if (hops <= 0) return false
            visited.add(from)
            for (edge in from.edges) {
                val next = edge.other(from)
                if (next !in visited && canReach(next, target, hops - 1, visited)) { reachable[key] = true; return true }
            }
            reachable[key] = false
            return false
        }
        fun apply(route: PancakeNativeRoute): Pair<PancakeFraction, BigInteger> {
            var amount = route.amount
            var gas = BigInteger.ZERO
            for (i in route.pools.indices) {
                val edge = requireNotNull(edges[key(route.pools[i], route.path[i], route.path[i + 1])])
                val quote = requireNotNull(edge.pool.quote(route.path[i], route.path[i + 1], amount)) { "Pool cannot quote allocated input" }
                edge.pool = quote.poolAfter; amount = quote.quote
                // The reference replay estimates on the after-state, so its initialized-crossing surcharge is zero.
                gas += if (edge.pool.type == 1 || edge.pool.type == 3) {
                    if (edge.pool.chainId == 8453L) BigInteger.ZERO else BigInteger.valueOf(82000)
                } else quote.gas
            }
            return amount to gas
        }
    }
    private class Prices(graph: Graph, origin: Vertex, gasPrice: BigInteger, chain: Long) {
        private val prices = linkedMapOf(origin to PancakeFraction(1))
        private val done = mutableSetOf<Vertex>()
        private val weights = mutableMapOf<Edge, BigInteger>()
        private var frontier = mutableListOf<Edge>()
        private val gasInOrigin: PancakeFraction
        init {
            fun addEdges(from: Vertex) {
                val price = prices.getValue(from)
                val fresh = from.edges.filter { edge ->
                    if (edge.other(from) in done) false else {
                        weights[edge] = (price * edge.pool.reserve(from.token)).quotient
                        true
                    }
                }.sortedWith(compareBy { weights.getValue(it) }).toMutableList()
                val merged = mutableListOf<Edge>()
                while (frontier.isNotEmpty() && fresh.isNotEmpty()) {
                    merged.add(if (weights.getValue(frontier.first()) < weights.getValue(fresh.first())) frontier.removeAt(0) else fresh.removeAt(0))
                }
                frontier = (merged + frontier + fresh).toMutableList()
            }
            addEdges(origin); done.add(origin)
            while (frontier.isNotEmpty()) {
                val edge = frontier.removeAt(frontier.lastIndex)
                val from = if (edge.b in done) edge.b else edge.a
                val to = edge.other(from)
                if (to in done) continue
                prices[to] = edge.pool.price(to.token, from.token) * prices.getValue(from)
                addEdges(to); done.add(to)
            }
            val native = requireNotNull(graph.vertices[PancakeNativePool.canonical(chain, PancakeAmmQuote.NATIVE)]) { "Native gas currency is absent" }
            gasInOrigin = prices.getValue(native) * gasPrice
        }
        fun price(from: Vertex, to: Vertex) = prices.getValue(from) / prices.getValue(to)
        fun gas(vertex: Vertex) = gasInOrigin / prices.getValue(vertex)
        fun cost(vertex: Vertex, gas: BigInteger) = (gas(vertex) * gas).quotient
    }
    private data class State(val amount: PancakeFraction, val gas: BigInteger, val hops: Int,
                             val edge: Edge? = null, val score: PancakeFraction? = null)
    private data class Solve(val trade: PancakeNativeTrade, val adjustedOutput: BigInteger, val gasInInput: BigInteger)

    fun findBestTrade(chain: Long, input: String, output: String, amount: BigInteger, gasPrice: BigInteger,
                      pools: List<PancakeNativePool>, maxHops: Int = 3, split: Boolean = true): PancakeNativeTrade {
        require(amount.signum() > 0 && amount.bitLength() <= 256 && gasPrice.signum() >= 0 && gasPrice.bitLength() <= 128)
        require(maxHops in 1..3 && pools.size in 1..512 && pools.all { it.chainId == chain })
        val from = PancakeNativePool.canonical(chain, input)
        val to = PancakeNativePool.canonical(chain, output)
        require(from != to)
        val first = try { solve(chain, from, to, amount, gasPrice, pools, maxHops, 1) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (!split) throw e else solve(chain, from, to, amount, gasPrice, pools, maxHops, 10) }
        if (!split) return first.trade
        val streams = adaptiveSplits(first)
        if (streams == 1) return first.trade
        val other = solve(chain, from, to, amount, gasPrice, pools, maxHops, streams)
        return if (other.adjustedOutput > first.adjustedOutput) other.trade else first.trade
    }

    private fun solve(chain: Long, input: String, output: String, amount: BigInteger, gasPrice: BigInteger,
                      pools: List<PancakeNativePool>, maxHops: Int, streams: Int): Solve {
        val graph = Graph(pools)
        val start = requireNotNull(graph.vertices[input])
        val finish = requireNotNull(graph.vertices[output])
        val prices = Prices(graph, start, gasPrice, chain)
        fun pathFor(inputAmount: PancakeFraction): PancakeNativeRoute? {
            val best = mutableMapOf(start to State(inputAmount, BigInteger.ZERO, 0))
            val done = mutableSetOf<Vertex>()
            val pending = mutableListOf(start)
            while (pending.isNotEmpty()) {
                checkActive()
                var chosen = 0
                for (i in 1 until pending.size) {
                    val candidate = best.getValue(pending[i]).score
                    val current = best.getValue(pending[chosen]).score
                    if (candidate != null && (current == null || candidate > current)) chosen = i
                }
                val vertex = pending[chosen]
                val state = best.getValue(vertex)
                if (vertex === finish) {
                    val selected = mutableListOf<PancakeNativePool>()
                    val path = mutableListOf(output)
                    var cursor = finish
                    while (best.getValue(cursor).edge != null) {
                        val edge = requireNotNull(best.getValue(cursor).edge)
                        val prior = edge.other(cursor)
                        selected.add(0, edge.pool); path.add(0, prior.token); cursor = prior
                        require(selected.size <= maxHops) { "Route exceeds hop bound" }
                    }
                    require(cursor === start)
                    return PancakeNativeRoute(inputAmount, state.amount.quotient, path, selected, state.gas)
                }
                pending.removeAt(chosen)
                for (edge in vertex.edges) {
                    val next = edge.other(vertex)
                    if (next in done || !graph.canReach(next, finish, maxHops - state.hops - 1)) continue
                    checkActive()
                    try {
                        val quote = edge.pool.quote(vertex.token, next.token, state.amount) ?: continue
                        val gas = state.gas + quote.gas
                        val price = prices.price(next, finish)
                        val score = price * quote.quote - price * prices.gas(next) * gas
                        val old = best[next]
                        if (old?.edge == null) pending.add(next)
                        if (old?.edge == null || old.score == null || score > old.score) {
                            best[next] = State(quote.quote, gas, state.hops + 1, edge, score)
                        }
                    } catch (e: CancellationException) { throw e } catch (_: Exception) { }
                }
                done.add(vertex)
            }
            return null
        }
        val routes = mutableListOf<PancakeNativeRoute>()
        var assigned = PancakeFraction(0)
        var merged = false
        val whole = PancakeFraction(amount)
        for (i in 0 until streams) {
            checkActive()
            val part = if (i == streams - 1) PancakeFraction(1) - assigned
                else PancakeFraction(BigInteger.ONE, streams.toBigInteger())
            val value = if (i == streams - 1) whole - whole * assigned else whole * part
            val route = requireNotNull(pathFor(value)) { "No executable liquidity path" }
                .copy(percent = part.decimal(6).toDouble() * 100)
            graph.apply(route)
            val existing = routes.indexOfFirst { old ->
                old.pools.size == route.pools.size && old.pools.indices.all {
                    old.pools[it].type == route.pools[it].type && old.pools[it].id.equals(route.pools[it].id, true)
                }
            }
            if (existing < 0) routes.add(route) else {
                val old = routes[existing]
                routes[existing] = old.copy(amount = old.amount + route.amount, amountOut = old.amountOut + route.amountOut,
                    gas = old.gas + route.gas, percent = old.percent + route.percent)
                merged = true
            }
            assigned += part
        }
        if (merged) {
            val fresh = Graph(pools)
            for (i in routes.indices) {
                checkActive()
                val (out, gas) = fresh.apply(routes[i])
                routes[i] = routes[i].copy(amountOut = out.quotient, gas = gas)
            }
        }
        val gasInInput = routes.fold(BigInteger.ZERO) { sum, route -> sum + prices.cost(start, route.gas) }
        val adjusted = routes.fold(BigInteger.ZERO) { sum, route -> sum + route.amountOut - prices.cost(finish, route.gas) }
        return Solve(PancakeNativeTrade(chain, input, output, amount, routes), adjusted, gasInInput)
    }

    private fun adaptiveSplits(solved: Solve): Int {
        val trade = solved.trade
        var mid = PancakeFraction(0)
        for (route in trade.routes) {
            var price = PancakeFraction(1)
            for (i in route.pools.indices) price *= route.pools[i].price(route.path[i], route.path[i + 1])
            mid += price * route.amount
        }
        val impact = (mid - PancakeFraction(trade.amountOut)) / mid
        if (impact <= PancakeFraction(0)) return 10
        val ratio = if (impact > PancakeFraction(1_000_000)) impact.decimal().setScale(0).toDouble() else impact.decimal(6).toDouble()
        val optimal = sqrt(trade.amountIn.toDouble() * ratio / solved.gasInInput.toDouble())
        return if (optimal.isFinite()) floor(optimal + 0.5).coerceIn(1.0, 100.0).toInt() else 10
    }

    fun routes(chain: Long, input: String, output: String, amount: PancakeFraction,
               pools: List<PancakeNativePool>, maxHops: Int = 3): List<PancakeNativeRoute> {
        val graph = Graph(pools)
        val from = PancakeNativePool.canonical(chain, input)
        val to = PancakeNativePool.canonical(chain, output)
        val result = mutableListOf<PancakeNativeRoute>()
        fun walk(token: String, path: List<String>, selected: List<PancakeNativePool>) {
            checkActive()
            if (token == to) {
                require(result.size < 4096) { "Route graph exceeds the bounded search limit" }
                result.add(PancakeNativeRoute(amount, BigInteger.ZERO, path, selected,
                    selected.fold(BigInteger.ZERO) { sum, pool -> sum + when (pool.type) {
                        0 -> BigInteger.valueOf(185000); 2 -> BigInteger.valueOf(250000)
                        4 -> BigInteger.ZERO
                        else -> if (chain == 8453L) BigInteger.ZERO else BigInteger.valueOf(82000)
                    } }))
                return
            }
            if (selected.size == maxHops) return
            for (edge in graph.vertices[token]?.edges.orEmpty()) {
                val next = if (edge.a.token == token) edge.b.token else edge.a.token
                if (next in path || selected.any { it.id.equals(edge.pool.id, true) }) continue
                walk(next, path + next, selected + edge.pool)
            }
        }
        walk(from, listOf(from), emptyList())
        return result
    }
}

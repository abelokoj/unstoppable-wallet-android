/*
 * Pool adapter and stable probe semantics ported from PancakeSwap smart-router 7.8.0
 * and the pinned SDKs identified in PancakePoolMath.kt. Copyright PancakeSwap contributors.
 * Upstream distributed LICENSE: GNU GPL version 3; see the bundled PancakeSwap notice.
 */
package io.horizontalsystems.walletkit.modules.multiswap.providers

import com.google.gson.JsonObject
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.math.pow

/** A detached pool snapshot. Native asset ordering is kept separate from wrapped graph identity. */
internal class PancakeNativePool private constructor(
    val chainId: Long,
    raw: JsonObject,
    fractionalBalances: List<PancakeFraction>?,
) {
    constructor(chainId: Long, raw: JsonObject) : this(chainId, raw, null)

    private val snapshot = raw.deepCopy()
    val raw: JsonObject get() = snapshot.deepCopy()
    val type = snapshot.get("type").asInt.also { require(it in 0..4) }
    val id = snapshot.text(if (type >= 3) "id" else "address").lowercase().also {
        require(it.matches(Regex(if (type >= 3) "0x[0-9a-f]{64}" else "0x[0-9a-f]{40}")))
    }
    data class Currency(val address: String, val decimals: Int, val symbol: String)
    val currencies: List<Currency> = when (type) {
        0 -> listOf(snapshot.getAsJsonObject("reserve0").getAsJsonObject("currency"), snapshot.getAsJsonObject("reserve1").getAsJsonObject("currency"))
        1 -> listOf(snapshot.getAsJsonObject("token0"), snapshot.getAsJsonObject("token1"))
        2 -> snapshot.getAsJsonArray("balances").map { it.asJsonObject.getAsJsonObject("currency") }
        else -> listOf(snapshot.getAsJsonObject("currency0"), snapshot.getAsJsonObject("currency1"))
    }.map { currency ->
        Currency(currency.text("address").lowercase().also { require(it.matches(Regex("0x[0-9a-f]{40}"))) },
            currency.text("decimals").toInt().also { require(it in 0..255) }, currency.get("symbol")?.asString.orEmpty())
    }
    val tokens = currencies.map { canonical(chainId, it.address) }
    private val stableBalances = if (type == 2) fractionalBalances ?: snapshot.getAsJsonArray("balances").map { PancakeFraction(it.asJsonObject.uint("value")) } else null
    private val fee: PancakeFraction by lazy {
        if (type == 2) {
            val value = snapshot.text("fee").also { require(it.matches(Regex("[0-9]{1,2}(?:\\.[0-9]{1,6})?"))) }
            PancakeFraction(BigDecimal(value).movePointRight(6).toBigIntegerExact(), BigInteger.valueOf(100_000_000))
        }
        else PancakeFraction(snapshot.uint("fee"), BigInteger.valueOf(1_000_000))
    }
    private val ticks: List<PancakePoolMath.Tick> by lazy {
        snapshot.getAsJsonArray("ticks")?.map { element ->
            val tick = element.asJsonObject
            PancakePoolMath.Tick(tick.text("index").toInt().also { require(it in PancakePoolMath.MIN_TICK..PancakePoolMath.MAX_TICK) },
                tick.text("liquidityNet").also { require(it.matches(Regex("-?[0-9]{1,39}"))) }.toBigInteger().also { require(it >= -ONE.shiftLeft(127) && it < ONE.shiftLeft(127)) })
        }?.also { list -> require(list.zipWithNext().all { (a, b) -> a.index < b.index }) } ?: emptyList()
    }

    private val binSpotPrice: PancakeFraction by lazy {
        val exponent = snapshot.text("activeId").toInt() - 8388608
        require(kotlin.math.abs(exponent) < (1 shl 20))
        val step = snapshot.text("binStep").toInt().also { require(it in 1..100) }
        require(PancakePoolMath.binPrice(snapshot.text("activeId").toInt(), step).signum() > 0)
        val n = BigInteger.valueOf(10000L + step).pow(kotlin.math.abs(exponent))
        val d = BigInteger.valueOf(10000).pow(kotlin.math.abs(exponent))
        if (exponent >= 0) PancakeFraction(n, d) else PancakeFraction(d, n)
    }

    init {
        require(currencies.size in 2..3 && tokens.distinct().size == tokens.size)
        if (type != 2) require(currencies.size == 2 && currencies[0].address < currencies[1].address)
    }

    private fun index(token: String) = tokens.indexOf(canonical(chainId, token)).also { require(it >= 0) }

    fun reserve(token: String): BigInteger {
        val i = index(token)
        return if (type == 2) stableBalances!![i].quotient else snapshot.getAsJsonObject("reserve$i")?.uint("value") ?: ZERO
    }

    /** Exact output-raw / input-raw ratio, with the SDK's stable spot-price probe rule. */
    fun price(base: String, quote: String): PancakeFraction {
        val i = index(base); val j = index(quote)
        require(i != j)
        return when (type) {
            0 -> PancakeFraction(reserve(quote), reserve(base))
            1, 3 -> {
                val sqrt = snapshot.uint("sqrtRatioX96")
                val forward = PancakeFraction(sqrt * sqrt, ONE.shiftLeft(192))
                if (i == 0) forward else forward.inverse()
            }
            2 -> {
                var probe = BigInteger.TEN.pow(currencies[i].decimals)
                val baseTokens = reserve(base).toDouble() / 10.0.pow(currencies[i].decimals)
                val quoteTokens = reserve(quote).toDouble() / 10.0.pow(currencies[j].decimals)
                val smaller = minOf(baseTokens, quoteTokens)
                if (smaller > 0 && smaller < 1) probe = stableProbe(smaller, currencies[i].decimals)
                require(probe > ZERO)
                stableQuote(i, j, PancakeFraction(probe)) / PancakeFraction(probe)
            }
            else -> if (i == 0) binSpotPrice else binSpotPrice.inverse()
        }
    }

    data class Swap(val quote: PancakeFraction, val poolAfter: PancakeNativePool, val gas: BigInteger) {
        val amountOut: BigInteger get() = quote.quotient
    }

    fun quote(input: String, output: String, amount: BigInteger): Swap? = quote(input, output, PancakeFraction(amount))

    fun quote(input: String, output: String, amount: PancakeFraction): Swap? {
        if (amount.quotient <= ZERO || amount.quotient.bitLength() > 256) return null
        return try {
            val i = index(input); val j = index(output)
            require(i != j)
            val after = snapshot.deepCopy()
            var balancesAfter: List<PancakeFraction>? = null
            var gas = ZERO
            val result = when (type) {
                0 -> {
                    val a = reserve(input); val b = reserve(output)
                    require(a > ZERO && b > ZERO)
                    val withFee = amount.quotient * BigInteger.valueOf(9975)
                    val out = withFee * b / (a * BigInteger.valueOf(10000) + withFee)
                    require((a + amount.quotient).bitLength() <= 256)
                    after.getAsJsonObject("reserve$i").addProperty("value", (a + amount.quotient).toString())
                    after.getAsJsonObject("reserve$j").addProperty("value", (b - out).toString())
                    gas = BigInteger.valueOf(185000)
                    PancakeFraction(out)
                }
                2 -> {
                    val out = stableQuote(i, j, amount)
                    balancesAfter = stableBalances!!.mapIndexed { k, balance ->
                        when (k) { i -> balance + amount; j -> balance - out; else -> balance }
                    }
                    balancesAfter.forEachIndexed { k, balance ->
                        require(balance >= PancakeFraction(0) && balance.quotient.bitLength() <= 256)
                        after.getAsJsonArray("balances")[k].asJsonObject.addProperty("value", balance.quotient.toString())
                    }
                    gas = BigInteger.valueOf(250000)
                    out
                }
                1, 3 -> {
                    require(ticks.isNotEmpty())
                    val protocol = if (type == 3) (snapshot.get("protocolFee")?.asString?.toInt() ?: 0) and 4095 else 0
                    // The current official adapter adds protocolFee[0] in both directions.
                    val totalFee = snapshot.text("fee").toInt() + protocol
                    val spacing = snapshot.get("tickSpacing")?.asString?.toInt() ?: when (totalFee) {
                        100 -> 1; 500 -> 10; 2500 -> 50; 10000 -> 200; else -> error("Unknown tick spacing")
                    }
                    val previousTick = snapshot.text("tick").toInt()
                    val swapped = PancakePoolMath.concentrated(amount.quotient, i == 0,
                        snapshot.uint("sqrtRatioX96"), snapshot.uint("liquidity"), previousTick, spacing, totalFee, ticks)
                    after.addProperty("sqrtRatioX96", swapped.sqrt.toString())
                    after.addProperty("liquidity", swapped.liquidity.toString())
                    after.addProperty("tick", swapped.tick)
                    if (chainId in GAS_CHAINS) {
                        val beforeIndex = PancakePoolMath.indexAtOrBelow(ticks, previousTick)
                        val afterIndex = PancakePoolMath.indexAtOrBelow(ticks, swapped.tick)
                        if (previousTick != swapped.tick) require(beforeIndex >= 0 && afterIndex >= 0)
                        val crossed = if (previousTick == swapped.tick) 0 else kotlin.math.abs(beforeIndex - afterIndex)
                        gas = BigInteger.valueOf(82000L + 31000L * crossed)
                    }
                    PancakeFraction(swapped.output)
                }
                else -> {
                    val bins = snapshot.getAsJsonObject("reserveOfBin").entrySet().map { (id, element) ->
                        val b = element.asJsonObject
                        PancakePoolMath.Bin(id.toInt().also { require(it in 0..0xffffff) },
                            b.uint("reserveX").also { require(it.bitLength() <= 128) }, b.uint("reserveY").also { require(it.bitLength() <= 128) })
                    }
                    val out = PancakePoolMath.bin(amount.quotient, i == 0, snapshot.text("activeId").toInt(),
                        snapshot.text("binStep").toInt(), snapshot.text("fee").toInt(), snapshot.get("protocolFee")?.asString?.toInt() ?: 0, bins)
                    // The pinned bin adapter reports poolAfter unchanged; route split accounting must keep that behavior.
                    PancakeFraction(out)
                }
            }
            if (result.quotient <= ZERO) null else Swap(result, PancakeNativePool(chainId, after, balancesAfter), gas)
        } catch (_: RuntimeException) { null }
    }

    /** Match smart-router's bundled parseUnits, including its legacy exponent-notation behavior. */
    private fun stableProbe(value: Double, decimals: Int): BigInteger {
        val number = BigDecimal.valueOf(value).stripTrailingZeros()
        val text = if (value < 0.000001) number.toString().lowercase() else number.toPlainString()
        val parts = text.split('.')
        var integer = parts[0]
        var fraction = (parts.getOrNull(1) ?: "0").trimEnd('0')
        if (decimals == 0) {
            if (kotlin.math.floor((".$fraction").toDoubleOrNull()?.plus(0.5) ?: Double.NaN) == 1.0) integer = (integer.toBigInteger() + ONE).toString()
            fraction = ""
        } else if (fraction.length > decimals) {
            val left = fraction.take(decimals - 1)
            val unit = fraction[decimals - 1]
            val right = fraction.drop(decimals)
            val rounded = kotlin.math.floor("$unit.$right".toDouble() + 0.5)
            require(rounded.isFinite())
            fraction = if (rounded > 9) ((left.ifEmpty { "0" }.toBigInteger() + ONE).toString() + "0").padStart(left.length + 1, '0')
                else left + rounded.toInt().toString()
            if (fraction.length > decimals) {
                fraction = fraction.drop(1)
                integer = (integer.toBigInteger() + ONE).toString()
            }
            fraction = fraction.take(decimals)
        } else fraction = fraction.padEnd(decimals, '0')
        return (integer + fraction).toBigInteger()
    }

    private fun stableQuote(input: Int, output: Int, amount: PancakeFraction): PancakeFraction {
        val precision = BigInteger.TEN.pow(18)
        fun normalized(value: PancakeFraction, index: Int) = (value * precision).quotient / BigInteger.TEN.pow(currencies[index].decimals)
        val normalizedBalances = stableBalances!!.mapIndexed { index, balance -> normalized(balance, index) }
        val normalizedOutput = PancakePoolMath.stable(snapshot.uint("amplifier"), normalizedBalances, input, output, normalized(amount, input), fee)
        return PancakeFraction(normalizedOutput * BigInteger.TEN.pow(currencies[output].decimals), precision)
    }

    fun descriptor(input: String, output: String): JsonObject {
        val i = index(input); val j = index(output)
        require(i != j)
        val pair = (if (type == 2) listOf(currencies[i], currencies[j]) else currencies).sortedBy { it.address }
        return JsonObject().apply {
            addProperty("provider", "pancakeswap")
            addProperty("type", listOf("v2", "v3", "stableswap", "infinityCl", "infinityBin")[type])
            addProperty("address", id)
            addProperty("token0", pair[0].address); addProperty("token1", pair[1].address)
            addProperty("fee", when (type) { 0 -> "2500"; 2 -> (fee * BigInteger.TEN.pow(10)).quotient.toString(); else -> snapshot.uint("fee").toString() })
            if (type >= 3) {
                val strideName = if (type == 3) "tickSpacing" else "binStep"
                val stride = snapshot.text(strideName).toInt()
                addProperty(strideName, stride)
                addProperty("poolManager", snapshot.text("poolManager").lowercase())
                addProperty("hooks", snapshot.get("hooks")?.asString?.lowercase() ?: NATIVE)
                val bitmap = snapshot.get("hooksRegistrationBitmap")?.asString?.let {
                    if (it.startsWith("0x")) it.drop(2).toBigInteger(16) else it.toBigInteger()
                } ?: ZERO
                require(bitmap >= ZERO && bitmap.bitLength() <= 16)
                val parameters = BigInteger.valueOf(stride.toLong()).shiftLeft(16).or(bitmap)
                addProperty("parameters", "0x" + parameters.toString(16).padStart(64, '0'))
            }
        }
    }

    companion object {
        private val ZERO = BigInteger.ZERO
        private val ONE = BigInteger.ONE
        private const val NATIVE = "0x0000000000000000000000000000000000000000"
        private val GAS_CHAINS = setOf(1L, 56L, 324L, 42161L, 5L, 97L, 204L, 5611L, 280L)
        fun canonical(chain: Long, token: String): String = PancakeRoutePlanner.wrapped(chain, token).lowercase()
        private fun JsonObject.text(name: String) = requireNotNull(get(name)?.takeIf { it.isJsonPrimitive }).asString
        private fun JsonObject.uint(name: String) = text(name).also { require(it.matches(Regex("[0-9]{1,78}"))) }.toBigInteger().also { require(it.bitLength() <= 256) }
    }
}

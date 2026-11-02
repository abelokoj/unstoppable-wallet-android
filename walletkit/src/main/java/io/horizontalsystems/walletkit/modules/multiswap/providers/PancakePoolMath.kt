/*
 * Kotlin port of PancakeSwap v3-sdk (TickMath, SqrtPriceMath, SwapMath, tickList),
 * stable-swap-sdk (getD, getY, getSwapOutput), and infinity-sdk (bin swap math).
 * Copyright PancakeSwap and Uniswap contributors. Upstream distributed LICENSE: GNU GPL version 3.
 * Upstream: https://github.com/pancakeswap/pancake-frontend/tree/develop/packages
 * See the bundled PancakeSwap source/license notice for the pinned package versions.
 */
package io.horizontalsystems.walletkit.modules.multiswap.providers

import java.math.BigInteger

/** Integer arithmetic follows the pinned SDK's exact-input rounding, including word boundaries. */
internal object PancakePoolMath {
    private val ZERO = BigInteger.ZERO
    private val ONE = BigInteger.ONE
    private val TWO = BigInteger.valueOf(2)
    val Q96: BigInteger = ONE.shiftLeft(96)
    val Q128: BigInteger = ONE.shiftLeft(128)
    val MAX_UINT256: BigInteger = ONE.shiftLeft(256) - ONE
    private val MILLION = BigInteger.valueOf(1_000_000)
    const val MIN_TICK = -887272
    const val MAX_TICK = 887272
    val MIN_SQRT: BigInteger = BigInteger("4295128739")
    val MAX_SQRT: BigInteger = BigInteger("1461446703485210103287273052203988822378723970342")

    data class Tick(val index: Int, val liquidityNet: BigInteger)
    data class Concentrated(val output: BigInteger, val sqrt: BigInteger, val liquidity: BigInteger, val tick: Int)

    private val tickMultipliers = listOf(
        "fffcb933bd6fad37aa2d162d1a594001", "fff97272373d413259a46990580e213a",
        "fff2e50f5f656932ef12357cf3c7fdcc", "ffe5caca7e10e4e61c3624eaa0941cd0",
        "ffcb9843d60f6159c9db58835c926644", "ff973b41fa98c081472e6896dfb254c0",
        "ff2ea16466c96a3843ec78b326b52861", "fe5dee046a99a2a811c461f1969c3053",
        "fcbe86c7900a88aedcffc83b479aa3a4", "f987a7253ac413176f2b074cf7815e54",
        "f3392b0822b70005940c7a398e4b70f3", "e7159475a2c29b7443b29c7fa6e889d9",
        "d097f3bdfd2022b8845ad8f792aa5825", "a9f746462d870fdf8a65dc1f90e061e5",
        "70d869a156d2a1b890bb3df62baf32f7", "31be135f97d08fd981231505542fcfa6",
        "9aa508b5b7a84e1c677de54f3e99bc9", "5d6af8dedb81196699c329225ee604",
        "2216e584f5fa1ea926041bedfe98", "48a170391f7dc42444e8fa2",
    ).map { BigInteger(it, 16) }

    fun ceil(numerator: BigInteger, denominator: BigInteger): BigInteger {
        require(numerator >= ZERO && denominator > ZERO)
        val (q, r) = numerator.divideAndRemainder(denominator)
        return if (r == ZERO) q else q + ONE
    }

    fun sqrtAtTick(tick: Int): BigInteger {
        require(tick in MIN_TICK..MAX_TICK)
        val magnitude = kotlin.math.abs(tick)
        var ratio = Q128
        for (bit in tickMultipliers.indices) {
            if (magnitude and (1 shl bit) != 0) ratio = (ratio * tickMultipliers[bit]).shiftRight(128)
        }
        if (tick > 0) ratio = MAX_UINT256 / ratio
        return ceil(ratio, ONE.shiftLeft(32))
    }

    fun tickAtSqrt(sqrt: BigInteger): Int {
        require(sqrt >= MIN_SQRT && sqrt < MAX_SQRT)
        val x128 = sqrt.shiftLeft(32)
        val msb = x128.bitLength() - 1
        var r = if (msb >= 128) x128.shiftRight(msb - 127) else x128.shiftLeft(127 - msb)
        var log = BigInteger.valueOf((msb - 128).toLong()).shiftLeft(64)
        repeat(14) { i ->
            r = (r * r).shiftRight(127)
            val f = r.shiftRight(128)
            log = log.or(f.shiftLeft(63 - i))
            r = r.shiftRight(f.toInt())
        }
        val scaled = log * BigInteger("255738958999603826347141")
        val low = (scaled - BigInteger("3402992956809132418596140100660247210")).shiftRight(128).toInt()
        val high = (scaled + BigInteger("291339464771989622907027621153398088495")).shiftRight(128).toInt()
        return if (low == high || sqrtAtTick(high) > sqrt) low else high
    }

    private fun delta0(a: BigInteger, b: BigInteger, liquidity: BigInteger, up: Boolean): BigInteger {
        val lower = minOf(a, b); val upper = maxOf(a, b)
        val numerator = liquidity.shiftLeft(96) * (upper - lower)
        return if (up) ceil(ceil(numerator, upper), lower) else numerator / upper / lower
    }

    private fun delta1(a: BigInteger, b: BigInteger, liquidity: BigInteger, up: Boolean): BigInteger {
        val numerator = liquidity * (a - b).abs()
        return if (up) ceil(numerator, Q96) else numerator / Q96
    }

    private fun nextSqrt(sqrt: BigInteger, liquidity: BigInteger, amount: BigInteger, zeroForOne: Boolean): BigInteger {
        require(sqrt > ZERO && liquidity > ZERO)
        if (amount == ZERO) return sqrt
        if (!zeroForOne) return sqrt + amount * Q96 / liquidity
        val numerator = liquidity.shiftLeft(96)
        val product = (amount * sqrt).and(MAX_UINT256)
        if (product / amount == sqrt) {
            val denominator = (numerator + product).and(MAX_UINT256)
            if (denominator >= numerator) return ceil(numerator * sqrt, denominator)
        }
        return ceil(numerator, numerator / sqrt + amount)
    }

    fun indexAtOrBelow(ticks: List<Tick>, tick: Int): Int {
        var low = 0; var high = ticks.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (ticks[middle].index <= tick) low = middle + 1 else high = middle
        }
        return low - 1
    }

    fun concentrated(
        amount: BigInteger, zeroForOne: Boolean, initialSqrt: BigInteger, initialLiquidity: BigInteger,
        initialTick: Int, spacing: Int, fee: Int, ticks: List<Tick>,
    ): Concentrated {
        require(amount > ZERO && spacing in 1..32767 && fee in 0 until 1_000_000 && ticks.isNotEmpty())
        require(initialTick in MIN_TICK until MAX_TICK && initialLiquidity >= ZERO && initialLiquidity.bitLength() <= 128)
        require(initialSqrt >= sqrtAtTick(initialTick) && initialSqrt <= sqrtAtTick(initialTick + 1))
        val feeInteger = BigInteger.valueOf(fee.toLong())
        val limit = if (zeroForOne) MIN_SQRT + ONE else MAX_SQRT - ONE
        require(if (zeroForOne) initialSqrt > limit else initialSqrt < limit)
        var remaining = amount; var output = ZERO
        var sqrt = initialSqrt; var liquidity = initialLiquidity; var tick = initialTick
        while (remaining != ZERO && sqrt != limit) {
            val start = sqrt
            val compressed = Math.floorDiv(tick, spacing)
            val index = indexAtOrBelow(ticks, tick)
            val candidate = if (zeroForOne) ticks.getOrNull(index) else ticks.getOrNull(index + 1)
            val boundary = if (zeroForOne) (compressed shr 8 shl 8) * spacing
                else (((compressed + 1 shr 8) + 1 shl 8) - 1) * spacing
            val nextTick = (if (zeroForOne) maxOf(boundary, candidate?.index ?: boundary)
                else minOf(boundary, candidate?.index ?: boundary)).coerceIn(MIN_TICK, MAX_TICK)
            val initialized = candidate?.index == nextTick
            val nextTickSqrt = sqrtAtTick(nextTick)
            val target = if (zeroForOne) maxOf(nextTickSqrt, limit) else minOf(nextTickSqrt, limit)
            val lessFee = remaining * (MILLION - feeInteger) / MILLION
            val targetInput = if (zeroForOne) delta0(target, sqrt, liquidity, true) else delta1(sqrt, target, liquidity, true)
            sqrt = if (lessFee >= targetInput) target else nextSqrt(sqrt, liquidity, lessFee, zeroForOne)
            val amountIn = if (sqrt == target) targetInput else if (zeroForOne) delta0(sqrt, start, liquidity, true) else delta1(start, sqrt, liquidity, true)
            output += if (zeroForOne) delta1(sqrt, start, liquidity, false) else delta0(start, sqrt, liquidity, false)
            val feeAmount = if (sqrt != target) remaining - amountIn else ceil(amountIn * feeInteger, MILLION - feeInteger)
            remaining -= amountIn + feeAmount
            require(remaining >= ZERO)
            if (sqrt == nextTickSqrt) {
                if (initialized) {
                    liquidity += if (zeroForOne) -candidate!!.liquidityNet else candidate!!.liquidityNet
                    require(liquidity >= ZERO && liquidity.bitLength() <= 128)
                }
                tick = if (zeroForOne) nextTick - 1 else nextTick
            } else if (sqrt != start) tick = tickAtSqrt(sqrt)
        }
        return Concentrated(output, sqrt, liquidity, tick)
    }

    fun stable(amplifier: BigInteger, balances: List<BigInteger>, input: Int, output: Int, amount: BigInteger, fee: PancakeFraction): BigInteger {
        require(amplifier > ZERO && balances.size > 1 && input != output && amount >= ZERO)
        require(balances.all { it >= ZERO } && fee >= PancakeFraction(0) && fee < PancakeFraction(1))
        val n = BigInteger.valueOf(balances.size.toLong())
        val sum = balances.fold(ZERO, BigInteger::add)
        if (sum == ZERO) return ZERO
        val ann = amplifier * n
        var d = sum
        for (iteration in 0 until 255) {
            var dp = d
            for (balance in balances) dp = dp * d / (balance * n + ONE)
            val previous = d
            d = (ann * sum + dp * n) * d / ((ann - ONE) * d + (n + ONE) * dp)
            if ((d - previous).abs() <= ONE) break
        }
        var changedSum = ZERO; var c = d
        for ((index, balance) in balances.withIndex()) {
            if (index == output) continue
            val changed = balance + if (index == input) amount else ZERO
            require(changed > ZERO)
            changedSum += changed
            c = c * d / (changed * n)
        }
        c = c * d / (ann * n)
        val b = changedSum + d / ann
        var y = d
        for (iteration in 0 until 255) {
            val previous = y
            y = (y * y + c) / (TWO * y + b - d)
            if ((y - previous).abs() <= ONE) break
        }
        val dy = balances[output] - y
        require(dy >= ZERO)
        return dy - (fee * dy).quotient
    }

    fun binPrice(id: Int, step: Int): BigInteger {
        require(id in 0..0xffffff && step in 1..100)
        var exponent = id - 8388608
        if (exponent == 0) return Q128
        var invert = exponent < 0
        exponent = kotlin.math.abs(exponent)
        require(exponent < (1 shl 20))
        var squared = Q128 + BigInteger.valueOf(step.toLong()).shiftLeft(128) / BigInteger.valueOf(10000)
        var result = Q128
        if (squared >= Q128) {
            squared = MAX_UINT256 / squared
            invert = !invert
        }
        repeat(20) { bit ->
            if (exponent and (1 shl bit) != 0) result = result * squared / Q128
            squared = squared * squared / Q128
        }
        return if (invert) MAX_UINT256 / result else result
    }

    data class Bin(val id: Int, val x: BigInteger, val y: BigInteger)

    fun bin(amount: BigInteger, zeroForOne: Boolean, activeId: Int, step: Int, fee: Int, protocolFee: Int, bins: List<Bin>): BigInteger {
        require(amount > ZERO && fee in 0..100000 && step in 1..100)
        val protocol = if (zeroForOne) protocolFee and 4095 else protocolFee shr 12
        require(protocol in 0..1000)
        val swapFee = BigInteger.valueOf((protocol + fee - protocol.toLong() * fee / 1_000_000).toLong())
        var remaining = amount; var output = ZERO
        require(bins.any { it.id == activeId })
        val ordered = if (zeroForOne) bins.filter { it.id <= activeId }.sortedByDescending { it.id }
            else bins.filter { it.id >= activeId }.sortedBy { it.id }
        for (bin in ordered) {
            val reserve = if (zeroForOne) bin.y else bin.x
            if (reserve == ZERO) continue
            val price = binPrice(bin.id, step)
            require(price > ZERO)
            val maximum = if (zeroForOne) ceil(reserve.shiftLeft(128), price) else binMultiply(reserve, price, true)
            val maximumFee = ceil(maximum * swapFee, MILLION - swapFee)
            if (remaining >= maximum + maximumFee) {
                remaining -= maximum + maximumFee
                output += reserve
            } else {
                val net = remaining - ceil(remaining * swapFee, MILLION)
                val result = if (zeroForOne) binMultiply(net, price, false) else {
                    // shortcut: the pinned SDK high-word division is invalid for x >= 2^128; reject until the SDK is fixed.
                    require(net.bitLength() <= 128)
                    net.shiftLeft(128) / price
                }
                output += minOf(result, reserve)
                break
            }
            if (remaining == ZERO) break
        }
        return output
    }

    private fun binMultiply(x: BigInteger, y: BigInteger, up: Boolean): BigInteger {
        val product = x * y
        // shortcut: the pinned SDK high-word multiply is invalid at x*y >= 2^256-1; reject until the SDK is fixed.
        require(product < MAX_UINT256)
        return if (up) ceil(product, Q128) else product.shiftRight(128)
    }
}

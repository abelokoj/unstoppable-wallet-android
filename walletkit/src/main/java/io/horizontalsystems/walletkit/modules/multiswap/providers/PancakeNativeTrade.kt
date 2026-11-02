package io.horizontalsystems.walletkit.modules.multiswap.providers

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

internal class PancakeFraction(numerator: BigInteger, denominator: BigInteger = BigInteger.ONE) : Comparable<PancakeFraction> {
    val numerator: BigInteger
    val denominator: BigInteger
    init {
        require(denominator.signum() != 0)
        val sign = if (denominator.signum() < 0) -BigInteger.ONE else BigInteger.ONE
        val gcd = numerator.gcd(denominator)
        this.numerator = numerator / gcd * sign
        this.denominator = denominator / gcd * sign
    }
    constructor(value: Long) : this(BigInteger.valueOf(value))
    val quotient: BigInteger get() = numerator / denominator
    operator fun plus(other: PancakeFraction) = PancakeFraction(numerator * other.denominator + other.numerator * denominator, denominator * other.denominator)
    operator fun minus(other: PancakeFraction) = this + PancakeFraction(-other.numerator, other.denominator)
    operator fun times(other: PancakeFraction) = PancakeFraction(numerator * other.numerator, denominator * other.denominator)
    operator fun times(other: BigInteger) = PancakeFraction(numerator * other, denominator)
    operator fun div(other: PancakeFraction) = PancakeFraction(numerator * other.denominator, denominator * other.numerator)
    fun inverse() = PancakeFraction(denominator, numerator)
    fun decimal(precision: Int = 18): BigDecimal = BigDecimal(numerator).divide(BigDecimal(denominator), MathContext(precision, RoundingMode.HALF_UP))
    override fun compareTo(other: PancakeFraction) = (numerator * other.denominator).compareTo(other.numerator * denominator)
}

internal data class PancakeNativeRoute(
    val amount: PancakeFraction,
    val amountOut: BigInteger,
    val path: List<String>,
    val pools: List<PancakeNativePool>,
    val gas: BigInteger = BigInteger.ZERO,
    val percent: Double = 100.0,
    val initializedTicks: Int = 0,
) {
    val amountIn: BigInteger get() = amount.quotient
}

internal data class PancakeNativeTrade(
    val chainId: Long,
    val inputToken: String,
    val outputToken: String,
    val amountIn: BigInteger,
    val routes: List<PancakeNativeRoute>,
) {
    val amountOut: BigInteger get() = routes.fold(BigInteger.ZERO) { sum, route -> sum + route.amountOut }
    val gas: BigInteger get() = routes.fold(BigInteger.ZERO) { sum, route -> sum + route.gas }
}

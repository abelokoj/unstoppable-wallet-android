package io.horizontalsystems.walletkit.core.managers

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import kotlin.math.log10
import kotlin.math.pow

class NumberRounding {

    companion object {
        /** Compression kicks in above this many leading zeros. */
        const val COMPRESSION_MIN_ZEROS = 2

        /** Characters after the '.' for compressed values: "0" + subscript + digits. */
        const val SUBSCRIPT_BUDGET = 7

        /** Decimal cap for sub-1 values that are not compressed. */
        const val UNCOMPRESSED_MAX_DECIMALS = 6
    }

    fun getRoundedFull(value: BigDecimal, minimumFractionDigits: Int, maximumFractionDigits: Int): BigDecimalRounded {
        val mostLowValue = BigDecimal(BigInteger.ONE, maximumFractionDigits)

        return when {
            value < mostLowValue -> {
                BigDecimalRounded.Regular(BigDecimal.ZERO)
            }
            else -> {
                BigDecimalRounded.Regular(
                    simpleRoundingStrategy(
                        value = value,
                        minimumFractionDigits = minimumFractionDigits,
                        maximumFractionDigits = maximumFractionDigits,
                        maxFractionNonZeroDigits = 8
                    ))
            }
        }
    }

    fun getRoundedShort(value: BigDecimal, maximumFractionDigits: Int): BigDecimalRounded {
        val maximumFractionDigitsCoerced = maximumFractionDigits.coerceAtMost(8)
        val mostLowValue = BigDecimal(BigInteger.ONE, maximumFractionDigitsCoerced)

        return when {
            value.compareTo(BigDecimal.ZERO) == 0 -> {
                BigDecimalRounded.Regular(BigDecimal.ZERO)
            }
            value < mostLowValue -> {
                BigDecimalRounded.LessThen(mostLowValue)
            }
            value < BigDecimal("19999.5") -> {
                BigDecimalRounded.Regular(
                    simpleRoundingStrategy(
                        value = value,
                        minimumFractionDigits = 0,
                        maximumFractionDigits = maximumFractionDigitsCoerced,
                        // Open Swap fork: was 4. The formatter compresses leading zeros to a
                        // subscript and budgets 7 chars after the '.', so a one-digit subscript
                        // leaves room for 5 significant digits. Rounding to 4 here would throw
                        // the fifth away before the formatter ever sees it.
                        maxFractionNonZeroDigits = 5
                    ))
            }
            else -> {
                largeNumberStrategy(value)
            }
        }
    }

    fun getRoundedShort(value: BigDecimal): BigDecimalRounded {
        return getRoundedShort(value, 8)
    }

    fun getRoundedCoinShort(value: BigDecimal, coinDecimals: Int): BigDecimalRounded {
        return getRoundedShort(value, coinDecimals)
    }

    fun getRoundedCoinFull(value: BigDecimal, coinDecimals: Int): BigDecimalRounded {
        return getRoundedFull(value, 4.coerceAtMost(coinDecimals), coinDecimals)
    }

    fun getRoundedCurrencyShort(value: BigDecimal, currencyDecimals: Int): BigDecimalRounded {
        return getRoundedShort(value, currencyDecimals)
    }

    fun getRoundedCurrencyFull(value: BigDecimal): BigDecimalRounded {
        return getRoundedFull(value, 0, 18)
    }

    private fun largeNumberStrategy(value: BigDecimal): BigDecimalRounded.Large {
        val shortened = getShortened(value)
        val shortenedValue = shortened.value
        return when {
            // Open Swap fork: two decimals at every magnitude, and no stripTrailingZeros, so
            // abbreviated values line up: 1.72 T, 505.00 B, 250.00 M. Upstream used 2/1/0
            // decimals by size and stripped zeros, giving ragged widths.
            else -> {
                val rounded = shortenedValue.setScale(2, RoundingMode.HALF_UP)
                shortened.copy(value = rounded)
            }
        }
    }

    private fun simpleRoundingStrategy(
        value: BigDecimal,
        minimumFractionDigits: Int,
        maximumFractionDigits: Int,
        maxFractionNonZeroDigits: Int,
    ): BigDecimal {
        val decimals = when {
            value < BigDecimal("1") ->  {
                // Open Swap fork: two regimes for sub-1 values.
                //
                // More than 2 leading zeros: the formatter compresses them to a subscript with
                // a 7-char budget after the '.', so keep zeros + (budget - "0" - subscript
                // width) decimals. Rounding tighter here would discard digits the formatter
                // still needs.
                //
                // 2 or fewer leading zeros: no compression, so cap at 6 decimals. Upstream's
                // zeros + maxFractionNonZeroDigits gave 5 to 7 depending on the value, which
                // read inconsistently down a column.
                val zeros = getNumberOfZerosAfterDot(value)
                val wanted = if (zeros > COMPRESSION_MIN_ZEROS) {
                    zeros + (SUBSCRIPT_BUDGET - 1 - zeros.toString().length)
                } else {
                    UNCOMPRESSED_MAX_DECIMALS
                }
                minOf(maximumFractionDigits, wanted)
            }
            value < BigDecimal("1.01") -> 4
            value < BigDecimal("1.1") -> 3
            value < BigDecimal("20") -> 2
            value < BigDecimal("200") -> 1
            else -> 0
        }

        val coerced = decimals.coerceIn(minimumFractionDigits, maximumFractionDigits)

        // Open Swap fork: keep trailing zeros only where the source value actually had
        // digits at those positions, rather than stripping all or padding all.
        //
        //   0.0559803 -> 0.055980   sixth digit is a real zero from truncation, so keep it
        //   0.5       -> 0.5        padding to 0.500000 would invent five zeros
        //   0.08512   -> 0.08512    only five digits exist
        //
        // Upstream stripped unconditionally, which lost the first case.
        val scaled = value.setScale(coerced, RoundingMode.DOWN)
        return if (value.stripTrailingZeros().scale() > coerced) scaled else scaled.stripTrailingZeros()
    }

    private fun getShortened(value: BigDecimal): BigDecimalRounded.Large {
        val base = log10(value.toDouble()).toInt() + 1
        val groupCount = (base - 1) / 3

        return shortByGroupCount(groupCount, value)
    }

    private fun getNumberOfZerosAfterDot(value: BigDecimal): Int {
        return value.scale() - value.precision()
    }

    private fun shortByGroupCount(
        groupCount: Int,
        value: BigDecimal
    ): BigDecimalRounded.Large {
        val suffix = when (groupCount) {
            1 -> LargeNumberName.Thousand
            2 -> LargeNumberName.Million
            3 -> LargeNumberName.Billion
            4 -> LargeNumberName.Trillion
            5 -> LargeNumberName.Quadrillion
            // Open Swap fork: above quadrillion there is no suffix anyone would recognise,
            // so the formatter renders these in scientific notation instead. Reachable via
            // token balances (18 decimals x quadrillion supply), not fiat values.
            else -> LargeNumberName.Scientific
        }

        return when (suffix) {
            null -> BigDecimalRounded.Large(value, LargeNumberName.None)
            else -> {
                val t = groupCount * 3
                val shortened = value.divide(BigDecimal(10.0.pow(t.toDouble())))
                if (shortened >= BigDecimal("999.5")) {
                    shortByGroupCount(groupCount + 1, value)
                } else {
                    BigDecimalRounded.Large(shortened, suffix)
                }
            }
        }
    }
}

sealed class BigDecimalRounded {
    abstract val value: BigDecimal

    data class LessThen(override val value: BigDecimal) : BigDecimalRounded()
    data class Regular(override val value: BigDecimal) : BigDecimalRounded()
    data class Large(override val value: BigDecimal, val name: LargeNumberName) : BigDecimalRounded()
}

enum class LargeNumberName {
    None,
    Thousand,
    Million,
    Billion,
    Trillion,
    Quadrillion,
    Scientific;
}

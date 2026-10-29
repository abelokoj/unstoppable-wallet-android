package io.horizontalsystems.walletkit.core.managers

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

class NumberRoundingTest {
    private val rounding = NumberRounding()

    @Test fun `unlimited approval amounts retain their magnitude without decimal division failures`() {
        val unlimited = BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE)
        for (decimals in listOf(0, 6, 18)) {
            val value = BigDecimal(unlimited, decimals)
            val result = rounding.getRoundedCoinShort(value, decimals) as BigDecimalRounded.Large
            assertEquals(LargeNumberName.Scientific, result.name)
            assertEquals(value.setScale(2, RoundingMode.HALF_UP), result.value)
        }
    }

    @Test fun `scientific amounts preserve the full value for the formatter exponent`() {
        for (value in listOf(BigDecimal("1E18"), BigDecimal("1.23456789E32"), BigDecimal("1E400"))) {
            val result = rounding.getRoundedShort(value) as BigDecimalRounded.Large
            assertEquals(LargeNumberName.Scientific, result.name)
            assertEquals(value.setScale(2, RoundingMode.HALF_UP), result.value)
        }
    }

    @Test fun `existing abbreviated values and suffix promotion remain unchanged`() {
        for ((value, expected, suffix) in listOf(
            Triple("25000", "25.00", LargeNumberName.Thousand),
            Triple("250000000", "250.00", LargeNumberName.Million),
            Triple("304700000000", "304.70", LargeNumberName.Billion),
            Triple("1720000000000", "1.72", LargeNumberName.Trillion),
            Triple("999500000", "1.00", LargeNumberName.Billion),
        )) {
            assertEquals(BigDecimalRounded.Large(BigDecimal(expected), suffix), rounding.getRoundedShort(BigDecimal(value)))
        }
    }
}
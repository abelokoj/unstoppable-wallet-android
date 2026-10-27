package io.horizontalsystems.bankwallet.core.managers

import io.horizontalsystems.walletkit.core.managers.BigDecimalRounded
import io.horizontalsystems.walletkit.core.managers.LargeNumberName
import io.horizontalsystems.walletkit.core.managers.NumberRounding
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class NumberRoundingTest {
    private val numberRounding = NumberRounding()

    @Test
    fun getShort_zero() {
        assertShortRegular("0", "0")
    }

    @Test
    fun getShort_lessThenMinimum_lessThenSign() {
        assertLessThen("0.000000001", "0.00000001")
    }

    @Test
    fun getShort_lessThan1_keepsForkPrecision() {
        assertShortRegular("0.123456789", "0.123456")
        assertShortRegular("0.012345678", "0.012345")
        assertShortRegular("0.001234567", "0.001234")
        assertShortRegular("0.000123456", "0.00012345")
        assertShortRegular("0.000012345", "0.00001234")
        assertShortRegular("0.000001234", "0.00000123")
        assertShortRegular("0.999999999", "0.999999")

        // noTrailingZeros
        assertShortRegular("0.10001", "0.10001")
    }

    @Test
    fun getShort_range_show4Decimals() {
        // 1 until 1.01 show 4 decimals

        assertShortRegular("1", "1")
        assertShortRegular("1.00123456789", "1.0012")
        assertShortRegular("1.00994", "1.0099")
        assertShortRegular("1.00995", "1.0099")
    }

    @Test
    fun getShort_range_show3Decimals() {
        // 1.01 until 1.1 show 3 decimals

        assertShortRegular("1.01", "1.01")
        assertShortRegular("1.0123456789", "1.012")
        assertShortRegular("1.0994", "1.099")
        assertShortRegular("1.0995", "1.099")
    }

    @Test
    fun getShort_range_show2Decimals() {
        // 1.1 until 20 show 2 decimals

        assertShortRegular("1.1", "1.1")
        assertShortRegular("1.123456789", "1.12")
        assertShortRegular("19.994", "19.99")
        assertShortRegular("19.995", "19.99")
    }

    @Test
    fun getShort_range_show1Decimals() {
        // 20 until 200 show 1 decimals

        assertShortRegular("20", "20")
        assertShortRegular("20.123456789", "20.1")
        assertShortRegular("20.456789", "20.4")
        assertShortRegular("199.94", "199.9")
        assertShortRegular("199.95", "199.9")
    }

    @Test
    fun getShort_range_showNoDecimals() {
        // 200 until 20000 show 0 decimals

        assertShortRegular("200", "200")
        assertShortRegular("200.1", "200")
        assertShortRegular("200.5", "200")

        assertShortRegular("19999.1", "19999")
        assertShortLarge("19999.5", "20.00", LargeNumberName.Thousand)
    }

    @Test
    fun getShort_thousands_keepTwoDecimals() {
        // Open Swap keeps two decimals on abbreviated values.

        assertShortLarge("20000", "20.00", LargeNumberName.Thousand)
        assertShortLarge("20123", "20.12", LargeNumberName.Thousand)
        assertShortLarge("20350", "20.35", LargeNumberName.Thousand)
        assertShortLarge("199555", "199.56", LargeNumberName.Thousand)
        assertShortLarge("199950", "199.95", LargeNumberName.Thousand)
    }

    @Test
    fun getShort_thousands_roundAndCarryAtMillion() {

        assertShortLarge("200000", "200.00", LargeNumberName.Thousand)
        assertShortLarge("998500", "998.50", LargeNumberName.Thousand)
        assertShortLarge("999499", "999.50", LargeNumberName.Thousand)
        assertShortLarge("999500", "1.00", LargeNumberName.Million)
    }

    @Test
    fun getShort_millions_keepTwoDecimals() {
        // 1M until 20M show 2 decimals

        assertShortLarge("1234567", "1.23", LargeNumberName.Million)
        assertShortLarge("19123456", "19.12", LargeNumberName.Million)
        assertShortLarge("19995000", "20.00", LargeNumberName.Million)
    }

    private fun assertLessThen(value: String, expectedValue: String, ) {
        val actual = numberRounding.getRoundedShort(BigDecimal(value), 8) as BigDecimalRounded.LessThen
        assertEquals(expectedValue, actual.value.toPlainString())
    }

    private fun assertShortLarge(
        value: String,
        expectedValue: String,
        expectedSuffix: LargeNumberName
    ) {
        val actual = numberRounding.getRoundedShort(BigDecimal(value), 8) as BigDecimalRounded.Large

        assertEquals(expectedValue, actual.value.toPlainString())
        assertEquals(expectedSuffix, actual.name)
    }

    private fun assertShortRegular(
        value: String,
        expectedValue: String,
    ) {
        val actual = numberRounding.getRoundedShort(BigDecimal(value), 8) as BigDecimalRounded.Regular

        assertEquals(expectedValue, actual.value.toPlainString())
    }

    @Test
    fun getFull_belowMinimum_returnsZero() {
        // When value is smaller than the minimum representable value (10^-maximumFractionDigits)
        assertFullRegular("0.000000001", 0, 8, "0")
        assertFullRegular("0.0000000001", 0, 6, "0")
    }

    @Test
    fun getFull_smallNumbers_respectsMinimumDigits() {
        // Test minimum fraction digits enforcement
        assertFullRegular("0.1", 4, 8, "0.1")
        assertFullRegular("0.123", 6, 8, "0.123")
        assertFullRegular("1.5", 2, 8, "1.5")
    }

    @Test
    fun getFull_smallNumbers_showCorrectPrecision() {
        // Uncompressed sub-1 values keep six decimals; compressed ones respect the caller's cap.
        assertFullRegular("0.123456789", 0, 8, "0.123456")
        assertFullRegular("0.012345678", 0, 8, "0.012345")
        assertFullRegular("0.001234567", 0, 8, "0.001234")
        assertFullRegular("0.000123456", 0, 8, "0.00012345")
        assertFullRegular("0.000012345", 0, 8, "0.00001234")
        assertFullRegular("0.000001234", 0, 8, "0.00000123")
        assertFullRegular("0.001506812962699895", 4, 18, "0.001506")

        // Test with limited maximum digits
        assertFullRegular("0.123456789", 0, 4, "0.1234")
        assertFullRegular("0.012345678", 0, 6, "0.012345")
    }

    private fun assertFullRegular(
        value: String,
        minimumFractionDigits: Int,
        maximumFractionDigits: Int,
        expectedValue: String
    ) {
        val actual = numberRounding.getRoundedFull(
            BigDecimal(value),
            minimumFractionDigits,
            maximumFractionDigits
        ) as BigDecimalRounded.Regular

        assertEquals(expectedValue, actual.value.toPlainString())
    }
}

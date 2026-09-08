package io.horizontalsystems.walletkit.core.managers

import io.horizontalsystems.walletkit.R
import io.horizontalsystems.walletkit.core.IAppNumberFormatter
import io.horizontalsystems.walletkit.core.providers.Translator
import io.horizontalsystems.walletkit.modules.market.Value
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class NumberFormatter(
        private val languageManager: LanguageManager
        ) : IAppNumberFormatter {

    private var formatters = ConcurrentHashMap<String, NumberFormat>()
    private val numberRounding = NumberRounding()

    override fun format(value: Number, minimumFractionDigits: Int, maximumFractionDigits: Int, prefix: String, suffix: String): String {
        val bigDecimalValue = when (value) {
            is Double -> value.toBigDecimal()
            is Float -> value.toBigDecimal()
            is BigDecimal -> value
            else -> throw UnsupportedOperationException()
        }

        val formatter = getFormatter(languageManager.currentLocale, minimumFractionDigits, maximumFractionDigits)

        val mostLowValue = BigDecimal(BigInteger.ONE, maximumFractionDigits)

        return if (bigDecimalValue > BigDecimal.ZERO && bigDecimalValue < mostLowValue) {
            "< " + prefix + formatter.format(mostLowValue) + suffix
        } else {
            prefix + formatter.format(bigDecimalValue) + suffix
        }
    }

    override fun formatCoinFull(value: BigDecimal, code: String?, coinDecimals: Int): String {
        val rounded = numberRounding.getRoundedCoinFull(value, coinDecimals)
        return formatRounded(rounded = rounded, prefix = null, suffix = code?.let { " $it" })
    }

    override fun formatCoinShort(value: BigDecimal, code: String?, coinDecimals: Int): String {
        val rounded = numberRounding.getRoundedCoinShort(value, coinDecimals)
        return formatRounded(rounded = rounded, prefix = null, suffix = code?.let { " $it" })
    }

    override fun formatNumberShort(value: BigDecimal, maximumFractionDigits: Int): String {
        val rounded = numberRounding.getRoundedShort(value, maximumFractionDigits)
        return formatRounded(rounded = rounded, prefix = null, suffix = null)
    }

    override fun formatFiatFull(value: BigDecimal, symbol: String): String {
        val rounded = numberRounding.getRoundedCurrencyFull(value)
        return formatRounded(rounded = rounded, prefix = symbol, suffix = null)
    }

    override fun formatFiatShort(
        value: BigDecimal,
        symbol: String,
        currencyDecimals: Int
    ): String {
        val rounded = numberRounding.getRoundedCurrencyShort(value, currencyDecimals)
        return formatRounded(rounded = rounded, prefix = symbol, suffix = null)
    }

    private fun formatRounded(rounded: BigDecimalRounded, prefix: String?, suffix: String?): String {
        // Open Swap fork: abbreviated values (304.70 B) need a minimum of 2 fraction digits.
        // NumberFormat drops trailing zeros when the minimum is 0, undoing the setScale(2)
        // in NumberRounding.largeNumberStrategy and giving ragged widths like 304.7 B.
        // Open Swap fork: NumberFormat strips trailing zeros when the minimum is 0, undoing
        // the scale the rounding step deliberately chose.
        val minimumFractionDigits = when {
            // abbreviated: 304.70 B, 250.00 M
            rounded is BigDecimalRounded.Large -> 2
            // sub-1: match the rounding step's scale exactly, so 0.055980 keeps its sixth
            // digit while 0.5 is not padded to 0.500000
            rounded.value.abs() < BigDecimal.ONE -> rounded.value.scale().coerceAtLeast(0)
            // >= 1: at least 2 decimals, as currency conventionally shows. 1 -> 1.00,
            // 20 -> 20.00, 999.5 -> 999.50. Real precision beyond 2 is kept, since the
            // maximum is unbounded.
            else -> 2
        }
        val formatter = getFormatter(languageManager.currentLocale, minimumFractionDigits, Int.MAX_VALUE)
        var formattedNumber = formatter.format(rounded.value)

        prefix?.let {
            formattedNumber = "$prefix$formattedNumber"
        }

        if (rounded is BigDecimalRounded.LessThen) {
            formattedNumber = "< $formattedNumber"
        }

        // Open Swap fork: nothing past quadrillion has a recognisable suffix, so show
        // scientific notation: 1.23E18 rather than 1230000000000000000.
        if ((rounded as? BigDecimalRounded.Large)?.name == LargeNumberName.Scientific) {
            val exponent = rounded.value.precision() - rounded.value.scale() - 1
            val mantissa = rounded.value.movePointLeft(exponent)
                .setScale(2, java.math.RoundingMode.HALF_UP)
            return "${prefix ?: ""}${mantissa}E$exponent${suffix ?: ""}"
        }

        when ((rounded as? BigDecimalRounded.Large)?.name) {
            LargeNumberName.Thousand -> R.string.CoinPage_MarketCap_Thousand
            LargeNumberName.Million -> R.string.CoinPage_MarketCap_Million
            LargeNumberName.Billion -> R.string.CoinPage_MarketCap_Billion
            LargeNumberName.Trillion -> R.string.CoinPage_MarketCap_Trillion
            LargeNumberName.Quadrillion -> R.string.CoinPage_MarketCap_Quadrillion
            else -> null
        }?.let {
            formattedNumber = Translator.getString(R.string.LargeNumberFormat, formattedNumber, Translator.getString(it))
        }

        suffix?.let {
            formattedNumber = "$formattedNumber$suffix"
        }

        return compressLeadingZeros(formattedNumber)
    }

    /**
     * Open Swap fork: renders long runs of leading zeros in sub-1 values as a subscript
     * count, so 0.0000012345 becomes 0.0₅1234.
     *
     * Applied here because formatRounded is the single path every format method routes
     * through, so coin, fiat and plain number formatting all get it.
     *
     * Only triggers on more than MIN_ZEROS zeros -- 0.001234 stays as-is, since
     * compressing two zeros saves nothing and reads worse. Always emits exactly
     * SIGNIFICANT digits after the subscript, padding with zeros if the value has
     * fewer, so column widths stay consistent in lists.
     *
     * Prefix and suffix are preserved, and any value with a non-zero integer part is
     * returned untouched.
     */
    private fun compressLeadingZeros(formatted: String): String {
        val match = COMPRESSIBLE.matchEntire(formatted) ?: return formatted
        val (prefix, integerPart, fraction, suffix) = match.destructured

        // only sub-1 values; 1234.0000012 is left alone
        if (integerPart.filter { it.isDigit() } != "0") return formatted

        val zeros = fraction.takeWhile { it == '0' }.length
        if (zeros <= MIN_ZEROS) return formatted

        // Budget: "0" + subscript + digits always totals BUDGET chars after the '.', so a
        // two-digit subscript yields 4 digits and a one-digit subscript yields 5. Compressed
        // values then line up with each other in a list.
        val width = BUDGET - 1 - zeros.toString().length
        val digits = fraction.drop(zeros).take(width).padEnd(width, '0')
        val subscript = zeros.toString().map { SUBSCRIPTS[it.digitToInt()] }.joinToString("")

        return prefix + "0.0" + subscript + digits + suffix
    }

    companion object {
        private const val BUDGET = NumberRounding.SUBSCRIPT_BUDGET
        private const val MIN_ZEROS = NumberRounding.COMPRESSION_MIN_ZEROS
        private const val SUBSCRIPTS = "₀₁₂₃₄₅₆₇₈₉"

        // prefix (symbol, "< ", sign) | integer part | fraction | suffix (code, unit)
        private val COMPRESSIBLE = Regex("^([^0-9]*)([\\d,]+)[.,](\\d+)(.*)$")
    }

    private fun getFormatter(locale: Locale, minimumFractionDigits: Int, maximumFractionDigits: Int): NumberFormat {
        val formatterId = "${locale.language}-$minimumFractionDigits-$maximumFractionDigits"

        if (formatters[formatterId] == null) {
            formatters[formatterId] = NumberFormat.getInstance(locale).apply {
                this.roundingMode = RoundingMode.FLOOR

                this.minimumFractionDigits = minimumFractionDigits
                this.maximumFractionDigits = maximumFractionDigits
            }
        }

        return formatters[formatterId] ?: throw Exception("No formatter")
    }

    override fun formatValueAsDiff(value: Value): String =
        when (value) {
            is Value.Currency -> {
                val currencyValue = value.currencyValue
                val formatted = formatFiatShort(currencyValue.value.abs(), currencyValue.currency.symbol, currencyValue.currency.decimal)
                sign(value.currencyValue.value) + formatted
            }
            is Value.Percent -> {
                format(value.percent.abs(), 0, 2, sign(value.percent), "%")
            }
        }

    private fun sign(value: BigDecimal): String {
        return when (value.signum()) {
            1 -> "+"
            -1 -> "-"
            else -> ""
        }
    }
}

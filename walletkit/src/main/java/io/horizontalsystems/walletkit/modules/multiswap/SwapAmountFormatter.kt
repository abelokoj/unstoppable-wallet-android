package io.horizontalsystems.walletkit.modules.multiswap

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

internal fun formatSwapAmount(value: BigDecimal, locale: Locale = Locale.US): String {
    val formatter = NumberFormat.getNumberInstance(locale).apply { isGroupingUsed = false }
    if (value.signum() == 0) return formatter.format(BigDecimal.ZERO)

    val sign = if (value.signum() < 0) "-" else ""
    val absolute = value.abs()
    val exponent = absolute.precision().toLong() - absolute.scale() - 1
    val scientific = exponent >= 18 || exponent < -(12 - sign.length)
    val shift = if (!scientific && exponent >= 3) (exponent / 3 * 3).toInt() else 0
    val suffix = when {
        scientific -> "E$exponent"
        shift == 3 -> "K"
        shift == 6 -> "M"
        shift == 9 -> "B"
        shift == 12 -> "T"
        shift == 15 -> "Q"
        else -> ""
    }
    val amount = if (scientific) {
        BigDecimal(absolute.unscaledValue(), absolute.precision() - 1)
    } else {
        absolute.movePointLeft(shift)
    }
    val integerDigits = (amount.precision() - amount.scale()).coerceAtLeast(1)
    val decimals = (14 - sign.length - suffix.length - integerDigits - 1).coerceAtLeast(0)
    val rounded = amount.setScale(decimals, RoundingMode.DOWN)
    formatter.maximumFractionDigits = decimals
    // Use the whole budget when precision was lost, even if the last visible digits are zero.
    formatter.minimumFractionDigits = if (rounded.compareTo(amount) == 0) 0 else decimals
    return sign + formatter.format(rounded) + suffix
}

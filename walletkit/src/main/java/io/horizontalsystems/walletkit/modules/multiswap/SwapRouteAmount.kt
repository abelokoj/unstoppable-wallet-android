package io.horizontalsystems.walletkit.modules.multiswap

import io.horizontalsystems.walletkit.core.App
import java.math.BigDecimal
import java.math.RoundingMode

internal fun compactSwapRouteAmount(value: BigDecimal): Pair<BigDecimal, String>? {
    if (value < BigDecimal("1000")) return null

    val exponent = value.precision() - value.scale() - 1
    val shift = if (exponent < 18) exponent / 3 * 3 else exponent
    val suffix = when (shift) {
        3 -> "K"
        6 -> "M"
        9 -> "B"
        12 -> "T"
        15 -> "Q"
        else -> "E$shift"
    }
    // Round display values down so an abbreviated quote never promises extra tokens.
    return value.movePointLeft(shift).setScale(4, RoundingMode.DOWN).stripTrailingZeros() to suffix
}

internal fun formatCompactSwapAmount(value: BigDecimal): String? =
    compactSwapRouteAmount(value)?.let { (amount, suffix) ->
        App.numberFormatter.format(amount, 0, 4) + suffix
    }

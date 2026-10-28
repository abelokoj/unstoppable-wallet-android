package io.horizontalsystems.walletkit.modules.privatesend

import java.math.BigDecimal
import java.math.RoundingMode

/** Same-asset output: reserve the quoted fee and buffer from the source budget, rounding down. */
internal fun privateSendMaximumOutput(output: BigDecimal, requiredDeposit: BigDecimal, budget: BigDecimal, decimals: Int): BigDecimal {
    if (output <= BigDecimal.ZERO || budget <= BigDecimal.ZERO || requiredDeposit <= BigDecimal.ZERO ||
        requiredDeposit.precision() > 128 || requiredDeposit.scale() !in -128..255 || decimals !in 0..255)
        throw PrivateSendError.MaxUnavailable
    if (requiredDeposit <= budget) return output
    return (output - (requiredDeposit - budget)).setScale(decimals, RoundingMode.DOWN).stripTrailingZeros()
        .takeIf { it > BigDecimal.ZERO && it < output } ?: throw PrivateSendError.MaxUnavailable
}

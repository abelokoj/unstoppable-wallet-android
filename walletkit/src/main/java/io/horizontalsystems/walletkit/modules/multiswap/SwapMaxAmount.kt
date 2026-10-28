package io.horizontalsystems.walletkit.modules.multiswap

import io.horizontalsystems.marketkit.models.TokenType
import java.math.BigDecimal
import java.math.RoundingMode

internal fun swapFeePaidFromAsset(type: TokenType) = when (type) {
    TokenType.Native, is TokenType.Derived, is TokenType.AddressTyped -> true
    else -> false
}

/** Unsigned quotes are rebuilt for each amount; never shrink an existing transaction's value or calldata. */
internal class SwapMaxAmount(private val requested: BigDecimal, private val decimals: Int) {
    var amount = requested
        private set
    var ready = false
        private set
    var failed = false
        private set
    private var adjustments = 0
    private var probing = false

    fun invalidate() { ready = false }
    fun reset() { ready = false; failed = false; adjustments = 0 }

    fun reconcile(maximum: BigDecimal?, sendable: Boolean): BigDecimal? {
        ready = false
        failed = false
        val target = maximum?.min(requested)?.setScale(decimals, RoundingMode.DOWN)?.stripTrailingZeros()
        if (target != null && amount <= target && sendable && (!probing || amount.compareTo(target) == 0)) {
            probing = false
            ready = true
            return null
        }
        if (adjustments >= 4 || (target != null && (target <= BigDecimal.ZERO || (!probing && amount <= target)))) {
            failed = true
            return null
        }
        // A full-balance contract call can be refused before a fee is known. A smaller
        // unsigned quote lets the existing chain service estimate the real fee first.
        val next = (target ?: amount.divide(BigDecimal(2)))
            .setScale(decimals, RoundingMode.DOWN).stripTrailingZeros()
        if (next <= BigDecimal.ZERO || next.compareTo(amount) == 0) {
            failed = true
            return null
        }
        adjustments++
        probing = maximum == null
        amount = next
        return next
    }
}

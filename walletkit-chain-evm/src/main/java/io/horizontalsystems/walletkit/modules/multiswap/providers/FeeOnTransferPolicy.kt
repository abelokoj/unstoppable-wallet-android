package io.horizontalsystems.walletkit.modules.multiswap.providers

import io.horizontalsystems.marketkit.models.Token
import io.horizontalsystems.marketkit.models.TokenType

/**
 * Controls whether V2-router swaps are encoded against the plain router functions
 * (`swapExactTokensForTokens`) or the fee-on-transfer variants
 * (`swapExactTokensForTokensSupportingFeeOnTransferTokens`).
 *
 * Background: a token that skims a tax on `transfer` delivers less to the pair than the
 * nominal `amountIn`. The plain router functions compute the expected output from the
 * nominal amount, so the pair's `x*y=k` check fails and the transaction reverts with
 * `UniswapV2: K` / `PancakeSwap: K`. The fee-on-transfer variants recompute from actual
 * balance deltas instead, which is the only correct way to trade these tokens.
 *
 * The fee-on-transfer variants are behaviourally a superset: for a token with no tax the
 * balance delta equals the nominal amount, so the outcome is identical. They cost slightly
 * more gas and return no value, neither of which matters here. [AUTO] therefore uses them
 * for every ERC20-input swap rather than trying to sniff a tax rate off the token contract,
 * which has no standard interface and is trivially spoofable.
 */
enum class FeeOnTransferPolicy {
    /**
     * Never use the fee-on-transfer variants. Matches stock upstream behaviour: taxed
     * tokens will keep failing with `K`.
     */
    OFF,

    /**
     * Use the fee-on-transfer variant whenever the input token is an ERC20 (i.e. any sell
     * or token-to-token swap, the only cases that can trip the `K` invariant on the way in).
     * Native-coin-in buys stay on the plain function.
     */
    AUTO,

    /**
     * Use the fee-on-transfer variant for every swap, including native-coin-in buys. Buys of
     * a taxed token still benefit, because `amountOutMin` is then checked against the amount
     * actually received after the tax rather than the pair's nominal output.
     */
    ALWAYS;

    fun appliesTo(tokenIn: Token): Boolean = when (this) {
        OFF -> false
        AUTO -> tokenIn.type is TokenType.Eip20
        ALWAYS -> true
    }

    companion object {
        /**
         * Single switch for this fork. Change to [OFF] to restore stock behaviour, or to
         * [ALWAYS] to also route native-coin-in buys through the fee-on-transfer functions.
         */
        val current: FeeOnTransferPolicy = AUTO
    }
}

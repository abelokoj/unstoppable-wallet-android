package io.horizontalsystems.walletkit.modules.multiswap.providers

import io.horizontalsystems.walletkit.R
import io.horizontalsystems.walletkit.core.App

object ThorChainProvider : BaseThorChainProvider(
    baseUrl = "https://gateway.liquify.com/chain/thorchain_api/thorchain/",
    // Open Swap fork: affiliate fee removed. swapFeeBps was 100 bps (1.00%) in release
    // builds, 25 bps (0.25%) in debug; now 0, so THORChain takes no affiliate cut.
    // Open Swap fork: affiliate removed. Was affiliate = "hrz", affiliateBps =
    // SWAP_FEE_BPS. This matters beyond the fee: THORChain returns a memo built from these
    // values and that memo is broadcast on-chain (OP_RETURN / tx data / shielded memo),
    // so a non-null affiliate permanently tags every swap.
    affiliate = null,
    affiliateBps = null,
) {
    override val streamingInterval: Long = 0
    override val id = THORCHAIN_PROVIDER_ID
    override val icon = R.drawable.swap_provider_thorchain
    override val title = "THORChain"
    override val riskLevel = RiskLevel.EXCELLENT
    override val securedAssetsSupported = true
}

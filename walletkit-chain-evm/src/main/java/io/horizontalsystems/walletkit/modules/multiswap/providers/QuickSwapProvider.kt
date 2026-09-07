package io.horizontalsystems.walletkit.modules.multiswap.providers

import io.horizontalsystems.walletkit.R
import io.horizontalsystems.marketkit.models.BlockchainType

object QuickSwapProvider : BaseUniswapProvider() {
    override val id = "quickswap"
    override val icon = R.drawable.swap_provider_quickswap
    override val title = "QuickSwap"
    override val riskLevel = RiskLevel.EXCELLENT

    override fun supports(blockchainType: BlockchainType): Boolean {
        return blockchainType == BlockchainType.Polygon
    }
}

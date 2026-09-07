package io.horizontalsystems.walletkit.modules.multiswap.providers

import io.horizontalsystems.walletkit.R
import io.horizontalsystems.marketkit.models.BlockchainType

object PancakeSwapProvider : BaseUniswapProvider() {
    override val id = "pancake"
    override val icon = R.drawable.swap_provider_pancake
    override val title = "PancakeSwap"
    override val riskLevel = RiskLevel.EXCELLENT

    override fun supports(blockchainType: BlockchainType): Boolean {
        return blockchainType == BlockchainType.BinanceSmartChain
    }
}

package io.horizontalsystems.walletkit.modules.multiswap.providers

import io.horizontalsystems.walletkit.R
import io.horizontalsystems.marketkit.models.BlockchainType

object UniswapProvider : BaseUniswapProvider() {
    override val id = "uniswap"
    override val icon = R.drawable.swap_provider_uniswap
    override val title = "Uniswap"
    override val riskLevel = RiskLevel.EXCELLENT

    override fun supports(blockchainType: BlockchainType): Boolean {
        return blockchainType == BlockchainType.Ethereum
    }
}

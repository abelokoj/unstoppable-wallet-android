package io.horizontalsystems.walletkit.modules.multiswap.providers

import io.horizontalsystems.walletkit.core.isEvm
import io.horizontalsystems.walletkit.core.chain.ChainRegistry
import io.horizontalsystems.walletkit.modules.multiswap.history.AllBridgeTracker
import io.horizontalsystems.marketkit.models.BlockchainType

object MultiSwapProviderRegistry {
    // One instance serves both the swap screen and private send, so its token list and
    // chain set are fetched and cached once.
    private val nearConfidential = USwapProvider(UProvider.NearConfidential)

    val allProviders: List<IMultiSwapProvider> = listOf(
        // Cross-chain providers
        USwapProvider(UProvider.Near),
        nearConfidential,
        USwapProvider(UProvider.QuickEx),
        USwapProvider(UProvider.LetsExchange),
        USwapProvider(UProvider.StealthEx),
        USwapProvider(UProvider.Exolix),
        USwapProvider(UProvider.Cce),
        USwapProvider(UProvider.Swapuz),
        USwapProvider(UProvider.Lizex),
        USwapProvider(UProvider.Bitania),
        USwapProvider(UProvider.XSwap),
        USwapProvider(UProvider.Barter),
        USwapProvider(UProvider.Circle),
        USwapProvider(UProvider.Pegasus),
        USwapProvider(UProvider.Jupiter),
        USwapProvider(UProvider.Lifi),
        USwapProvider(
            UProvider.Across,
            // Across currently originates on EVM-compatible chains and TRON. Keep unsupported
            // source families out of the quote fan-out even if a stale backend catalog lists them.
            supportsSourceToken = { it.blockchainType.isEvm || it.blockchainType == BlockchainType.Tron },
        ),
        RelayProvider,
        PancakeCrossChainProvider,
        // Axelar ITS' Stellar leg is a server-built XDR envelope (signed_transaction), so
        // Stellar joins the default sourceAddress build signal (EVM covers the other leg).
        USwapProvider(
            UProvider.AxelarIts,
            shouldIncludeSourceAddress = { it.blockchainType.isEvm || it.blockchainType == BlockchainType.Stellar },
        ),
    ) + ChainRegistry.all.flatMap { it.swapProviders() }

    /**
     * Providers no longer offered for swapping. They stay reachable by id so swaps already made
     * with them keep resolving their type and stage layout in history, but no quote is requested
     * from them. Open Swap's direct providers remain active and retain their original IDs.
     */
    private val retiredProviders: List<IMultiSwapProvider>
        get() = listOf(
            // Same-chain AllBridge swaps were one transaction; bridges were deposit and delivery.
            RetiredSwapProvider(AllBridgeTracker.PROVIDER_ID, "AllBridge", SwapProviderType.DEX) { tokenIn, tokenOut -> tokenIn == tokenOut },
        )

    /**
     * Confidential rails: the providers private send may route a same-asset transfer through.
     * They also quote on the ordinary swap screen as members of [allProviders], where a
     * confidential route is just one more card beside the public ones.
     */
    val confidentialProviders: List<USwapProvider> = listOf(
        nearConfidential,
    )

    private val providersById: Map<String, IMultiSwapProvider> by lazy {
        (allProviders + retiredProviders).associateBy { it.id }
    }

    fun isSingleTransactionSwap(providerId: String, tokenInBlockchainTypeUid: String, tokenOutBlockchainTypeUid: String): Boolean {
        val provider = providersById[providerId] ?: return false
        return provider.isSingleTransactionSwap(tokenInBlockchainTypeUid, tokenOutBlockchainTypeUid)
    }

    fun providerType(providerId: String): SwapProviderType? {
        return providersById[providerId]?.type
    }

    fun riskLevel(providerId: String): RiskLevel? = providersById[providerId]?.riskLevel
}

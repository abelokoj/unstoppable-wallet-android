package io.horizontalsystems.walletkit.modules.addtoken

import io.horizontalsystems.walletkit.core.chain.ChainRegistry
import io.horizontalsystems.walletkit.core.App
import io.horizontalsystems.walletkit.core.IAccountManager
import io.horizontalsystems.walletkit.core.ICoinManager
import io.horizontalsystems.walletkit.core.managers.MarketKitWrapper
import io.horizontalsystems.walletkit.core.managers.WalletManager
import io.horizontalsystems.walletkit.core.order
import io.horizontalsystems.walletkit.core.isCustom
import io.horizontalsystems.walletkit.core.stats.StatEvent
import io.horizontalsystems.walletkit.core.stats.StatPage
import io.horizontalsystems.walletkit.core.stats.stat
import io.horizontalsystems.walletkit.entities.Wallet
import io.horizontalsystems.marketkit.models.Blockchain
import io.horizontalsystems.marketkit.models.BlockchainType
import io.horizontalsystems.marketkit.models.Token
import io.horizontalsystems.marketkit.models.TokenType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope

class AddTokenService(
    private val coinManager: ICoinManager,
    private val walletManager: WalletManager,
    private val accountManager: IAccountManager,
    marketKit: MarketKitWrapper,
) {

    private val blockchainTypes = listOf(
        BlockchainType.Ethereum,
        BlockchainType.BinanceSmartChain,
        BlockchainType.Polygon,
        BlockchainType.Avalanche,
        BlockchainType.Gnosis,
        BlockchainType.Fantom,
        BlockchainType.ArbitrumOne,
        BlockchainType.Optimism,
        BlockchainType.Base,
        BlockchainType.ZkSync,
        BlockchainType.RobinhoodChain,
    ) + ChainRegistry.all.filter { it.supportsCustomTokens }.map { it.blockchainType }

    val blockchains = marketKit
        .blockchains(blockchainTypes.map { it.uid })
        .sortedBy { it.type.order }

    val accountType = accountManager.activeAccount?.type

    suspend fun tokenInfo(blockchain: Blockchain, reference: String): TokenInfo? {
        if (reference.isEmpty()) return null

        val blockchainService = ChainRegistry[blockchain.type]?.addTokenBlockchainService(blockchain)
            ?: throw TokenError.InvalidReference

        if (!blockchainService.isValid(reference)) throw TokenError.InvalidReference

        val query = blockchainService.tokenQuery(reference)
        val token = coinManager.getToken(query)
        if (token != null && token.type !is TokenType.Unsupported) {
            val alreadyEnabled = walletManager.activeWallets.any { it.token.tokenQuery == query }
            return TokenInfo(token, alreadyEnabled)
        }

        try {
            val customToken = blockchainService.token(reference)
            return TokenInfo(customToken, false)
        } catch (e: Throwable) {
            throw TokenError.NotFound
        }
    }

    suspend fun discoverTokenInfos(reference: String): List<TokenInfo> = supervisorScope {
        if (reference.isBlank()) return@supervisorScope emptyList()

        blockchains.distinctBy { it.type.uid }.map { blockchain ->
            async(Dispatchers.IO) {
                runCatching { tokenInfo(blockchain, reference.trim()) }.getOrNull()
            }
        }.awaitAll()
            .filterNotNull()
            .distinctBy { it.token.tokenQuery.id }
            .sortedBy { it.token.blockchainType.order }
    }

    fun addToken(token: TokenInfo) {
        val account = accountManager.activeAccount ?: return
        if (token.token.isCustom) {
            coinManager.saveCustomToken(token.token)
        }
        val wallet = Wallet(token.token, account)
        if (walletManager.activeWallets.none { it.token.tokenQuery == token.token.tokenQuery }) {
            walletManager.save(listOf(wallet))
        }

        stat(page = StatPage.AddToken, event = StatEvent.AddToken(token.token))
    }

    sealed class TokenError : Exception() {
        object InvalidReference : TokenError()
        object NotFound : TokenError()
    }

    data class TokenInfo(
        val token: Token,
        val inCoinList: Boolean,
    )
}

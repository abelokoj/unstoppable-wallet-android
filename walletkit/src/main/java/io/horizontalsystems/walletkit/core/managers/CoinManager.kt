package io.horizontalsystems.walletkit.core.managers

import io.horizontalsystems.walletkit.core.ICoinManager
import io.horizontalsystems.walletkit.core.ILocalStorage
import io.horizontalsystems.walletkit.core.customCoinUid
import io.horizontalsystems.marketkit.models.Coin
import io.horizontalsystems.marketkit.models.Token
import io.horizontalsystems.marketkit.models.TokenQuery

class CoinManager(
    private val marketKit: MarketKitWrapper,
    private val walletManager: WalletManager,
    private val localStorage: ILocalStorage,
) : ICoinManager {

    override fun getToken(query: TokenQuery): Token? {
        return marketKit.token(query) ?: customToken(query)
    }

    private fun customToken(tokenQuery: TokenQuery): Token? =
        walletManager.activeWallets.find { it.token.tokenQuery == tokenQuery }?.token
            ?: customTokens.find { it.tokenQuery == tokenQuery }

    override val customTokens: List<Token>
        get() {
            val records = localStorage.customTokenRecords
            val blockchains = marketKit.blockchains(
                records.mapNotNull { TokenQuery.fromId(it.tokenQueryId)?.blockchainType?.uid }.distinct()
            ).associateBy { it.uid }

            return records.mapNotNull { record ->
                val query = TokenQuery.fromId(record.tokenQueryId) ?: return@mapNotNull null
                val blockchain = blockchains[query.blockchainType.uid] ?: return@mapNotNull null
                Token(
                    coin = Coin(
                        uid = query.customCoinUid,
                        name = record.name,
                        code = record.code,
                        image = record.image,
                    ),
                    blockchain = blockchain,
                    type = query.tokenType,
                    decimals = record.decimals,
                )
            }
        }

    override fun saveCustomToken(token: Token) {
        val record = CustomTokenRecord(
            tokenQueryId = token.tokenQuery.id,
            name = token.coin.name,
            code = token.coin.code,
            decimals = token.decimals,
            image = token.coin.image,
        )
        localStorage.customTokenRecords =
            (localStorage.customTokenRecords.filterNot { it.tokenQueryId == record.tokenQueryId } + record)
                .sortedBy { it.tokenQueryId }
    }
}

data class CustomTokenRecord(
    val tokenQueryId: String,
    val name: String,
    val code: String,
    val decimals: Int,
    val image: String?,
)

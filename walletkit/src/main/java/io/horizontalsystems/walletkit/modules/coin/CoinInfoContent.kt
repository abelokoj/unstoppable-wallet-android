package io.horizontalsystems.walletkit.modules.coin

import io.horizontalsystems.marketkit.models.BlockchainType
import io.horizontalsystems.marketkit.models.FullCoin
import io.horizontalsystems.marketkit.models.Token
import io.horizontalsystems.marketkit.models.TokenType
import io.horizontalsystems.walletkit.R
import io.horizontalsystems.walletkit.core.assetUrl
import io.horizontalsystems.walletkit.core.displayCode
import io.horizontalsystems.walletkit.core.eip20TokenUrl
import io.horizontalsystems.walletkit.core.jettonUrl
import io.horizontalsystems.walletkit.core.nep141TokenUrl
import io.horizontalsystems.walletkit.core.xrpAssetUrl
import java.net.URI

internal sealed interface CoinInfoContent {
    data class Market(val coin: FullCoin) : CoinInfoContent
    data class Basic(val token: Token) : CoinInfoContent
    data object Missing : CoinInfoContent
}

internal fun resolveCoinInfo(coinUid: String, coins: List<FullCoin>, tokens: List<Token>): CoinInfoContent {
    coins.firstOrNull { it.coin.uid == coinUid }?.let { return CoinInfoContent.Market(it) }
    tokens.firstOrNull { it.coin.uid == coinUid }?.let { return CoinInfoContent.Basic(it) }
    return CoinInfoContent.Missing
}

internal data class BasicTokenReference(val value: String, val title: Int, val explorerUrl: String?)

internal fun Token.basicTokenReference(): BasicTokenReference? {
    val reference = when (val type = type) {
        is TokenType.Eip20 -> BasicTokenReference(type.address, R.string.AddToken_AddressOrSymbol, blockchain.eip20TokenUrl(type.address))
        is TokenType.Spl -> BasicTokenReference(type.address, R.string.CoinInfo_MintAddress, blockchain.eip20TokenUrl(type.address))
        is TokenType.Jetton -> BasicTokenReference(type.address, R.string.AddToken_AddressOrSymbol, blockchain.jettonUrl(type.address))
        is TokenType.Nep141 -> BasicTokenReference(type.contractId, R.string.AddToken_AddressOrSymbol, blockchain.nep141TokenUrl(type.contractId))
        is TokenType.Asset -> BasicTokenReference("${type.code}-${type.issuer}", R.string.CoinInfo_AssetIdentifier, blockchain.assetUrl(type.code, type.issuer))
        is TokenType.XrpAsset -> BasicTokenReference("${type.displayCode}.${type.issuer}", R.string.CoinInfo_AssetIdentifier, blockchain.xrpAssetUrl(type))
        is TokenType.ZanoAsset -> BasicTokenReference(type.reference, R.string.CoinInfo_AssetIdentifier, null)
        is TokenType.ThorchainAsset -> BasicTokenReference(type.denom, R.string.CoinInfo_AssetIdentifier, null)
        else -> return null
    }
    val url = reference.explorerUrl?.takeIf {
        try { URI(it).let { uri -> uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null } }
        catch (_: Exception) { false }
    }
    return reference.copy(explorerUrl = url)
}

internal fun Token.basicTokenStandard(): String? = when (type) {
    is TokenType.Eip20 -> when (blockchainType) {
        BlockchainType.BinanceSmartChain -> "BEP-20"
        BlockchainType.Tron -> "TRC-20"
        else -> "ERC-20"
    }
    is TokenType.Spl -> "SPL"
    is TokenType.Jetton -> "Jetton"
    is TokenType.Nep141 -> "NEP-141"
    is TokenType.Asset -> "Stellar"
    is TokenType.XrpAsset -> "XRP Ledger"
    is TokenType.ThorchainAsset -> "THORChain"
    is TokenType.ZanoAsset -> "Zano"
    else -> null
}

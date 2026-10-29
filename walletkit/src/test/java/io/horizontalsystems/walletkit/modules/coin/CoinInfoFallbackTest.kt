package io.horizontalsystems.walletkit.modules.coin

import io.horizontalsystems.marketkit.models.Blockchain
import io.horizontalsystems.marketkit.models.BlockchainType
import io.horizontalsystems.marketkit.models.Coin
import io.horizontalsystems.marketkit.models.FullCoin
import io.horizontalsystems.marketkit.models.Token
import io.horizontalsystems.marketkit.models.TokenType
import org.junit.Assert.*
import org.junit.Test

class CoinInfoFallbackTest {
    private val chain = Blockchain(BlockchainType.BinanceSmartChain, "BNB Smart Chain", "https://bscscan.com/token/\$ref")
    private val address = "0x" + "a".repeat(40)
    private val custom = Token(Coin("custom-bsc-token", "Custom token", "CUSTOM"), chain, TokenType.Eip20(address), 18)

    @Test fun `unlisted saved tokens open basic details without requiring a market record`() {
        assertEquals(CoinInfoContent.Basic(custom), resolveCoinInfo(custom.coin.uid, emptyList(), listOf(custom)))
    }

    @Test fun `catalogued coins keep their full market page`() {
        val market = FullCoin(custom.coin, listOf(custom))
        assertEquals(CoinInfoContent.Market(market), resolveCoinInfo(custom.coin.uid, listOf(market), listOf(custom)))
    }

    @Test fun `missing or unrelated records produce the existing not found state`() {
        assertEquals(CoinInfoContent.Missing, resolveCoinInfo("missing", emptyList(), emptyList()))
        assertEquals(CoinInfoContent.Missing, resolveCoinInfo("missing", listOf(FullCoin(custom.coin, listOf(custom))), listOf(custom)))
        val sameSymbol = custom.copy(coin = Coin("other", "Other token", custom.coin.code))
        assertEquals(CoinInfoContent.Missing, resolveCoinInfo(custom.coin.uid, emptyList(), listOf(sameSymbol)))
    }

    @Test fun `EVM and Solana retain full chain specific references and standards`() {
        assertEquals(address, custom.basicTokenReference()?.value)
        assertEquals("https://bscscan.com/token/$address", custom.basicTokenReference()?.explorerUrl)
        assertEquals("BEP-20", custom.basicTokenStandard())
        val solana = Blockchain(BlockchainType.Solana, "Solana", "https://solscan.io/token/\$ref")
        val mint = "So11111111111111111111111111111111111111112"
        val spl = custom.copy(blockchain = solana, type = TokenType.Spl(mint))
        assertEquals(mint, spl.basicTokenReference()?.value)
        assertEquals("https://solscan.io/token/$mint", spl.basicTokenReference()?.explorerUrl)
        assertEquals("SPL", spl.basicTokenStandard())
        assertNull(custom.copy(type = TokenType.Native).basicTokenReference())
    }

    @Test fun `explorer actions never accept non web schemes`() {
        val unsafe = custom.copy(blockchain = Blockchain(BlockchainType.BinanceSmartChain, "BNB", "javascript:alert(1)"))
        assertNull(unsafe.basicTokenReference()?.explorerUrl)
    }
}

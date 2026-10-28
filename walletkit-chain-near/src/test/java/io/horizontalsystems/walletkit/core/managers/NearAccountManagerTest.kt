package io.horizontalsystems.walletkit.core.managers

import io.horizontalsystems.marketkit.models.Blockchain
import io.horizontalsystems.marketkit.models.BlockchainType
import io.horizontalsystems.marketkit.models.Coin
import io.horizontalsystems.marketkit.models.Token
import io.horizontalsystems.marketkit.models.TokenQuery
import io.horizontalsystems.marketkit.models.TokenType
import io.horizontalsystems.walletkit.entities.Account
import io.horizontalsystems.walletkit.entities.AccountOrigin
import io.horizontalsystems.walletkit.entities.AccountType
import io.horizontalsystems.walletkit.entities.Wallet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NearAccountManagerTest {
    private val owner = Account("owner", "Owner", AccountType.NearAddress("owner.near"), AccountOrigin.Restored, 0)
    private val other = owner.copy(id = "other", type = AccountType.NearAddress("other.near"))
    private val contractId = "usdt.tether-token.near"
    private val token = Token(
        Coin("near-usdt", "Tether", "USDT"),
        Blockchain(BlockchainType.Near, "NEAR", null),
        TokenType.Nep141(contractId),
        decimals = 6,
    )
    private val query = TokenQuery(BlockchainType.Near, TokenType.Nep141(contractId))

    @Test
    fun staleOwnerCannotEnableTokensForTheNewActiveAccount() {
        assertTrue(nearTokenQueriesToEnable(owner, other, emptyList(), listOf(contractId)).isEmpty())
        assertTrue(nearTokenQueriesToEnable(owner, null, emptyList(), listOf(contractId)).isEmpty())
    }

    @Test
    fun anotherAccountsWalletCannotSuppressTheOwnersToken() {
        assertEquals(
            listOf(query),
            nearTokenQueriesToEnable(owner, owner, listOf(Wallet(token, other)), listOf(contractId)),
        )
    }

    @Test
    fun onlyTheOwnersExistingWalletSuppressesItsToken() {
        assertTrue(
            nearTokenQueriesToEnable(owner, owner, listOf(Wallet(token, owner)), listOf(contractId)).isEmpty()
        )
        assertEquals(listOf(query), nearTokenQueriesToEnable(owner, owner, emptyList(), listOf(contractId, contractId)))
    }
}

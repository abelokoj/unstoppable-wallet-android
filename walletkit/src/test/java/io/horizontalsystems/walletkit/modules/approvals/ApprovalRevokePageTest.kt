package io.horizontalsystems.walletkit.modules.approvals

import io.horizontalsystems.marketkit.models.BlockchainType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ApprovalRevokePageTest {
    private val target = ApprovalTarget("0x" + "11".repeat(20), "0x" + "22".repeat(20), ApprovalKind.Token)

    @Test fun `confirmation state is not shared across different permissions wallets or networks`() {
        val page = ApprovalRevokePage("wallet-a", BlockchainType.Ethereum, target)
        assertNotEquals(page.contentKey(), page.copy(accountId = "wallet-b").contentKey())
        assertNotEquals(page.contentKey(), page.copy(blockchainType = BlockchainType.Base).contentKey())
        assertNotEquals(page.contentKey(), page.copy(target = target.copy(spender = "0x" + "33".repeat(20))).contentKey())
        assertEquals(page.contentKey(), page.copy().contentKey())
    }
}

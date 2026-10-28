package io.horizontalsystems.walletkit.modules.approvals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import java.math.BigDecimal
import org.junit.Test

class ApprovalSelectionTest {
    private val first = "0x" + "a".repeat(40)
    private val second = "0x" + "b".repeat(40)
    private val spender = "0x" + "c".repeat(40)

    @Test fun `grouping combines spenders per contract without duplicate revocations`() {
        val token = ActiveApproval(ApprovalTarget(first, spender, ApprovalKind.Token), "12")
        val otherSpender = token.copy(target = token.target.copy(spender = second))
        val nft = ActiveApproval(ApprovalTarget(second, spender, ApprovalKind.Nft, "42"), "1")
        val items = groupApprovalItems(listOf(token, token, otherSpender, nft))
        assertEquals(2, items.size)
        assertEquals(listOf(token, otherSpender), items.first().approvals)
        assertEquals(listOf(nft), ApprovalAccessState(items = items, selectedContracts = setOf(second)).selectedApprovals)
        assertEquals(emptyList<ActiveApproval>(), ApprovalAccessState(items = items).selectedApprovals)
        assertEquals(3, ApprovalAccessState(items = items, selectedContracts = setOf(first, second)).selectedApprovals.size)
    }

    @Test fun `review state stays scoped to account network and selected permissions`() {
        val target = ApprovalTarget(first, spender, ApprovalKind.Token)
        val page = ApprovalSelectionReviewPage("wallet", io.horizontalsystems.marketkit.models.BlockchainType.Ethereum, listOf(target))
        assertEquals(page.contentKey(), page.copy(targets = listOf(target, target)).contentKey())
        assertEquals(page.contentKey(), page.copy(tokens = mapOf(first to ApprovalTokenInfo("Ribbit", "RIBBIT", 18))).contentKey())
        assertNotEquals(page.contentKey(), page.copy(accountId = "other").contentKey())
        assertNotEquals(page.contentKey(), page.copy(blockchainType = io.horizontalsystems.marketkit.models.BlockchainType.Base).contentKey())
        assertNotEquals(page.contentKey(), page.copy(targets = listOf(target.copy(spender = second))).contentKey())
    }

    @Test fun `allowances scale exactly using token decimals without guessing`() {
        assertEquals(BigDecimal("1.234567"), ApprovalTokenInfo(decimals = 6).amount("1234567"))
        assertEquals(BigDecimal("1.000000000000000001"), ApprovalTokenInfo(decimals = 18).amount("1000000000000000001"))
        assertEquals(BigDecimal("123"), ApprovalTokenInfo(decimals = 0).amount("123"))
        assertEquals(BigDecimal(ApprovalAbi.maxUint256, 18), ApprovalTokenInfo(decimals = 18).amount(ApprovalAbi.maxUint256.toString()))
        assertEquals(BigDecimal(java.math.BigInteger.ONE, 255), ApprovalTokenInfo(decimals = 255).amount("1"))
        assertNull(ApprovalTokenInfo().amount("123"))
        assertNull(ApprovalTokenInfo(decimals = -1).amount("123"))
        assertNull(ApprovalTokenInfo(decimals = 256).amount("123"))
        listOf("", "-1", "1.5", "0xff", " 1", "١٢٣", "9".repeat(79), (ApprovalAbi.maxUint256 + java.math.BigInteger.ONE).toString()).forEach {
            assertNull(ApprovalTokenInfo(decimals = 18).amount(it))
        }
    }

    @Test fun `approval page defaults to BNB Smart Chain independent of sorted network order`() {
        val arbitrum = io.horizontalsystems.marketkit.models.Blockchain(io.horizontalsystems.marketkit.models.BlockchainType.ArbitrumOne, "Arbitrum", null)
        val bnb = io.horizontalsystems.marketkit.models.Blockchain(io.horizontalsystems.marketkit.models.BlockchainType.BinanceSmartChain, "BNB Smart Chain", null)
        assertEquals(bnb, defaultApprovalNetwork(listOf(arbitrum, bnb)))
        assertEquals(arbitrum, defaultApprovalNetwork(listOf(arbitrum)))
        assertNull(defaultApprovalNetwork(emptyList()))
    }

}

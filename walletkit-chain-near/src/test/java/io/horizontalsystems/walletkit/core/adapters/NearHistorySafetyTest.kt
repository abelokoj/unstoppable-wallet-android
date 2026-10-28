package io.horizontalsystems.walletkit.core.adapters

import io.horizontalsystems.nearkit.models.FtMetadata
import io.horizontalsystems.nearkit.models.FtTransfer
import io.horizontalsystems.nearkit.models.NearTransfer
import io.horizontalsystems.nearkit.models.Transaction
import io.horizontalsystems.nearkit.models.TxAction
import io.horizontalsystems.marketkit.models.Blockchain
import io.horizontalsystems.marketkit.models.BlockchainType
import io.horizontalsystems.marketkit.models.Coin
import io.horizontalsystems.marketkit.models.Token
import io.horizontalsystems.marketkit.models.TokenQuery
import io.horizontalsystems.marketkit.models.TokenType
import io.horizontalsystems.walletkit.core.ICoinManager
import io.horizontalsystems.walletkit.core.factories.NearTransactionConverter
import io.horizontalsystems.walletkit.entities.Account
import io.horizontalsystems.walletkit.entities.AccountOrigin
import io.horizontalsystems.walletkit.entities.AccountType
import io.horizontalsystems.walletkit.modules.transactions.TransactionSource
import kotlinx.coroutines.runBlocking
import io.horizontalsystems.walletkit.entities.TransactionValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

class NearHistorySafetyTest {
    private val self = "victim.near"
    private val trusted = "trusted-contact.near"
    private val fakeContract = "counterfeit.near"

    @Test
    fun `unknown issuer survives metadata and cannot use a contact spam exemption`() = runBlocking {
        var spamCheckerCalled = false
        val converter = converter(metadata()) { spamCheckerCalled = true; false }
        val receive = converter.convert(incomingToken())
        val value = receive.mainValue!!
        assertTrue(value is TransactionValue.TokenValue)
        assertEquals(fakeContract, value.contractId)
        assertEquals("USDC", value.coinCode)
        assertEquals(BigDecimal.ONE.setScale(6), value.decimalValue)
        assertTrue(receive.spam)
        assertFalse(spamCheckerCalled)
        assertEquals(trusted, (receive.type as NearTransactionRecord.Type.Receive).from)
        assertEquals(listOf(fakeContract), receive.type.values.mapNotNull { it.contractId })
    }

    @Test
    fun `metadata read failure stays raw and distinct from real zero decimals`() = runBlocking {
        val failedMetadata = converter(null).convert(incomingToken())
        val raw = failedMetadata.mainValue!!
        assertTrue(raw is TransactionValue.RawValue)
        assertEquals(BigInteger("1000000"), (raw as TransactionValue.RawValue).value)
        assertNull(raw.decimals)
        assertNull(raw.decimalValue)
        assertEquals(fakeContract, raw.contractId)
        assertEquals(fakeContract, raw.abs.contractId)
        assertTrue(failedMetadata.spam)
        val zeroDecimals = converter(metadata(0)).convert(incomingToken()).mainValue!!
        assertTrue(zeroDecimals is TransactionValue.TokenValue)
        assertEquals(0, zeroDecimals.decimals)
        assertEquals(BigDecimal("1000000"), zeroDecimals.decimalValue)
    }

    @Test
    fun `incoming spam cannot consume the outgoing context limit`() {
        val history = (250 downTo 1).map { n ->
            transaction("spam-$n", n.toLong() + 100, near = listOf(native("flood.near", self)))
        } + transaction("genuine", 100, signer = self, near = listOf(native(self, trusted)))
        var pages = 0
        val context = NearTransactionHistory.outgoingContext(self, 1, 1000, "anchor", { true }) { time, _, size ->
            pages++
            history.filter { it.timestamp < (time ?: Long.MAX_VALUE) }.take(size)
        }
        assertEquals(listOf(trusted), context.map { it.recipientAddress })
        assertEquals(3, pages)
    }

    @Test
    fun `unsigned and unknown token logs cannot become trusted outgoing history`() {
        val forged = FtTransfer(fakeContract, self, "lookalike.near", BigInteger.TEN, null)
        assertNull(NearTransactionHistory.outgoingCounterparty(transaction(ft = listOf(forged)), self) { true })
        assertNull(NearTransactionHistory.outgoingCounterparty(transaction(signer = self, ft = listOf(forged)), self) { false })
        val genuine = forged.copy(contractId = "usdc.near", to = trusted)
        assertEquals(trusted, NearTransactionHistory.outgoingCounterparty(transaction(signer = self, ft = listOf(genuine)), self) { it == "usdc.near" })
        assertNull(NearTransactionHistory.outgoingCounterparty(transaction(signer = self, ft = listOf(genuine)).copy(status = Transaction.Status.Failed), self) { true })
    }

    @Test
    fun `internal native counterparties match only successful movements involving this account`() {
        val tx = transaction(near = listOf(native("vault.near", self)))
        assertTrue(NearTransactionHistory.matchesAddress(tx, self, "vault.near"))
        assertFalse(NearTransactionHistory.matchesAddress(tx.copy(nearTransfers = listOf(native("vault.near", "other.near"))), self, "vault.near"))
        assertFalse(NearTransactionHistory.matchesAddress(tx.copy(nearTransfers = listOf(native("vault.near", self).copy(success = false))), self, "vault.near"))
    }

    @Test
    fun `outgoing scan stops on a repeating cursor and at its page cap`() {
        var repeatedCalls = 0
        val repeatedPage = List(NearTransactionHistory.PAGE_SIZE) { transaction("repeat", 10) }
        assertTrue(NearTransactionHistory.outgoingContext(self, 1, 100, "anchor", { true }) { _, _, _ ->
            repeatedCalls++
            repeatedPage
        }.isEmpty())
        assertEquals(2, repeatedCalls)
        var cappedCalls = 0
        assertTrue(NearTransactionHistory.outgoingContext(self, 1, 10000, "anchor", { true }) { _, _, size ->
            cappedCalls++
            List(size) { transaction("page-$cappedCalls-$it", 10000L - cappedCalls * size - it) }
        }.isEmpty())
        assertEquals(NearTransactionHistory.MAX_CONTEXT_PAGES, cappedCalls)
    }

    private fun converter(metadata: FtMetadata?, spamChecker: () -> Boolean = { false }): NearTransactionConverter {
        val blockchain = Blockchain(BlockchainType.Near, "NEAR", null)
        val account = Account("account", "Account", AccountType.NearAddress(self), AccountOrigin.Restored, 0)
        val coins = object : ICoinManager {
            override fun getToken(query: TokenQuery): Token? = null
            override val customTokens = emptyList<Token>()
            override fun saveCustomToken(token: Token) = Unit
        }
        return NearTransactionConverter(
            TransactionSource(blockchain, account, null), self, coins,
            Token(Coin("near", "NEAR", "NEAR"), blockchain, TokenType.Native, 24),
            { metadata ?: error("Metadata RPC unavailable") },
            { _, _, _ -> spamChecker() },
        )
    }

    private fun incomingToken() = transaction(
        hash = "11111111111111111111111111111111",
        ft = listOf(FtTransfer(fakeContract, trusted, self, BigInteger("1000000"), null)),
    )

    private fun metadata(decimals: Int = 6) = FtMetadata(fakeContract, "USD Coin", "USDC", decimals, "ft-1.0.0")
    private fun native(from: String, to: String) = NearTransfer(from, to, BigInteger.TEN, NearTransfer.Kind.Transfer, true)
    private fun transaction(
        hash: String = "tx",
        time: Long = 10,
        signer: String = "attacker.near",
        near: List<NearTransfer> = emptyList(),
        ft: List<FtTransfer> = emptyList(),
    ) = Transaction(
        hash = hash,
        blockHeight = time,
        timestamp = time,
        signerId = signer,
        receiverId = "router.near",
        actions = listOf(TxAction("FunctionCall")),
        nearTransfers = near,
        ftTransfers = ft,
        fee = BigInteger.ONE,
        status = Transaction.Status.Success,
        failure = null,
        expiresAfterHeight = null,
    )
}

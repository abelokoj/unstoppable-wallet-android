package io.horizontalsystems.walletkit.core.adapters

import io.horizontalsystems.nearkit.models.NearTransfer
import io.horizontalsystems.nearkit.models.Transaction
import io.horizontalsystems.walletkit.core.managers.PoisoningScorer
import java.math.BigInteger

internal object NearTransactionHistory {
    const val PAGE_SIZE = 100
    const val MAX_CONTEXT_PAGES = 20

    fun outgoingContext(
        selfAccount: String,
        limit: Int,
        beforeTimestamp: Long?,
        beforeHash: String?,
        isKnownToken: (String) -> Boolean,
        getPage: (Long?, String?, Int) -> List<Transaction>,
    ): List<PoisoningScorer.OutgoingTxInfo> {
        if (limit <= 0) return emptyList()
        val result = mutableListOf<PoisoningScorer.OutgoingTxInfo>()
        var timestamp = beforeTimestamp
        var hash = beforeHash
        // Incoming spam does not consume the recipient limit; cap work independently of matches.
        repeat(MAX_CONTEXT_PAGES) {
            val page = getPage(timestamp, hash, PAGE_SIZE)
            for (tx in page) {
                val recipient = outgoingCounterparty(tx, selfAccount, isKnownToken) ?: continue
                result += PoisoningScorer.OutgoingTxInfo(recipient, tx.timestamp, tx.blockHeight?.toInt())
                if (result.size >= limit) return result
            }
            val last = page.lastOrNull() ?: return result
            if (page.size < PAGE_SIZE || (last.timestamp == timestamp && last.hash == hash)) return result
            timestamp = last.timestamp
            hash = last.hash
        }
        return result
    }

    fun outgoingCounterparty(tx: Transaction, selfAccount: String, isKnownToken: (String) -> Boolean): String? {
        if (!tx.isSigner(selfAccount) || !tx.isSuccess) return null
        tx.nearTransfers.firstOrNull {
            it.success && it.kind == NearTransfer.Kind.Transfer && it.amount > BigInteger.ZERO &&
                it.from == selfAccount && it.to != selfAccount
        }?.let { return it.to }
        return tx.ftTransfers.firstOrNull {
            it.amount > BigInteger.ZERO && it.from == selfAccount && it.to != null &&
                it.to != selfAccount && isKnownToken(it.contractId)
        }?.to
    }

    fun matchesAddress(tx: Transaction, selfAccount: String, address: String): Boolean =
        tx.signerId == address || tx.receiverId == address ||
            tx.ftTransfers.any { it.from == address || it.to == address } ||
            tx.nearTransfers.any {
                it.success && it.kind == NearTransfer.Kind.Transfer &&
                    ((it.from == selfAccount && it.to == address) || (it.to == selfAccount && it.from == address))
            }
}

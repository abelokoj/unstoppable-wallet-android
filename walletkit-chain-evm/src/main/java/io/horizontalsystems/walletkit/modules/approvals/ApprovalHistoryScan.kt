package io.horizontalsystems.walletkit.modules.approvals

import com.google.gson.JsonObject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class ApprovalHistoryTransaction(val hash: String, val block: Long)

/** Session pagination only: every new scan starts from remote address history, never the wallet database. */
internal class ApprovalHistoryScan(val pageSize: Int = 50) {
    var head: Long = 0
        private set
    var complete = false
        private set
    var oldestBlock: Long = 0
        private set
    private var started = false
    private var page = 1
    private var pending: List<ApprovalHistoryTransaction>? = null
    private var next = 0

    suspend fun scan(latest: Long, fetch: suspend (Long, Int) -> List<ApprovalHistoryTransaction>,
        readReceipt: suspend (ApprovalHistoryTransaction) -> Unit, onProgress: (Long) -> Unit) {
        require(latest >= 0 && pageSize in 1..100)
        if (!started || complete || latest < head) {
            head = latest
            oldestBlock = latest
            started = true
            complete = false
            page = 1
            pending = null
            next = 0
        }
        val rows = pending ?: fetch(head, page).also {
            require(it.size <= pageSize)
            require(it.all { row -> row.block in 0..head })
            pending = it
        }
        while (next < rows.size) {
            currentCoroutineContext().ensureActive()
            val tx = rows[next]
            readReceipt(tx)
            next++
            oldestBlock = minOf(oldestBlock, tx.block)
            onProgress(oldestBlock)
        }
        complete = rows.size < pageSize
        if (!complete) page++
        pending = null
        next = 0
    }
}

internal fun parseApprovalHistory(response: JsonObject, owner: String, head: Long, limit: Int): List<ApprovalHistoryTransaction> {
    val status = response.get("status")?.asString
    val result = response.get("result")
    require(result != null && result.isJsonArray) { "Address history is unavailable" }
    val rows = result.asJsonArray
    require(rows.size() <= limit)
    require(status == "1" || (status == "0" && response.get("message")?.asString == "No transactions found" && rows.size() == 0))
    val parsed = rows.map { item ->
        val tx = item.asJsonObject
        val hash = tx.get("hash")?.asString ?: error("Missing transaction identity")
        require(hash.matches(Regex("^0x[0-9a-fA-F]{64}$")))
        val block = tx.get("blockNumber")?.asString?.toLongOrNull()
        require(block != null && block in 0..head)
        require(tx.get("from")?.asString?.equals(owner, true) == true || tx.get("to")?.asString?.equals(owner, true) == true)
        ApprovalHistoryTransaction(hash.lowercase(), block)
    }
    require(parsed.map { it.hash }.distinct().size == parsed.size)
    require(parsed.zipWithNext().all { (a, b) -> a.block >= b.block })
    return parsed
}

internal fun approvalHistoryUsesTor(enabled: Boolean, connected: Boolean): Boolean {
    if (enabled && !connected) throw ApprovalHistoryTorRequired()
    return enabled
}

/** A provider's null receipt means unavailable, never an empty transaction without approvals. */
internal suspend fun readApprovalReceipt(
    hash: String,
    primary: suspend (String) -> JsonObject?,
    fallback: (suspend (String) -> JsonObject?)?,
): JsonObject = primary(hash) ?: requireNotNull(fallback?.invoke(hash)) { "Transaction receipt is unavailable" }

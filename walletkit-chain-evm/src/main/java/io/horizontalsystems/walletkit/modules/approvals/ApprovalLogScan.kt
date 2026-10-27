package io.horizontalsystems.walletkit.modules.approvals

/** Advances only after both approval-event queries and their candidates have been accepted. */
internal class ApprovalLogScan {
    var nextBlock: Long? = null
        private set
    var scannedHead = 0L
        private set
    private var started = false
    private var chunkSize = Long.MAX_VALUE

    suspend fun scan(latest: Long, fetch: suspend (Long, Long) -> Unit, onProgress: (Long) -> Unit) {
        require(latest >= 0 && latest < Long.MAX_VALUE)
        if (!started || latest < scannedHead) {
            started = true
            scannedHead = latest
            nextBlock = latest
        }
        // Each range issues two RPC calls; keep each button press bounded and resumable.
        var attempts = 0
        while (attempts++ < 12 && (scannedHead < latest || nextBlock != null)) {
            val catchingUp = scannedHead < latest
            val to = if (catchingUp) scannedHead + minOf(chunkSize, latest - scannedHead) else nextBlock!!
            val from = if (catchingUp) scannedHead + 1 else to - minOf(chunkSize, to + 1) + 1
            try {
                fetch(from, to)
                if (catchingUp) scannedHead = to else nextBlock = if (from == 0L) null else from - 1
                onProgress(from)
            } catch (error: ApprovalLogRangeError) {
                if (from == to) throw error
                chunkSize = maxOf(1, (to - from + 1) / 2)
            }
        }
    }
}

internal open class ApprovalLogRangeError : Exception("The selected RPC could not complete the approval query")

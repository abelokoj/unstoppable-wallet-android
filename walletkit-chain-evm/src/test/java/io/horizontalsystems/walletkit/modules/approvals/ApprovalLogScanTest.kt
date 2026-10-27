package io.horizontalsystems.walletkit.modules.approvals

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ApprovalLogScanTest {
    @Test fun `limited RPC ranges resume without losing old or new blocks`() = runBlocking {
        val scan = ApprovalLogScan()
        val covered = mutableSetOf<Long>()
        val fetch: suspend (Long, Long) -> Unit = { from, to ->
            if (to - from + 1 > 2) throw ApprovalLogRangeError()
            covered.addAll(from..to)
        }
        scan.scan(30, fetch) {}
        assertNotNull(scan.nextBlock)
        repeat(3) { scan.scan(30, fetch) {} }
        assertNull(scan.nextBlock)
        assertEquals((0L..30L).toSet(), covered)

        scan.scan(100, fetch) {}
        assertTrue(scan.scannedHead < 100)
        repeat(6) { scan.scan(100, fetch) {} }
        assertEquals(100L, scan.scannedHead)
        assertEquals((0L..100L).toSet(), covered)
    }

    @Test fun `unlimited node completes in one range`() = runBlocking {
        val scan = ApprovalLogScan()
        val ranges = mutableListOf<Pair<Long, Long>>()
        scan.scan(500, { from, to -> ranges += from to to }) {}
        assertEquals(listOf(0L to 500L), ranges)
        assertNull(scan.nextBlock)
        assertEquals(500L, scan.scannedHead)
    }

    @Test fun `transport failure never advances a range`() = runBlocking {
        val scan = ApprovalLogScan()
        try {
            scan.scan(20, { _, _ -> throw IllegalStateException("offline") }) {}
            fail("Expected the transport error")
        } catch (_: IllegalStateException) {
            assertEquals(20L, scan.nextBlock)
        }
    }

    @Test fun `cancellation keeps the unacknowledged range available`() = runBlocking {
        val scan = ApprovalLogScan()
        try {
            scan.scan(20, { _, _ -> throw CancellationException() }) {}
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            assertEquals(20L, scan.nextBlock)
        }
    }

    @Test fun `one rejected block fails rather than claiming completion`() = runBlocking {
        val scan = ApprovalLogScan()
        try {
            scan.scan(0, { _, _ -> throw ApprovalLogRangeError() }) {}
            fail("Expected a refused log query")
        } catch (_: ApprovalLogRangeError) {
            assertEquals(0L, scan.nextBlock)
        }
    }
}

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

    @Test fun `BSC sized history makes progress on the first scan with a 5000 block provider`() = runBlocking {
        val head = 130_386_712L
        val scan = ApprovalLogScan()
        val accepted = mutableListOf<LongRange>()
        scan.scan(head, { from, to ->
            if (to - from + 1 > 5000) throw ApprovalLogRangeError()
            accepted += from..to
        }) {}
        assertTrue(accepted.isNotEmpty())
        assertTrue(scan.nextBlock!! < head - 10_000)
        assertEquals(head, scan.scannedHead)
        assertTrue(accepted.all { it.first >= 0 && it.last <= head })
    }

    @Test fun `a provider refusing even one block fails instead of returning an empty scan`() = runBlocking {
        val scan = ApprovalLogScan()
        val requests = mutableListOf<LongRange>()
        try {
            scan.scan(130_386_712, { from, to ->
                requests += from..to
                throw ApprovalLogRangeError()
            }) {}
            fail("Expected the disabled log method to be reported")
        } catch (_: ApprovalLogRangeError) {
            assertEquals(130_386_712L, scan.nextBlock)
            assertEquals(2, requests.size)
            assertEquals(130_386_712L..130_386_712L, requests.last())
        }
    }

    @Test fun `large EVM history makes progress with single block and public provider limits`() = runBlocking {
        for (limit in listOf(1L, 25L, 100L, 5000L)) {
            val scan = ApprovalLogScan()
            val head = 519_223_998L
            var accepted = 0
            scan.scan(head, { from, to ->
                if (to - from + 1 > limit) throw ApprovalLogRangeError()
                accepted++
            }) {}
            assertTrue("No progress for limit $limit", accepted > 0)
            assertTrue(scan.nextBlock!! < head)
            assertEquals(head, scan.scannedHead)
        }
    }
}

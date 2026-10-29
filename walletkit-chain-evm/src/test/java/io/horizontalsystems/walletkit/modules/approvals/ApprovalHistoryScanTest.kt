package io.horizontalsystems.walletkit.modules.approvals

import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ApprovalHistoryScanTest {
    private val owner = "0x" + "11".repeat(20)
    private val other = "0x" + "22".repeat(20)
    private fun tx(n: Int, block: Long) = ApprovalHistoryTransaction("0x" + n.toString(16).padStart(64, '0'), block)

    @Test fun `a cold scan fetches remote pages and keeps its snapshot stable while new blocks arrive`() = runBlocking {
        val scan = ApprovalHistoryScan(pageSize = 2)
        val requests = mutableListOf<Pair<Long, Int>>()
        val checked = mutableListOf<ApprovalHistoryTransaction>()
        val first = listOf(tx(1, 100), tx(2, 90))
        scan.scan(100, { head, page -> requests += head to page; first }, { checked += it }) {}
        assertFalse(scan.complete)
        scan.scan(120, { head, page -> requests += head to page; listOf(tx(3, 10)) }, { checked += it }) {}
        assertEquals(listOf(100L to 1, 100L to 2), requests)
        assertEquals(listOf(tx(1, 100), tx(2, 90), tx(3, 10)), checked)
        assertTrue(scan.complete)
        assertEquals(100L, scan.head)
    }

    @Test fun `a failed receipt keeps the same page and retries only unfinished transactions`() = runBlocking {
        val scan = ApprovalHistoryScan(pageSize = 3)
        val rows = listOf(tx(1, 100), tx(2, 90))
        val attempts = mutableListOf<Int>()
        var shouldFail = true
        val read: suspend (ApprovalHistoryTransaction) -> Unit = {
            val n = it.hash.takeLast(2).toInt(16)
            attempts += n
            if (n == 2 && shouldFail) error("receipt unavailable")
        }
        try { scan.scan(100, { _, _ -> rows }, read) {}; fail("Expected receipt failure") }
        catch (_: IllegalStateException) { }
        assertFalse(scan.complete)
        shouldFail = false
        scan.scan(110, { _, _ -> error("The unfinished page must be retained") }, read) {}
        assertEquals(listOf(1, 2, 2), attempts)
        assertTrue(scan.complete)
    }

    @Test fun `provider errors are never treated as empty history`() {
        for (message in listOf("NOTOK", "No transactions found")) {
            val invalid = JsonParser.parseString("""{"status":"0","message":"$message","result":"rate limited"}""").asJsonObject
            assertThrows(IllegalArgumentException::class.java) { parseApprovalHistory(invalid, owner, 100, 50) }
        }
        val empty = JsonParser.parseString("""{"status":"0","message":"No transactions found","result":[]}""").asJsonObject
        assertTrue(parseApprovalHistory(empty, owner, 100, 50).isEmpty())
    }

    @Test fun `history is bound to the requested owner block range and transaction identity`() {
        fun response(from: String = owner, block: String = "90", hash: String = tx(1, 90).hash) =
            JsonParser.parseString("""{"status":"1","message":"OK","result":[{"hash":"$hash","blockNumber":"$block","from":"$from","to":"$other"}]}""").asJsonObject
        assertEquals(listOf(tx(1, 90)), parseApprovalHistory(response(), owner, 100, 50))
        assertThrows(IllegalArgumentException::class.java) { parseApprovalHistory(response(from = other), owner, 100, 50) }
        assertThrows(IllegalArgumentException::class.java) { parseApprovalHistory(response(block = "101"), owner, 100, 50) }
        assertThrows(IllegalArgumentException::class.java) { parseApprovalHistory(response(hash = "0x1"), owner, 100, 50) }
    }
    @Test fun `history follows optional Tor and fails closed when selected Tor is unavailable`() {
        assertFalse(approvalHistoryUsesTor(false, false))
        assertFalse(approvalHistoryUsesTor(false, true))
        assertTrue(approvalHistoryUsesTor(true, true))
        assertThrows(ApprovalHistoryTorRequired::class.java) { approvalHistoryUsesTor(true, false) }
    }
    @Test fun `a provider returning a missing receipt uses the permitted historical reader`() = runBlocking {
        val hash = tx(1, 90).hash
        val expected = JsonParser.parseString("""{"transactionHash":"$hash","blockNumber":"0x5a","logs":[]}""").asJsonObject
        var fallbackCalls = 0
        val receipt = readApprovalReceipt(hash, { null }, {
            assertEquals(hash, it)
            fallbackCalls++
            expected
        })
        assertSame(expected, receipt)
        assertEquals(1, fallbackCalls)
    }

    @Test fun `an available selected-provider receipt never contacts the fallback`() = runBlocking {
        val expected = JsonParser.parseString("""{"logs":[]}""").asJsonObject
        assertSame(expected, readApprovalReceipt(tx(1, 90).hash, { expected }, { error("Unexpected fallback") }))
    }

    @Test fun `a missing receipt without an available fallback remains a discovery failure`() = runBlocking {
        for (fallback in listOf<(suspend (String) -> com.google.gson.JsonObject?)?>(null, { null })) {
            try {
                readApprovalReceipt(tx(1, 90).hash, { null }, fallback)
                fail("A missing receipt cannot be an empty successful scan")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun `cancellation or provider guard errors never trigger a receipt fallback`() = runBlocking {
        for (error in listOf(kotlinx.coroutines.CancellationException(), IllegalStateException("Provider changed"))) {
            try {
                readApprovalReceipt(tx(1, 90).hash, { throw error }, { fail("Unexpected fallback"); null })
                fail("Expected original failure")
            } catch (actual: Exception) { assertSame(error, actual) }
        }
    }
}

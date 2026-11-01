package io.horizontalsystems.walletkit.modules.approvals

import com.google.gson.JsonParser
import io.horizontalsystems.walletkit.entities.EvmSyncSource
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ApprovalRpcFallbackTest {
    @Test fun `missing receipts empty logs and zero allowances trigger fallback but real data does not`() {
        fun json(text: String) = JsonParser.parseString(text)
        assertTrue(isEmptyApprovalRpcResult("eth_getTransactionReceipt", json("null")))
        assertTrue(isEmptyApprovalRpcResult("eth_getLogs", json("[]")))
        assertTrue(isEmptyApprovalRpcResult("eth_call", json("\"0x${"0".repeat(64)}\"")))
        assertFalse(isEmptyApprovalRpcResult("eth_call", json("\"0x${"0".repeat(63)}1\"")))
        assertFalse(isEmptyApprovalRpcResult("eth_getLogs", json("[{}]")))
        assertFalse(isEmptyApprovalRpcResult("eth_getTransactionReceipt", json("{\"logs\":[]}")))
    }

    @Test fun `all saved endpoint URLs retain their own credentials and selected order`() {
        val selected = EvmSyncSource("selected", "Blockrazor", listOf(URI("https://selected.example")))
        val builtin = EvmSyncSource("builtin", "Builtin", listOf(URI("https://one.example"), URI("https://two.example")))
        val custom = EvmSyncSource("custom", "Custom", listOf(URI("wss://custom.example")), true, "custom-secret")
        val endpoints = approvalRpcEndpoints(selected, listOf(builtin, selected, custom))
        assertEquals(listOf("https://selected.example", "https://one.example", "https://two.example", "wss://custom.example"), endpoints.map { it.url })
        assertEquals(listOf(null, null, null, "custom-secret"), endpoints.map { it.auth })
    }

    @Test fun `selected provider is used first without contacting others after a positive result`() = runBlocking {
        val calls = mutableListOf<String>()
        val result = readApprovalRpcSources(listOf("selected", "builtin", "custom"), {}, List<Int>::isEmpty) {
            calls += it
            listOf(1)
        }
        assertEquals(listOf("selected"), calls)
        assertEquals(listOf(1), result)
    }

    @Test fun `errors and empty selected responses fall through every builtin and custom endpoint`() = runBlocking {
        val calls = mutableListOf<String>()
        val result = readApprovalRpcSources(listOf("blockrazor", "empty", "builtin", "custom"), {}, List<Int>::isEmpty) {
            calls += it
            when (it) {
                "blockrazor" -> error("Historical requests disabled")
                "empty" -> emptyList()
                "builtin" -> error("Wrong chain")
                else -> listOf(42)
            }
        }
        assertEquals(listOf("blockrazor", "empty", "builtin", "custom"), calls)
        assertEquals(listOf(42), result)
    }

    @Test fun `a clean empty result requires every endpoint to complete successfully`() = runBlocking {
        var calls = 0
        assertTrue(readApprovalRpcSources(listOf(1, 2, 3), {}, List<Int>::isEmpty) {
            calls++
            emptyList()
        }.isEmpty())
        assertEquals(3, calls)
        try {
            readApprovalRpcSources(listOf(1, 2), {}, List<Int>::isEmpty) {
                if (it == 1) emptyList() else error("Unavailable")
            }
            fail("An unavailable endpoint must not become a successful empty scan")
        } catch (_: IllegalStateException) { }
    }

    @Test fun `cancellation never attempts another provider`() = runBlocking {
        var calls = 0
        try {
            readApprovalRpcSources(listOf(1, 2), {}, List<Int>::isEmpty) {
                calls++
                throw CancellationException()
            }
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
        assertEquals(1, calls)
    }

    @Test fun `account provider or Tor changes discard in-flight data and stop fallback`() = runBlocking {
        for (failRequest in listOf(false, true)) {
            var changed = false
            var calls = 0
            try {
                readApprovalRpcSources(listOf(1, 2), { check(!changed) }, List<Int>::isEmpty) {
                    calls++
                    changed = true
                    if (failRequest) error("Network failure") else listOf(1)
                }
                fail("Expected settings guard")
            } catch (_: IllegalStateException) { }
            assertEquals(1, calls)
        }
    }
}

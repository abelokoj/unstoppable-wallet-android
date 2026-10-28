package io.horizontalsystems.walletkit.modules.nearaccount

import io.horizontalsystems.nearkit.crypto.PublicKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class NearAccountDiscoveryTest {
    private val publicKey = PublicKey(ByteArray(32) { it.toByte() })

    @Test
    fun `duplicates and invalid ids cannot amplify ownership or detail requests`() = runBlocking {
        val checked = mutableListOf<String>()
        val loaded = mutableListOf<String>()
        val ids = boundedNearAccountLookup(
            publicKey,
            candidates = { List(10_000) { "owned.near" } + listOf("BAD.near", "bad..near", "", publicKey.implicitAccountId) },
            hasFullAccess = { checked += it; true },
            loadAccount = { loaded += it; it },
        )
        assertEquals(listOf("owned.near"), checked)
        assertEquals(listOf(publicKey.implicitAccountId, "owned.near"), ids)
        assertEquals(ids, loaded)
    }

    @Test
    fun `only twenty valid named candidates are checked and full access remains mandatory`() = runBlocking {
        val checked = mutableListOf<String>()
        val candidates = (1..100).map { "account$it.near" }
        val ids = boundedNearAccountLookup(
            publicKey,
            candidates = { listOf("BAD.near", "bad..near") + candidates },
            hasFullAccess = { checked += it; it == "account2.near" },
            loadAccount = { it },
        )
        assertEquals(candidates.take(20), checked)
        assertEquals(listOf(publicKey.implicitAccountId, "account2.near"), ids)
    }

    @Test
    fun `a stalled lookup becomes a normal failure for the manual and default paths`() = runBlocking {
        try {
            boundedNearAccountLookup(
                publicKey, candidates = { delay(10_000); emptyList() },
                hasFullAccess = { true }, loadAccount = { it }, timeoutMs = 50,
            )
            fail("Expected discovery timeout")
        } catch (e: IOException) {
            assertEquals("NEAR account discovery timed out", e.message)
        }
    }

    @Test
    fun `the timeout also covers ownership and account detail requests`() = runBlocking {
        for (stallOwnership in listOf(true, false)) {
            try {
                boundedNearAccountLookup(
                    publicKey, candidates = { listOf("owned.near") },
                    hasFullAccess = { if (stallOwnership) delay(10_000); true },
                    loadAccount = { delay(10_000); it }, timeoutMs = 50,
                )
                fail("Expected bounded account loading")
            } catch (e: IOException) {
                assertEquals("NEAR account discovery timed out", e.message)
            }
        }
    }

    @Test
    fun `leaving the picker still propagates cancellation`() = runBlocking {
        try {
            boundedNearAccountLookup(
                publicKey, candidates = { throw CancellationException("left picker") },
                hasFullAccess = { true }, loadAccount = { it },
            )
            fail("Expected cancellation")
        } catch (e: CancellationException) {
            assertEquals("left picker", e.message)
        }
    }
}

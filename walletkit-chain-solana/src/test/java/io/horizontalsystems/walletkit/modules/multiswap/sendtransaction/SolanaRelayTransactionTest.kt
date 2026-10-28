package io.horizontalsystems.walletkit.modules.multiswap.sendtransaction

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

class SolanaRelayTransactionTest {
    private val gson = Gson()
    private fun plan(name: String): SendTransactionData.Solana.WithRelayInstructions {
        val f = gson.fromJson(javaClass.classLoader!!.getResource("pancake-relay-$name.json")!!.readText(), JsonObject::class.java)
        val quote = f.getAsJsonObject("quote")
        val data = f.getAsJsonObject("response").getAsJsonArray("steps").single().asJsonObject
            .getAsJsonArray("items").single().asJsonObject.getAsJsonObject("data")
        return SendTransactionData.Solana.WithRelayInstructions(
            gson.fromJson(data.get("instructions"), Array<SolanaInstruction>::class.java).toList(),
            data.getAsJsonArray("addressLookupTableAddresses").map { it.asString }, f["owner"].asString,
            quote["inputToken"].asString, quote["amount"].asString.toBigInteger(), quote["requestId"].asString, 100)
    }

    @Test fun `captured native SOL and wrapped SOL swap routes validate without signing`() {
        for (name in listOf("nativeSOL_to_EVM", "SOL_to_EVM")) {
            val p = plan(name)
            val result = SolanaRelayInstructions.validate(p)
            assertTrue(result.writable.contains(p.senderAddress))
            assertThrows(IllegalArgumentException::class.java) { SolanaRelayInstructions.validate(p.copy(inputAmount = p.inputAmount + BigInteger.ONE)) }
        }
    }

    @Test fun `rejects extra signer altered Relay program and excessive slippage`() {
        val p = plan("nativeSOL_to_EVM")
        val first = p.instructions.first()
        val key = first.keys.first { it.isSigner }
        val extra = first.copy(keys = first.keys.map { if (it == key) it.copy(pubkey = SolanaRelayInstructions.WSOL) else it })
        assertThrows(IllegalArgumentException::class.java) { SolanaRelayInstructions.validate(p.copy(instructions = listOf(extra))) }
        assertThrows(IllegalArgumentException::class.java) { SolanaRelayInstructions.validate(p.copy(slippageBps = 5001)) }
        assertThrows(IllegalArgumentException::class.java) { SolanaRelayInstructions.validate(p.copy(instructions = listOf(first.copy(programId = SolanaRelayInstructions.WSOL)))) }
    }
    @Test fun `simulation rejects Token 2022 extension changes and appended permissions`() {
        val p = plan("nativeSOL_to_EVM")
        val token = SolanaRelayInstructions.associated(p.senderAddress, SolanaRelayInstructions.WSOL, SolanaRelayInstructions.TOKEN_2022)
        val validation = SolanaRelayInstructions.validate(p).let { it.copy(writable = it.writable + token) }
        val data = ByteArray(182).apply {
            org.sol4k.Base58.decode(SolanaRelayInstructions.WSOL).copyInto(this, 0)
            org.sol4k.Base58.decode(p.senderAddress).copyInto(this, 32)
            this[165] = 2
        }
        val wallet = SolanaRelayInstructions.Account(SolanaRelayInstructions.NATIVE, 1_000_000_000L, byteArrayOf())
        val account = SolanaRelayInstructions.Account(SolanaRelayInstructions.TOKEN_2022, 2_000_000L, data)
        val before = mapOf(p.senderAddress to wallet, token to account)
        assertEquals(BigInteger.ONE, SolanaRelayInstructions.validateChanges(p, validation, before, before, BigInteger.ONE))
        for (changed in listOf(data.copyOf().apply { this[170] = 1 }, data + byteArrayOf(1))) {
            val after = before + (token to account.copy(data = changed))
            assertThrows(IllegalArgumentException::class.java) {
                SolanaRelayInstructions.validateChanges(p, validation, before, after, BigInteger.ONE)
            }
        }
    }

}

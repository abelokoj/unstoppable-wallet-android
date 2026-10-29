package io.horizontalsystems.walletkit.modules.approvals

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class EvmApprovalReceiptTest {
    private val hash = "0x" + "aa".repeat(32)
    private val owner = "0x" + ApprovalAbi.addressWord("0x" + "11".repeat(20))
    private val spender = "0x" + "22".repeat(20)
    private val contract = "0x" + "33".repeat(20)

    private fun event(signature: String = EvmApprovalAccessService.APPROVAL_TOPIC, tokenId: String? = null) = JsonObject().apply {
        addProperty("transactionHash", hash)
        addProperty("blockNumber", "0xa")
        addProperty("address", contract)
        add("topics", JsonArray().apply {
            add(signature); add(owner); add("0x" + ApprovalAbi.addressWord(spender))
            tokenId?.let { add("0x" + ApprovalAbi.uintWord(it)) }
        })
        addProperty("data", if (tokenId == null) "0x" + "0".repeat(63) + "1" else "0x")
    }

    private fun receipt(vararg events: JsonObject) = JsonObject().apply {
        addProperty("transactionHash", hash)
        addProperty("blockNumber", "0xa")
        addProperty("status", "0x1")
        add("logs", JsonArray().apply { events.forEach(::add) })
    }

    private fun parse(value: JsonObject) = EvmApprovalAccessService.parseReceipt(value, hash, owner, 10)

    @Test fun `cached receipts distinguish ERC20 NFT and collection permissions from their events`() {
        val approvals = parse(receipt(event(), event(tokenId = "42"), event(EvmApprovalAccessService.APPROVAL_ALL_TOPIC)))
        assertEquals(listOf(
            ApprovalTarget(contract, spender, ApprovalKind.Token),
            ApprovalTarget(contract, spender, ApprovalKind.Nft, "42"),
            ApprovalTarget(contract, spender, ApprovalKind.NftCollection),
        ), approvals)
    }

    @Test fun `other owners and unrelated events cannot seed permission targets`() {
        val otherOwner = event().apply { getAsJsonArray("topics").set(1, JsonPrimitive("0x" + "44".repeat(32))) }
        val unrelated = event().apply { getAsJsonArray("topics").set(0, JsonPrimitive("0x" + "55".repeat(32))) }
        assertEquals(listOf(ApprovalTarget(contract, spender, ApprovalKind.Token)), parse(receipt(otherOwner, unrelated, event())))
    }

    @Test fun `a receipt from another transaction or block is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            parse(receipt(event()).apply { addProperty("transactionHash", "0x" + "bb".repeat(32)) })
        }
        assertThrows(IllegalArgumentException::class.java) {
            parse(receipt(event()).apply { addProperty("blockNumber", "0xb") })
        }
    }

    @Test fun `matching logs must belong to the requested transaction and block`() {
        assertThrows(IllegalArgumentException::class.java) {
            parse(receipt(event().apply { addProperty("transactionHash", "0x" + "bb".repeat(32)) }))
        }
        assertThrows(IllegalArgumentException::class.java) {
            parse(receipt(event().apply { addProperty("blockNumber", "0xb") }))
        }
    }

    @Test fun `failed transactions and removed logs do not create approvals`() {
        assertTrue(parse(receipt(event()).apply { addProperty("status", "0x0") }).isEmpty())
        assertTrue(parse(receipt(event().apply { addProperty("removed", true) })).isEmpty())
    }

    @Test fun `malformed status and matching approval data fail closed`() {
        assertThrows(IllegalArgumentException::class.java) {
            parse(receipt(event()).apply { addProperty("status", "0x2") })
        }
        assertThrows(IllegalArgumentException::class.java) {
            parse(receipt(event().apply { addProperty("data", "0x1") }))
        }
    }

    @Test fun `old pre-status receipts remain readable without weakening transaction binding`() {
        assertEquals(1, parse(receipt(event()).apply { remove("status") }).size)
    }
}
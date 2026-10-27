package io.horizontalsystems.walletkit.modules.approvals

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.horizontalsystems.ethereumkit.crypto.CryptoUtils
import io.horizontalsystems.ethereumkit.crypto.InternalBouncyCastleProvider
import org.junit.Assert.*
import org.junit.Test
import java.security.Security

class EvmApprovalLogTest {
    companion object {
        init {
            Security.addProvider(InternalBouncyCastleProvider.getInstance())
        }
    }

    private val ownerTopic = "0x" + "0".repeat(24) + "11".repeat(20)
    private val spender = "0x" + "22".repeat(20)
    private val contract = "0x" + "33".repeat(20)
    private fun event(signature: String, id: String? = null): JsonObject = JsonParser.parseString("""
        {"address":"$contract","blockNumber":"0xa","removed":false,
         "topics":["$signature","$ownerTopic","0x${ApprovalAbi.addressWord(spender)}"${id?.let { ",\"0x${ApprovalAbi.uintWord(it)}\"" } ?: ""}],
         "data":"${if (id == null) "0x" + "0".repeat(63) + "1" else "0x"}"}
    """).asJsonObject
    private fun parse(log: JsonObject, signature: String = EvmApprovalAccessService.APPROVAL_TOPIC) =
        EvmApprovalAccessService.parseLog(log, ownerTopic, signature, 0, 10)

    @Test fun `event topics match canonical Keccak signatures`() {
        fun topic(text: String) = "0x" + CryptoUtils.sha3(text.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
        assertEquals(topic("Approval(address,address,uint256)"), EvmApprovalAccessService.APPROVAL_TOPIC)
        assertEquals(topic("ApprovalForAll(address,address,bool)"), EvmApprovalAccessService.APPROVAL_ALL_TOPIC)
    }

    @Test fun `ERC20 approval is distinguished from token-specific NFT approval`() {
        assertEquals(ApprovalTarget(contract, spender, ApprovalKind.Token), parse(event(EvmApprovalAccessService.APPROVAL_TOPIC)))
        assertEquals(ApprovalTarget(contract, spender, ApprovalKind.Nft, "42"), parse(event(EvmApprovalAccessService.APPROVAL_TOPIC, "42")))
    }

    @Test fun `collection event is identified without relying on token metadata`() {
        assertEquals(ApprovalTarget(contract, spender, ApprovalKind.NftCollection),
            parse(event(EvmApprovalAccessService.APPROVAL_ALL_TOPIC), EvmApprovalAccessService.APPROVAL_ALL_TOPIC))
    }

    @Test fun `removed events are not treated as permission candidates`() {
        val log = event(EvmApprovalAccessService.APPROVAL_TOPIC).apply { addProperty("removed", true) }
        assertNull(parse(log))
    }

    @Test fun `unrelated owner and out-of-range logs fail closed`() {
        val log = event(EvmApprovalAccessService.APPROVAL_TOPIC)
        log.getAsJsonArray("topics").set(1, com.google.gson.JsonPrimitive("0x" + "44".repeat(32)))
        assertThrows(IllegalArgumentException::class.java) { parse(log) }
        assertThrows(IllegalArgumentException::class.java) {
            parse(event(EvmApprovalAccessService.APPROVAL_TOPIC).apply { addProperty("blockNumber", "0xb") })
        }
    }

    @Test fun `malformed topic and NFT identifier cannot create a candidate`() {
        val log = event(EvmApprovalAccessService.APPROVAL_TOPIC)
        log.getAsJsonArray("topics").set(2, com.google.gson.JsonPrimitive("0x" + "ff".repeat(32)))
        assertThrows(IllegalArgumentException::class.java) { parse(log) }
    }
}

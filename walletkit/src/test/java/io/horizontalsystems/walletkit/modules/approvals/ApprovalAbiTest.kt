package io.horizontalsystems.walletkit.modules.approvals

import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

class ApprovalAbiTest {
    private val contract = "0x" + "11".repeat(20)
    private val spender = "0x" + "22".repeat(20)
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }

    @Test fun `ERC20 revoke approves exactly zero for the chosen spender`() {
        val data = ApprovalAbi.revoke(ApprovalTarget(contract, spender, ApprovalKind.Token))
        assertEquals(contract, data.to)
        assertEquals(BigInteger.ZERO, data.value)
        assertEquals("095ea7b3" + "0".repeat(24) + "22".repeat(20) + "0".repeat(64), hex(data.input))
    }

    @Test fun `single NFT revoke clears approved address not token ID`() {
        val data = ApprovalAbi.revoke(ApprovalTarget(contract, spender, ApprovalKind.Nft, "42"))
        assertEquals(BigInteger.ZERO, data.value)
        assertEquals("095ea7b3" + "0".repeat(64) + "2a".padStart(64, '0'), hex(data.input))
    }

    @Test fun `collection revoke disables operator and never enables it`() {
        val data = ApprovalAbi.revoke(ApprovalTarget(contract, spender, ApprovalKind.NftCollection))
        assertEquals(BigInteger.ZERO, data.value)
        assertEquals("a22cb465" + "0".repeat(24) + "22".repeat(20) + "0".repeat(64), hex(data.input))
    }

    @Test fun `max NFT ID fits uint256 without truncation`() {
        assertEquals("f".repeat(64), ApprovalAbi.uintWord(ApprovalAbi.maxUint256.toString()))
    }

    @Test fun `invalid addresses and IDs are rejected before calldata construction`() {
        listOf("", "0x" + "0".repeat(40), "0x1234", "https://dapp.example", "0x" + "z".repeat(40)).forEach {
            assertThrows(IllegalArgumentException::class.java) { ApprovalAbi.revoke(ApprovalTarget(it, spender, ApprovalKind.Token)) }
            assertThrows(IllegalArgumentException::class.java) { ApprovalAbi.revoke(ApprovalTarget(contract, it, ApprovalKind.Token)) }
        }
        listOf("-1", "1.5", "0x01", "", ApprovalAbi.maxUint256.add(BigInteger.ONE).toString()).forEach {
            assertThrows(IllegalArgumentException::class.java) { ApprovalAbi.revoke(ApprovalTarget(contract, spender, ApprovalKind.Nft, it)) }
        }
    }

    @Test fun `non-NFT target cannot smuggle a token ID`() {
        assertThrows(IllegalArgumentException::class.java) {
            ApprovalAbi.revoke(ApprovalTarget(contract, spender, ApprovalKind.Token, "1"))
        }
    }

    @Test fun `address case does not create distinct permissions`() {
        val a = ApprovalTarget("0x" + "ab".repeat(20), spender, ApprovalKind.Token)
        val b = a.copy(contract = "0x" + "AB".repeat(20))
        assertEquals(a.key, b.key)
        assertEquals(a.contract, ApprovalAbi.revoke(b).to)
    }
}

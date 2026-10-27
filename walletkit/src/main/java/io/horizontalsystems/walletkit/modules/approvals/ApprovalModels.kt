package io.horizontalsystems.walletkit.modules.approvals

import io.horizontalsystems.walletkit.modules.multiswap.sendtransaction.EvmTransactionData
import kotlinx.serialization.Serializable
import java.math.BigInteger

@Serializable
enum class ApprovalKind { Token, Nft, NftCollection }

/** No provider may supply arbitrary transaction calldata to this tool. */
@Serializable
data class ApprovalTarget(
    val contract: String,
    val spender: String,
    val kind: ApprovalKind,
    val tokenId: String? = null,
) {
    val key: String get() = "${contract.lowercase()}:${spender.lowercase()}:$kind:$tokenId"
}

data class ActiveApproval(val target: ApprovalTarget, val rawAllowance: String)

data class ApprovalScan(
    val approvals: List<ActiveApproval>,
    val nextBlock: Long?,
    val head: Long,
    val failedChecks: Int,
    val latestHead: Long = head,
)

interface ApprovalAccessService {
    val owner: String
    suspend fun scan(onProgress: (Long) -> Unit): ApprovalScan
    suspend fun check(target: ApprovalTarget): ActiveApproval?
    suspend fun revokeData(target: ApprovalTarget): EvmTransactionData
}

/** Pure, allowlisted ABI construction shared by scan validation and transaction review. */
object ApprovalAbi {
    private val addressPattern = Regex("^0x[0-9a-fA-F]{40}$")
    private val zeroAddress = "0x" + "0".repeat(40)
    val maxUint256: BigInteger = BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE)

    fun address(value: String): String {
        require(addressPattern.matches(value)) { "Invalid EVM address" }
        require(!value.equals(zeroAddress, true)) { "Zero address is not a permission target" }
        return value.lowercase()
    }

    fun addressWord(value: String) = address(value).removePrefix("0x").padStart(64, '0')

    fun uintWord(value: String): String {
        require(value.length in 1..78 && value.all(Char::isDigit)) { "Invalid token ID" }
        val number = value.toBigInteger()
        require(number.signum() >= 0 && number <= maxUint256) { "Invalid token ID" }
        return number.toString(16).padStart(64, '0')
    }

    fun validate(target: ApprovalTarget) {
        address(target.contract)
        address(target.spender)
        if (target.kind == ApprovalKind.Nft) uintWord(requireNotNull(target.tokenId))
        else require(target.tokenId == null)
    }

    fun revoke(target: ApprovalTarget): EvmTransactionData {
        validate(target)
        val data = when (target.kind) {
            ApprovalKind.Token -> "095ea7b3" + addressWord(target.spender) + "0".repeat(64)
            ApprovalKind.Nft -> "095ea7b3" + "0".repeat(64) + uintWord(target.tokenId!!)
            ApprovalKind.NftCollection -> "a22cb465" + addressWord(target.spender) + "0".repeat(64)
        }
        return EvmTransactionData(address(target.contract), BigInteger.ZERO,
            data.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
    }
}

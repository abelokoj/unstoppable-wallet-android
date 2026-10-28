package io.horizontalsystems.walletkit.modules.approvals

import io.horizontalsystems.walletkit.modules.multiswap.sendtransaction.EvmTransactionData
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.BigInteger
import io.horizontalsystems.marketkit.models.Token

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

@Serializable
data class ActiveApproval(val target: ApprovalTarget, val rawAllowance: String)

data class ApprovalAssetMetadata(val name: String, val symbol: String, val decimals: Int?)

@Serializable
data class ApprovalTokenInfo(val name: String? = null, val symbol: String? = null, val decimals: Int? = null) {
    fun amount(rawAllowance: String): BigDecimal? {
        val scale = decimals?.takeIf { it in 0..255 } ?: return null
        if (rawAllowance.length !in 1..78 || rawAllowance.any { it !in '0'..'9' }) return null
        val raw = rawAllowance.toBigIntegerOrNull()?.takeIf { it <= ApprovalAbi.maxUint256 } ?: return null
        return BigDecimal(raw, scale)
    }
}

data class ApprovalAccessItem(
    val contract: String,
    val token: Token?,
    val approvals: List<ActiveApproval>,
    val name: String? = null,
    val imageUrl: String? = null,
) {
    val tokenInfo: ApprovalTokenInfo
        get() = ApprovalTokenInfo(token?.coin?.name ?: name, token?.coin?.code, token?.decimals)
}

internal fun groupApprovalItems(approvals: List<ActiveApproval>): List<ApprovalAccessItem> =
    approvals.distinctBy { it.target.key }.groupBy { it.target.contract.lowercase() }
        .map { (contract, permissions) -> ApprovalAccessItem(contract, null, permissions) }

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
    suspend fun metadata(target: ApprovalTarget): ApprovalAssetMetadata? = null
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

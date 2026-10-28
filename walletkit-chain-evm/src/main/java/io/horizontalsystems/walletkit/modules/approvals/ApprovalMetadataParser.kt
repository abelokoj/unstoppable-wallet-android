package io.horizontalsystems.walletkit.modules.approvals

import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Contract strings are display-only and untrusted; reject oversized or deceptive labels. */
internal object ApprovalMetadataParser {
    fun text(hex: String, maxLength: Int): String? = runCatching {
        require(hex.startsWith("0x") && hex.length in 66..2114 && hex.length % 2 == 0)
        require(hex.substring(2).all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' })
        val bytes = hex.substring(2).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val payload = if (bytes.size == 32) {
            bytes.takeWhile { it != 0.toByte() }.toByteArray()
        } else {
            require(bytes.size >= 64 && BigInteger(1, bytes.copyOfRange(0, 32)) == BigInteger.valueOf(32))
            val size = BigInteger(1, bytes.copyOfRange(32, 64)).intValueExact()
            require(size in 1..512 && 64 + size <= bytes.size)
            bytes.copyOfRange(64, 64 + size)
        }
        val result = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(payload)).toString().trim()
        require(result.length in 1..maxLength && result.none {
            it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt()
        })
        result
    }.getOrNull()
}

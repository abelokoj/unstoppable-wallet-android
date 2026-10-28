package io.horizontalsystems.walletkit.modules.approvals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApprovalMetadataParserTest {
    private fun dynamic(text: String): String {
        val bytes = text.toByteArray()
        return "0x" + "20".padStart(64, '0') + bytes.size.toString(16).padStart(64, '0') +
            bytes.joinToString("") { "%02x".format(it) }.padEnd(((bytes.size + 31) / 32) * 64, '0')
    }

    @Test fun `accepts normal and legacy contract labels`() {
        assertEquals("USD Coin", ApprovalMetadataParser.text(dynamic("USD Coin"), 128))
        assertEquals("USDC", ApprovalMetadataParser.text("0x55534443".padEnd(66, '0'), 32))
    }

    @Test fun `rejects malformed bounds invalid utf8 and deceptive labels`() {
        assertNull(ApprovalMetadataParser.text(dynamic("X").dropLast(64), 128))
        assertNull(ApprovalMetadataParser.text("0x" + "ff".repeat(32), 128))
        assertNull(ApprovalMetadataParser.text("0x+1".padEnd(66, '0'), 128))
        assertNull(ApprovalMetadataParser.text(dynamic("X".repeat(33)), 32))
        assertNull(ApprovalMetadataParser.text(dynamic("US\nDC"), 128))
        assertNull(ApprovalMetadataParser.text(dynamic("US\u202eDC"), 128))
        assertNull(ApprovalMetadataParser.text(dynamic(" "), 128))
    }
}

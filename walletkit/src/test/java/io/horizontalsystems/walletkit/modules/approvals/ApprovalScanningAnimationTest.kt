package io.horizontalsystems.walletkit.modules.approvals

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ApprovalScanningAnimationTest {
    private fun asset(theme: String) = File("src/main/res/raw/approval_scanning_$theme.json")
        .readText(Charsets.UTF_8)

    @Test
    fun themedAnimationsPreserveTheApprovedLoopAndLayerMotion() {
        val light = JsonParser.parseString(asset("light")).asJsonObject
        val dark = JsonParser.parseString(asset("dark")).asJsonObject
        for (animation in listOf(light, dark)) {
            assertEquals(1000, animation["w"].asInt)
            assertEquals(600, animation["h"].asInt)
            val duration = (animation["op"].asDouble - animation["ip"].asDouble) / animation["fr"].asDouble
            assertEquals(2.4, duration, 0.000001)
        }
        val lightLayers = light.getAsJsonArray("layers")
        val darkLayers = dark.getAsJsonArray("layers")
        assertEquals(lightLayers.size(), darkLayers.size())
        lightLayers.zip(darkLayers).forEach { (a, b) ->
            for (key in listOf("ind", "ty", "nm", "ks", "ip", "op", "tt", "td")) {
                assertEquals(key, a.asJsonObject[key], b.asJsonObject[key])
            }
        }
    }

    @Test
    fun packagedAnimationsAreOfflineVectorsWithOnlyExampleAddresses() {
        for (theme in listOf("light", "dark")) {
            val raw = asset(theme)
            val animation = JsonParser.parseString(raw).asJsonObject
            assertEquals(0, animation.getAsJsonArray("assets").size())
            assertFalse(animation.has("fonts"))
            assertFalse(animation.has("chars"))
            assertFalse(Regex("(?i)https?:|file:|javascript:").containsMatchIn(raw))
            assertFalse(Regex("\\\"x\\\"\\s*:\\s*\\\"").containsMatchIn(raw))
            for (address in listOf("0xdead...", "0x0000", "0x...dead", "0xaaaa", "0x2026...")) {
                assertTrue(raw.contains("Example address $address"))
            }
            assertEquals(22, Regex("Example address ").findAll(raw).count())
        }
    }

    @Test
    fun animationSelectionCyclesInOrderAndRecoversInvalidStoredValues() {
        val expected = listOf(ApprovalScanAnimation.Scanner, ApprovalScanAnimation.Together,
            ApprovalScanAnimation.Tom, ApprovalScanAnimation.Jerry, ApprovalScanAnimation.Original)
        var stored = 0
        repeat(15) { scan ->
            val selected = ApprovalScanAnimation.fromStoredIndex(stored)
            assertEquals(expected[scan % expected.size], selected)
            stored = selected.next.ordinal
        }
        for (invalid in listOf(-1, 5, Int.MIN_VALUE, Int.MAX_VALUE)) {
            assertEquals(ApprovalScanAnimation.Scanner, ApprovalScanAnimation.fromStoredIndex(invalid))
        }
    }

    private fun media() = JsonParser.parseString(File("src/test/resources/approval-cartoon-media.json").readText()).asJsonObject

    private fun sha256(bytes: ByteArray) = java.security.MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }

    @Test
    fun cartoonVideosMatchTheApprovedBaselineExports() {
        val videos = media().getAsJsonArray("videos")
        assertEquals(8, videos.size())
        assertEquals(8, videos.map { it.asJsonObject["video"].asString }.toSet().size)
        for (entry in videos) {
            val item = entry.asJsonObject
            val bytes = File("src/main/res/raw/" + item["video"].asString).readBytes()
            assertEquals(item["bytes"].asInt, bytes.size)
            assertEquals("ftyp", bytes.copyOfRange(4, 8).decodeToString())
            assertEquals(item["sha256"].asString, sha256(bytes))
            assertFalse(File("src/main/res/raw/approval_scanning_" + item["name"].asString + ".webp").exists())
        }
    }

    @Test
    fun stillPostersAreStaticAndTheMediaFitsTheBudget() {
        val fixture = media()
        var total = 37_929
        for (entry in fixture.getAsJsonArray("videos")) {
            val item = entry.asJsonObject
            val bytes = File("src/main/res/drawable-nodpi/" + item["poster"].asString).readBytes()
            assertEquals(item["posterBytes"].asInt, bytes.size)
            assertEquals(item["posterSha256"].asString, sha256(bytes))
            assertEquals("RIFF", bytes.copyOfRange(0, 4).decodeToString())
            assertEquals("WEBP", bytes.copyOfRange(8, 12).decodeToString())
            var offset = 12
            while (offset + 8 <= bytes.size) {
                val tag = bytes.copyOfRange(offset, offset + 4).decodeToString()
                assertFalse(tag == "ANIM" || tag == "ANMF")
                val length = (0 until 4).fold(0) { value, i ->
                    value or ((bytes[offset + 4 + i].toInt() and 255) shl (8 * i))
                }
                assertTrue(length >= 0 && length <= bytes.size - offset - 8)
                offset += 8 + length + (length and 1)
            }
            assertEquals(bytes.size, offset)
            total += item["bytes"].asInt + bytes.size
        }
        assertEquals(fixture["estimatedPackagedMediaBytes"].asInt, total)
        assertTrue(total < 3_000_000)
    }

    @Test
    fun cryptoScannerFilesRemainUnchanged() {
        for (entry in media().getAsJsonArray("scanner")) {
            val item = entry.asJsonObject
            val bytes = File("src/main/res/raw/" + item["name"].asString).readBytes()
            assertEquals(item["sha256"].asString, sha256(bytes))
        }
    }
}

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
            ApprovalScanAnimation.Tom, ApprovalScanAnimation.Jerry)
        var stored = 0
        repeat(12) { scan ->
            val selected = ApprovalScanAnimation.fromStoredIndex(stored)
            assertEquals(expected[scan % expected.size], selected)
            stored = selected.next.ordinal
        }
        for (invalid in listOf(-1, 4, Int.MIN_VALUE, Int.MAX_VALUE)) {
            assertEquals(ApprovalScanAnimation.Scanner, ApprovalScanAnimation.fromStoredIndex(invalid))
        }
    }

    @Test
    fun cartoonAnimationsAreTransparentSmoothOfflineLoops() {
        for (name in listOf("together", "tom", "jerry")) {
            val bytes = File("src/main/res/raw/approval_scanning_$name.webp").readBytes()
            fun tag(offset: Int) = bytes.copyOfRange(offset, offset + 4).decodeToString()
            fun little(offset: Int, count: Int) = (0 until count).fold(0) { value, i ->
                value or ((bytes[offset + i].toInt() and 255) shl (i * 8))
            }
            assertEquals("RIFF", tag(0))
            assertEquals("WEBP", tag(8))
            assertEquals(bytes.size - 8, little(4, 4))
            var offset = 12
            var frames = 0
            var duration = 0
            var hasCanvas = false
            var hasLoop = false
            while (offset + 8 <= bytes.size) {
                val length = little(offset + 4, 4)
                val body = offset + 8
                assertTrue(length >= 0 && length <= bytes.size - body)
                when (tag(offset)) {
                    "VP8X" -> {
                        assertTrue(length >= 10)
                        assertEquals(0x12, bytes[body].toInt() and 0x12)
                        assertEquals(640, little(body + 4, 3) + 1)
                        assertEquals(400, little(body + 7, 3) + 1)
                        hasCanvas = true
                    }
                    "ANIM" -> {
                        assertTrue(length >= 6)
                        assertEquals(0, little(body + 4, 2))
                        hasLoop = true
                    }
                    "ANMF" -> {
                        assertTrue(length >= 16)
                        val frameDuration = little(body + 12, 3)
                        assertTrue(frameDuration > 0)
                        duration += frameDuration
                        frames++
                    }
                }
                offset = body + length + (length and 1)
            }
            assertEquals(bytes.size, offset)
            assertTrue(hasCanvas && hasLoop && frames >= 200)
            assertEquals(13_600, duration)
        }
    }
}

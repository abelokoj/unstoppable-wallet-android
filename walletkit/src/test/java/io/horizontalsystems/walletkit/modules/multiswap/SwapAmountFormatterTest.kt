package io.horizontalsystems.walletkit.modules.multiswap

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

class SwapAmountFormatterTest {
    @Test fun `approved examples use the full fourteen character budget`() {
        for ((value, expected) in listOf(
            "0.0067893598097938" to "0.006789359809",
            "123456789.123456" to "123.456789123M",
            "12345678901.2345" to "12.3456789012B",
            "1919124441080.779" to "1.91912444108T",
            "1234500.0000000000" to "1.2345M",
        )) assertEquals(expected, formatSwapAmount(BigDecimal(value)))
    }

    @Test fun `discarded nonzero precision keeps budget zeroes but exact zeroes are not padded`() {
        assertEquals("11.4905245190T", formatSwapAmount(BigDecimal("11490524519035.5023")))
        assertEquals("1.000000000000", formatSwapAmount(BigDecimal("1.0000000000009")))
        for ((value, expected) in listOf("0.0000" to "0", "1000000" to "1M", "0.0000010000" to "0.000001")) {
            assertEquals(expected, formatSwapAmount(BigDecimal(value)))
        }
    }

    @Test fun `tiny amounts remain nonzero and do not invent precision`() {
        assertEquals("1.23456789E-18", formatSwapAmount(BigDecimal("1.234567891234E-18")))
        assertEquals("1E-400", formatSwapAmount(BigDecimal("1E-400")))
        assertEquals("0.000000000001", formatSwapAmount(BigDecimal("1E-12")))
        assertEquals("-1E-12", formatSwapAmount(BigDecimal("-1E-12")))
    }

    @Test fun `boundaries never round up into a larger unit`() {
        assertEquals("999.999999999K", formatSwapAmount(BigDecimal("999999.99999999")))
        assertEquals("999.999999999M", formatSwapAmount(BigDecimal("999999999.99999")))
        assertEquals("1B", formatSwapAmount(BigDecimal("1000000000")))
        assertEquals("1T", formatSwapAmount(BigDecimal("1000000000000")))
        assertEquals("1Q", formatSwapAmount(BigDecimal("1000000000000000")))
        assertEquals("1E18", formatSwapAmount(BigDecimal("1E18")))
    }

    @Test fun `all magnitudes fit fourteen characters without overstating amounts`() {
        for (exponent in -400..400 step 13) for (digits in listOf("1.0000000000009", "12.34567890123456789", "999.9999999999999")) {
            val value = BigDecimal(digits).scaleByPowerOfTen(exponent)
            val before = value.toString()
            val shown = formatSwapAmount(value)
            assertTrue(shown, shown.length <= 14)
            val shift = when (shown.last()) { 'K' -> 3; 'M' -> 6; 'B' -> 9; 'T' -> 12; 'Q' -> 15; else -> 0 }
            val displayed = if (shift == 0) BigDecimal(shown) else BigDecimal(shown.dropLast(1)).scaleByPowerOfTen(shift)
            assertTrue(shown, displayed > BigDecimal.ZERO && displayed <= value)
            assertEquals(before, value.toString())
        }
    }

    @Test fun `locale punctuation is retained without grouping characters`() {
        assertEquals("0,006789359809", formatSwapAmount(BigDecimal("0.0067893598097938"), Locale.GERMANY))
        assertEquals("123,456789123M", formatSwapAmount(BigDecimal("123456789.123456"), Locale.FRANCE))
        assertTrue(formatSwapAmount(BigDecimal("123456789.123456"), Locale.forLanguageTag("fa-IR")).length <= 14)
    }

    @Test fun `unfocused display transformation preserves raw text and bounded cursor mappings`() {
        for (raw in listOf("0.0067893598097938", "1919124441080.779", "1E-30", "1000")) {
            val original = AnnotatedString(raw)
            val shown = SwapAmountVisualTransformation(Locale.US).filter(original)
            assertEquals(raw, original.text)
            assertTrue(shown.text.length <= 14)
            assertEquals(0, shown.offsetMapping.originalToTransformed(0))
            assertEquals(shown.text.length, shown.offsetMapping.originalToTransformed(raw.length))
            assertEquals(raw.length, shown.offsetMapping.transformedToOriginal(shown.text.length))
            var previous = -1
            for (offset in 0..raw.length) {
                val mapped = shown.offsetMapping.originalToTransformed(offset)
                assertTrue(mapped in 0..shown.text.length && mapped >= previous)
                previous = mapped
            }
            previous = -1
            for (offset in 0..shown.text.length) {
                val mapped = shown.offsetMapping.transformedToOriginal(offset)
                assertTrue(mapped in 0..raw.length && mapped >= previous)
                previous = mapped
            }
        }
    }

    @Test fun `partial input stays editable without reinterpretation`() {
        for (raw in listOf("", ".", "-", "invalid")) {
            assertEquals(raw, SwapAmountVisualTransformation(Locale.US).filter(AnnotatedString(raw)).text.text)
        }
    }
}

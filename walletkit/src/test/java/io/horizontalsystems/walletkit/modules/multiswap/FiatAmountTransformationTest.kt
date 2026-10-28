package io.horizontalsystems.walletkit.modules.multiswap

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Test

class FiatAmountTransformationTest {
    @Test fun `empty fiat input keeps the caret after the currency before placeholder zero`() {
        for (symbol in listOf("$", "US$", "₦", "")) {
            val display = FiatAmountTransformation(symbol).filter(AnnotatedString(""))
            assertEquals(symbol + "0", display.text.text)
            assertEquals(symbol.length, display.offsetMapping.originalToTransformed(0))
            for (offset in 0..display.text.length) assertEquals(0, display.offsetMapping.transformedToOriginal(offset))
        }
    }

    @Test fun `typed values retain a non editable prefix and correct digit positions`() {
        val display = FiatAmountTransformation("US$").filter(AnnotatedString("12.50"))
        assertEquals("US$12.50", display.text.text)
        for (offset in 0..5) {
            assertEquals(offset + 3, display.offsetMapping.originalToTransformed(offset))
            assertEquals(offset, display.offsetMapping.transformedToOriginal(offset + 3))
        }
        assertEquals(0, display.offsetMapping.transformedToOriginal(0))
    }
}

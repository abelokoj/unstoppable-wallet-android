package io.horizontalsystems.walletkit.modules.multiswap

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import java.util.Locale

internal class SwapAmountVisualTransformation(private val locale: Locale) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val value = text.text.toBigDecimalOrNull() ?: return TransformedText(text, OffsetMapping.Identity)
        val display = AnnotatedString(formatSwapAmount(value, locale))
        return TransformedText(display, object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                if (offset == text.length) display.length else offset.coerceIn(0, display.length)

            override fun transformedToOriginal(offset: Int): Int =
                if (offset == display.length) text.length else offset.coerceIn(0, text.length)
        })
    }
}

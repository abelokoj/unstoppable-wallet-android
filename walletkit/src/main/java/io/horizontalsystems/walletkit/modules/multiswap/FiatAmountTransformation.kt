package io.horizontalsystems.walletkit.modules.multiswap

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

internal class FiatAmountTransformation(private val symbol: String) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        return TransformedText(AnnotatedString(symbol + text.text.ifEmpty { "0" }), object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = offset + symbol.length
            override fun transformedToOriginal(offset: Int) = (offset - symbol.length).coerceIn(0, text.length)
        })
    }
}

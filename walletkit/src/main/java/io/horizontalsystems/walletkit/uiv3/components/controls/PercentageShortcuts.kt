package io.horizontalsystems.walletkit.uiv3.components.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.horizontalsystems.walletkit.ui.compose.ComposeAppTheme
import io.horizontalsystems.walletkit.ui.compose.components.ButtonSecondary
import java.math.BigDecimal

internal fun isAcceptablePercentInput(text: String): Boolean {
    if (text.isEmpty()) return true
    if (!Regex("^\\d{0,3}(\\.\\d{0,2})?$").matches(text)) return false
    val value = text.toBigDecimalOrNull() ?: return text == "."
    return value < BigDecimal(100) || text == "100"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PercentageShortcuts(
    percents: List<Int>,
    disabledPercents: Set<Int>,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    onSelectExact: (BigDecimal) -> Unit,
) {
    var customText by remember { mutableStateOf("") }
    val customFocusRequester = remember { FocusRequester() }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = ComposeAppTheme.typography.captionSB
    val measuredWidth = with(density) {
        textMeasurer.measure(customText.ifEmpty { "__" }, style).size.width.toDp()
    }
    val customDisabled = customText.toBigDecimalOrNull()?.let {
        it >= BigDecimal(100) && 100 in disabledPercents
    } == true
    val customColor = when {
        !enabled -> ComposeAppTheme.colors.andy
        customDisabled -> ComposeAppTheme.colors.lucian
        else -> ComposeAppTheme.colors.leah
    }

    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        percents.forEach { percent ->
            val percentEnabled = enabled && percent !in disabledPercents
            ButtonSecondary(
                enabled = percentEnabled,
                onClick = {
                    customText = ""
                    onSelect(percent)
                },
            ) {
                Text(
                    text = "$percent%",
                    style = style,
                    color = if (percentEnabled) ComposeAppTheme.colors.leah else ComposeAppTheme.colors.andy,
                )
            }
        }
        ButtonSecondary(
            enabled = enabled,
            onClick = { customFocusRequester.requestFocus() },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    modifier = Modifier.width(measuredWidth).focusRequester(customFocusRequester),
                    value = customText,
                    onValueChange = { if (isAcceptablePercentInput(it)) customText = it },
                    enabled = enabled,
                    singleLine = true,
                    textStyle = style.copy(color = customColor, textAlign = TextAlign.End),
                    cursorBrush = SolidColor(customColor),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        customText.toBigDecimalOrNull()?.let { entered ->
                            if (entered > BigDecimal.ZERO && !customDisabled) onSelectExact(entered)
                        }
                    }),
                    decorationBox = { inner ->
                        Box {
                            if (customText.isEmpty()) {
                                Text(
                                    text = "__",
                                    style = style,
                                    color = ComposeAppTheme.colors.andy,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                            inner()
                        }
                    },
                )
                Text(
                    text = "%",
                    modifier = Modifier.padding(start = if (customText.isEmpty()) 4.dp else 2.dp),
                    style = style,
                    color = customColor,
                )
            }
        }
    }
}

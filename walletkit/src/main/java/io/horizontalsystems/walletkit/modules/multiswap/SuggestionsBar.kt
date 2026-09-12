package io.horizontalsystems.walletkit.modules.multiswap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.horizontalsystems.walletkit.R
import io.horizontalsystems.walletkit.ui.compose.ComposeAppTheme
import io.horizontalsystems.walletkit.ui.compose.components.BoxTyler44
import io.horizontalsystems.walletkit.ui.compose.components.ButtonSecondary
import io.horizontalsystems.walletkit.ui.compose.components.ButtonSecondaryCircle
import java.math.BigDecimal

// Accepts an empty field, or a number 0-100 with at most two decimal places. Rejects
// anything else as it is typed, so the field can never hold a value that cannot be applied.
private fun isAcceptablePercentInput(text: String): Boolean {
    if (text.isEmpty()) return true
    if (!Regex("^\\d{0,3}(\\.\\d{0,2})?$").matches(text)) return false
    val value = text.toBigDecimalOrNull() ?: return text == "."
    // 100 is the ceiling, so nothing may follow it - no decimal point, no further digits.
    if (value >= BigDecimal(100)) return text == "100"
    return true
}

@Composable
fun SuggestionsBar(
    modifier: Modifier = Modifier,
    percents: List<Int> = listOf(25, 50, 75, 100),
    onDelete: () -> Unit,
    onSelect: (Int) -> Unit,
    onSelectExact: (BigDecimal) -> Unit,
    onCustomFocusChanged: (Boolean) -> Unit,
    selectEnabled: Boolean,
    deleteEnabled: Boolean,
) {
    var customText by remember { mutableStateOf("") }
    val customFocusRequester = remember { FocusRequester() }

    // Measure the text at the real style so the field is exactly as wide as its content.
    // A guessed per-character width leaves dead space on one side and pushes the % out.
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val measuredStyle = ComposeAppTheme.typography.captionSB
    val measuredWidth = with(density) {
        val shown = customText.ifEmpty { "__" }
        // The % sign and its gaps live inside decorationBox, so the field must be wide
        // enough to hold them - otherwise the padding has nowhere to go and collapses.
        textMeasurer.measure(shown, measuredStyle).size.width.toDp()
    }

    val applyCustom = {
        customText.toBigDecimalOrNull()?.let { entered ->
            if (entered > BigDecimal.ZERO) onSelectExact.invoke(entered)
        }
        Unit
    }

    Box(modifier = modifier) {
        BoxTyler44(borderTop = true) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                percents.forEach { percent ->
                    ButtonSecondary(
                        enabled = selectEnabled,
                        onClick = {
                            customText = ""
                            onSelect.invoke(percent)
                        }
                    ) {
                        Text(
                            text = "$percent%",
                            style = ComposeAppTheme.typography.captionSB,
                            color = if (selectEnabled) {
                                ComposeAppTheme.colors.leah
                            } else {
                                ComposeAppTheme.colors.andy
                            },
                        )
                    }
                }

                ButtonSecondary(
                    enabled = selectEnabled,
                    onClick = { customFocusRequester.requestFocus() }
                ) {
                    // The % is a sibling of the field, not part of its decorationBox. Inside
                    // decorationBox it is laid out within the field's measured width, so there
                    // is no room after it and the button's own content padding never applies.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(
                            modifier = Modifier
                                .width(measuredWidth)
                                .focusRequester(customFocusRequester)
                                .onFocusChanged { onCustomFocusChanged.invoke(it.hasFocus) },
                            value = customText,
                            onValueChange = { new ->
                                if (isAcceptablePercentInput(new)) customText = new
                            },
                            enabled = selectEnabled,
                            singleLine = true,
                            textStyle = ComposeAppTheme.typography.captionSB.copy(
                                color = if (selectEnabled) {
                                    ComposeAppTheme.colors.leah
                                } else {
                                    ComposeAppTheme.colors.andy
                                },
                                textAlign = TextAlign.End
                            ),
                            cursorBrush = SolidColor(ComposeAppTheme.colors.leah),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { applyCustom() }),
                            decorationBox = { inner ->
                                Box {
                                    if (customText.isEmpty()) {
                                        Text(
                                            text = "__",
                                            style = ComposeAppTheme.typography.captionSB,
                                            color = ComposeAppTheme.colors.andy,
                                            maxLines = 1,
                                            softWrap = false,
                                        )
                                    }
                                    inner()
                                }
                            }
                        )
                        Text(
                            text = "%",
                            modifier = Modifier.padding(
                                start = if (customText.isEmpty()) 4.dp else 2.dp
                            ),
                            style = ComposeAppTheme.typography.captionSB,
                            color = if (selectEnabled) {
                                ComposeAppTheme.colors.leah
                            } else {
                                ComposeAppTheme.colors.andy
                            },
                        )
                    }
                }

                ButtonSecondaryCircle(
                    icon = R.drawable.ic_delete_20,
                    enabled = deleteEnabled,
                    tint = if (deleteEnabled) {
                        ComposeAppTheme.colors.leah
                    } else {
                        ComposeAppTheme.colors.andy
                    },
                    onClick = {
                        customText = ""
                        onDelete.invoke()
                    }
                )
            }
        }
    }
}

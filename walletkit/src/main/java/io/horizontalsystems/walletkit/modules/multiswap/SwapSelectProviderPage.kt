package io.horizontalsystems.walletkit.modules.multiswap

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.padding
import io.horizontalsystems.walletkit.uiv3.components.cell.ImageType
import io.horizontalsystems.walletkit.uiv3.components.cell.CellLeftImage
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.horizontalsystems.walletkit.BuildConfig
import io.horizontalsystems.walletkit.R
import io.horizontalsystems.walletkit.core.App
import io.horizontalsystems.walletkit.core.stats.StatEvent
import io.horizontalsystems.walletkit.core.stats.StatPage
import io.horizontalsystems.walletkit.core.stats.stat
import io.horizontalsystems.walletkit.modules.multiswap.providers.SwapProviderType
import io.horizontalsystems.walletkit.modules.multiswap.ui.RiskScore
import io.horizontalsystems.walletkit.modules.nav3.HSNavigation
import io.horizontalsystems.walletkit.modules.nav3.HSPage
import io.horizontalsystems.walletkit.ui.compose.ComposeAppTheme
import io.horizontalsystems.walletkit.ui.compose.components.HsDivider
import io.horizontalsystems.walletkit.ui.compose.components.VSpacer
import io.horizontalsystems.walletkit.ui.compose.components.subhead_leah
import io.horizontalsystems.walletkit.uiv3.components.BoxBordered
import io.horizontalsystems.walletkit.uiv3.components.HSScaffold
import io.horizontalsystems.walletkit.uiv3.components.cell.CellPrimary
import io.horizontalsystems.walletkit.uiv3.components.cell.HSString
import io.horizontalsystems.walletkit.uiv3.components.cell.hs
import io.horizontalsystems.walletkit.uiv3.components.controls.ButtonVariant
import io.horizontalsystems.walletkit.uiv3.components.controls.HSDropdownButton
import io.horizontalsystems.walletkit.uiv3.components.menu.MenuGroup
import io.horizontalsystems.walletkit.uiv3.components.menu.MenuItemX
import io.horizontalsystems.walletkit.uiv3.components.tabs.TabsSectionButtons
import kotlinx.serialization.Serializable
import kotlin.math.roundToLong

@Serializable
data class SwapSelectProviderPage(val parentScreenContentKey: String) : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        SwapSelectProviderScreen(navigation, parentScreenContentKey)
    }
}

@Composable
fun SwapSelectProviderScreen(
    navigation: HSNavigation,
    parentScreenContentKey: String
) {
    val swapViewModel = navigation.viewModelForScreen<SwapViewModel>(parentScreenContentKey)
    val viewModel = viewModel<SwapSelectProviderViewModel>(
        factory = SwapSelectProviderViewModel.Factory(
            swapViewModel.uiState.quotes,
            swapViewModel.uiState.quote
        )
    )

    val uiState = viewModel.uiState

    SwapSelectProviderScreenInner(
        onClickClose = navigation::removeLastOrNull,
        quotes = uiState.quoteViewItems,
        currentQuote = uiState.selectedQuote,
        sortType = uiState.sortType,
        onSortTypeChange = {
            viewModel.setSortType(it)
        },
        onBadgeClick = {
            navigation.slideFromBottom(RiskLevelInfoSheet)
        }
    ) {
        swapViewModel.onSelectQuote(it)
        navigation.removeLastOrNull()

        stat(page = StatPage.SwapProvider, event = StatEvent.SwapSelectProvider(it.provider.id))
    }
}

@Composable
private fun SwapSelectProviderScreenInner(
    onClickClose: () -> Unit,
    quotes: List<QuoteViewItem>,
    currentQuote: SwapProviderQuote?,
    sortType: ProviderSortType,
    onSortTypeChange: (ProviderSortType) -> Unit,
    onBadgeClick: () -> Unit,
    onSelectQuote: (SwapProviderQuote) -> Unit,
) {
    HSScaffold(
        title = stringResource(R.string.Swap_Providers),
        onBack = onClickClose,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(ComposeAppTheme.colors.lawrence)
        ) {
            TabsSectionButtons(
                left = {
                    ProviderSortingSelector(
                        sortType = sortType,
                        sortTypes = listOf(
                            ProviderSortType.BestPrice,
                            ProviderSortType.BestTime,
                        ),
                        onSelectSortType = {
                            onSortTypeChange.invoke(it)
                        }
                    )
                }
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (quotes.isNotEmpty()) {
                    HsDivider()
                }

                quotes.forEach { viewItem ->
                    val provider = viewItem.quote.provider
                    val icon = if (viewItem.quote == currentQuote) {
                        R.drawable.selector_checked_20
                    } else {
                        R.drawable.selector_unchecked_20
                    }
                    val iconTint = if (viewItem.quote == currentQuote) {
                        ComposeAppTheme.colors.jacob
                    } else {
                        ComposeAppTheme.colors.andy
                    }
                    BoxBordered(bottom = true) {
                        CellPrimary(
                            // Two-row layout restored from upstream 82f4fbc0d^, the last commit
                            // before provider icons and names were removed. Provider logo left,
                            // title + amount on row 1, time/risk + fiat on row 2, selector right.
                            left = {
                                provider.icon?.let { providerIcon ->
                                    CellLeftImage(
                                        painter = painterResource(providerIcon),
                                        type = ImageType.Rectangle,
                                        size = 32
                                    )
                                }
                            },
                            middle = {
                                Row(
                                    horizontalArrangement = spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row {
                                            Text(
                                                text = provider.title,
                                                style = ComposeAppTheme.typography.headline2,
                                                color = ComposeAppTheme.colors.leah,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = viewItem.tokenAmount,
                                                style = ComposeAppTheme.typography.subheadSB,
                                                color = ComposeAppTheme.colors.leah,
                                            )
                                        }
                                        Row {
                                            Row(
                                                horizontalArrangement = spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(end = 4.dp)
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.clock_filled_24),
                                                    modifier = Modifier.size(16.dp),
                                                    tint = ComposeAppTheme.colors.grey,
                                                    contentDescription = null
                                                )
                                                Text(
                                                    text = viewItem.estimationTime?.let {
                                                        formatSwapTime(it, provider.type)
                                                    } ?: stringResource(R.string.NotAvailable),
                                                    style = ComposeAppTheme.typography.subhead,
                                                    color = if (viewItem.timeStatus == SwapTimeStatus.Attention) {
                                                        ComposeAppTheme.colors.jacob
                                                    } else {
                                                        ComposeAppTheme.colors.grey
                                                    },
                                                )
                                                Text(
                                                    text = "|",
                                                    style = ComposeAppTheme.typography.subhead,
                                                    color = ComposeAppTheme.colors.grey,
                                                )
                                                RiskScore(
                                                    riskLevel = provider.riskLevel,
                                                    modifier = Modifier.clickable {
                                                        onBadgeClick.invoke()
                                                    },
                                                )
                                            }
                                            Row(horizontalArrangement = spacedBy(4.dp)) {
                                                viewItem.fiatAmount?.let {
                                                    Text(
                                                        text = it,
                                                        style = ComposeAppTheme.typography.subhead,
                                                        color = ComposeAppTheme.colors.grey,
                                                        textAlign = TextAlign.End,
                                                    )
                                                }
                                                getPriceImpact(viewItem.priceImpactData)?.let {
                                                    Text(
                                                        text = it.text,
                                                        style = ComposeAppTheme.typography.subhead,
                                                        color = it.color ?: ComposeAppTheme.colors.grey,
                                                        textAlign = TextAlign.End,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Icon(
                                        modifier = Modifier.size(20.dp),
                                        painter = painterResource(icon),
                                        contentDescription = null,
                                        tint = iconTint
                                    )
                                }
                            },
                            onClick = { onSelectQuote.invoke(viewItem.quote) }
                        )
                    }
                }
            }
            VSpacer(32.dp)
        }
    }
}

@Composable
fun ProviderSortingSelector(
    sortType: ProviderSortType,
    sortTypes: List<ProviderSortType>,
    onSelectSortType: (ProviderSortType) -> Unit
) {
    var showSortTypeSelectorDialog by remember { mutableStateOf(false) }

    HSDropdownButton(
        variant = ButtonVariant.Secondary,
        title = stringResource(sortType.title),
        onClick = {
            showSortTypeSelectorDialog = true
        }
    )

    if (showSortTypeSelectorDialog) {
        MenuGroup(
            title = stringResource(R.string.Balance_Sort_PopupTitle),
            items = sortTypes.map {
                MenuItemX(stringResource(it.title), it == sortType, it)
            },
            onDismissRequest = {
                showSortTypeSelectorDialog = false
            },
            onSelectItem = onSelectSortType
        )
    }
}

@Composable
private fun getPriceImpact(priceImpactData: PriceImpactData?): HSString? {
    if (priceImpactData == null) {
        return null
    }
    val color = when (priceImpactData.priceImpactLevel) {
        PriceImpactLevel.Normal -> null
        PriceImpactLevel.Warning -> ComposeAppTheme.colors.jacob
        else -> ComposeAppTheme.colors.lucian
    }
    val value =
        App.numberFormatter.format(priceImpactData.priceImpact, 0, 2, prefix = "-", suffix = "%")
    return "($value)".hs(color = color)
}

fun formatDurationShort(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return buildString {
        if (hours > 0) append("${hours}h ")
        if (minutes > 0) append("${minutes}m ")
        if (seconds > 0 || (hours == 0L && minutes == 0L)) append("${seconds}s")
    }.trim()
}

// CEX estimates are a measured average, so display them as a ±25% range;
// DEX providers quote their own timing and keep the single value.
fun formatSwapTime(estimationTime: Long, providerType: SwapProviderType): String =
    when (providerType) {
        SwapProviderType.CEX -> formatSwapTimeRange(estimationTime)
        SwapProviderType.DEX -> "~${formatDurationShort(estimationTime)}"
    }

fun formatSwapTimeRange(totalSeconds: Long): String {
    val minSeconds = roundSecondsToMinutes(totalSeconds * 0.75)
    val maxSeconds = roundSecondsToMinutes(totalSeconds * 1.25)
    return if (minSeconds == maxSeconds) {
        "~${formatDurationShort(minSeconds)}"
    } else {
        "${formatDurationShort(minSeconds)}-${formatDurationShort(maxSeconds)}"
    }
}

fun roundSecondsToMinutes(seconds: Double): Long =
    (seconds / 60).roundToLong().coerceAtLeast(1) * 60

@Preview
@Composable
private fun SwapSelectProviderScreenPreview() {
    ComposeAppTheme(darkTheme = false) {
        SwapSelectProviderScreenInner(
            onClickClose = {},
            quotes = listOf(
//                SwapProviderQuote(
//                    SwapMainModule.OneInchProvider,
//                    quote.amountOut,
//                    quote.fee,
//                    quote.fields
//                ),
//                SwapProviderQuote(
//                    SwapMainModule.UniswapV3Provider,
//                    quote.amountOut,
//                    quote.fee,
//                    quote.fields
//                ),
            ),
            currentQuote = null,
            sortType = ProviderSortType.BestPrice,
            onSortTypeChange = {},
            onBadgeClick = {},
            onSelectQuote = {}
        )
    }
}

package io.horizontalsystems.walletkit.modules.multiswap

import io.horizontalsystems.walletkit.ui.compose.IconSizes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.horizontalsystems.walletkit.R
import io.horizontalsystems.walletkit.core.App
import io.horizontalsystems.walletkit.core.badge
import io.horizontalsystems.walletkit.core.imageUrl
import io.horizontalsystems.walletkit.core.isNative
import io.horizontalsystems.walletkit.ui.compose.ComposeAppTheme
import io.horizontalsystems.walletkit.ui.compose.components.CoinImage
import io.horizontalsystems.walletkit.ui.compose.components.HSpacer
import io.horizontalsystems.walletkit.ui.compose.components.HsDivider
import io.horizontalsystems.walletkit.ui.compose.components.HsImage
import io.horizontalsystems.walletkit.ui.compose.components.ListEmptyView
import io.horizontalsystems.walletkit.ui.compose.components.HSCircularProgressIndicator
import io.horizontalsystems.walletkit.ui.compose.components.HsSwitch
import io.horizontalsystems.walletkit.ui.compose.components.ButtonPrimaryYellow
import io.horizontalsystems.walletkit.ui.compose.components.VSpacer
import io.horizontalsystems.walletkit.ui.compose.components.subhead2_leah
import io.horizontalsystems.walletkit.uiv3.components.BoxBordered
import io.horizontalsystems.walletkit.uiv3.components.HSScaffold
import io.horizontalsystems.walletkit.uiv3.components.bottom.BottomSearchBar
import io.horizontalsystems.walletkit.uiv3.components.cell.CellMiddleInfo
import io.horizontalsystems.walletkit.uiv3.components.cell.CellPrimary
import io.horizontalsystems.walletkit.uiv3.components.cell.CellRightInfo
import io.horizontalsystems.walletkit.uiv3.components.cell.hs
import io.horizontalsystems.walletkit.uiv3.components.section.SectionHeaderColored
import io.horizontalsystems.marketkit.models.Token

@Composable
fun SelectSwapCoinDialogScreen(
    title: String,
    uiState: SwapSelectCoinUiState,
    onSearchTextChanged: (String) -> Unit,
    onClose: () -> Unit,
    onRecordRecent: (CoinBalanceItem) -> Unit,
    onSetDiscoveredTokenEnabled: (Token, Boolean) -> Unit,
    onAddTokenManually: () -> Unit,
    onClickItem: (CoinBalanceItem) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    HSScaffold(
        title = title,
        onBack = onClose,
    ) {
        val isSearching = searchQuery.isNotBlank()

        // Tokens picked while the search field is active are remembered as recent.
        val onClick: (CoinBalanceItem) -> Unit = { coinItem ->
            if (isSearchActive) onRecordRecent(coinItem)
            onClickItem(coinItem)
        }

        if (isSearching && uiState.searchResults.isEmpty() && uiState.discoveredTokens.isEmpty() && !uiState.discoveryLoading) {
            ListEmptyView(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ComposeAppTheme.colors.lawrence),
                text = stringResource(R.string.EmptyResults),
                icon = R.drawable.ic_not_available
            )
            ManualAddTokenButton(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 88.dp),
                onClick = onAddTokenManually,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .background(ComposeAppTheme.colors.lawrence)
            ) {
                if (isSearching) {
                    if (uiState.discoveryLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillParentMaxSize()
                                    .padding(bottom = 88.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    HSCircularProgressIndicator()
                                    VSpacer(12.dp)
                                    subhead2_leah(text = stringResource(R.string.Swap_SearchingTokenNetworks))
                                }
                            }
                        }
                    } else {
                        itemsIndexed(uiState.searchResults) { index, coinItem ->
                            CoinCell(coinItem, onClick, top = index == 0)
                        }
                        if (uiState.discoveredTokens.isNotEmpty()) {
                            item {
                                SectionHeaderColored(title = stringResource(R.string.Swap_DiscoveredTokens))
                            }
                            itemsIndexed(uiState.discoveredTokens) { index, item ->
                                DiscoveredTokenCell(
                                    item = item,
                                    top = index == 0,
                                    onEnabledChange = { enabled ->
                                        onSetDiscoveredTokenEnabled(item.token, enabled)
                                    },
                                    onClick = {
                                        if (item.enabled) {
                                            onClick(CoinBalanceItem(item.token, null, null))
                                        } else {
                                            onSetDiscoveredTokenEnabled(item.token, true)
                                        }
                                    },
                                )
                            }
                        }
                        item {
                            ManualAddTokenButton(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                                onClick = onAddTokenManually,
                            )
                        }
                    }
                } else if (isSearchActive) {
                    // Search active with empty input — show recently picked tokens.
                    if (uiState.recent.isNotEmpty()) {
                        item {
                            SectionHeaderColored(title = stringResource(R.string.Swap_RecentSearch))
                        }
                        itemsIndexed(uiState.recent) { index, coinItem ->
                            CoinCell(coinItem, onClick, top = index == 0)
                        }
                    }
                } else {
                    if (uiState.popular.isNotEmpty()) {
                        item {
                            SectionHeaderColored(
                                title = stringResource(R.string.Swap_PopularTokens)
                            )
                        }
                        item {
                            PopularTokensRow(uiState.popular, onClick)
                        }
                    }
                    if (uiState.yourTokens.isNotEmpty()) {
                        item {
                            SectionHeaderColored(title = stringResource(R.string.Swap_YourTokens))
                        }
                        itemsIndexed(uiState.yourTokens) { index, coinItem ->
                            CoinCell(coinItem, onClick, top = index == 0)
                        }
                    }
                    if (uiState.topTokens.isNotEmpty()) {
                        item {
                            SectionHeaderColored(title = stringResource(R.string.Swap_TopTokens))
                        }
                        itemsIndexed(uiState.topTokens) { index, coinItem ->
                            CoinCell(coinItem, onClick, top = index == 0)
                        }
                    }
                }
                item {
                    VSpacer(height = 88.dp)
                }
            }
        }

        BottomSearchBar(
            searchQuery = searchQuery,
            isSearchActive = isSearchActive,
            onActiveChange = { isSearchActive = it },
            onSearchQueryChange = { q ->
                searchQuery = q
                onSearchTextChanged(q)
            },
        )
    }
}

@Composable
private fun ManualAddTokenButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    ButtonPrimaryYellow(
        modifier = modifier.fillMaxWidth(),
        title = stringResource(R.string.Swap_AddTokenManually),
        onClick = onClick,
    )
}

@Composable
private fun DiscoveredTokenCell(
    item: DiscoveredSwapToken,
    top: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    BoxBordered(top = top, bottom = true) {
        CellPrimary(
            left = {
                CoinImage(token = item.token, modifier = Modifier.size(IconSizes.Token))
            },
            middle = {
                CellMiddleInfo(
                    title = item.token.coin.code.hs,
                    subtitle = "${item.token.coin.name} · ${item.token.blockchain.name}".hs,
                )
            },
            right = {
                HsSwitch(
                    checked = item.enabled,
                    onCheckedChange = onEnabledChange,
                )
            },
            onClick = onClick,
        )
    }
}

@Composable
private fun PopularTokensRow(
    items: List<CoinBalanceItem>,
    onClickItem: (CoinBalanceItem) -> Unit,
) {
    HsDivider()
    LazyRow(
        modifier = Modifier.height(62.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(items) { coinItem ->
            PopularTokenChip(coinItem) { onClickItem.invoke(coinItem) }
        }
    }
    HsDivider()
}

@Composable
private fun PopularTokenChip(
    coinItem: CoinBalanceItem,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(ComposeAppTheme.colors.blade)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TokenIconWithBadge(coinItem.token)
        HSpacer(8.dp)
        subhead2_leah(text = coinItem.token.coin.code)
    }
}

@Composable
private fun TokenIconWithBadge(token: Token) {
    Box(
        modifier = Modifier.size(24.dp),
    ) {
        if (token.type.isNative) {
            CoinImage(
                token = token,
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(ComposeAppTheme.colors.white)
                    .size(20.dp)
            )
        } else {
            CoinImage(
                token = token,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .clip(CircleShape)
                    .background(ComposeAppTheme.colors.white)
                    .size(20.dp)
            )
            val badgeShape = RoundedCornerShape(2.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 0.5.dp, end = 0.5.dp)
                    .size(11.dp)
                    .clip(badgeShape)
                    .background(ComposeAppTheme.colors.blade)
            )
            HsImage(
                url = token.blockchainType.imageUrl,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(10.dp)
                    .clip(badgeShape),
            )
        }
    }
}

@Composable
private fun CoinCell(
    coinItem: CoinBalanceItem,
    onClickItem: (CoinBalanceItem) -> Unit,
    top: Boolean = false,
) {
    BoxBordered(top = top, bottom = true) {
        CellPrimary(
            left = {
                CoinImage(
                    token = coinItem.token,
                    modifier = Modifier.size(IconSizes.Token)
                )
            },
            middle = {
                CellMiddleInfo(
                    title = coinItem.token.coin.code.hs,
                    badge = coinItem.token.badge?.hs,
                    subtitle = coinItem.token.coin.name.hs,
                )
            },
            right = {
                CellRightInfo(
                    title = coinItem.balance?.let {
                        App.numberFormatter.formatCoinShort(
                            it,
                            coinItem.token.coin.code,
                            8
                        ).hs
                    } ?: "".hs,
                    subtitle = coinItem.fiatBalanceValue?.let { fiatBalanceValue ->
                        App.numberFormatter.formatFiatShort(
                            fiatBalanceValue.value,
                            fiatBalanceValue.currency.symbol,
                            2
                        ).hs
                    } ?: "".hs
                )
            },
            onClick = { onClickItem.invoke(coinItem) },
        )
    }
}

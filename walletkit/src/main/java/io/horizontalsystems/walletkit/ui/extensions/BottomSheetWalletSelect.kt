package io.horizontalsystems.walletkit.ui.extensions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Velocity
import io.horizontalsystems.walletkit.R
import io.horizontalsystems.walletkit.entities.Account
import io.horizontalsystems.walletkit.ui.compose.ComposeAppTheme
import io.horizontalsystems.walletkit.ui.compose.components.CellUniversalLawrenceSection
import io.horizontalsystems.walletkit.ui.compose.components.HeaderText
import io.horizontalsystems.walletkit.ui.compose.components.HsRadioButton
import io.horizontalsystems.walletkit.ui.compose.components.RowUniversal
import io.horizontalsystems.walletkit.ui.compose.components.headline2_leah
import io.horizontalsystems.walletkit.ui.compose.components.subhead2_grey
import io.horizontalsystems.walletkit.uiv3.components.bottom.BottomSearchBar

@Composable
fun WalletSwitchBottomSheet(
    wallets: List<Account>,
    watchingAddresses: List<Account>,
    selectedAccount: Account?,
    searchQuery: String,
    isSearchActive: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onSelectListener: (Account) -> Unit,
    onCancelClick: () -> Unit
) {
    val comparator = compareBy<Account> { it.name.lowercase() }
    val lazyListState = rememberLazyListState()
    val listBoundaryConnection = remember(lazyListState) {
        object : NestedScrollConnection {
            private fun isAtBoundary(deltaY: Float): Boolean =
                (deltaY < 0 && !lazyListState.canScrollForward) ||
                    (deltaY > 0 && !lazyListState.canScrollBackward)

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (isAtBoundary(available.y)) Offset(0f, available.y) else Offset.Zero

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset =
                if (isAtBoundary(available.y)) Offset(0f, available.y) else Offset.Zero

            override suspend fun onPreFling(available: Velocity): Velocity =
                if (isAtBoundary(available.y)) Velocity(0f, available.y) else Velocity.Zero

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                if (isAtBoundary(available.y)) Velocity(0f, available.y) else Velocity.Zero
        }
    }

    LaunchedEffect(lazyListState.isScrollInProgress) {
        if (lazyListState.isScrollInProgress && isSearchActive) {
            onSearchActiveChange(false)
        }
    }

    BottomSheetHeader(
        iconPainter = painterResource(R.drawable.icon_24_lock),
        iconTint = ColorFilter.tint(ComposeAppTheme.colors.jacob),
        title = stringResource(R.string.ManageAccount_SwitchWallet_Title),
        onCloseClick = onCancelClick,
        modifier = Modifier.fillMaxHeight(0.9f),
        scrollable = false,
    ) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(listBoundaryConnection),
                state = lazyListState,
            ) {
                item {
                    Spacer(Modifier.height(12.dp))
                }

                if (wallets.isNotEmpty()) {
                    item {
                        HeaderText(
                            text = stringResource(R.string.ManageAccount_Wallets)
                        )
                        Section(
                            items = wallets.sortedWith(comparator),
                            selectedItem = selectedAccount,
                            onSelectListener = onSelectListener,
                        )
                    }
                }

                if (watchingAddresses.isNotEmpty()) {
                    item {
                        if (wallets.isNotEmpty()) {
                            Spacer(Modifier.height(24.dp))
                        }
                        HeaderText(
                            text = stringResource(R.string.ManageAccount_WatchAddresses)
                        )
                        Section(
                            items = watchingAddresses.sortedWith(comparator),
                            selectedItem = selectedAccount,
                            onSelectListener = onSelectListener,
                        )
                    }
                }

                item {
                    Spacer(Modifier.height(96.dp))
                }
            }

            BottomSearchBar(
                searchQuery = searchQuery,
                isSearchActive = isSearchActive,
                onActiveChange = onSearchActiveChange,
                onSearchQueryChange = onSearchQueryChange,
            )
        }
    }
}

@Composable
private fun Section(
    items: List<Account>,
    selectedItem: Account?,
    onSelectListener: (Account) -> Unit,
) {
    CellUniversalLawrenceSection(items, showFrame = true) { item ->
        RowUniversal(
            modifier = Modifier.padding(horizontal = 16.dp),
            onClick = {
                onSelectListener.invoke(item)
            },
        ) {
            HsRadioButton(
                selected = item == selectedItem,
                onClick = {
                    onSelectListener.invoke(item)
                }
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                headline2_leah(text = item.name)
                subhead2_grey(text = item.type.detailedDescription)
            }
            if (item.isWatchAccount) {
                Icon(
                    modifier = Modifier.padding(start = 16.dp),
                    painter = painterResource(id = R.drawable.ic_eye_20),
                    contentDescription = null,
                    tint = ComposeAppTheme.colors.grey
                )
            }
        }
    }
}

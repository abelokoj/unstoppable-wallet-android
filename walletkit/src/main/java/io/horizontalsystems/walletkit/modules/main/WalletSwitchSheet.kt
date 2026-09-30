package io.horizontalsystems.walletkit.modules.main

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import io.horizontalsystems.walletkit.core.stats.StatEntity
import io.horizontalsystems.walletkit.core.stats.StatEvent
import io.horizontalsystems.walletkit.core.stats.StatPage
import io.horizontalsystems.walletkit.core.stats.stat
import io.horizontalsystems.walletkit.modules.nav3.HSNavigation
import io.horizontalsystems.walletkit.ui.extensions.HSBottomSheet
import io.horizontalsystems.walletkit.ui.extensions.WalletSwitchBottomSheet
import io.horizontalsystems.walletkit.uiv3.components.bottomsheet.BottomSheetContent
import kotlinx.serialization.Serializable

@Serializable
data object WalletSwitchSheet : HSBottomSheet() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        WalletSwitchScreen(navigation)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WalletSwitchScreen(navigation: HSNavigation) {
    val viewModel = viewModel<WalletSwitchViewModel>(factory = WalletSwitchViewModel.Factory())
    val uiState = viewModel.uiState
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    BottomSheetContent(
        onDismissRequest = {
            navigation.removeLastOrNull()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        WalletSwitchBottomSheet(
            wallets = uiState.wallets,
            watchingAddresses = uiState.watchWallets,
            selectedAccount = uiState.activeWallet,
            searchQuery = searchQuery,
            isSearchActive = isSearchActive,
            onSearchQueryChange = {
                searchQuery = it
                viewModel.updateFilter(it)
            },
            onSearchActiveChange = { isSearchActive = it },
            onSelectListener = { account ->
                viewModel.onSelect(account)
                navigation.removeLastOrNull()

                stat(
                    page = StatPage.SwitchWallet,
                    event = StatEvent.Select(StatEntity.Wallet)
                )
            },
            onCancelClick = {
                navigation.removeLastOrNull()
            }
        )
    }
}

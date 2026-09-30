package io.horizontalsystems.walletkit.modules.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.horizontalsystems.walletkit.core.App
import io.horizontalsystems.walletkit.core.IAccountManager
import io.horizontalsystems.walletkit.core.ViewModelUiState
import io.horizontalsystems.walletkit.entities.Account

class WalletSwitchViewModel(
    private val accountManager: IAccountManager
) : ViewModelUiState<WalletSwitchViewModel.UiState>() {

    private var searchQuery = ""

    private val wallets: List<Account>
        get() = accountManager.accounts.filter { !it.isWatchAccount && matchesSearch(it) }

    private val watchWallets: List<Account>
        get() = accountManager.accounts.filter { it.isWatchAccount && matchesSearch(it) }

    private val activeWallet: Account?
        get() = accountManager.activeAccount

    override fun createState() = UiState(
        wallets = wallets,
        watchWallets = watchWallets,
        activeWallet = activeWallet
    )

    fun onSelect(account: Account) {
        accountManager.setActiveAccountId(account.id)
    }

    fun updateFilter(query: String) {
        searchQuery = query
        emitState()
    }

    private fun matchesSearch(account: Account): Boolean =
        searchQuery.isBlank() || account.name.contains(searchQuery.trim(), ignoreCase = true)

    data class UiState(
        val wallets: List<Account>,
        val watchWallets: List<Account>,
        val activeWallet: Account?
    )

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return WalletSwitchViewModel(App.accountManager) as T
        }
    }
}

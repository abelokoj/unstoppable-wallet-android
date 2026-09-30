package io.horizontalsystems.walletkit.modules.contacts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.horizontalsystems.walletkit.core.App
import io.horizontalsystems.walletkit.modules.contacts.model.addressFor
import io.horizontalsystems.marketkit.models.BlockchainType

class ChooseContactViewModel(
    private val repository: ContactsRepository,
    private val blockchainType: BlockchainType
) : ViewModel() {

    var items: List<ContactViewItem> by mutableStateOf(listOf())
        private set

    private var query: String? = null

    init {
        rebuildItems()
    }

    fun onEnterQuery(query: String?) {
        this.query = query

        rebuildItems()
    }

    private fun rebuildItems() {
        items = repository.getContactsFiltered(blockchainType, query)
            .mapNotNull { contact ->
                val address = contact.addressFor(blockchainType) ?: return@mapNotNull null
                ContactViewItem(
                    contact.name,
                    address.address
                )
            }
    }

    class Factory(private val blockchainType: BlockchainType) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChooseContactViewModel(App.contactsRepository, blockchainType) as T
        }
    }
}

data class ContactViewItem(val name: String, val address: String)

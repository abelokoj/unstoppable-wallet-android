package io.horizontalsystems.walletkit.modules.contacts.model

import io.horizontalsystems.marketkit.models.Blockchain
import io.horizontalsystems.marketkit.models.BlockchainType
import io.horizontalsystems.walletkit.core.managers.EvmBlockchainManager
import kotlinx.serialization.Serializable
import java.util.Objects

@Serializable
data class Contact(
    val uid: String,
    val name: String,
    val addresses: List<ContactAddress>
)

fun Contact.addressFor(blockchainType: BlockchainType): ContactAddress? {
    return addresses.firstOrNull { it.blockchain.type == blockchainType }
        ?: addresses.firstOrNull { it.isCompatibleWith(blockchainType) }
}

@Serializable
data class ContactAddress(
    val blockchain: Blockchain,
    val address: String
) {
    fun isCompatibleWith(blockchainType: BlockchainType): Boolean {
        return blockchain.type == blockchainType ||
                (blockchain.type.isEvm() && blockchainType.isEvm())
    }

    override fun equals(other: Any?): Boolean {
        return other is ContactAddress && other.blockchain == blockchain && other.address.equals(address, ignoreCase = true)
    }

    override fun hashCode(): Int {
        return Objects.hash(blockchain, address.lowercase())
    }
}

fun BlockchainType.isEvm(): Boolean = EvmBlockchainManager.blockchainTypes.contains(this)

data class ContactNameAddress(
    val name: String,
    val contactAddress: ContactAddress
)

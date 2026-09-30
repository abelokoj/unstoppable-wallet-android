package io.horizontalsystems.walletkit.modules.contacts

import io.horizontalsystems.marketkit.models.Blockchain
import io.horizontalsystems.marketkit.models.BlockchainType
import io.horizontalsystems.walletkit.modules.contacts.model.Contact
import io.horizontalsystems.walletkit.modules.contacts.model.ContactAddress
import io.horizontalsystems.walletkit.modules.contacts.model.addressFor
import io.horizontalsystems.walletkit.modules.contacts.model.isEvm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactAddressCompatibilityTest {

    private val ethereum = Blockchain(BlockchainType.Ethereum, "Ethereum", null)
    private val base = Blockchain(BlockchainType.Base, "Base", null)
    private val bitcoin = Blockchain(BlockchainType.Bitcoin, "Bitcoin", null)

    @Test
    fun `supported EVM chains share the same contact address namespace`() {
        assertTrue(BlockchainType.Ethereum.isEvm())
        assertTrue(BlockchainType.BinanceSmartChain.isEvm())
        assertTrue(BlockchainType.ArbitrumOne.isEvm())
        assertTrue(BlockchainType.Base.isEvm())
    }

    @Test
    fun `non EVM chains remain isolated contact address namespaces`() {
        assertFalse(BlockchainType.Bitcoin.isEvm())
        assertFalse(BlockchainType.Solana.isEvm())
        assertFalse(BlockchainType.Tron.isEvm())
    }

    @Test
    fun `contact saved on one EVM chain is available on another EVM chain`() {
        val ethereumAddress = ContactAddress(ethereum, "0x123")
        val contact = Contact("id", "Alice", listOf(ethereumAddress))

        assertEquals(ethereumAddress, contact.addressFor(BlockchainType.Base))
    }

    @Test
    fun `exact chain address wins when contact also has another EVM address`() {
        val ethereumAddress = ContactAddress(ethereum, "0x123")
        val baseAddress = ContactAddress(base, "0x456")
        val contact = Contact("id", "Alice", listOf(ethereumAddress, baseAddress))

        assertEquals(baseAddress, contact.addressFor(BlockchainType.Base))
    }

    @Test
    fun `contact saved on a non EVM chain is not shared with other chains`() {
        val contact = Contact("id", "Alice", listOf(ContactAddress(bitcoin, "bc1q123")))

        assertNull(contact.addressFor(BlockchainType.Solana))
        assertNull(contact.addressFor(BlockchainType.Ethereum))
    }
}

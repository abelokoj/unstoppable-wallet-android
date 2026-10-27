package io.horizontalsystems.walletkit.core.managers

import io.horizontalsystems.walletkit.core.ITorManager
import io.horizontalsystems.walletkit.modules.settings.privacy.tor.TorStatus
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Proxy

class TorSocksProxyTest {
    private class Tor : ITorManager {
        override var isTorEnabled = true
        override val torStatusFlow = MutableStateFlow(TorStatus.Connected)
        override val bootstrapProgressFlow = MutableStateFlow<Int?>(null)
        override fun start() = Unit
        override suspend fun stop() = true
        override fun setTorAsEnabled() { isTorEnabled = true }
        override fun setTorAsDisabled() { isTorEnabled = false }
        override suspend fun newCircuits() = Unit
    }

    private fun withProperties(block: () -> Unit) {
        val keys = listOf("socksProxyHost", "socksProxyPort")
        val saved = keys.associateWith(System::getProperty)
        try {
            System.setProperty("socksProxyHost", "127.0.0.1")
            System.setProperty("socksProxyPort", "9050")
            block()
        } finally {
            saved.forEach { (key, value) ->
                if (value == null) System.clearProperty(key) else System.setProperty(key, value)
            }
        }
    }

    @Test
    fun `connected proxy uses the new port after a restart`() = withProperties {
        val tor = Tor()
        assertEquals(Proxy.Type.SOCKS, tor.requireSocksProxy().type())
        assertEquals(9050, (tor.requireSocksProxy().address() as InetSocketAddress).port)
        System.setProperty("socksProxyPort", "19050")
        assertEquals(19050, (tor.requireSocksProxy().address() as InetSocketAddress).port)
    }

    @Test
    fun `unready or disabled Tor refuses the request`() = withProperties {
        val tor = Tor()
        tor.isTorEnabled = false
        assertThrows(IllegalStateException::class.java) { tor.requireSocksProxy() }
        tor.isTorEnabled = true
        tor.torStatusFlow.value = TorStatus.Connecting
        assertThrows(IllegalStateException::class.java) { tor.requireSocksProxy() }
    }

    @Test
    fun `missing invalid blocked or remote proxy refuses the request`() = withProperties {
        val tor = Tor()
        for (port in listOf(null, "invalid", "-1", "0", "1", "65536")) {
            if (port == null) System.clearProperty("socksProxyPort") else System.setProperty("socksProxyPort", port)
            assertThrows(IllegalStateException::class.java) { tor.requireSocksProxy() }
        }
        System.setProperty("socksProxyPort", "9050")
        System.setProperty("socksProxyHost", "192.0.2.1")
        assertThrows(IllegalStateException::class.java) { tor.requireSocksProxy() }
    }
}

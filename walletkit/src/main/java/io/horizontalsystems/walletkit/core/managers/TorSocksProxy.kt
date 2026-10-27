package io.horizontalsystems.walletkit.core.managers

import io.horizontalsystems.walletkit.core.ITorManager
import io.horizontalsystems.walletkit.modules.settings.privacy.tor.TorStatus
import java.net.InetSocketAddress
import java.net.Proxy

fun ITorManager.requireSocksProxy(): Proxy {
    check(isTorEnabled && torStatusFlow.value == TorStatus.Connected) { "Wait for Tor to connect" }
    val host = System.getProperty("socksProxyHost")
    val port = System.getProperty("socksProxyPort")?.toIntOrNull()
    check(host == "127.0.0.1" && port != null && port in 2..65535) { "Tor proxy is unavailable" }
    return Proxy(Proxy.Type.SOCKS, InetSocketAddress(host, port))
}

package io.horizontalsystems.walletkit.modules.multiswap.providers

import io.horizontalsystems.walletkit.core.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicInteger

/** Kotlin AMM routing uses the wallet's read-only RPC transport and global Tor setting. */
internal object PancakeAmmRouting {
    private val slots = Semaphore(2)

    suspend fun quote(chain: Long, input: String, output: String, amount: BigInteger,
                      rpc: PancakeCrossChainNetwork.Rpc): PancakeAmmQuote = slots.withPermit {
        withTimeout(120_000) {
            val tor = App.torKitManager.isTorEnabled
            val pools = PancakeCrossChainNetwork.request {
                it.pools(chain, input, output, protocols = if (chain == 56L) "stable,v2,v3,infinityCl,infinityBin" else "v2,v3")
            }.getAsJsonArray("data")
            require(pools != null && pools.size() in 1..512 && pools.toString().length <= 8_000_000) { "Invalid PancakeSwap pool catalog" }
            val gasPrice = rpc.gasPrice()
            val quote = withContext(Dispatchers.Default) {
                val requests = AtomicInteger()
                PancakeNativeQuoteEngine { call ->
                    require(requests.incrementAndGet() <= 256) { "Routing read budget exceeded" }
                    rpc.read(call)
                }.quote(chain, input, output, amount, gasPrice, pools)
            }
            check(tor == App.torKitManager.isTorEnabled) { "Privacy setting changed while quoting" }
            quote
        }
    }
}

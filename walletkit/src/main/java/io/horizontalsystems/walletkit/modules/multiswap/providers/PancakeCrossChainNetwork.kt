package io.horizontalsystems.walletkit.modules.multiswap.providers

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.horizontalsystems.marketkit.models.BlockchainType
import io.horizontalsystems.walletkit.core.App
import io.horizontalsystems.walletkit.core.managers.requireSocksProxy
import io.horizontalsystems.walletkit.modules.settings.privacy.tor.TorStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Credentials
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

internal object PancakeNetworks {
    const val SOLANA = 8000001001L
    const val SOL = "11111111111111111111111111111111"
    const val WSOL = "So11111111111111111111111111111111111111112"
    val ids = mapOf(BlockchainType.Ethereum to 1L, BlockchainType.BinanceSmartChain to 56L,
        BlockchainType.Base to 8453L, BlockchainType.ArbitrumOne to 42161L, BlockchainType.ZkSync to 324L,
        BlockchainType.Solana to SOLANA)
    fun blockchain(id: Long) = requireNotNull(ids.entries.find { it.value == id }?.key)
    fun apiName(id: Long) = if (id == SOLANA) "sol" else PancakeCrossChainTransaction.chain(id).apiName
}

internal object PancakeCrossChainNetwork {
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).callTimeout(45, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false)
        .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", "OpenSwap").build()) }
        .build()
    private fun api(tor: Boolean): PancakeCrossChainApi = Retrofit.Builder()
        .baseUrl("https://cross-chain.pancakeswap.com/gateway/api/")
        .client(client.newBuilder().apply { if (tor) proxy(App.torKitManager.requireSocksProxy()) }.build())
        .addConverterFactory(GsonConverterFactory.create()).build().create(PancakeCrossChainApi::class.java)
    private val directApi by lazy { api(false) }

    suspend fun <T> request(block: suspend (PancakeCrossChainApi) -> T): T {
        val tor = App.torKitManager.isTorEnabled
        if (tor && App.torKitManager.torStatusFlow.value != TorStatus.Connected) throw RetryableSwapError()
        val result = try {
            block(if (tor) api(true) else directApi)
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) {
            // Network errors can contain credential-bearing custom RPC URLs. Keep them out of route logs.
            throw IllegalStateException("PancakeSwap network request failed")
        }
        check(tor == App.torKitManager.isTorEnabled && (!tor || App.torKitManager.torStatusFlow.value == TorStatus.Connected))
        return result
    }

    class Rpc(private val chain: Long) {
        private val source = requireNotNull(App.evmSyncSourceManager.getHttpSyncSource(PancakeNetworks.blockchain(chain)))
        private val url = source.uri.toString()
        private val auth = source.auth?.let { Credentials.basic("", it) }
        private val lock = Mutex()
        private var checked = false

        private suspend fun raw(method: String, params: JsonArray): JsonElement {
            require(App.evmSyncSourceManager.getHttpSyncSource(PancakeNetworks.blockchain(chain))?.uri == source.uri) { "Network provider changed" }
            val response = request { api -> api.rpc(url, auth, JsonObject().apply {
                addProperty("jsonrpc", "2.0"); addProperty("id", 1); addProperty("method", method); add("params", params)
            }) }
            require(!response.has("error") && response.get("id")?.asInt == 1) { "Network provider rejected request" }
            return requireNotNull(response.get("result")?.takeUnless { it.isJsonNull }) { "Network result unavailable" }
        }

        private suspend fun verify() = lock.withLock {
            if (!checked) {
                val id = raw("eth_chainId", JsonArray()).asString
                require(id.matches(Regex("^0x[0-9a-fA-F]{1,16}$")) && id.drop(2).toLong(16) == chain) { "Network provider chain mismatch" }
                checked = true
            }
        }

        suspend fun read(call: PancakePoolCall): String {
            verify()
            val target = PancakeCrossChainTransaction.address(call.address)
            require(call.data.matches(Regex("^0x(?:[0-9a-fA-F]{2}){4,4096}$")))
            return raw("eth_call", JsonArray().apply {
                add(JsonObject().apply { addProperty("to", target); addProperty("data", call.data) }); add("latest")
            }).asString.also { require(it.matches(Regex("^0x(?:[0-9a-fA-F]{2}){0,16384}$"))) }
        }

        suspend fun receipt(hash: String): JsonObject? {
            verify()
            require(hash.matches(Regex("^0x[0-9a-fA-F]{64}$")))
            return try { raw("eth_getTransactionReceipt", JsonArray().apply { add(hash) }).asJsonObject }
            catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        }
    }
}

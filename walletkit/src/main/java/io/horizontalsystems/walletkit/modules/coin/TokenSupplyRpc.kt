package io.horizontalsystems.walletkit.modules.coin

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.horizontalsystems.walletkit.core.App
import io.horizontalsystems.walletkit.core.managers.requireSocksProxy
import io.horizontalsystems.walletkit.modules.settings.privacy.tor.TorStatus
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.TimeUnit

/** Public token reads only; the caller supplies the wallet's currently selected RPC. */
class TokenSupplyRpc(private val url: String, private val auth: String? = null, private val sourceUnchanged: () -> Boolean) {
    private val tor = App.torKitManager.isTorEnabled
    private val api by lazy {
        Retrofit.Builder().baseUrl("https://localhost/").addConverterFactory(GsonConverterFactory.create())
            .client(client.newBuilder().apply { if (tor) proxy(App.torKitManager.requireSocksProxy()) }.build())
            .build().create(Api::class.java)
    }

    private fun checkRouting() {
        check(sourceUnchanged() && App.torKitManager.isTorEnabled == tor &&
            (!tor || App.torKitManager.torStatusFlow.value == TorStatus.Connected)) { "Network settings changed or unavailable" }
    }

    private suspend fun call(method: String, params: JsonArray = JsonArray()): JsonElement {
        currentCoroutineContext().ensureActive()
        checkRouting()
        val response = api.call(url, auth, JsonObject().apply {
            addProperty("jsonrpc", "2.0"); addProperty("id", 1); addProperty("method", method); add("params", params)
        })
        currentCoroutineContext().ensureActive()
        checkRouting()
        require(!response.has("error") && response.get("id")?.asString == "1")
        return requireNotNull(response.get("result")?.takeUnless { it.isJsonNull })
    }

    suspend fun evmSupply(address: String, decimals: Int, chainId: Long): BigDecimal {
        require(address.matches(Regex("^0x[0-9a-fA-F]{40}$")) && address.drop(2).any { it != '0' })
        val chain = call("eth_chainId").asString
        require(chain.matches(Regex("^0x[0-9a-fA-F]{1,16}$")) && chain.drop(2).toBigInteger(16) == chainId.toBigInteger())
        suspend fun read(selector: String) = call("eth_call", JsonArray().apply {
            add(JsonObject().apply { addProperty("to", address); addProperty("data", selector) }); add("latest")
        }).asString
        require(evmAmount(read("0x313ce567"), 0) == decimals.toBigDecimal())
        return evmAmount(read("0x18160ddd"), decimals)
    }

    suspend fun splSupply(mint: String, decimals: Int): BigDecimal {
        require(mint.matches(Regex("^[1-9A-HJ-NP-Za-km-z]{32,44}$")))
        require(call("getGenesisHash").asString == "5eykt4UsFv8P8NJdTREpY1vzqKqZKvdpKuc147dw2N9d")
        val result = call("getTokenSupply", JsonArray().apply {
            add(mint); add(JsonObject().apply { addProperty("commitment", "finalized") })
        }).asJsonObject
        return splAmount(result, decimals)
    }

    private interface Api {
        @POST suspend fun call(@Url url: String, @Header("Authorization") auth: String?, @Body body: JsonObject): JsonObject
    }

    companion object {
        private val client = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false).build()

        internal fun evmAmount(value: String, decimals: Int): BigDecimal {
            require(decimals in 0..255 && value.matches(Regex("^0x[0-9a-fA-F]{64}$")))
            return BigDecimal(value.drop(2).toBigInteger(16), decimals)
        }

        internal fun splAmount(result: JsonObject, decimals: Int): BigDecimal {
            require(decimals in 0..255)
            val value = requireNotNull(result.get("value")?.takeIf { it.isJsonObject }).asJsonObject
            val reportedDecimals = requireNotNull(value.get("decimals"))
            require(reportedDecimals.isJsonPrimitive && reportedDecimals.asJsonPrimitive.isNumber && reportedDecimals.asString == decimals.toString())
            val amount = requireNotNull(value.get("amount"))
            require(amount.isJsonPrimitive && amount.asJsonPrimitive.isString)
            val raw = amount.asString
            require(raw.length in 1..20 && raw.all { it in '0'..'9' })
            val integer = raw.toBigInteger()
            require(integer <= BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE))
            return BigDecimal(integer, decimals)
        }
    }
}

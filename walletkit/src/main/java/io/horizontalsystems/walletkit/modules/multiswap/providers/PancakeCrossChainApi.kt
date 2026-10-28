package io.horizontalsystems.walletkit.modules.multiswap.providers

import com.google.gson.JsonObject
import io.horizontalsystems.walletkit.modules.multiswap.history.SwapStatus
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Header
import retrofit2.http.Url
import java.math.BigInteger

internal interface PancakeCrossChainApi {
    @GET("v1/routes")
    suspend fun routes(): Routes

    @POST("v1/metadata")
    suspend fun quote(@Query("originChainId") origin: Long, @Query("destinationChainId") destination: Long,
                      @Query("inputToken") input: String, @Query("outputToken") output: String,
                      @Query("amount") amount: String, @Body body: JsonObject): PancakeBridgeQuote

    @POST("v1/metadata")
    suspend fun nonEvmQuote(@Query("originChainId") origin: Long, @Query("destinationChainId") destination: Long,
                           @Query("inputToken") input: String, @Query("outputToken") output: String,
                           @Query("amount") amount: String, @Body body: JsonObject): JsonObject

    @POST("v1/calldata")
    suspend fun calldata(@Body body: JsonObject): JsonObject

    @GET("https://hub-gateway.pancakeswap.com/v2/quote")
    suspend fun ammQuote(@Query("chainId") chain: Long, @Query("inputToken") input: String,
                         @Query("outputToken") output: String, @Query("amount") amount: String,
                         @Query("tradeType") tradeType: String = "exactIn",
                         @Query("protocol") protocols: String = "v2,v3,stableswap,infinityCl,infinityBin",
                         @Query("maxSplits") splits: Int = 3,
                         @Query("swapFeeBps") fee: Int = 0): JsonObject

    @POST
    suspend fun rpc(@Url url: String, @Header("Authorization") auth: String?, @Body request: JsonObject): JsonObject

    @GET("v1/status/{chain}")
    suspend fun rawStatus(@Path("chain") chain: String, @Query("txHash") hash: String,
                           @Query("type") type: String): JsonObject

    @GET("v1/status/{chain}")
    suspend fun status(@Path("chain") chain: String, @Query("txHash") hash: String,
                       @Query("type") type: String = "EVM"): PancakeBridgeStatus

    data class Routes(val routes: List<Route> = emptyList())
    data class Route(val originChainId: Long, val destinationChainId: Long,
                     val originToken: String, val destinationToken: String)
}

internal data class PancakeBridgeStatus(
    val status: String? = null,
    val originChainId: Long? = null,
    val destinationChainId: Long? = null,
    val transactionId: String? = null,
    val inputToken: String? = null,
    val outputToken: String? = null,
    val inputAmount: String? = null,
    val outputAmount: String? = null,
    val data: List<Command>? = null,
) {
    data class Command(val command: String? = null, val status: CommandStatus? = null, val metadata: Metadata? = null)
    data class CommandStatus(val code: String? = null)
    data class Metadata(val originChainId: Long? = null, val destinationChainId: Long? = null,
                        val bridgeStatus: String? = null, val depositTxHash: String? = null,
                        val fillTx: String? = null, val depositRefundTxHash: String? = null,
                        val chainId: Long? = null, val tx: String? = null)

    val outboundHash: String?
        get() {
            val chain = destinationChainId ?: return null
            val swapHash = data?.lastOrNull { it.command == "SWAP" && it.metadata?.chainId == chain }?.metadata?.tx
            return (swapHash ?: data?.singleOrNull { it.command == "BRIDGE" }?.metadata?.fillTx)?.takeIf { isHash(it, chain) }
        }

    fun validatedStatus(hash: String, origin: Long, destination: Long, input: String, output: String,
                        amount: BigInteger, minimumOutput: BigInteger): SwapStatus? {
        require(isHash(hash, origin) && transactionId.equals(hash, origin != PancakeNetworks.SOLANA)) {
            "Bridge status changed the transaction"
        }
        require(originChainId == origin && destinationChainId == destination)
        require(PancakeNonEvmQuote.sameAsset(origin, input, inputToken) && PancakeNonEvmQuote.sameAsset(destination, output, outputToken))
        require(PancakeCrossChainTransaction.uint(inputAmount) == amount)
        val commands = data?.takeIf { it.isNotEmpty() } ?: return null
        require(commands.all { it.command == "BRIDGE" || it.command == "SWAP" } && commands.size in 1..3)
        val bridge = commands.singleOrNull { it.command == "BRIDGE" } ?: return null
        val meta = bridge.metadata ?: return null
        require(meta.originChainId == origin && meta.destinationChainId == destination && meta.depositTxHash.equals(hash, origin != PancakeNetworks.SOLANA))
        if (meta.depositRefundTxHash?.takeIf { isHash(it, origin) } != null) return SwapStatus.Refunded
        val bridgeIndex = commands.indexOf(bridge)
        require(bridgeIndex <= 1 && commands.size - bridgeIndex - 1 <= 1)
        require(commands.take(bridgeIndex).all { it.command == "SWAP" && it.metadata?.chainId == origin })
        require(commands.drop(bridgeIndex + 1).all { it.command == "SWAP" && it.metadata?.chainId == destination })
        return when (status?.uppercase()) {
            "SUCCESS" -> if (commands.all { it.status?.code?.uppercase() == "SUCCESS" } && meta.bridgeStatus == "filled" && outboundHash != null) {
                if (PancakeCrossChainTransaction.uint(outputAmount) >= minimumOutput) SwapStatus.Completed else SwapStatus.ActionRequired
            } else null
            "PARTIAL_SUCCESS", "FAILED" -> SwapStatus.ActionRequired
            "PENDING", "BRIDGE_PENDING" -> SwapStatus.Swapping
            else -> null
        }
    }

    private fun isHash(value: String, chain: Long) = if (chain == PancakeNetworks.SOLANA)
        value.matches(Regex("^[1-9A-HJ-NP-Za-km-z]{64,88}$")) else value.matches(Regex("^0x[0-9a-fA-F]{64}$"))
}

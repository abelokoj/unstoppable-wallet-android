package io.horizontalsystems.walletkit.modules.multiswap.providers

import com.google.gson.JsonObject
import io.horizontalsystems.walletkit.modules.multiswap.sendtransaction.EvmTransactionData
import java.math.BigInteger

internal data class PancakeNonEvmQuote(val raw: JsonObject, val requestId: String,
    val amountOut: BigInteger, val minimumOut: BigInteger, val estimatedTime: Long?) {
    companion object {
        fun parse(raw: JsonObject, origin: Long, destination: Long, input: String, output: String,
                  amount: BigInteger, slippageBps: Int, expected: PancakeNonEvmQuote? = null): PancakeNonEvmQuote {
            require(origin != destination && (origin == PancakeNetworks.SOLANA || destination == PancakeNetworks.SOLANA))
            require(slippageBps in 0..5000)
            if (expected == null) require(raw.get("supported")?.asBoolean == true) { "PancakeSwap route unavailable" }
            require(raw.get("originChainId")?.asLong == origin && raw.get("destinationChainId")?.asLong == destination)
            require(sameAsset(origin, input, raw.get("inputToken")?.asString) && sameAsset(destination, output, raw.get("outputToken")?.asString))
            if (expected == null) require(PancakeCrossChainTransaction.uint(raw.get("amount")?.asString) == amount)
            val id = requireNotNull(raw.get("requestId")?.asString).also { require(it.matches(Regex("^0x[0-9a-fA-F]{64}$"))) }
            if (expected != null) require(id == expected.requestId) { "PancakeSwap quote changed" }
            val bridge = requireNotNull(raw.getAsJsonObject("bridgeTransactionData"))
            val out = PancakeCrossChainTransaction.uint(bridge.get("outputAmount")?.asString)
            val min = PancakeCrossChainTransaction.uint(bridge.get("minimumOutputAmount")?.asString)
            require(out > BigInteger.ZERO && min > BigInteger.ZERO && min <= out)
            require(min >= out * BigInteger.valueOf(10000L - slippageBps) / BigInteger.valueOf(10000)) { "PancakeSwap exceeded slippage" }
            if (expected != null) require(min >= expected.minimumOut) { "PancakeSwap minimum output changed" }
            val seconds = raw.get("expectedFillTimeSec")?.asString?.toLongOrNull()?.takeIf { it > 0 }
            return PancakeNonEvmQuote(raw.deepCopy(), id, out, min, seconds)
        }
        fun sameAsset(chain: Long, expected: String, actual: String?) =
            if (chain == PancakeNetworks.SOLANA) actual == expected else expected.equals(actual, true)
    }
}

/** Source deposits for the Relay rail used by PancakeSwap's Solana routes. */
internal object PancakeRelayEvmTransaction {
    const val DEPOSITORY = RelayDepositTransaction.DEPOSITORY

    fun build(response: JsonObject, chain: Long, input: String, amount: BigInteger, sender: String): EvmTransactionData {
        PancakeCrossChainTransaction.chain(chain)
        val steps = requireNotNull(response.getAsJsonArray("steps")).map { it.asJsonObject }
        require(steps.isNotEmpty() && steps.all { it.get("kind")?.asString == "transaction" && it.get("id")?.asString in setOf("deposit", "approve") })
        val deposit = steps.single { it.get("id").asString == "deposit" }
        val items = requireNotNull(deposit.getAsJsonArray("items")); require(items.size() == 1)
        val tx = requireNotNull(items.single().asJsonObject.getAsJsonObject("data"))
        return RelayDepositTransaction.build(chain, input, amount, sender, RelayApi.TransactionData(
            from = requireNotNull(tx.get("from")?.asString), to = requireNotNull(tx.get("to")?.asString),
            data = requireNotNull(tx.get("data")?.asString), value = requireNotNull(tx.get("value")?.asString),
            chainId = requireNotNull(tx.get("chainId")?.asLong),
        ))
    }
}

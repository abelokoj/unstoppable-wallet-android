package io.horizontalsystems.walletkit.modules.multiswap.providers

import com.google.gson.JsonObject
import io.horizontalsystems.walletkit.core.hexStringToByteArray
import io.horizontalsystems.walletkit.modules.multiswap.sendtransaction.EvmTransactionData
import org.web3j.crypto.Hash
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
    // relayprotocol/relay-settlement, ethereum-vm/deployments/addresses.prod.json.
    const val DEPOSITORY = "0x4cd00e387622c35bddb9b4c962c136462338bc31"
    private val nativeSelector = selector("depositNative(address,bytes32)")
    private val tokenSelector = selector("depositErc20(address,address,uint256,bytes32)")
    private val allowanceSelector = selector("depositErc20(address,address,bytes32)")

    fun build(response: JsonObject, chain: Long, input: String, amount: BigInteger, sender: String): EvmTransactionData {
        PancakeCrossChainTransaction.chain(chain)
        require(amount.signum() > 0 && amount.bitLength() <= 256)
        val owner = PancakeCrossChainTransaction.address(sender).also { require(it.drop(2).toBigInteger(16) > BigInteger.valueOf(2)) }
        val token = PancakeCrossChainTransaction.address(input)
        val steps = requireNotNull(response.getAsJsonArray("steps")).map { it.asJsonObject }
        require(steps.isNotEmpty() && steps.all { it.get("kind")?.asString == "transaction" && it.get("id")?.asString in setOf("deposit", "approve") })
        val deposit = steps.single { it.get("id").asString == "deposit" }
        val items = requireNotNull(deposit.getAsJsonArray("items")); require(items.size() == 1)
        val tx = requireNotNull(items.single().asJsonObject.getAsJsonObject("data"))
        require(tx.get("chainId")?.asLong == chain && tx.get("from")?.asString.equals(owner, true))
        require(tx.get("to")?.asString.equals(DEPOSITORY, true)) { "Unrecognized Relay deposit contract" }
        val data = requireNotNull(tx.get("data")?.asString).lowercase()
        require(data.matches(Regex("^0x[0-9a-f]{136,264}$")))
        fun word(index: Int): String = data.substring(10 + index * 64, 74 + index * 64)
        val credited = word(0).also { require(it.take(24).all { c -> c == '0' }) }.toBigInteger(16)
        require(credited == BigInteger.ZERO || credited == owner.drop(2).toBigInteger(16)) { "Relay changed the depositor" }
        val valueText = requireNotNull(tx.get("value")?.asString)
        val value = if (valueText.startsWith("0x")) valueText.drop(2).ifEmpty { "0" }.toBigInteger(16)
                    else PancakeCrossChainTransaction.uint(valueText)
        val intent: String
        val rebuilt: String
        if (token == PancakeCrossChainTransaction.NATIVE) {
            require(data.length == 138 && data.startsWith(nativeSelector) && value == amount)
            intent = word(1)
            rebuilt = nativeSelector + owner.drop(2).padStart(64, '0') + intent
        } else {
            require(value == BigInteger.ZERO && word(1) == token.drop(2).padStart(64, '0'))
            when {
                data.startsWith(tokenSelector) -> {
                    require(data.length == 266 && word(2).toBigInteger(16) == amount)
                    intent = word(3)
                }
                data.startsWith(allowanceSelector) -> { require(data.length == 202); intent = word(2) }
                else -> error("Unsupported Relay deposit method")
            }
            // Always use the explicit-amount overload, even if the service requests the allowance overload.
            rebuilt = tokenSelector + owner.drop(2).padStart(64, '0') + token.drop(2).padStart(64, '0') + amount.toString(16).padStart(64, '0') + intent
        }
        require(intent.toBigInteger(16) > BigInteger.ZERO)
        return EvmTransactionData(DEPOSITORY, if (token == PancakeCrossChainTransaction.NATIVE) amount else BigInteger.ZERO,
            rebuilt.hexStringToByteArray())
    }
    private fun selector(signature: String) = Hash.sha3String(signature).take(10)
}

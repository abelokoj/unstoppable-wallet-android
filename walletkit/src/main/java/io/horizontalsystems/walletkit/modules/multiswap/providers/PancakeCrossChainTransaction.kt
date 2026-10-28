package io.horizontalsystems.walletkit.modules.multiswap.providers

import io.horizontalsystems.walletkit.modules.multiswap.sendtransaction.EvmTransactionData
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.DynamicArray
import org.web3j.abi.datatypes.DynamicBytes
import org.web3j.abi.datatypes.DynamicStruct
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Type
import org.web3j.abi.datatypes.generated.Bytes32
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.utils.Numeric
import java.math.BigDecimal
import java.math.BigInteger

internal object PancakeCrossChainTransaction {
    const val NATIVE = "0x0000000000000000000000000000000000000000"
    private const val sender = "0xE82e2D3B9DB59f7c7b438239D92E2190a64E26ce"
    private const val adapter = "0x60Ac95eC1153aA8199751917edE25D0Ca49a36e2"
    data class Chain(val apiName: String, val sender: String, val adapter: String, val wrappedNative: String)
    // Published by developer.pancakeswap.finance/contracts/crosschain/addresses.
    private val chains = mapOf(
        1L to Chain("ethereum", sender, adapter, "0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2"),
        56L to Chain("bsc", sender, adapter, "0xbb4CdB9CBd36B01bD1cBaEBF2De08d9173bc095c"),
        8453L to Chain("base", sender, adapter, "0x4200000000000000000000000000000000000006"),
        42161L to Chain("arbitrum", sender, adapter, "0x82aF49447D8a07e3bd95BD0d56f35241523fBab1"),
        324L to Chain("zksync", "0xdE167bB9F640a3D6de7b8C16C28920755f5921F2",
            "0xb456E051867625D320A7a793897058eb7eB6093B", "0x5AEa5775959fBC2557Cc8789bC1bf90A239D9a91"),
    )

    fun chain(id: Long) = requireNotNull(chains[id]) { "Unsupported PancakeSwap bridge network" }
    fun address(value: String): String {
        require(value.matches(Regex("^0x[0-9a-fA-F]{40}$"))) { "Invalid bridge address" }
        return value.lowercase()
    }
    private fun recipient(value: String): String = address(value).also {
        require(it.removePrefix("0x").toBigInteger(16) > BigInteger.valueOf(2)) { "Invalid bridge recipient" }
    }
    fun uint(value: String?): BigInteger {
        require(value != null && value.matches(Regex("[0-9]{1,78}"))) { "Invalid bridge amount" }
        return value.toBigInteger().also { require(it.bitLength() <= 256) { "Bridge amount overflow" } }
    }
    fun rawAmount(amount: BigDecimal, decimals: Int): BigInteger {
        require(decimals in 0..255 && amount > BigDecimal.ZERO)
        return uint(amount.movePointRight(decimals).toBigIntegerExact().toString()).also { require(it.signum() > 0) }
    }

    fun build(origin: Long, destination: Long, inputToken: String, outputToken: String, amount: BigInteger,
              quote: PancakeBridgeQuote.BridgeData, senderAddress: String, recipientAddress: String): EvmTransactionData {
        require(origin != destination)
        val source = chain(origin)
        val target = chain(destination)
        val input = address(inputToken)
        val output = address(outputToken)
        val refund = recipient(senderAddress)
        val receiver = recipient(recipientAddress)
        val outputAmount = uint(quote.outputAmount).also { require(it.signum() > 0) }
        require(uint(amount.toString()).signum() > 0)
        val delivery = if (output == NATIVE) {
            listOf(Command(5, encode(Address(receiver), Uint256(outputAmount))))
        } else {
            listOf(Command(2, encode(Address(output), Address(receiver), Uint256(outputAmount))))
        }
        val message = encode(DynamicArray(Command::class.java, delivery), Address(receiver))
        val acrossData = encode(Address(address(requireNotNull(quote.exclusiveRelayer))),
            Uint256(requireNotNull(quote.exclusivityDeadline)), Uint256(requireNotNull(quote.quoteTimestamp)),
            Uint256(requireNotNull(quote.fillDeadline)))
        val bridge = DynamicStruct(
            bytes32(input), bytes32(if (output == NATIVE) target.wrappedNative else output),
            Uint256(amount), Uint256(outputAmount), Address(refund), Address(source.adapter),
            DynamicBytes(acrossData), Uint256(destination), bytes32(target.adapter), DynamicBytes(message),
        )
        // shortcut: bridge-token pairs only; add independently validated AMM commands before offering swap legs.
        val order = DynamicStruct(bytes32(input), Uint256(amount),
            DynamicArray(Command::class.java, listOf(Command(1, encode(bridge)))), DynamicBytes(byteArrayOf()))
        val calldata = FunctionEncoder.encode(Function("send", listOf(order), emptyList()))
        return EvmTransactionData(source.sender, if (input == NATIVE) amount else BigInteger.ZERO,
            Numeric.hexStringToByteArray(calldata))
    }

    fun routerChecks(order: PancakeSwapOrder, serverCalldata: String): List<PancakeRouterCheck> =
        PancakeSwapCalldata.routerChecks(order, serverCalldata)

    fun buildWithSwaps(order: PancakeSwapOrder, serverCalldata: String,
                       verifiedChecks: List<PancakeRouterCheck>): EvmTransactionData =
        PancakeSwapCalldata.build(order, serverCalldata, verifiedChecks)

    private class Command(command: Long, data: ByteArray) : DynamicStruct(Uint256(command), DynamicBytes(data))
    private fun bytes32(value: String) = Bytes32(Numeric.hexStringToByteArray(address(value)).let { ByteArray(12) + it })
    private fun encode(vararg values: Type<*>) = Numeric.hexStringToByteArray(FunctionEncoder.encodeConstructor(values.toList()))
}

internal data class PancakeBridgeQuote(
    val supported: Boolean = false,
    val amount: String? = null,
    val inputToken: String? = null,
    val outputToken: String? = null,
    val originChainId: Long? = null,
    val destinationChainId: Long? = null,
    val expectedFillTimeSec: String? = null,
    val isAmountTooLow: Boolean = false,
    val limits: Limits? = null,
    val bridgeTransactionData: BridgeData? = null,
) {
    data class Limits(val minDeposit: String? = null, val maxDeposit: String? = null)
    data class BridgeData(
        val outputAmount: String? = null,
        val totalRelayFee: String? = null,
        val exclusiveRelayer: String? = null,
        val exclusivityDeadline: Long? = null,
        val quoteTimestamp: Long? = null,
        val fillDeadline: Long? = null,
        val relayerFeePct: String? = null,
    )

    fun validated(origin: Long, destination: Long, input: String, output: String, requestedAmount: BigInteger, now: Long): BridgeData {
        require(supported && !isAmountTooLow) { "PancakeSwap bridge route unavailable" }
        require(originChainId == origin && destinationChainId == destination && origin != destination)
        require(inputToken.equals(input, true) && outputToken.equals(output, true)) { "Bridge changed the requested tokens" }
        val raw = PancakeCrossChainTransaction::uint
        require(raw(amount) == requestedAmount && requestedAmount.signum() > 0) { "Bridge changed the input amount" }
        val bounds = requireNotNull(limits) { "Missing bridge limits" }
        require(requestedAmount >= raw(bounds.minDeposit) && requestedAmount <= raw(bounds.maxDeposit)) { "Amount outside bridge limits" }
        val data = requireNotNull(bridgeTransactionData) { "Missing bridge quote" }
        require(raw(data.outputAmount).signum() > 0)
        require(raw(data.totalRelayFee) < requestedAmount) { "Invalid bridge fee" }
        val quoted = requireNotNull(data.quoteTimestamp)
        val fill = requireNotNull(data.fillDeadline)
        // The service can return a cached Across timestamp several minutes behind the HTTP response.
        require(quoted in now - 300..now + 30 && fill in now + 60..now + 21600 && fill > quoted) { "Bridge quote expired" }
        val exclusive = requireNotNull(data.exclusivityDeadline)
        require(exclusive in 0..0xffffffffL && quoted in 0..0xffffffffL && fill in 0..0xffffffffL)
        require(exclusive <= 300 || exclusive in now..minOf(fill, now + 600)) { "Invalid bridge exclusivity deadline" }
        val relayer = PancakeCrossChainTransaction.address(requireNotNull(data.exclusiveRelayer))
        require(exclusive == 0L || relayer != PancakeCrossChainTransaction.NATIVE)
        return data
    }
}

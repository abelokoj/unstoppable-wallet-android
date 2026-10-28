package io.horizontalsystems.walletkit.modules.multiswap.providers

import com.google.gson.JsonObject
import io.horizontalsystems.walletkit.modules.multiswap.sendtransaction.EvmTransactionData
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Bool
import org.web3j.abi.datatypes.DynamicArray
import org.web3j.abi.datatypes.DynamicBytes
import org.web3j.abi.datatypes.DynamicStruct
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Type
import org.web3j.abi.datatypes.generated.Bytes4
import org.web3j.abi.datatypes.generated.Bytes32
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.utils.Numeric
import java.math.BigInteger

internal data class PancakeSwapExpectation(
    val chainId: Long,
    val inputToken: String,
    val outputToken: String,
    val amountIn: BigInteger,
    val minimumOut: BigInteger,
    val rawQuote: JsonObject,
)

internal data class PancakeSwapOrder(
    val origin: Long,
    val destination: Long,
    val inputToken: String,
    val outputToken: String,
    val amount: BigInteger,
    val bridgeInput: String,
    val bridgeOutput: String,
    val bridgeAmount: BigInteger,
    val bridgeData: PancakeBridgeQuote.BridgeData,
    val sourceSwap: PancakeSwapExpectation?,
    val destinationSwap: PancakeSwapExpectation?,
    val senderAddress: String,
    val recipientAddress: String,
    val now: Long,
)

internal data class PancakeRouterCheck(val chainId: Long, val custodian: String, val router: String, val calldata: String)

internal object PancakeSwapCalldata {
    private val zero = BigInteger.ZERO
    private val balance = BigInteger.ONE.shiftLeft(255)
    private val native = PancakeCrossChainTransaction.NATIVE
    private val addressThis = "0x" + "0".repeat(39) + "2"
    private val msgSender = "0x" + "0".repeat(39) + "1"
    private fun address(value: String) = PancakeCrossChainTransaction.address(value)
    private fun uint(value: String?) = PancakeCrossChainTransaction.uint(value)
    private fun chain(value: Long) = PancakeCrossChainTransaction.chain(value)
    private fun wrapped(chainId: Long, token: String) = if (address(token) == native) address(chain(chainId).wrappedNative) else address(token)
    private fun party(value: String) = address(value).also { require(it.removePrefix("0x").toBigInteger(16) > BigInteger.valueOf(2)) }
    private data class Command(val code: Int, val data: ByteArray)
    private class EncodedCommand(code: Int, data: ByteArray) : DynamicStruct(Uint256(code.toLong()), DynamicBytes(data))
    private data class Validated(val source: ByteArray?, val destination: ByteArray?, val delivery: Command, val checks: List<PancakeRouterCheck>)

    fun routerChecks(order: PancakeSwapOrder, calldata: String): List<PancakeRouterCheck> = validate(order, calldata).checks

    fun build(order: PancakeSwapOrder, calldata: String, verifiedChecks: List<PancakeRouterCheck>): EvmTransactionData {
        val validated = validate(order, calldata)
        require(verifiedChecks.toSet() == validated.checks.toSet()) { "PancakeSwap router registry was not verified" }
        val o = order
        val dst = chain(o.destination)
        val delivery = buildList {
            validated.destination?.let { add(EncodedCommand(0, it)) }
            add(EncodedCommand(validated.delivery.code, validated.delivery.data))
        }
        val message = encode(DynamicArray(EncodedCommand::class.java, delivery), Address(party(o.recipientAddress)))
        val data = o.bridgeData
        val across = encode(Address(address(requireNotNull(data.exclusiveRelayer))), Uint256(requireNotNull(data.exclusivityDeadline)),
            Uint256(requireNotNull(data.quoteTimestamp)), Uint256(requireNotNull(data.fillDeadline)))
        val bridge = DynamicStruct(bytes32(o.bridgeInput), bytes32(wrapped(o.destination, o.bridgeOutput)),
            Uint256(o.bridgeAmount), Uint256(uint(data.outputAmount)), Address(party(o.senderAddress)), Address(chain(o.origin).adapter),
            DynamicBytes(across), Uint256(o.destination), bytes32(dst.adapter), DynamicBytes(message))
        val commands = buildList {
            validated.source?.let { add(EncodedCommand(0, it)) }
            add(EncodedCommand(1, encode(bridge)))
        }
        val local = DynamicStruct(bytes32(o.inputToken), Uint256(o.amount), DynamicArray(EncodedCommand::class.java, commands), DynamicBytes(byteArrayOf()))
        val input = Numeric.hexStringToByteArray(FunctionEncoder.encode(Function("send", listOf(local), emptyList())))
        return EvmTransactionData(chain(o.origin).sender, if (address(o.inputToken) == native) o.amount else zero, input)
    }

    private fun validate(o: PancakeSwapOrder, calldata: String): Validated {
        require(o.origin != o.destination && o.amount.signum() > 0 && o.bridgeAmount.signum() > 0)
        chain(o.origin); chain(o.destination)
        party(o.senderAddress); party(o.recipientAddress)
        require(uint(o.amount.toString()) == o.amount && uint(o.bridgeAmount.toString()) == o.bridgeAmount)
        require(uint(o.bridgeData.outputAmount).signum() > 0 && uint(o.bridgeData.totalRelayFee) < o.bridgeAmount)
        require(requireNotNull(o.bridgeData.fillDeadline) in o.now + 60..o.now + 21600)
        o.sourceSwap?.let {
            require(it.chainId == o.origin && address(it.inputToken) == address(o.inputToken) && address(it.outputToken) == address(o.bridgeInput))
            require(it.amountIn == o.amount && it.minimumOut == o.bridgeAmount)
        } ?: require(address(o.inputToken) == address(o.bridgeInput) && o.amount == o.bridgeAmount)
        o.destinationSwap?.let {
            require(it.chainId == o.destination && address(it.inputToken) == address(o.bridgeOutput) && address(it.outputToken) == address(o.outputToken))
            require(it.amountIn == uint(o.bridgeData.outputAmount) && it.minimumOut.signum() > 0)
        } ?: require(address(o.bridgeOutput) == address(o.outputToken))
        val input = hex(calldata)
        require(input.take(4).toByteArray().contentEquals(hex("0x27ad57d5"))) { "Unexpected bridge entry point" }
        val abi = Abi(input.copyOfRange(4, input.size))
        val start = abi.offset(0, 0)
        require(abi.address(start) == address(o.inputToken) && abi.word(start + 32) == o.amount)
        abi.bytesField(start, 3) // Parse even discarded Permit2 data so truncated/invalid encodings cannot pass.
        val commands = abi.commands(abi.offset(start, 2))
        val expectedCodes = if (o.sourceSwap != null) listOf(0, 1) else listOf(1)
        require(commands.take(expectedCodes.size).map { it.code } == expectedCodes) { "Unexpected source commands" }
        require(commands.drop(expectedCodes.size).all { safeSweep(it, o.senderAddress, setOf(address(o.inputToken), address(o.bridgeInput)), zero) })
        val sourceData = o.sourceSwap?.let { swap(commands[0].data, it, chain(o.origin).sender, o.now + 300) }
        val bridge = Abi(commands[expectedCodes.lastIndex].data)
        val b = bridge.offset(0, 0)
        require(bridge.address(b) == address(o.bridgeInput) && bridge.address(b + 32) == wrapped(o.destination, o.bridgeOutput))
        require(bridge.word(b + 64) == o.bridgeAmount && bridge.word(b + 96) == uint(o.bridgeData.outputAmount))
        require(bridge.address(b + 128) == party(o.senderAddress) && bridge.address(b + 160) == address(chain(o.origin).adapter))
        require(bridge.word(b + 224) == BigInteger.valueOf(o.destination) && bridge.address(b + 256) == address(chain(o.destination).adapter))
        val across = Abi(bridge.bytesField(b, 6))
        require(across.data.size == 128 && across.address(0) == address(requireNotNull(o.bridgeData.exclusiveRelayer)))
        require(across.word(32) == BigInteger.valueOf(requireNotNull(o.bridgeData.exclusivityDeadline)))
        require(across.word(64) == BigInteger.valueOf(requireNotNull(o.bridgeData.quoteTimestamp)))
        require(across.word(96) == BigInteger.valueOf(requireNotNull(o.bridgeData.fillDeadline)))
        val message = Abi(bridge.bytesField(b, 9))
        require(message.address(32) == party(o.recipientAddress)) { "Bridge fallback receiver changed" }
        val destination = message.commands(message.offset(0, 0))
        val swapOffset = if (o.destinationSwap != null) 1 else 0
        require(destination.size >= swapOffset + 1)
        val destinationData = o.destinationSwap?.let {
            require(destination[0].code == 0)
            swap(destination[0].data, it, chain(o.destination).adapter, requireNotNull(o.bridgeData.fillDeadline))
        }
        val delivery = destination[swapOffset]
        val minimum = o.destinationSwap?.minimumOut ?: uint(o.bridgeData.outputAmount)
        val output = address(o.outputToken)
        val validDelivery = if (delivery.code == 5 && output == native) {
            val a = Abi(delivery.data)
            a.data.size == 64 && a.address(0) == party(o.recipientAddress) && a.word(32) == minimum
        } else safeSweep(delivery, o.recipientAddress, setOf(output), minimum)
        require(validDelivery) { "Bridge delivery changed the token, receiver or minimum output" }
        require(destination.drop(swapOffset + 1).all { safeSweep(it, o.recipientAddress,
            setOf(output, address(o.bridgeOutput), native), zero) }) { "Unexpected destination payment" }
        return Validated(sourceData?.first, destinationData?.first, delivery,
            listOfNotNull(sourceData?.second, destinationData?.second))
    }

    private fun safeSweep(command: Command, recipient: String, tokens: Set<String>, minimum: BigInteger): Boolean {
        if (command.code != 2) return false
        val a = Abi(command.data)
        return a.data.size == 96 && a.address(0) in tokens && a.address(32) == party(recipient) && a.word(64) == minimum
    }

    private fun swap(data: ByteArray, expected: PancakeSwapExpectation, custodian: String, deadline: Long): Pair<ByteArray, PancakeRouterCheck> {
        val a = Abi(data)
        val start = a.offset(0, 0)
        require(a.address(start) == address(expected.inputToken)) { "Swap changed its input token" }
        require(a.word(start + 32) == expected.amountIn || a.word(start + 32) == balance)
        require(a.word(start + 64) == BigInteger.ONE) { "Swap attempted an additional token approval" }
        val router = a.address(start + 96)
        require(router != native && router != party(custodian))
        val input = a.bytesField(start, 4)
        require(input.take(4).toByteArray().contentEquals(hex("0x3593564c"))) { "Unsupported swap entry point" }
        val routerAbi = Abi(input.copyOfRange(4, input.size))
        val codes = routerAbi.bytesField(0, 0)
        val args = routerAbi.bytesArray(routerAbi.offset(0, 1))
        RouterValidator(expected, custodian, router).validate(codes, args)
        val normalized = FunctionEncoder.encode(Function("execute", listOf(DynamicBytes(codes),
            DynamicArray(DynamicBytes::class.java, args.map(::DynamicBytes)), Uint256(deadline)), emptyList()))
        // Exact prefunding leaves any positive bridge surplus in the adapter for its automatic owner sweep.
        val swap = DynamicStruct(Address(address(expected.inputToken)), Uint256(expected.amountIn), Bool(true),
            Address(router), DynamicBytes(hex(normalized)))
        val registryCall = FunctionEncoder.encode(Function("isSwapFunctionWhitelisted", listOf(Address(router),
            Bytes4(hex("0x3593564c"))), emptyList()))
        return encode(swap) to PancakeRouterCheck(expected.chainId, address(custodian), router, registryCall)
    }

    private fun hex(value: String): ByteArray {
        require(value.startsWith("0x") && value.length % 2 == 0 && value.length <= 524290 && value.drop(2).all { it in "0123456789abcdefABCDEF" })
        return Numeric.hexStringToByteArray(value)
    }
    private fun bytes32(value: String) = Bytes32(ByteArray(12) + hex(address(value)))
    private fun encode(vararg values: Type<*>) = Numeric.hexStringToByteArray(FunctionEncoder.encodeConstructor(values.toList()))

    private class Abi(val data: ByteArray) {
        init { require(data.size <= 262144) }
        fun word(at: Int): BigInteger {
            require(at >= 0 && at <= data.size - 32) { "Invalid ABI word" }
            return BigInteger(1, data.copyOfRange(at, at + 32))
        }
        fun int(at: Int): Int = word(at).also { require(it.bitLength() < 31) }.toInt()
        fun address(at: Int): String {
            require(word(at).bitLength() <= 160) { "Invalid ABI address" }
            return "0x" + Numeric.toHexStringNoPrefix(data.copyOfRange(at + 12, at + 32))
        }
        fun offset(base: Int, index: Int): Int {
            val relative = int(Math.addExact(base, Math.multiplyExact(index, 32)))
            require(relative % 32 == 0 && relative <= data.size - base)
            return base + relative
        }
        fun bytesField(base: Int, index: Int): ByteArray {
            val start = offset(base, index)
            val length = int(start)
            require(length <= data.size - start - 32)
            val end = start + 32 + length
            val paddedEnd = start + 32 + ((length + 31) / 32) * 32
            require(paddedEnd <= data.size && data.copyOfRange(end, paddedEnd).all { it == 0.toByte() })
            return data.copyOfRange(start + 32, end)
        }
        fun bytesArray(start: Int): List<ByteArray> {
            val size = int(start)
            require(size in 1..64 && 32L * size <= data.size - start - 32)
            return (0 until size).map { bytesField(start + 32, it) }
        }
        fun addresses(start: Int): List<String> {
            val size = int(start)
            require(size in 2..16 && 32L * size <= data.size - start - 32)
            return (0 until size).map { address(start + 32 + it * 32) }
        }
        fun commands(start: Int): List<Command> {
            val size = int(start)
            require(size in 1..32 && 32L * size <= data.size - start - 32)
            val base = start + 32
            return (0 until size).map {
                val element = offset(base, it)
                Command(int(element), bytesField(element, 1))
            }
        }
    }

    private class RouterValidator(private val expected: PancakeSwapExpectation, custodian: String, router: String) {
        private val receiver = address(custodian)
        private val router = address(router)
        private val routes = expected.rawQuote.getAsJsonArray("routes").map { it.asJsonObject }
        private val pools = routes.flatMap { it.getAsJsonArray("pools").map { pool -> pool.asJsonObject } }
        private val tokens = routes.flatMap { route -> route.getAsJsonArray("path").map { address(it.asJsonObject["address"].asString) } }.toSet() +
            setOf(native, address(chain(expected.chainId).wrappedNative), address(expected.inputToken), address(expected.outputToken))
        private val v2Pairs = pools.filter { type(it) == "v2" }.map { address(it["address"].asString) }.toSet()
        private var inputSpent = zero
        private var swaps = 0
        private var finalOutput = false

        fun validate(codes: ByteArray, inputs: List<ByteArray>) {
            commands(codes, inputs, 0)
            require(swaps > 0 && inputSpent == expected.amountIn && finalOutput) { "Swap input budget or output path changed" }
        }

        private fun commands(codes: ByteArray, inputs: List<ByteArray>, depth: Int) {
            require(depth <= 2 && codes.size == inputs.size && codes.size in 1..64)
            codes.zip(inputs).forEach { (byte, input) ->
                val code = byte.toInt() and 255
                val a = Abi(input)
                when (code) {
                    0, 8, 34 -> {
                        safeRecipient(a.address(0))
                        require(a.word(if (code == 34) 160 else 128) == zero) { "Permit2 payer is not allowed" }
                        val path: List<String>
                        val fees: List<Int>?
                        if (code == 0) {
                            val packed = a.bytesField(0, 3)
                            require(packed.size >= 43 && (packed.size - 20) % 23 == 0 && packed.size <= 20 + 23 * 15)
                            val count = (packed.size - 20) / 23
                            path = (0..count).map { "0x" + Numeric.toHexStringNoPrefix(packed.copyOfRange(it * 23, it * 23 + 20)) }
                            fees = (0 until count).map { BigInteger(1, packed.copyOfRange(it * 23 + 20, it * 23 + 23)).toInt() }
                        } else {
                            path = a.addresses(a.offset(0, 3))
                            fees = null
                        }
                        val kind = when (code) { 0 -> "v3"; 8 -> "v2"; else -> "stableswap" }
                        val matches = matchingSegments(path, kind, fees)
                        require(matches.isNotEmpty()) { "Swap path differs from the accepted quote" }
                        if (code == 34) {
                            val flags = a.offset(0, 4)
                            require(a.int(flags) == path.size - 1)
                            val values = (0 until path.size - 1).map { a.word(flags + 32 + it * 32) }
                            require(matches.any { segment -> segment.indices.all { i ->
                                val size = segment[i].get("tokenCount")?.asInt ?: segment[i].get("balances")?.asJsonArray?.size()
                                    ?: segment[i].get("tokens")?.asJsonArray?.size() ?: 2
                                values[i] == BigInteger.valueOf(size.toLong())
                            } }) { "Stable pool flags changed" }
                        }
                        amount(path.first(), path.last(), a.word(32), a.word(64))
                    }
                    4 -> {
                        require(a.data.size == 96 && a.address(0) in tokens)
                        safeRecipient(a.address(32))
                        if (same(a.address(0), expected.outputToken)) finalOutput = true
                    }
                    11, 12 -> {
                        require(a.data.size == 64)
                        safeRecipient(a.address(0))
                        val value = a.word(32)
                        require(value == balance || value <= expected.amountIn || value <= expected.minimumOut)
                        if (code == 12 && address(expected.outputToken) == native && a.address(0) in setOf(receiver, msgSender)) finalOutput = true
                    }
                    16 -> infinity(a)
                    33 -> commands(a.bytesField(0, 0), a.bytesArray(a.offset(0, 1)), depth + 1)
                    else -> throw IllegalArgumentException("Unsupported, fee-bearing or approval router command")
                }
            }
        }

        private fun safeRecipient(recipient: String) {
            // A mixed route can send an intermediate output straight to its quoted V2 pair.
            require(recipient in setOf(receiver, router, addressThis, msgSender) || recipient in v2Pairs) { "Unexpected swap payment recipient" }
        }
        private fun same(a: String, b: String) = wrapped(expected.chainId, a) == wrapped(expected.chainId, b)
        private fun type(pool: JsonObject) = pool["type"].asString.lowercase().let { if (it == "stable") "stableswap" else it }
        private fun amount(input: String, output: String, amount: BigInteger, minimum: BigInteger) {
            require(input in tokens || same(input, expected.inputToken))
            require(output in tokens || same(output, expected.outputToken))
            require(amount == balance || amount.signum() >= 0)
            require(minimum.signum() >= 0 && minimum.bitLength() <= 256)
            if (same(input, expected.inputToken) && amount != balance && amount != zero) {
                inputSpent += amount
                require(inputSpent <= expected.amountIn) { "Swap exceeds its input budget" }
            }
            if (same(output, expected.outputToken)) finalOutput = true
            swaps++
        }
        private fun matchingSegments(path: List<String>, kind: String, fees: List<Int>?): List<List<JsonObject>> {
            require(path.size in 2..16)
            return routes.flatMap { route ->
                val routePath = route.getAsJsonArray("path").map { address(it.asJsonObject["address"].asString) }
                val routePools = route.getAsJsonArray("pools").map { it.asJsonObject }
                require(routePools.size + 1 == routePath.size)
                (0..routePath.size - path.size).mapNotNull { start ->
                    val matched = path.indices.all { same(path[it], routePath[start + it]) } &&
                        (0 until path.size - 1).all { i -> type(routePools[start + i]) == kind &&
                            (fees == null || routePools[start + i]["fee"].asInt == fees[i]) }
                    if (matched) routePools.subList(start, start + path.size - 1) else null
                }
            }
        }

        private fun infinity(a: Abi) {
            val actions = a.bytesField(0, 0)
            val args = a.bytesArray(a.offset(0, 1))
            require(actions.size == args.size)
            actions.zip(args).forEach { (byte, raw) ->
                val action = byte.toInt() and 255
                val p = Abi(raw)
                when (action) {
                    6, 28 -> {
                        val start = p.offset(0, 0)
                        val zeroForOne = p.word(start + 192)
                        require(zeroForOne == zero || zeroForOne == BigInteger.ONE)
                        val first = p.address(start)
                        val second = p.address(start + 32)
                        val input = if (zeroForOne == BigInteger.ONE) first else second
                        val output = if (zeroForOne == BigInteger.ONE) second else first
                        poolKey(p, start, action == 6, input, output)
                        validateHookData(p.bytesField(start, 9))
                        val amountIn = p.word(start + 224)
                        val minimum = p.word(start + 256)
                        require(amountIn.bitLength() <= 128 && minimum.bitLength() <= 128)
                        amount(input, output, amountIn, minimum)
                    }
                    7, 29 -> {
                        val start = p.offset(0, 0)
                        var input = p.address(start)
                        val initial = input
                        val path = p.offset(start, 1)
                        val size = p.int(path)
                        require(size in 1..15)
                        for (index in 0 until size) {
                            val key = p.offset(path + 32, index)
                            val output = p.address(key)
                            val currencies = listOf(input, output).sortedBy { it.removePrefix("0x").toBigInteger(16) }
                            val fields = listOf(currencies[0], currencies[1], p.address(key + 64), p.address(key + 96))
                            matchingInfinity(fields, p.word(key + 32), p.word(key + 160), action == 7)
                            validateHookData(p.bytesField(key, 4))
                            input = output
                        }
                        val amountIn = p.word(start + 64)
                        val minimum = p.word(start + 96)
                        require(amountIn.bitLength() <= 128 && minimum.bitLength() <= 128)
                        amount(initial, input, amountIn, minimum)
                    }
                    11 -> {
                        require(p.data.size == 96 && p.address(0) in tokens && p.word(64) == zero) { "Infinity cannot use Permit2" }
                        val value = p.word(32)
                        require(value == zero || value == balance || value <= expected.amountIn)
                    }
                    14 -> {
                        require(p.data.size == 96 && p.address(0) in tokens)
                        safeRecipient(p.address(32))
                        if (same(p.address(0), expected.outputToken)) finalOutput = true
                    }
                    15 -> {
                        require(p.data.size == 64 && p.address(0) in tokens)
                        if (same(p.address(0), expected.outputToken)) finalOutput = true
                    }
                    else -> throw IllegalArgumentException("Unsupported or fee-bearing Infinity action")
                }
            }
        }

        private fun validateHookData(data: ByteArray) {
            // PancakeSwap's bridge SDK encodes the default hook sender as twenty zero bytes.
            require(data.isEmpty() || data.size == 20 && data.all { it == 0.toByte() }) { "Unexpected hook data" }
        }

        private fun poolKey(p: Abi, start: Int, isCl: Boolean, input: String, output: String) {
            val fields = listOf(p.address(start), p.address(start + 32), p.address(start + 64), p.address(start + 96))
            require(setOf(fields[0], fields[1]) == setOf(input, output))
            matchingInfinity(fields, p.word(start + 128), p.word(start + 160), isCl)
        }
        private fun matchingInfinity(fields: List<String>, fee: BigInteger, parameters: BigInteger, isCl: Boolean) {
            val manager = if (isCl) "0xa0ffb9c1ce1fe56963b0321b32e7a0302114058b" else "0xc697d2898e0d09264376196696c51d7abbbaa4a9"
            require(expected.chainId in setOf(56L, 8453L) && fields[3] == manager)
            require(pools.any { pool ->
                val kind = type(pool)
                val expectedKind = if (isCl) kind in setOf("infinitycl", "infinitystable") else kind == "infinitybin"
                val hook = pool.get("hooks")?.takeUnless { it.isJsonNull }?.asString ?: native
                val params = pool.get("parameters")?.asString ?: return@any false
                val parsedParams = if (params.startsWith("0x")) params.removePrefix("0x").toBigInteger(16) else params.toBigInteger()
                expectedKind && address(pool["token0"].asString) == fields[0] && address(pool["token1"].asString) == fields[1] &&
                    address(hook) == fields[2] && pool["fee"].asString.toBigInteger() == fee && parsedParams == parameters
            }) { "Infinity pool differs from the accepted quote" }
        }
    }
}

package io.horizontalsystems.walletkit.modules.multiswap.providers

import com.google.gson.JsonObject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.DynamicArray
import org.web3j.abi.datatypes.DynamicBytes
import org.web3j.abi.datatypes.DynamicStruct
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.StaticStruct
import org.web3j.abi.datatypes.Type
import org.web3j.abi.datatypes.generated.Bytes32
import org.web3j.abi.datatypes.generated.Uint128
import org.web3j.abi.datatypes.generated.Uint24
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.utils.Numeric
import java.math.BigInteger

/** Read-only encodings ported from PancakeSwap smart-router 7.8.0 / infinity-sdk 1.1.0 (GPL-3.0). */
internal class PancakeNativeQuoter(private val read: suspend (PancakePoolCall) -> String) {
    suspend fun verify(chainId: Long, route: PancakeNativeRoute): PancakeNativeRoute {
        val call = encode(chainId, route)
        return verified(read(call), route)
    }

    /** Failed subcalls are discarded, as in the SDK; successful routes retain their original order and input. */
    suspend fun verify(chainId: Long, routes: List<PancakeNativeRoute>): List<PancakeNativeRoute> = batch(chainId, routes, false)

    suspend fun candidates(chainId: Long, routes: List<PancakeNativeRoute>): List<PancakeNativeRoute> = batch(chainId, routes, true)

    private suspend fun batch(chainId: Long, routes: List<PancakeNativeRoute>, candidate: Boolean): List<PancakeNativeRoute> = coroutineScope {
        require(chainId in wrappedNative)
        require(routes.size <= 4096)
        val calls = routes.map { encode(chainId, it, candidate) }
        val concurrentBatches = Semaphore(4)
        routes.indices.chunked(16).map { indexes ->
            async {
                concurrentBatches.withPermit {
                    val batch = indexes.map { calls[it] }
                    val encoded = FunctionEncoder.encode(Function("multicallWithGasLimitation", listOf(
                        DynamicArray(MulticallInput::class.java, batch.map(::MulticallInput)), Uint256(3_000_000L)
                    ), emptyList()))
                    val results = batchResults(read(PancakePoolCall(multicall.getValue(chainId), encoded)), batch.size)
                    indexes.mapIndexedNotNull { index, original ->
                        results[index]?.let { result ->
                            try { verified(result, routes[original]) }
                            catch (_: IllegalArgumentException) { null }
                        }
                    }
                }
            }
        }.awaitAll().flatten()
    }

    internal fun encode(chainId: Long, route: PancakeNativeRoute, candidate: Boolean = false): PancakePoolCall {
        require(chainId in wrappedNative && route.amountIn.signum() > 0 && route.amountIn.bitLength() <= 256)
        require(route.pools.size in 1..3 && route.path.size == route.pools.size + 1)
        val path = route.path.map(::address)
        route.pools.forEachIndexed { index, pool ->
            require(pool.chainId == chainId && pool.type in 0..4)
            val tokens = pool.currencies.map { canonical(chainId, it.address) }
            require(canonical(chainId, path[index]) in tokens && canonical(chainId, path[index + 1]) in tokens)
            require(canonical(chainId, path[index]) != canonical(chainId, path[index + 1]))
            if (pool.type >= 3) {
                require(chainId == 56L)
                require(address(pool.raw.get("hooks")?.asString ?: NATIVE) == NATIVE)
                require(address(pool.raw["poolManager"].asString) == if (pool.type == 3) CL_MANAGER else BIN_MANAGER)
                require(uint(pool.raw, "fee") < BigInteger.valueOf(1_000_000))
            }
        }
        val types = route.pools.map { it.type }.toSet()
        val mixedInfinity = chainId == 56L && (types.size > 1 || candidate && types.any { it >= 3 })
        val classic = route.pools.all { it.type < 3 }
        val arguments: List<Type<*>>
        val target: String
        val function: String
        when {
            mixedInfinity -> {
                target = MIXED_INFINITY_QUOTER
                function = "quoteMixedExactInput"
                val actualPath = listOf(currency(route.pools.first(), chainId, path.first())) +
                    route.pools.mapIndexed { i, p -> currency(p, chainId, path[i + 1]) }
                val params = route.pools.map { p -> DynamicBytes(when (p.type) {
                    0, 2 -> byteArrayOf()
                    1 -> abi(Uint24(uint(p.raw, "fee")))
                    else -> abi(DynamicStruct(poolKey(p), DynamicBytes(byteArrayOf())))
                }) }
                arguments = listOf(DynamicArray(Address::class.java, actualPath.map(::Address)),
                    DynamicBytes(route.pools.map { p -> when (p.type) { 0 -> 2; 1 -> 3; 2 -> 0; 3 -> 4; else -> 5 }.toByte() }.toByteArray()),
                    DynamicArray(DynamicBytes::class.java, params), Uint256(route.amountIn))
            }
            classic -> {
                val packed = canonical(chainId, path.first()).removePrefix("0x") + route.pools.mapIndexed { index, pool ->
                    val fee = if (pool.type == 1) uint(pool.raw, "fee") else BigInteger.valueOf(0x800000L)
                    require(fee.bitLength() <= 24)
                    fee.toString(16).padStart(6, '0') + canonical(chainId, path[index + 1]).removePrefix("0x")
                }.joinToString("")
                val allV3 = types == setOf(1)
                target = if (allV3) v3Quoter.getValue(chainId) else mixedQuoter.getValue(chainId)
                function = "quoteExactInput"
                arguments = if (allV3) listOf(DynamicBytes(Numeric.hexStringToByteArray(packed)), Uint256(route.amountIn))
                    else listOf(DynamicBytes(Numeric.hexStringToByteArray(packed)),
                        DynamicArray(Uint256::class.java, route.pools.map { p -> Uint256(when (p.type) {
                            1 -> 0L; 0 -> 1L; else -> when (p.currencies.size) { 2 -> 2L; 3 -> 3L; else -> error("Unsupported stable pool size") }
                        }) }), Uint256(route.amountIn))
            }
            else -> {
                require(types.size == 1 && types.single() in 3..4)
                require(route.amountIn.bitLength() <= 128)
                target = if (types.single() == 3) CL_QUOTER else BIN_QUOTER
                function = "quoteExactInput"
                val keys = route.pools.mapIndexed { i, p -> PathKey(currency(p, chainId, path[i + 1]), p) }
                arguments = listOf(DynamicStruct(Address(currency(route.pools.first(), chainId, path.first())),
                    DynamicArray(PathKey::class.java, keys), Uint128(route.amountIn)))
            }
        }
        return PancakePoolCall(target, FunctionEncoder.encode(Function(function, arguments, emptyList())))
    }

    private fun verified(result: String, route: PancakeNativeRoute): PancakeNativeRoute {
        val data = AbiResult(result)
        var initializedTicks = BigInteger.ZERO
        if (route.pools.any { it.type >= 3 } || route.pools.map { it.type }.toSet().size > 1 && route.pools.first().chainId == 56L) {
            require(data.size == 64)
        } else {
            require(data.size >= 192 && data.index(32) == 128)
            val prices = data.index(128)
            require(prices <= route.pools.size)
            val ticksStart = 160 + prices * 32
            require(data.index(64) == ticksStart)
            val ticks = data.index(ticksStart)
            require(ticks == prices && data.size == ticksStart + 32 + ticks * 32)
            if (route.pools.all { it.type == 1 }) require(prices == route.pools.size)
            repeat(prices) { require(data.word(160 + it * 32).bitLength() <= 160) }
            repeat(ticks) {
                val crossed = data.word(ticksStart + 32 + it * 32)
                require(crossed.bitLength() <= 32)
                initializedTicks += crossed
            }
        }
        require(initializedTicks.bitLength() <= 31)
        return route.copy(amountOut = data.word(0).also { require(it.signum() > 0) }, initializedTicks = initializedTicks.toInt())
    }

    private fun batchResults(result: String, count: Int): List<String?> {
        val data = AbiResult(result)
        require(data.size >= 128 && data.index(32) == 96 && data.index(96) == count)
        val last = data.index(64)
        require(last in 0 until count)
        val arrayStart = 128
        var cursor = count * 32
        val results = (0 until count).map { index ->
            require(data.index(arrayStart + index * 32) == cursor)
            val tuple = arrayStart + cursor
            val success = data.word(tuple)
            require(success == BigInteger.ZERO || success == BigInteger.ONE)
            require(data.index(tuple + 64) == 96)
            val bytes = data.bytes(tuple + 96)
            cursor += 128 + ((bytes.length - 2) / 2 + 31) / 32 * 32
            if (index <= last && success == BigInteger.ONE) bytes else null
        }
        require(arrayStart + cursor == data.size)
        return results
    }

    private class MulticallInput(call: PancakePoolCall) : DynamicStruct(Address(call.address), Uint256(1_000_000L), DynamicBytes(Numeric.hexStringToByteArray(call.data)))
    private class PathKey(output: String, pool: PancakeNativePool) : DynamicStruct(Address(output), Uint24(uint(pool.raw, "fee")),
        Address(NATIVE), Address(pool.raw["poolManager"].asString), DynamicBytes(byteArrayOf()), parameters(pool))

    /** Reject malformed ABI, revert payloads and truncated dynamic tails before accepting any quote. */
    private class AbiResult(value: String) {
        private val hex = value.removePrefix("0x")
        val size: Int get() = hex.length / 2
        init { require(value.startsWith("0x") && hex.length in 64..262_144 && hex.length % 64 == 0 && hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) }
        fun word(offset: Int): BigInteger {
            require(offset >= 0 && offset % 32 == 0 && offset <= size - 32)
            return hex.substring(offset * 2, (offset + 32) * 2).toBigInteger(16)
        }
        fun index(offset: Int): Int = word(offset).also { require(it.bitLength() < 31) }.toInt()
        fun bytes(offset: Int): String {
            val count = index(offset)
            require(count <= size - offset - 32)
            val end = offset + 32 + count
            val padded = (end + 31) / 32 * 32
            require(padded <= size && hex.substring(end * 2, padded * 2).all { it == '0' })
            return "0x" + hex.substring((offset + 32) * 2, end * 2)
        }
    }

    companion object {
        private const val NATIVE = "0x0000000000000000000000000000000000000000"
        private const val CL_MANAGER = "0xa0ffb9c1ce1fe56963b0321b32e7a0302114058b"
        private const val BIN_MANAGER = "0xc697d2898e0d09264376196696c51d7abbbaa4a9"
        private const val CL_QUOTER = "0xd0737c9762912dd34c3271197e362aa736df0926"
        private const val BIN_QUOTER = "0xc631f4b0fc2dd68ad45f74b2942628db117dd359"
        private const val MIXED_INFINITY_QUOTER = "0x2dcbf7b985c8c5c931818e4e107bae8aac8dab7c"
        private val wrappedNative = mapOf(1L to "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2", 56L to "0xbb4cdb9cbd36b01bd1cbaebf2de08d9173bc095c",
            8453L to "0x4200000000000000000000000000000000000006", 42161L to "0x82af49447d8a07e3bd95bd0d56f35241523fbab1", 324L to "0x5aea5775959fbc2557cc8789bc1bf90a239d9a91")
        private val v3Quoter = wrappedNative.keys.associateWith { if (it == 324L) "0x3d146fce6c1006857750cbe8af44f76a28041ccc" else "0xb048bbc1ee6b733fffcfb9e9cef7375518e25997" }
        private val mixedQuoter = mapOf(1L to "0x678aa4bf4e210cf2166753e054d5b7c31cc7fa86", 56L to "0x678aa4bf4e210cf2166753e054d5b7c31cc7fa86",
            8453L to "0x4c650fb471fe4e0f476fd3437c3411b1122c4e3b", 42161L to "0x5457fa0318753e9eac2d17dffdb6383da207d705", 324L to "0x9b1edfb3848660402e4f1dc25733764e80aa627a")
        private val multicall = mapOf(1L to "0xc0916d7e360c31d5f6d0c497e6a36b7b0e80e3cf", 56L to "0x39eecae833c944ebb94942fa44cae46e87a8da17",
            8453L to "0x3efaab8d7a631cff5ccf5f149d1bbb3b5bfda2c0", 42161L to "0xbffe39cdd04f0183e0493c1deb6e275c5cf84adf", 324L to "0x95071cbd09184083e7f732a710c2e30c9882fd5f")
        private fun address(value: String) = value.lowercase().also { require(it.matches(Regex("0x[0-9a-f]{40}"))) }
        private fun canonical(chainId: Long, value: String) = address(value).let { if (it == NATIVE) wrappedNative.getValue(chainId) else it }
        private fun currency(pool: PancakeNativePool, chainId: Long, token: String) = pool.currencies.first {
            canonical(chainId, it.address) == canonical(chainId, token)
        }.address.let(::address)
        private fun uint(pool: JsonObject, key: String): BigInteger = pool[key].asString.let {
            require(it.matches(Regex("[0-9]{1,78}")))
            it.toBigInteger().also { n -> require(n.bitLength() <= 256) }
        }
        private fun parameters(pool: PancakeNativePool): Bytes32 {
            val spacing = uint(pool.raw, if (pool.type == 3) "tickSpacing" else "binStep")
            require(spacing.signum() > 0 && spacing.bitLength() <= 16)
            val bitmap = pool.raw.get("hooksRegistrationBitmap")?.asString?.let {
                require(it.matches(Regex("0x[0-9a-fA-F]{1,4}|[0-9]{1,5}")))
                if (it.startsWith("0x")) it.removePrefix("0x").toBigInteger(16) else it.toBigInteger()
            } ?: BigInteger.ZERO
            require(bitmap.signum() == 0)
            return Bytes32(Numeric.toBytesPadded(spacing.shiftLeft(16).or(bitmap), 32))
        }
        private fun poolKey(pool: PancakeNativePool) = StaticStruct(Address(address(pool.currencies[0].address)), Address(address(pool.currencies[1].address)),
            Address(NATIVE), Address(pool.raw["poolManager"].asString), Uint24(uint(pool.raw, "fee")), parameters(pool))
        private fun abi(vararg values: Type<*>) = Numeric.hexStringToByteArray(FunctionEncoder.encodeConstructor(values.toList()))
    }
}

package io.horizontalsystems.walletkit.modules.multiswap.providers

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.web3j.crypto.Hash
import java.math.BigInteger

/** Official stable-hook discovery is bound to the canonical factory, never a hosted hook label. */
internal object PancakeInfinityStableQuote {
    private val ZERO = BigInteger.ZERO
    private val ONE = BigInteger.ONE
    private const val MANAGER = "0xa0ffb9c1ce1fe56963b0321b32e7a0302114058b"
    private const val WRAPPED_BNB = "0xbb4cdb9cbd36b01bd1cbaebf2de08d9173bc095c"
    private val intermediates = listOf("0x55d398326f99059ff775485246999027b3197955",
        "0x8ac76a51cc950d9822d68b83fe1ad97b32cd580d", WRAPPED_BNB, "0x2170ed0880ac9a755fd29b2688956bd959f933f8")
    private data class Pool(val index: Int, val hook: String, val token0: JsonObject, val token1: JsonObject,
                            val id: String, val parameters: BigInteger)
    private data class Cached(val until: Long, val pools: List<Pool>)
    private val cache = mutableMapOf<Pair<String, String>, Cached>()
    private val mutex = Mutex()

    suspend fun quote(
        chainId: Long,
        input: String,
        output: String,
        amount: BigInteger,
        rpc: suspend (PancakePoolCall) -> String,
        directHubQuote: (suspend (String, String, BigInteger) -> JsonObject?)? = null,
    ): JsonObject? {
        if (chainId != 56L || amount <= ZERO || input.equals(output, true)) return null
        val originalIn = address(input); val originalOut = address(output)
        val source = wrapped(originalIn); val destination = wrapped(originalOut)
        if (source == destination) return null
        val candidates = mutableListOf<JsonObject>()
        suspend fun attempt(block: suspend () -> Unit) {
            try { block() } catch (e: CancellationException) { throw e } catch (_: Exception) { }
        }
        attempt { direct(source, destination, amount, rpc)?.let { candidates.add(it) } }
        if (directHubQuote != null) {
            for (middle in intermediates.filter { it != source && it != destination }) {
                attempt {
                    val prefix = direct(source, middle, amount, rpc) ?: return@attempt
                    val nextAmount = prefix.uint("outputAmount")
                    val suffix = directHubQuote(middle, destination, nextAmount) ?: return@attempt
                    val valid = PancakeAmmQuote.parse(suffix, 56, middle, destination, nextAmount).raw
                    combine(prefix, valid)?.let { candidates.add(it) }
                }
                attempt {
                    val prefix = directHubQuote(source, middle, amount) ?: return@attempt
                    val valid = PancakeAmmQuote.parse(prefix, 56, source, middle, amount).raw
                    val suffix = direct(middle, destination, valid.uint("outputAmount"), rpc) ?: return@attempt
                    combine(valid, suffix)?.let { candidates.add(it) }
                }
                attempt {
                    val prefix = direct(source, middle, amount, rpc) ?: return@attempt
                    val suffix = direct(middle, destination, prefix.uint("outputAmount"), rpc) ?: return@attempt
                    combine(prefix, suffix)?.let { candidates.add(it) }
                }
            }
        }
        return candidates.maxByOrNull { it.uint("outputAmount") }?.apply {
            addProperty("srcToken", originalIn); addProperty("dstToken", originalOut)
        }
    }

    private suspend fun direct(input: String, output: String, amount: BigInteger, rpc: suspend (PancakePoolCall) -> String): JsonObject? {
        val pools = pools(input, output, rpc)
        var best: JsonObject? = null
        for (pool in pools) {
            try {
                val inputIs0 = pool.token0.text("address") == input
                val result = word(rpc(call(pool.hook, "get_dy(int128,int128,uint256)",
                    if (inputIs0) ZERO else ONE, if (inputIs0) ONE else ZERO, amount)))
                if (result <= ZERO || best?.uint("outputAmount")?.let { result <= it } == true) continue
                val ordered = if (inputIs0) listOf(pool.token0, pool.token1) else listOf(pool.token1, pool.token0)
                val descriptor = JsonObject().apply {
                    addProperty("address", pool.id); addProperty("type", "infinityStable"); addProperty("fee", 0)
                    addProperty("provider", "pancakeswap"); addProperty("poolManager", MANAGER)
                    addProperty("token0", pool.token0.text("address")); addProperty("token1", pool.token1.text("address"))
                    addProperty("hooks", pool.hook); addProperty("parameters", "0x" + uintWord(pool.parameters))
                    addProperty("tickSpacing", 1); addProperty("factoryIndex", pool.index); addProperty("factoryLookup", "pair")
                }
                best = JsonObject().apply {
                    addProperty("chainId", 56); addProperty("tradeType", "exactIn")
                    addProperty("srcToken", input); addProperty("dstToken", output)
                    addProperty("inputAmount", amount.toString()); addProperty("outputAmount", result.toString())
                    addProperty("swapFee", "0"); addProperty("swapFeeBps", "0")
                    addProperty("quoteSource", "officialInfinityStableFactory")
                    add("routes", JsonArray().apply { add(JsonObject().apply {
                        addProperty("percent", 100); addProperty("inputAmount", amount.toString()); addProperty("outputAmount", result.toString())
                        add("path", JsonArray().apply { ordered.forEach { add(it.deepCopy()) } })
                        add("pools", JsonArray().apply { add(descriptor) })
                    }) })
                }
            } catch (e: CancellationException) { throw e } catch (_: Exception) { }
        }
        return best
    }

    private suspend fun pools(input: String, output: String, rpc: suspend (PancakePoolCall) -> String): List<Pool> = mutex.withLock {
        val (a, b) = listOf(input, output).sorted()
        val key = a to b
        cache[key]?.takeIf { it.until > System.currentTimeMillis() }?.let { return@withLock it.pools }
        val pools = mutableListOf<Pool>()
        for (index in 0..255) {
            val hook = asAddress(word(rpc(call(PancakeAmmQuote.STABLE_FACTORY, "find_pool_for_coins(address,address,uint256)", hex(a), hex(b), BigInteger.valueOf(index.toLong())))))
            if (hook == PancakeAmmQuote.NATIVE) {
                cache[key] = Cached(System.currentTimeMillis() + 300_000, pools)
                return@withLock pools
            }
            try {
                val coin0 = asAddress(word(rpc(call(hook, "coins(uint256)", ZERO))))
                val coin1 = asAddress(word(rpc(call(hook, "coins(uint256)", ONE))))
                require(coin0 == a && coin1 == b)
                require(asAddress(word(rpc(call(hook, "pool_manager()")))) == MANAGER)
                val bitmap = word(rpc(call(hook, "getHooksRegistrationBitmap()")))
                require(bitmap.bitLength() <= 16)
                val parameters = BigInteger.valueOf(65536).or(bitmap)
                val encodedKey = listOf(hex(a), hex(b), hex(hook), hex(MANAGER), ZERO, parameters).joinToString("") { uintWord(it) }
                val id = Hash.sha3("0x$encodedKey")
                val slot = words(rpc(call(MANAGER, "getSlot0(bytes32)", hex(id))))
                require(slot.size == 4 && slot[0] > ZERO && slot[3] == ZERO)
                fun token(coin: String, decimals: BigInteger) = JsonObject().apply {
                    require(decimals in ZERO..BigInteger.valueOf(255))
                    addProperty("address", coin); addProperty("decimals", decimals.intValueExact()); addProperty("symbol", "")
                }
                val token0 = token(a, word(rpc(call(a, "decimals()"))))
                val token1 = token(b, word(rpc(call(b, "decimals()"))))
                pools.add(Pool(index, hook, token0, token1, id, parameters))
            } catch (e: CancellationException) { throw e } catch (_: Exception) { }
        }
        error("Infinity Stable pair catalog exceeds the bounded lookup limit")
    }

    private fun combine(prefix: JsonObject, suffix: JsonObject): JsonObject? {
        val left = prefix.getAsJsonArray("routes").singleOrNull()?.asJsonObject ?: return null
        val right = suffix.getAsJsonArray("routes").singleOrNull()?.asJsonObject ?: return null
        if (left.get("percent").asInt != 100 || right.get("percent").asInt != 100) return null
        require(prefix.uint("outputAmount") == suffix.uint("inputAmount"))
        val leftPath = left.getAsJsonArray("path"); val rightPath = right.getAsJsonArray("path")
        require(leftPath.last().asJsonObject.text("address").equals(rightPath.first().asJsonObject.text("address"), true))
        val count = left.getAsJsonArray("pools").size() + right.getAsJsonArray("pools").size()
        if (count > 3) return null
        return prefix.deepCopy().apply {
            addProperty("dstToken", suffix.text("dstToken")); addProperty("outputAmount", suffix.text("outputAmount"))
            add("routes", JsonArray().apply { add(JsonObject().apply {
                addProperty("percent", 100); addProperty("inputAmount", prefix.text("inputAmount")); addProperty("outputAmount", suffix.text("outputAmount"))
                add("path", JsonArray().apply { leftPath.forEach { add(it.deepCopy()) }; rightPath.drop(1).forEach { add(it.deepCopy()) } })
                add("pools", JsonArray().apply { left.getAsJsonArray("pools").forEach { add(it.deepCopy()) }; right.getAsJsonArray("pools").forEach { add(it.deepCopy()) } })
            }) })
        }
    }

    private fun wrapped(value: String) = if (value == PancakeAmmQuote.NATIVE) WRAPPED_BNB else value
    private fun address(value: String) = value.lowercase().also { require(it.matches(Regex("^0x[0-9a-f]{40}$"))) }
    private fun hex(value: String) = value.removePrefix("0x").toBigInteger(16)
    private fun uintWord(value: BigInteger) = value.toString(16).padStart(64, '0').also { require(value.signum() >= 0 && it.length == 64) }
    private fun call(target: String, signature: String, vararg args: BigInteger) = PancakePoolCall(target, Hash.sha3String(signature).take(10) + args.joinToString("") { uintWord(it) })
    private fun words(value: String): List<BigInteger> {
        require(value.matches(Regex("^0x(?:[0-9a-fA-F]{64}){1,256}$")))
        return value.drop(2).chunked(64).map { it.toBigInteger(16) }
    }
    private fun word(value: String) = words(value).single()
    private fun asAddress(value: BigInteger) = "0x" + value.toString(16).padStart(40, '0').also { require(value.bitLength() <= 160) }
    private fun JsonObject.text(key: String) = requireNotNull(get(key)?.takeIf { it.isJsonPrimitive }).asString
    private fun JsonObject.uint(key: String) = text(key).also { require(it.matches(Regex("[0-9]{1,78}"))) }.toBigInteger().also { require(it.bitLength() <= 256) }
}

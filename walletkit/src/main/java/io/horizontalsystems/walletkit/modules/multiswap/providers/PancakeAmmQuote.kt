package io.horizontalsystems.walletkit.modules.multiswap.providers

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.web3j.crypto.Hash
import java.math.BigInteger

internal data class PancakePoolCall(val address: String, val data: String)

internal class PancakeAmmQuote private constructor(
    private val data: JsonObject,
    val chainId: Long,
    val inputToken: String,
    val outputToken: String,
    val amountIn: BigInteger,
    val amountOut: BigInteger,
) {
    val raw: JsonObject get() = data.deepCopy()
    private val routes get() = data.getAsJsonArray("routes").map { it.asJsonObject }
    val stateCalls: List<PancakePoolCall> = routes.flatMap { route ->
        route.getAsJsonArray("pools").flatMap { element ->
            val pool = element.asJsonObject
            val target = pool.text("address")
            when (pool.text("type")) {
                "v2" -> listOf(call(target, "getReserves()"), call(target, "token0()"), call(target, "token1()"))
                "v3" -> listOf(call(target, "slot0()"), call(target, "liquidity()"), call(target, "fee()"),
                    call(target, "token0()"), call(target, "token1()"))
                "stableswap" -> buildList {
                    add(call(target, "N_COINS()")); add(call(target, "A()")); add(call(target, "fee()")); add(call(target, "FEE_DENOMINATOR()"))
                    for (i in 0..2) {
                        add(call(target, "coins(uint256)", BigInteger.valueOf(i.toLong())))
                        add(call(target, "balances(uint256)", BigInteger.valueOf(i.toLong())))
                    }
                }
                "infinityCl", "infinityBin" -> buildList {
                    add(call(pool.text("poolManager"), "getSlot0(bytes32)", hex(target)))
                    if (pool.text("type") == "infinityCl") add(call(pool.text("poolManager"), "getLiquidity(bytes32)", hex(target)))
                }
                "infinityStable" -> {
                    val hook = pool.text("hooks")
                    listOf(registryCall(pool),
                        call(hook, "getHooksRegistrationBitmap()"), call(hook, "get_balances()"), call(hook, "A()"), call(hook, "fee()"),
                        call(pool.text("poolManager"), "getSlot0(bytes32)", hex(target)))
                }
                else -> error("Unsupported PancakeSwap pool")
            }
        }
    }.distinct()

    fun additionalStateCalls(states: Map<PancakePoolCall, String>): List<PancakePoolCall> {
        val known = routes.flatMap { it.getAsJsonArray("path").map { token -> token.asJsonObject.text("address") } }.toSet()
        return routes.flatMap { it.getAsJsonArray("pools").map { pool -> pool.asJsonObject } }
            .filter { it.text("type") == "stableswap" }.flatMap { pool ->
                val target = pool.text("address")
                val count = stateWords(requireNotNull(states[call(target, "N_COINS()")])).single().intValueExact()
                require(count in 2..3) { "Unsupported StableSwap coin count" }
                (0 until count).mapNotNull { i ->
                    val coin = addressWord(stateWords(requireNotNull(states[call(target, "coins(uint256)", BigInteger.valueOf(i.toLong()))])).single())
                    if (coin in known) null else call(coin, "decimals()")
                }
            }.distinct()
    }

    fun asTrade(minimumOutput: BigInteger, states: Map<PancakePoolCall, String>): JsonObject {
        require(minimumOutput > ZERO && minimumOutput <= amountOut) { "Invalid swap minimum output" }
        fun read(target: String, signature: String, vararg args: BigInteger): List<BigInteger> {
            val value = requireNotNull(states[call(target, signature, *args)]) { "Missing PancakeSwap pool state" }
            return stateWords(value)
        }
        fun checkedTokens(pool: JsonObject, stable: Boolean = false) {
            val target = pool.text("address")
            val a = if (stable) read(target, "coins(uint256)", ZERO) else read(target, "token0()")
            val b = if (stable) read(target, "coins(uint256)", ONE) else read(target, "token1()")
            require(a.single() == hex(pool.text("token0")) && b.single() == hex(pool.text("token1"))) { "Pool tokens changed" }
        }
        fun pathCurrency(token: JsonObject) = currency(token.text("address"), token.int("decimals"), token.text("symbol"))
        fun poolTrade(pool: JsonObject, path: List<JsonObject>): JsonObject {
            val type = pool.text("type")
            val cur0 = pathCurrency(path.first { it.text("address") == pool.text("token0") })
            val cur1 = pathCurrency(path.first { it.text("address") == pool.text("token1") })
            val target = pool.text("address")
            return JsonObject().apply {
                when (type) {
                    "v2" -> {
                        checkedTokens(pool)
                        val reserve = read(target, "getReserves()")
                        require(reserve.size == 3 && reserve[0] > ZERO && reserve[1] > ZERO)
                        addProperty("type", 0); addProperty("address", target)
                        add("reserve0", amount(cur0, reserve[0])); add("reserve1", amount(cur1, reserve[1]))
                    }
                    "v3" -> {
                        checkedTokens(pool)
                        require(read(target, "fee()").single() == pool.uint("fee")) { "Pool fee changed" }
                        val slot = read(target, "slot0()")
                        val liquidity = read(target, "liquidity()").single()
                        require(slot.size == 7 && slot[0] > ZERO && liquidity > ZERO && slot[5].bitLength() <= 32)
                        addProperty("type", 1); addProperty("address", target)
                        add("token0", cur0); add("token1", cur1); addProperty("fee", pool.int("fee"))
                        addProperty("sqrtRatioX96", slot[0].toString()); addProperty("liquidity", liquidity.toString())
                        addProperty("tick", signedTick(slot[1]))
                        add("token0ProtocolFee", fraction(slot[5].and(MASK_16), BigInteger.valueOf(10000)))
                        add("token1ProtocolFee", fraction(slot[5].shiftRight(16), BigInteger.valueOf(10000)))
                    }
                    "stableswap" -> {
                        val count = read(target, "N_COINS()").single().intValueExact()
                        require(count in 2..3) { "Unsupported StableSwap coin count" }
                        val coins = (0 until count).map { i -> addressWord(read(target, "coins(uint256)", BigInteger.valueOf(i.toLong())).single()) }
                        require(coins.distinct().size == count && coins.containsAll(listOf(pool.text("token0"), pool.text("token1"))))
                        val tokens = coins.map { coin ->
                            path.firstOrNull { it.text("address") == coin }?.let(::pathCurrency) ?: currency(coin,
                                read(coin, "decimals()").single().intValueExact().also { require(it in 0..255) }, "")
                        }
                        val balances = (0 until count).map { i -> read(target, "balances(uint256)", BigInteger.valueOf(i.toLong())).single() }
                        val amplifier = read(target, "A()").single(); val fee = read(target, "fee()").single()
                        val denominator = read(target, "FEE_DENOMINATOR()").single()
                        require(balances.all { it > ZERO } && amplifier > ZERO && denominator > ZERO && fee < denominator)
                        require(fee == pool.uint("fee")) { "Stable pool fee changed" }
                        pool.addProperty("tokenCount", count)
                        pool.add("tokens", JsonArray().apply { tokens.forEach { token -> add(JsonObject().apply {
                            addProperty("address", token.text("address")); addProperty("decimals", token.int("decimals")); addProperty("symbol", token.text("symbol"))
                        }) } })
                        addProperty("type", 2); addProperty("address", target)
                        add("balances", JsonArray().apply { balances.forEachIndexed { i, balance -> add(amount(tokens[i], balance)) } })
                        addProperty("amplifier", amplifier.toString()); add("fee", fraction(fee, denominator))
                    }
                    else -> {
                        val manager = pool.text("poolManager")
                        val slot = read(manager, "getSlot0(bytes32)", hex(target))
                        addProperty("type", poolType(type)); addProperty("id", target); addProperty("poolManager", manager)
                        add("currency0", cur0); add("currency1", cur1); addProperty("hooks", pool.text("hooks"))
                        addProperty("hooksRegistrationBitmap", "0x" + hex(pool.text("parameters")).and(MASK_16).toString(16))
                        addProperty("fee", pool.int("fee"))
                        if (type == "infinityBin") {
                            require(slot.size == 3 && slot[0].signum() > 0 && slot[0].bitLength() <= 24)
                            require(slot[2] == pool.uint("fee")) { "Infinity pool fee changed" }
                            addProperty("activeId", slot[0].intValueExact()); addProperty("protocolFee", slot[1].intValueExact())
                            addProperty("binStep", pool.int("binStep")); addProperty("dynamic", false)
                        } else {
                            require(slot.size == 4 && slot[0] > ZERO)
                            addProperty("tickSpacing", pool.int("tickSpacing"))
                            if (type == "infinityCl") {
                                val liquidity = read(manager, "getLiquidity(bytes32)", hex(target)).single()
                                require(liquidity > ZERO && slot[3] == pool.uint("fee")) { "Infinity pool state changed" }
                                addProperty("sqrtRatioX96", slot[0].toString()); addProperty("tick", signedTick(slot[1]))
                                addProperty("liquidity", liquidity.toString()); addProperty("protocolFee", slot[2].intValueExact())
                                addProperty("dynamic", false)
                            } else {
                                val hook = pool.text("hooks")
                                require(read(registryCall(pool).address, if (pool.get("factoryLookup")?.asString == "pair") "find_pool_for_coins(address,address,uint256)" else "pool_list(uint256)", *if (pool.get("factoryLookup")?.asString == "pair") arrayOf(hex(pool.text("token0")), hex(pool.text("token1")), pool.uint("factoryIndex")) else arrayOf(pool.uint("factoryIndex"))).single() == hex(hook)) { "Unregistered stable hook" }
                                require(read(hook, "getHooksRegistrationBitmap()").single() == hex(pool.text("parameters")).and(MASK_16))
                                val encodedBalances = read(hook, "get_balances()")
                                require(encodedBalances.size == 4 && encodedBalances[0] == BigInteger.valueOf(32) && encodedBalances[1] == BigInteger.valueOf(2))
                                val balances = encodedBalances.drop(2)
                                require(balances.all { it > ZERO })
                                val amplifier = read(hook, "A()").single(); val fee = read(hook, "fee()").single()
                                require(amplifier > ZERO && fee < BigInteger.TEN.pow(10))
                                addProperty("stableFee", fee.longValueExact()); addProperty("amplifier", amplifier.longValueExact())
                                add("reserve0", amount(cur0, balances[0])); add("reserve1", amount(cur1, balances[1]))
                            }
                        }
                    }
                }
            }
        }
        var assigned = ZERO
        val encoded = JsonArray()
        routes.forEachIndexed { index, route ->
            val path = route.getAsJsonArray("path").map { it.asJsonObject }
            val minimum = if (index == routes.lastIndex) minimumOutput - assigned else minimumOutput * route.uint("outputAmount") / amountOut
            assigned += minimum
            encoded.add(JsonObject().apply {
                val pools = route.getAsJsonArray("pools").map { it.asJsonObject }
                val types = pools.map { poolType(it.text("type")) }.toSet()
                addProperty("type", if (types.size == 1) routeType(types.single()) else 3)
                add("path", JsonArray().apply { path.forEach { add(pathCurrency(it)) } })
                add("pools", JsonArray().apply { pools.forEach { add(poolTrade(it, path)) } })
                addProperty("percent", route.int("percent"))
                add("inputAmount", amount(pathCurrency(path.first()), route.uint("inputAmount")))
                add("outputAmount", amount(pathCurrency(path.last()), minimum))
            })
        }
        val first = routes.first().getAsJsonArray("path").first().asJsonObject
        val last = routes.first().getAsJsonArray("path").last().asJsonObject
        return JsonObject().apply {
            addProperty("tradeType", 0)
            add("inputAmount", amount(currency(inputToken, first.int("decimals"), first.text("symbol")), amountIn))
            add("outputAmount", amount(currency(outputToken, last.int("decimals"), last.text("symbol")), minimumOutput))
            add("routes", encoded)
        }
    }

    private fun currency(address: String, decimals: Int, symbol: String) = JsonObject().apply {
        val native = address == NATIVE
        addProperty("chainId", chainId); addProperty("decimals", decimals)
        addProperty("symbol", if (native) if (chainId == 56L) "BNB" else "ETH" else symbol)
        addProperty("isNative", native); addProperty("isToken", !native)
        if (!native) addProperty("address", address)
    }

    companion object {
        const val NATIVE = "0x0000000000000000000000000000000000000000"
        const val STABLE_FACTORY = "0x44de03599d1088b205d959b09a842448a0a63173"
        private const val CL_MANAGER = "0xa0ffb9c1ce1fe56963b0321b32e7a0302114058b"
        private const val BIN_MANAGER = "0xc697d2898e0d09264376196696c51d7abbbaa4a9"
        private val ZERO = BigInteger.ZERO
        private val ONE = BigInteger.ONE
        private val MASK_16 = BigInteger.valueOf(65535)
        private val wrappedNative = mapOf(1L to "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2",
            56L to "0xbb4cdb9cbd36b01bd1cbaebf2de08d9173bc095c", 8453L to "0x4200000000000000000000000000000000000006",
            42161L to "0x82af49447d8a07e3bd95bd0d56f35241523fbab1", 324L to "0x5aea5775959fbc2557cc8789bc1bf90a239d9a91")

        fun parse(data: JsonObject, expectedChainId: Long, expectedInput: String, expectedOutput: String, expectedAmount: BigInteger): PancakeAmmQuote {
            val raw = data.deepCopy()
            val input = address(expectedInput); val output = address(expectedOutput)
            require(raw.uint("chainId").longValueExact() == expectedChainId && expectedChainId in wrappedNative.keys)
            require(address(raw.text("srcToken")) == input && address(raw.text("dstToken")) == output && input != output)
            require(raw.text("tradeType") == "exactIn" && raw.uint("inputAmount") == expectedAmount && expectedAmount > ZERO)
            require(raw.uint("swapFeeBps") == ZERO && raw.uint("swapFee") == ZERO) { "Unexpected PancakeSwap application fee" }
            val outputAmount = raw.uint("outputAmount").also { require(it > ZERO) }
            val routes = raw.getAsJsonArray("routes").also { require(it.size() in 1..32) }.map { it.asJsonObject }
            require(routes.sumOf { it.int("percent") } == 100)
            require(routes.fold(ZERO) { sum, r -> sum + r.uint("inputAmount") } == expectedAmount)
            require(routes.fold(ZERO) { sum, r -> sum + r.uint("outputAmount") } == outputAmount)
            val decimalsByToken = mutableMapOf<String, Int>()
            for (route in routes) {
                require(route.int("percent") in 1..100 && route.uint("inputAmount") > ZERO && route.uint("outputAmount") > ZERO)
                val path = route.getAsJsonArray("path").also { require(it.size() in 2..17) }.map { it.asJsonObject }
                val pools = route.getAsJsonArray("pools").map { it.asJsonObject }
                require(pools.size == path.size - 1)
                for (token in path) {
                    val a = address(token.text("address")); token.addProperty("address", a)
                    val decimals = token.int("decimals"); require(decimals in 0..255 && token.text("symbol").length <= 64)
                    require(decimalsByToken.putIfAbsent(a, decimals)?.let { it == decimals } != false) { "Inconsistent token decimals" }
                    if (a == NATIVE || a == wrappedNative[expectedChainId]) require(decimals == 18)
                }
                fun endpoint(actual: String, expected: String) = actual == expected || expected == NATIVE && actual == wrappedNative[expectedChainId]
                require(endpoint(path.first().text("address"), input) && endpoint(path.last().text("address"), output))
                for ((i, pool) in pools.withIndex()) {
                    require(pool.text("provider") == "pancakeswap") { "Non-PancakeSwap liquidity source" }
                    val type = pool.text("type"); require(type in setOf("v2", "v3", "stableswap", "infinityCl", "infinityBin", "infinityStable"))
                    val t0 = address(pool.text("token0")); val t1 = address(pool.text("token1"))
                    pool.addProperty("token0", t0); pool.addProperty("token1", t1)
                    require(t0 < t1 && setOf(t0, t1) == setOf(path[i].text("address"), path[i + 1].text("address")))
                    val fee = pool.uint("fee")
                    require(if (type == "stableswap") fee < BigInteger.TEN.pow(10) else fee < BigInteger.valueOf(1000000) || type == "infinityStable" && fee == BigInteger.valueOf(8388608)) { "Unbounded pool fee" }
                    if (type == "v2") require(fee == BigInteger.valueOf(2500))
                    if (type.startsWith("infinity")) {
                        require(expectedChainId == 56L || expectedChainId == 8453L)
                        val manager = address(pool.text("poolManager"))
                        require(manager == if (type == "infinityBin") BIN_MANAGER else CL_MANAGER)
                        pool.addProperty("poolManager", manager)
                        val hook = pool.get("hooks")?.takeUnless { it.isJsonNull }?.asString?.let(::address) ?: NATIVE
                        require(hook == NATIVE || type == "infinityStable") { "Unreviewed Infinity hook" }
                        if (type == "infinityStable") require(expectedChainId == 56L && hook != NATIVE && pool.uint("factoryIndex") < BigInteger.valueOf(100000))
                        val stride = pool.int(if (type == "infinityBin") "binStep" else "tickSpacing")
                        require(stride in 1..(if (type == "infinityBin") 65535 else 32767))
                        val registration = pool.get("parameters")?.takeUnless { it.isJsonNull }?.asString?.let(::hex)?.and(MASK_16) ?: ZERO
                        if (hook == NATIVE) require(registration == ZERO)
                        val parameters = BigInteger.valueOf(stride.toLong()).shiftLeft(16).or(registration)
                        val key = listOf(hex(t0), hex(t1), hex(hook), hex(manager), fee, parameters).joinToString("") { word(it) }
                        val id = pool.text("address").lowercase().also { require(it.matches(Regex("^0x[0-9a-f]{64}$"))) }
                        require(Hash.sha3("0x$key").equals(id, true)) { "Infinity pool key does not match its ID" }
                        pool.addProperty("address", id); pool.addProperty("parameters", "0x" + word(parameters)); pool.addProperty("hooks", hook)
                    } else {
                        pool.addProperty("address", address(pool.text("address")).also { require(it != NATIVE) })
                        require(t0 != NATIVE)
                    }
                }
            }
            return PancakeAmmQuote(raw, expectedChainId, input, output, expectedAmount, outputAmount)
        }

        private fun registryCall(pool: JsonObject) = if (pool.get("factoryLookup")?.asString == "pair")
            call(STABLE_FACTORY, "find_pool_for_coins(address,address,uint256)", hex(pool.text("token0")), hex(pool.text("token1")), pool.uint("factoryIndex"))
        else call(STABLE_FACTORY, "pool_list(uint256)", pool.uint("factoryIndex"))

        private fun stateWords(value: String): List<BigInteger> {
            require(value.matches(Regex("^0x(?:[0-9a-fA-F]{64}){1,256}$"))) { "Malformed pool state" }
            return value.drop(2).chunked(64).map { it.toBigInteger(16) }
        }
        private fun addressWord(value: BigInteger) = "0x" + value.toString(16).padStart(40, '0').also {
            require(value.signum() > 0 && value.bitLength() <= 160)
        }

        private fun JsonObject.text(key: String): String = requireNotNull(get(key)?.takeIf { it.isJsonPrimitive }) { "Missing $key" }.asString
        private fun JsonObject.uint(key: String): BigInteger = text(key).also { require(it.matches(Regex("[0-9]{1,78}"))) }.toBigInteger().also { require(it.bitLength() <= 256) }
        private fun JsonObject.int(key: String) = uint(key).intValueExact()
        private fun address(value: String) = value.lowercase().also { require(it.matches(Regex("^0x[0-9a-f]{40}$"))) }
        private fun hex(value: String) = value.removePrefix("0x").also { require(it.matches(Regex("[0-9a-fA-F]{1,64}"))) }.toBigInteger(16)
        private fun word(value: BigInteger) = value.toString(16).padStart(64, '0').also { require(value.signum() >= 0 && it.length == 64) }
        private fun call(address: String, signature: String, vararg args: BigInteger) = PancakePoolCall(address.lowercase(), Hash.sha3String(signature).take(10) + args.joinToString("") { word(it) })
        private fun amount(currency: JsonObject, value: BigInteger) = JsonObject().apply {
            add("currency", currency); addProperty("numerator", value.toString()); addProperty("denominator", "1")
            addProperty("decimalScale", BigInteger.TEN.pow(currency.get("decimals").asInt).toString())
        }
        private fun fraction(numerator: BigInteger, denominator: BigInteger) = JsonObject().apply {
            addProperty("numerator", numerator.toString()); addProperty("denominator", denominator.toString()); addProperty("isPercent", true)
        }
        private fun signedTick(value: BigInteger): Int = (if (value.testBit(255)) value - ONE.shiftLeft(256) else value).intValueExact().also { require(it in -887272..887272) }
        private fun poolType(type: String) = when (type) { "v2" -> 0; "v3" -> 1; "stableswap" -> 2; "infinityCl" -> 3; "infinityBin" -> 4; "infinityStable" -> 5; else -> error("Unsupported pool") }
        private fun routeType(pool: Int) = when (pool) { 3 -> 5; 4 -> 6; 5 -> 7; else -> pool }
    }
}

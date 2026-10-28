package io.horizontalsystems.walletkit.modules.multiswap.providers

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Test
import org.web3j.utils.Numeric
import java.math.BigInteger

class PancakeSwapCalldataTest {
    private val gson = Gson()
    private fun fixture(name: String): JsonObject = gson.fromJson(javaClass.classLoader!!
        .getResource("pancake-swap-$name.json")!!.readText(), JsonObject::class.java)
    private fun order(name: String): PancakeSwapOrder {
        val f = fixture(name)
        val request = f.getAsJsonObject("request")
        val bridge = request.getAsJsonArray("commands").first { it.asJsonObject["command"].asString == "BRIDGE" }
            .asJsonObject.getAsJsonObject("data")
        val swap = request.getAsJsonArray("commands").first { it.asJsonObject["command"].asString == "SWAP" }
            .asJsonObject.getAsJsonObject("data")
        val trade = swap.getAsJsonObject("trade")
        val quote = f.getAsJsonObject("quote")
        val expected = PancakeSwapExpectation(swap["originChainId"].asLong, quote["srcToken"].asString,
            quote["dstToken"].asString, quote["inputAmount"].asString.toBigInteger(),
            trade.getAsJsonObject("outputAmount")["numerator"].asString.toBigInteger(), quote)
        val origin = request["originChainId"].asLong
        val data = gson.fromJson(bridge["bridgeTransactionData"], PancakeBridgeQuote.BridgeData::class.java)
        return PancakeSwapOrder(origin, request["destinationChainId"].asLong, request["inputToken"].asString,
            request["outputToken"].asString, request["inputAmount"].asString.toBigInteger(),
            bridge["inputToken"].asString, bridge["outputToken"].asString, bridge["inputAmount"].asString.toBigInteger(),
            data, expected.takeIf { it.chainId == origin }, expected.takeUnless { it.chainId == origin },
            bridge["originChainRecipient"].asString, request["recipientOnDestChain"].asString,
            data.quoteTimestamp!! + 5)
    }
    private fun calldata(name: String) = fixture(name).getAsJsonObject("response")
        .getAsJsonObject("transactionData")["calldata"].asString

    @Test fun `both native swap bridge directions require canonical router checks and retain exact budget`() {
        for (name in listOf("eth-bnb-real", "bnb-eth-real")) {
            val order = order(name)
            val input = calldata(name)
            val checks = PancakeCrossChainTransaction.routerChecks(order, input)
            assertEquals(1, checks.size)
            assertEquals(56L, checks.single().chainId)
            assertEquals("0x3ad6ac4068569a9970fcc5868cabca96e34d5fc3", checks.single().router)
            assertThrows(IllegalArgumentException::class.java) {
                PancakeCrossChainTransaction.buildWithSwaps(order, input, emptyList())
            }
            val built = PancakeCrossChainTransaction.buildWithSwaps(order, input, checks)
            assertEquals(PancakeCrossChainTransaction.chain(order.origin).sender, built.to)
            assertEquals(order.amount, built.value)
            assertTrue(built.input.size < Numeric.hexStringToByteArray(input).size)
            val normalized = Numeric.toHexStringNoPrefix(built.input)
            val deadline = if (order.sourceSwap != null) order.now + 300 else order.bridgeData.fillDeadline!!
            assertTrue(normalized.contains(deadline.toString(16).padStart(64, '0')))
            val swap = order.sourceSwap ?: order.destinationSwap!!
            val exactFunding = swap.inputToken.removePrefix("0x").lowercase().padStart(64, '0') +
                swap.amountIn.toString(16).padStart(64, '0') + "1".padStart(64, '0') +
                checks.single().router.removePrefix("0x").padStart(64, '0')
            assertTrue(normalized.contains(exactFunding))
        }
    }

    @Test fun `accepts captured V2 StableSwap and Infinity source swaps with zero native value`() {
        for (name in listOf("v2-real", "stableswap-real", "infinityCl-real")) {
            val order = order(name)
            val checks = PancakeCrossChainTransaction.routerChecks(order, calldata(name))
            assertEquals(1, checks.size)
            assertEquals(56L, checks.single().chainId)
            val built = PancakeCrossChainTransaction.buildWithSwaps(order, calldata(name), checks)
            assertEquals(BigInteger.ZERO, built.value)
            assertEquals(PancakeCrossChainTransaction.chain(56).sender, built.to)
        }
    }

    @Test fun `rejects changed parties bridge amounts tokens chains and minimum output`() {
        val o = order("eth-bnb-real")
        val c = calldata("eth-bnb-real")
        val other = "0x2222222222222222222222222222222222222222"
        val changed = listOf(o.copy(recipientAddress = other), o.copy(senderAddress = other),
            o.copy(amount = o.amount + BigInteger.ONE), o.copy(bridgeAmount = o.bridgeAmount + BigInteger.ONE),
            o.copy(bridgeInput = other), o.copy(bridgeOutput = other), o.copy(destination = 42161),
            o.copy(destinationSwap = o.destinationSwap!!.copy(minimumOut = o.destinationSwap.minimumOut + BigInteger.ONE)))
        changed.forEach { bad -> assertThrows(IllegalArgumentException::class.java) {
            PancakeCrossChainTransaction.routerChecks(bad, c)
        } }
    }

    @Test fun `rejects altered path and external recipients even with a registry approved router`() {
        val o = order("eth-bnb-real")
        val c = calldata("eth-bnb-real")
        val custodian = PancakeCrossChainTransaction.chain(56).adapter.removePrefix("0x").lowercase()
        val recipientWord = custodian.padStart(64, '0')
        val bad = c.lowercase().replace(recipientWord, "2".repeat(40).padStart(64, '0'))
        assertThrows(IllegalArgumentException::class.java) { PancakeCrossChainTransaction.routerChecks(o, bad) }
        val path = "2170ed0880ac9a755fd29b2688956bd959f933f8000064bb4cdb9cbd36b01bd1cbaebf2de08d9173bc095c"
        val altered = c.lowercase().replace(path, path.replace("000064", "0001f4"))
        assertNotEquals(c.lowercase(), altered)
        assertThrows(IllegalArgumentException::class.java) { PancakeCrossChainTransaction.routerChecks(o, altered) }
    }

    @Test fun `rejects fee commands Permit2 payers and an unverified replacement router`() {
        val o = order("eth-bnb-real")
        val c = calldata("eth-bnb-real").lowercase()
        val codes = "2".padStart(64, '0') + "000c" + "0".repeat(60)
        val fee = c.replace(codes, "2".padStart(64, '0') + "060c" + "0".repeat(60))
        assertNotEquals(c, fee)
        assertThrows(IllegalArgumentException::class.java) { PancakeCrossChainTransaction.routerChecks(o, fee) }
        val expectation = o.destinationSwap!!
        val prefix = "2".padStart(64, '0') + expectation.amountIn.toString(16).padStart(64, '0') +
            expectation.minimumOut.toString(16).padStart(64, '0') + "a0".padStart(64, '0')
        val payer = c.replace(prefix + "0".repeat(64), prefix + "1".padStart(64, '0'))
        assertNotEquals(c, payer)
        assertThrows(IllegalArgumentException::class.java) { PancakeCrossChainTransaction.routerChecks(o, payer) }
        val checks = PancakeCrossChainTransaction.routerChecks(o, c)
        assertThrows(IllegalArgumentException::class.java) {
            PancakeCrossChainTransaction.buildWithSwaps(o, c, listOf(checks.single().copy(router = o.recipientAddress)))
        }
    }

    @Test fun `rejects malformed ABI before reading remote offsets`() {
        val o = order("eth-bnb-real")
        for (bad in listOf("0x", "0x27ad57d5", "0x27ad57d5" + "f".repeat(64), calldata("eth-bnb-real").dropLast(12))) {
            assertThrows(IllegalArgumentException::class.java) { PancakeCrossChainTransaction.routerChecks(o, bad) }
        }
    }
}

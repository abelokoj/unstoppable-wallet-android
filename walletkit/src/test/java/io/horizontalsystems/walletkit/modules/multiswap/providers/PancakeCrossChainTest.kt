package io.horizontalsystems.walletkit.modules.multiswap.providers

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.horizontalsystems.walletkit.modules.multiswap.history.SwapStatus
import org.junit.Assert.*
import org.junit.Test
import org.web3j.utils.Numeric
import java.math.BigInteger

class PancakeCrossChainTest {
    private val owner = "0x1111111111111111111111111111111111111111"
    private val gson = Gson()
    private fun fixture(kind: String): JsonObject = gson.fromJson(
        javaClass.classLoader!!.getResource("pancake-crosschain-$kind.json")!!.readText(), JsonObject::class.java
    )
    private fun quote(kind: String = "erc20") = gson.fromJson(fixture(kind).get("metadata"), PancakeBridgeQuote::class.java)
    private fun validate(q: PancakeBridgeQuote, now: Long = quote().bridgeTransactionData!!.quoteTimestamp!! + 5) =
        q.validated(8453, 42161, quote().inputToken!!, quote().outputToken!!, BigInteger("100000000"), now)

    @Test fun `local ERC20 transaction matches public service except empty Permit2 data`() {
        assertEncoding("erc20")
    }

    @Test fun `native ETH bridge unwraps on destination and attaches exact native value`() {
        assertEncoding("native")
    }

    private fun assertEncoding(kind: String) {
        val q = quote(kind)
        val data = q.validated(8453, 42161, q.inputToken!!, q.outputToken!!, q.amount!!.toBigInteger(),
            q.bridgeTransactionData!!.quoteTimestamp!! + 5)
        val built = PancakeCrossChainTransaction.build(8453, 42161, q.inputToken!!, q.outputToken!!,
            q.amount!!.toBigInteger(), data, owner, owner)
        val reference = fixture(kind).getAsJsonObject("calldata").getAsJsonObject("transactionData")
        assertEquals(reference.get("router").asString.lowercase(), built.to.lowercase())
        // Captured API calldata includes an unused Permit2 struct. The wallet uses ordinary approve/transferFrom.
        val wire = Numeric.hexStringToByteArray(reference.get("calldata").asString)
        val orderStart = 4 + BigInteger(1, wire.copyOfRange(4, 36)).intValueExact()
        val permitStart = orderStart + BigInteger(1, wire.copyOfRange(orderStart + 96, orderStart + 128)).intValueExact()
        val withoutPermit = wire.copyOfRange(0, permitStart) + ByteArray(32)
        assertArrayEquals(withoutPermit, built.input)
        assertEquals(if (kind == "native") q.amount!!.toBigInteger() else BigInteger.ZERO, built.value)
    }

    @Test fun `rejects a quote for changed chains tokens or input amount`() {
        val q = quote()
        listOf(q.copy(originChainId = 1), q.copy(destinationChainId = 56),
            q.copy(inputToken = owner), q.copy(outputToken = owner), q.copy(amount = "99999999"),
            q.copy(supported = false), q.copy(isAmountTooLow = true), q.copy(bridgeTransactionData = null)
        ).forEach { bad -> assertThrows(IllegalArgumentException::class.java) { validate(bad) } }
    }

    @Test fun `rejects stale future expired and excessively long quotes`() {
        val q = quote()
        val b = q.bridgeTransactionData!!
        val now = b.quoteTimestamp!! + 5
        validate(q.copy(bridgeTransactionData = b.copy(quoteTimestamp = now - 180)), now)
        listOf(b.copy(quoteTimestamp = now - 301), b.copy(quoteTimestamp = now + 31),
            b.copy(fillDeadline = now), b.copy(fillDeadline = now + 21601),
            b.copy(exclusivityDeadline = -1), b.copy(exclusiveRelayer = "0x1234")
        ).forEach { bad -> assertThrows(IllegalArgumentException::class.java) { validate(q.copy(bridgeTransactionData = bad), now) } }
    }

    @Test fun `rejects invalid amounts fees and deposit limits`() {
        val q = quote()
        val b = q.bridgeTransactionData!!
        listOf(b.copy(outputAmount = "0"), b.copy(outputAmount = "-1"),
            b.copy(outputAmount = BigInteger.ONE.shiftLeft(256).toString()),
            b.copy(totalRelayFee = "-1"), b.copy(totalRelayFee = "100000000"),
            b.copy(totalRelayFee = null)
        ).forEach { bad -> assertThrows(IllegalArgumentException::class.java) { validate(q.copy(bridgeTransactionData = bad)) } }
        assertThrows(IllegalArgumentException::class.java) { validate(q.copy(limits = q.limits!!.copy(minDeposit = "100000001"))) }
        assertThrows(IllegalArgumentException::class.java) { validate(q.copy(limits = q.limits!!.copy(maxDeposit = "99999999"))) }
    }

    @Test fun `builder rejects reserved recipients unsupported chains and non integral input`() {
        val q = quote()
        val b = validate(q)
        for (recipient in listOf(PancakeCrossChainTransaction.NATIVE, "0x" + "0".repeat(39) + "1", "0x1234")) {
            assertThrows(IllegalArgumentException::class.java) {
                PancakeCrossChainTransaction.build(8453, 42161, q.inputToken!!, q.outputToken!!,
                    q.amount!!.toBigInteger(), b, owner, recipient)
            }
        }
        assertThrows(IllegalArgumentException::class.java) { PancakeCrossChainTransaction.chain(137) }
        assertThrows(ArithmeticException::class.java) {
            PancakeCrossChainTransaction.rawAmount("1.0000001".toBigDecimal(), 6)
        }
        assertThrows(IllegalArgumentException::class.java) { PancakeCrossChainTransaction.rawAmount(BigInteger.ONE.shiftLeft(256).toBigDecimal(), 0) }
    }

    @Test fun `status needs matching transaction chains assets and a confirmed fill`() {
        val s = status()
        assertEquals(SwapStatus.Completed, map(s))
        assertNull(map(s.copy(status = "NEW_UNKNOWN_STATUS")))
        for (bad in listOf(s.copy(transactionId = "0x" + "b".repeat(64)), s.copy(originChainId = 1),
            s.copy(destinationChainId = 56), s.copy(inputToken = owner), s.copy(outputToken = owner))) {
            assertThrows(IllegalArgumentException::class.java) { map(bad) }
        }
        assertNull(map(s.copy(data = emptyList())))
        assertNull(map(s.copy(data = listOf(s.data!!.single().copy(status = PancakeBridgeStatus.CommandStatus("PENDING"))))))
        assertEquals(SwapStatus.ActionRequired, map(s.copy(status = "PARTIAL_SUCCESS")))
        assertEquals(SwapStatus.ActionRequired, map(s.copy(outputAmount = "1")))
        assertEquals(SwapStatus.Swapping, map(s.copy(status = "BRIDGE_PENDING")))
    }

    private fun status(): PancakeBridgeStatus = gson.fromJson("""
        {"status":"SUCCESS","originChainId":8453,"destinationChainId":42161,
         "transactionId":"0x${"a".repeat(64)}","inputToken":"${quote().inputToken}",
         "outputToken":"${quote().outputToken}","inputAmount":"100000000","outputAmount":"99978844",
         "data":[{"command":"BRIDGE","status":{"code":"SUCCESS"},"metadata":{
           "originChainId":8453,"destinationChainId":42161,"bridgeStatus":"filled",
           "depositTxHash":"0x${"a".repeat(64)}","fillTx":"0x${"c".repeat(64)}"}}]}
    """.trimIndent(), PancakeBridgeStatus::class.java)

    private fun map(s: PancakeBridgeStatus) = s.validatedStatus("0x" + "a".repeat(64), 8453, 42161,
        quote().inputToken!!, quote().outputToken!!, BigInteger("100000000"), BigInteger("99978844"))
}

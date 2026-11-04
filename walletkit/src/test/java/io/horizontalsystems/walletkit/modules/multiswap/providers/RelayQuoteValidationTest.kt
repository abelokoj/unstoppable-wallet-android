package io.horizontalsystems.walletkit.modules.multiswap.providers

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Test
import org.web3j.utils.Numeric
import java.math.BigInteger

class RelayQuoteValidationTest {
    private val gson = Gson()
    private fun fixture(kind: String = "erc20") = gson.fromJson(javaClass.classLoader!!.getResource("relay-direct-$kind.json")!!.readText(), JsonObject::class.java)
    private fun validate(f: JsonObject, kind: String = "erc20") {
        RelayQuoteValidation.validate(gson.fromJson(f["response"], RelayApi.QuoteResponse::class.java),
            gson.fromJson(f["request"], RelayApi.QuoteRequest::class.java), if (kind == "native") 18 else 6, 18)
    }
    private fun rejected(change: (JsonObject) -> Unit) {
        val f = fixture(); change(f.getAsJsonObject("response"))
        assertThrows(IllegalArgumentException::class.java) { validate(f) }
    }
    private fun deposit(f: JsonObject) = f.getAsJsonObject("response").getAsJsonArray("steps").single {
        it.asJsonObject["id"].asString == "deposit"
    }.asJsonObject.getAsJsonArray("items").single().asJsonObject.getAsJsonObject("data")

    @Test fun `captured native and token quotes validate without wallet fees`() {
        validate(fixture()); validate(fixture("native"), "native")
    }
    @Test fun `changed recipient and sender fail`() {
        for (field in listOf("recipient", "sender")) rejected { it.getAsJsonObject("details").addProperty(field, "0x" + "44".repeat(20)) }
    }
    @Test fun `changed source or destination currency chain and decimals fail`() {
        for (side in listOf("currencyIn", "currencyOut")) {
            rejected { it.getAsJsonObject("details").getAsJsonObject(side).getAsJsonObject("currency").addProperty("address", "0x" + "44".repeat(20)) }
            rejected { it.getAsJsonObject("details").getAsJsonObject(side).getAsJsonObject("currency").addProperty("chainId", 56) }
            rejected { it.getAsJsonObject("details").getAsJsonObject(side).getAsJsonObject("currency").addProperty("decimals", 0) }
        }
    }
    @Test fun `missing identity metadata fails closed`() {
        rejected { it.getAsJsonObject("details").remove("recipient") }
        rejected { it.getAsJsonObject("details").getAsJsonObject("currencyOut").remove("currency") }
    }
    @Test fun `input amount and output minimum must match requested intent`() {
        rejected { it.getAsJsonObject("details").getAsJsonObject("currencyIn").addProperty("amount", "1000001") }
        for (value in listOf("0", "-1", "1", "999999999999999999999")) rejected {
            it.getAsJsonObject("details").getAsJsonObject("currencyOut").addProperty("minimumAmount", value)
        }
    }
    @Test fun `nonzero wallet fee and foreign approval spender fail`() {
        rejected { it.getAsJsonObject("fees").getAsJsonObject("app").addProperty("amount", "1") }
        rejected {
            val tx = it.getAsJsonArray("steps").first().asJsonObject.getAsJsonArray("items").single().asJsonObject.getAsJsonObject("data")
            tx.addProperty("data", "0x095ea7b3" + "44".repeat(20).padStart(64, '0') + "f4240".padStart(64, '0'))
        }
    }
    @Test fun `extra signature steps and changed request IDs fail`() {
        rejected { it.getAsJsonArray("steps").first().asJsonObject.addProperty("kind", "signature") }
        rejected { it.getAsJsonArray("steps").last().asJsonObject.addProperty("requestId", "0x" + "44".repeat(32)) }
    }
    @Test fun `deposits bind chain value and complete ABI length`() {
        for (field in listOf("chainId", "value", "data")) {
            val f = fixture(); val tx = deposit(f)
            when (field) {
                "chainId" -> tx.addProperty(field, 56)
                "value" -> tx.addProperty(field, "1")
                else -> tx.addProperty(field, tx[field].asString + "00")
            }
            assertThrows(IllegalArgumentException::class.java) { validate(f) }
        }
    }
    @Test fun `native amount is exact and zero credit is rebuilt to the sender`() {
        val f = fixture("native"); val tx = deposit(f); val owner = f.getAsJsonObject("request")["user"].asString
        val amount = f.getAsJsonObject("request")["amount"].asString.toBigInteger()
        tx.addProperty("data", tx["data"].asString.take(10) + "0".repeat(64) + tx["data"].asString.drop(74))
        val built = RelayDepositTransaction.build(1, RelayDepositTransaction.NATIVE, amount, owner, gson.fromJson(tx, RelayApi.TransactionData::class.java))
        assertEquals(amount, built.value)
        assertEquals(owner.drop(2).padStart(64, '0'), Numeric.toHexString(built.input).substring(10, 74))
        tx.addProperty("value", (amount + BigInteger.ONE).toString())
        assertThrows(IllegalArgumentException::class.java) { validate(f, "native") }
    }
    @Test fun `allowance consuming overload is replaced with explicit exact amount`() {
        val f = fixture(); val tx = deposit(f); val data = tx["data"].asString
        val selector = org.web3j.crypto.Hash.sha3String("depositErc20(address,address,bytes32)").take(10)
        tx.addProperty("data", selector + data.substring(10, 138) + data.substring(202))
        validate(f)
        val req = gson.fromJson(f["request"], RelayApi.QuoteRequest::class.java)
        val built = RelayDepositTransaction.build(1, req.originCurrency, req.amount.toBigInteger(), req.user, gson.fromJson(tx, RelayApi.TransactionData::class.java))
        assertEquals(data, Numeric.toHexString(built.input))
    }
    @Test fun `unverified networks and malformed uints fail before signing`() {
        val f = fixture(); val req = gson.fromJson(f["request"], RelayApi.QuoteRequest::class.java)
        val tx = gson.fromJson(deposit(f), RelayApi.TransactionData::class.java)
        assertThrows(IllegalArgumentException::class.java) { RelayDepositTransaction.build(250, req.originCurrency, req.amount.toBigInteger(), req.user, tx.copy(chainId = 250)) }
        for (value in listOf("-1", "0x", "0x" + "f".repeat(65), "1.0", "+1", "1".repeat(79))) {
            assertThrows(IllegalArgumentException::class.java) { RelayDepositTransaction.uint(value) }
        }
    }
}

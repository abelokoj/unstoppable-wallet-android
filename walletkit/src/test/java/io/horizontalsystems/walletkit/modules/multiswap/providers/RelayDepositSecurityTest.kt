package io.horizontalsystems.walletkit.modules.multiswap.providers

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.horizontalsystems.marketkit.models.*
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.InvocationTargetException
import java.math.BigDecimal

class RelayDepositSecurityTest {
    private val gson = Gson()
    private val owner = "0x" + "11".repeat(20)
    private val usdc = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"
    private fun fixture() = gson.fromJson(javaClass.classLoader!!.getResource("relay-direct-erc20.json")!!.readText(), JsonObject::class.java).getAsJsonObject("response")
    private fun deposit(response: JsonObject) = response.getAsJsonArray("steps").single { it.asJsonObject["id"].asString == "deposit" }.asJsonObject.getAsJsonArray("items").single().asJsonObject.getAsJsonObject("data")
    private fun validate(response: JsonObject): Any {
        val token = Token(Coin("usdc", "USD Coin", "USDC"), Blockchain(BlockchainType.Ethereum, "Ethereum", null), TokenType.Eip20(usdc), 6)
        val method = RelayProvider::class.java.getDeclaredMethod("validatedDepositTransaction", RelayApi.QuoteResponse::class.java, Token::class.java, BigDecimal::class.java, String::class.java)
        method.isAccessible = true
        try { return requireNotNull(method.invoke(RelayProvider, gson.fromJson(response, RelayApi.QuoteResponse::class.java), token, BigDecimal.ONE, owner)) }
        catch (error: InvocationTargetException) { throw error.targetException }
    }
    @Test fun `captured exact deposit remains accepted`() { validate(fixture()) }
    @Test fun `audit unrelated-token unlimited approval is rejected`() {
        val response = fixture()
        deposit(response).apply {
            addProperty("to", "0x" + "44".repeat(20))
            addProperty("data", "0x095ea7b3" + "99".repeat(20).padStart(64, '0') + "f".repeat(64))
        }
        assertThrows(IllegalArgumentException::class.java) { validate(response) }
    }
    @Test fun `unknown selector at canonical depository is rejected`() {
        val response = fixture(); val tx = deposit(response)
        tx.addProperty("data", "0xdeadbeef" + tx["data"].asString.drop(10))
        assertThrows(IllegalArgumentException::class.java) { validate(response) }
    }
    @Test fun `changed deposit token is rejected`() {
        val response = fixture(); val tx = deposit(response); val data = tx["data"].asString
        tx.addProperty("data", data.take(74) + "44".repeat(20).padStart(64, '0') + data.drop(138))
        assertThrows(IllegalArgumentException::class.java) { validate(response) }
    }
    @Test fun `changed deposit amount is rejected`() {
        val response = fixture(); val tx = deposit(response); val data = tx["data"].asString
        tx.addProperty("data", data.take(138) + "f4241".padStart(64, '0') + data.drop(202))
        assertThrows(IllegalArgumentException::class.java) { validate(response) }
    }
    @Test fun `crediting a different depositor is rejected`() {
        val response = fixture(); val tx = deposit(response); val data = tx["data"].asString
        tx.addProperty("data", data.take(10) + "44".repeat(20).padStart(64, '0') + data.drop(74))
        assertThrows(IllegalArgumentException::class.java) { validate(response) }
    }
    @Test fun `multiple deposit transactions are rejected`() {
        val response = fixture(); val steps = response.getAsJsonArray("steps")
        steps.add(steps.single { it.asJsonObject["id"].asString == "deposit" }.deepCopy())
        assertThrows(IllegalArgumentException::class.java) { validate(response) }
    }
}

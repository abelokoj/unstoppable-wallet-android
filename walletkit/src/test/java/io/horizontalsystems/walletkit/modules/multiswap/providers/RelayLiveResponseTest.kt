package io.horizontalsystems.walletkit.modules.multiswap.providers

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Replays read-only public responses captured with synthetic addresses; CI makes no network calls. */
@RunWith(Parameterized::class)
class RelayLiveResponseTest(private val name: String, private val case: JsonObject) {
    @Test fun `actual provider response satisfies the wallet trust boundary`() {
        val gson = Gson()
        RelayQuoteValidation.validate(gson.fromJson(case["response"], RelayApi.QuoteResponse::class.java),
            gson.fromJson(case["request"], RelayApi.QuoteRequest::class.java),
            case["inputDecimals"].asInt, case["outputDecimals"].asInt)
    }
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> {
            val text = RelayLiveResponseTest::class.java.classLoader!!.getResource("relay-live-verification.json")!!.readText()
            return Gson().fromJson(text, JsonObject::class.java).getAsJsonArray("cases").map {
                arrayOf(it.asJsonObject["name"].asString, it.asJsonObject)
            }
        }
    }
}

package io.horizontalsystems.walletkit.modules.multiswap.providers

import com.google.gson.Gson
import io.horizontalsystems.marketkit.models.Blockchain
import io.horizontalsystems.marketkit.models.BlockchainType
import io.horizontalsystems.marketkit.models.Coin
import io.horizontalsystems.marketkit.models.Token
import io.horizontalsystems.marketkit.models.TokenType
import io.horizontalsystems.walletkit.entities.Address
import io.horizontalsystems.walletkit.modules.multiswap.SwapCommitFailedException
import io.horizontalsystems.walletkit.modules.multiswap.SwapCommitFailure
import io.horizontalsystems.walletkit.modules.multiswap.SwapFinalQuote
import io.horizontalsystems.walletkit.modules.multiswap.SwapQuote
import io.horizontalsystems.walletkit.modules.multiswap.sendtransaction.SendTransactionData
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy
import java.math.BigDecimal
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

class StellarSwapProviderTest {
    private val gson = Gson()
    private val token = Token(
        Coin("stellar", "Stellar", "XLM"), Blockchain(BlockchainType.Stellar, "Stellar", null),
        TokenType.Native, 7,
    )
    private val route = gson.fromJson(
        """{"expectedBuyAmount":"2","minBuyAmount":"1.9","previewToken":"preview",
            "execution":{"method":"signed_transaction","transactions":[{"kind":"stellar","xdr":"reviewed-xdr"}]}}""",
        UnstoppableAPI.Response.Route::class.java,
    )
    private val calls = mutableListOf<String>()
    private var commit = UnstoppableAPI.Response.Commit("ok", "order-id")
    private var commitError: Throwable? = null
    private var rateRequest: UnstoppableAPI.Request.Rate? = null
    private var previewRequest: UnstoppableAPI.Request.Preview? = null
    private val api = Proxy.newProxyInstance(
        UnstoppableAPI::class.java.classLoader, arrayOf(UnstoppableAPI::class.java),
    ) { _, method, args ->
        calls += method.name
        when (method.name) {
            "preview" -> { previewRequest = args!![0] as UnstoppableAPI.Request.Preview; route }
            "rate" -> {
                rateRequest = args!![0] as UnstoppableAPI.Request.Rate
                UnstoppableAPI.Response.Rate(listOf(route.copy(providers = listOf("STELLAR_DEX"))))
            }
            "commit" -> {
                assertEquals("preview", (args!![0] as UnstoppableAPI.Request.Commit).previewToken)
                val error = commitError
                if (error != null) {
                    @Suppress("UNCHECKED_CAST")
                    val continuation = args.last() as Continuation<UnstoppableAPI.Response.Commit>
                    continuation.resumeWith(Result.failure(error))
                    COROUTINE_SUSPENDED
                } else {
                    commit
                }
            }
            "signed" -> UnstoppableAPI.Response.Signed("order-id", "signed")
            else -> error("Unexpected API call: ${method.name}")
        }
    } as UnstoppableAPI
    private val provider = StellarSwapProvider(api) { "source" }

    private suspend fun preview(selectedProvider: String = "SOROSWAP", recipient: Address? = null): SwapFinalQuote =
        provider.fetchFinalQuote(
            token, token, BigDecimal.ONE, null,
            SwapQuote(
                BigDecimal("2"), token, token, BigDecimal.ONE, null, null,
                StellarSwapProvider.StellarSwapQuoteExtraData(selectedProvider),
            ),
            recipient, BigDecimal.ONE,
        )

    private suspend fun failure(quote: SwapFinalQuote): SwapCommitFailure = try {
        provider.commit(quote)
        error("Commit should fail")
    } catch (e: SwapCommitFailedException) {
        e.failure
    }

    @Test
    fun `preview creates no order and commit preserves the reviewed xdr exactly once`() = runBlocking {
        val quote = preview()
        assertEquals(listOf("preview"), calls)
        assertNull(quote.providerSwapId)
        assertEquals("source", previewRequest?.sourceAddress)
        assertEquals("SOROSWAP", previewRequest?.provider)

        val result = provider.commit(quote)
        assertEquals("order-id", result.providerSwapId)
        assertSame(quote.sendTransactionData, result.quote.sendTransactionData)
        assertEquals("reviewed-xdr", (result.quote.sendTransactionData as SendTransactionData.Stellar.WithTransactionEnvelope).transactionEnvelope)
        assertNull(result.quote.providerContext)
        assertEquals(SwapCommitFailure.RefreshRequired, failure(quote))
        assertEquals(1, calls.count { it == "commit" })
    }

    @Test
    fun `commit cannot replace the reviewed signed execution`() = runBlocking {
        commit = commit.copy(execution = route.execution)
        assertEquals(SwapCommitFailure.Failed, failure(preview()))
    }

    @Test
    fun `a refused commit uses the shared failure and cannot be retried`() = runBlocking {
        commitError = UnstoppableAPI.CommitException(null, "rate_changed", null, null)
        val quote = preview()
        assertEquals(SwapCommitFailure.RateChanged, failure(quote))
        assertEquals(SwapCommitFailure.RefreshRequired, failure(quote))
        assertEquals(1, calls.count { it == "commit" })
    }

    @Test
    fun `a distinct recipient retains the current compatible-provider waterfall`() = runBlocking {
        preview("AQUARIUS", Address("recipient"))
        assertEquals(setOf("SOROSWAP", "STELLAR_DEX"), rateRequest?.providers)
        assertEquals("STELLAR_DEX", previewRequest?.provider)
        assertEquals("recipient", previewRequest?.destinationAddress)
    }
}

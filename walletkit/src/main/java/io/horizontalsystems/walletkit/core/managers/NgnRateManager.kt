package io.horizontalsystems.walletkit.core.managers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.math.BigDecimal

/**
 * Open Swap fork: USD -> NGN conversion for currencies the Horizontal Systems backend
 * does not serve.
 *
 * The backend whitelists currencies server-side and has no NGN, so asking it for NGN
 * prices returns nothing. Prices are fetched in USD and multiplied by one FX rate here.
 *
 * Deliberately NOT CoinGecko, despite coins carrying a coinGeckoId. A /simple/price call
 * would send the list of coins the user holds plus their IP to a third party -- a portfolio
 * fingerprint that builds a durable profile over repeated refreshes. Routing crypto prices
 * through the HS backend is what prevents that. This endpoint is asked one identical
 * question by every user and reveals nothing about holdings.
 *
 * Lives in the wallet rather than a market-kit fork so it survives upstream syncs. An
 * earlier version patched CoinPriceSchedulerProvider inside marketkit; upstream then
 * replaced RxJava with coroutines and the patch stopped compiling.
 */
object NgnRateManager {

    const val CURRENCY = "NGN"
    private const val BASE = "USD"
    private const val REFRESH_INTERVAL_MS = 60L * 60 * 1000      // endpoint updates ~daily
    private const val STALE_LIMIT_MS = 24L * 60 * 60 * 1000      // beyond this, hide prices

    private interface Service {
        @GET("v6/latest/{base}")
        suspend fun latest(@Path("base") base: String): RateResponse
    }

    data class RateResponse(val result: String?, val rates: Map<String, Double>?)

    private val service: Service by lazy {
        Retrofit.Builder()
            .baseUrl("https://open.er-api.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(Service::class.java)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var rate: BigDecimal? = null

    @Volatile
    private var fetchedAt: Long = 0

    @Volatile
    private var inFlight = false

    /**
     * Last known rate, or null if none has been fetched or the cached one is too old.
     * Read synchronously because the price methods are not suspending.
     */
    val currentRate: BigDecimal?
        get() = rate?.takeIf { System.currentTimeMillis() - fetchedAt < STALE_LIMIT_MS }

    /** Fetches in the background if the cached rate is missing or stale. Cheap to call often. */
    fun ensureFresh() {
        val age = System.currentTimeMillis() - fetchedAt
        if (inFlight || (rate != null && age < REFRESH_INTERVAL_MS)) return
        inFlight = true
        scope.launch {
            try {
                val response = withContext(Dispatchers.IO) { service.latest(BASE) }
                val value = response.rates?.get(CURRENCY)
                if (response.result == "success" && value != null && value > 0) {
                    rate = BigDecimal.valueOf(value)
                    fetchedAt = System.currentTimeMillis()
                }
            } catch (_: Throwable) {
                // keep the previous rate; FX moves far slower than the crypto being priced,
                // so a few-hours-old rate beats a blank balance
            } finally {
                inFlight = false
            }
        }
    }

    /** True when [currencyCode] must be served by converting from USD. */
    fun handles(currencyCode: String) = currencyCode == CURRENCY

    /** The currency to actually request from marketkit for [currencyCode]. */
    fun sourceCurrency(currencyCode: String) = if (handles(currencyCode)) BASE else currencyCode
}

package io.horizontalsystems.walletkit.modules.multiswap.providers

import io.horizontalsystems.walletkit.core.App
import io.horizontalsystems.walletkit.core.chain.ChainRegistry
import io.horizontalsystems.walletkit.core.managers.APIClient
import io.horizontalsystems.walletkit.core.managers.requireSocksProxy
import io.horizontalsystems.walletkit.entities.Address
import io.horizontalsystems.walletkit.modules.multiswap.SwapFinalQuote
import io.horizontalsystems.walletkit.modules.multiswap.SwapQuote
import io.horizontalsystems.walletkit.modules.multiswap.action.ISwapProviderAction
import io.horizontalsystems.walletkit.modules.multiswap.sendtransaction.EvmTransactionData
import io.horizontalsystems.walletkit.modules.multiswap.sendtransaction.SendTransactionData
import io.horizontalsystems.walletkit.modules.multiswap.sendtransaction.SendTransactionSettings
import io.horizontalsystems.walletkit.modules.multiswap.ui.DataFieldRecipient
import io.horizontalsystems.walletkit.modules.multiswap.ui.DataFieldSlippage
import io.horizontalsystems.walletkit.modules.settings.privacy.tor.TorStatus
import io.horizontalsystems.marketkit.models.BlockchainType
import io.horizontalsystems.marketkit.models.Token
import io.horizontalsystems.marketkit.models.TokenType
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.math.BigDecimal

/**
 * Direct Relay v2 integration. Relay builds the deposit transaction without requiring an API key;
 * importantly, this client never sends appFees, so the wallet does not add an integrator fee.
 */
object RelayProvider : IMultiSwapProvider {
    override val id = UProvider.Relay.id
    override val title = UProvider.Relay.title
    override val icon = UProvider.Relay.icon
    override val type = SwapProviderType.DEX
    override val isEvm = true
    override val requireTerms = true
    override val riskLevel = RiskLevel.GOOD

    private const val nativeCurrency = RelayDepositTransaction.NATIVE

    private val directApi = APIClient
        .retrofit("https://api.relay.link", 60)
        .create(RelayApi::class.java)

    private val api: RelayApi
        get() {
            if (!App.torKitManager.isTorEnabled) return directApi
            if (App.torKitManager.torStatusFlow.value != TorStatus.Connected) {
                throw RetryableSwapError()
            }
            // Tor chooses a new SOCKS port on restart; never retain a client bound to the old port.
            return APIClient.retrofit("https://api.relay.link", 60, App.torKitManager.requireSocksProxy())
                .create(RelayApi::class.java)
        }

    private val chainIds = mapOf(
        BlockchainType.Ethereum to 1L,
        BlockchainType.Optimism to 10L,
        BlockchainType.BinanceSmartChain to 56L,
        BlockchainType.Gnosis to 100L,
        BlockchainType.Polygon to 137L,
        BlockchainType.Fantom to 250L,
        BlockchainType.ZkSync to 324L,
        BlockchainType.RobinhoodChain to 4663L,
        BlockchainType.Base to 8453L,
        BlockchainType.ArbitrumOne to 42161L,
        BlockchainType.Avalanche to 43114L,
    )

    override fun isSingleTransactionSwap(
        tokenInBlockchainTypeUid: String,
        tokenOutBlockchainTypeUid: String,
    ) = false

    override fun supports(blockchainType: BlockchainType) = chainIds.containsKey(blockchainType)

    override fun supports(tokenFrom: Token, tokenTo: Token): Boolean =
        tokenFrom.blockchainType != tokenTo.blockchainType &&
            chainIds[tokenFrom.blockchainType] in RelayDepositTransaction.chains &&
            supports(tokenFrom.blockchainType) && supports(tokenTo.blockchainType) &&
            tokenFrom.type.isRelayCurrency && tokenTo.type.isRelayCurrency

    override suspend fun fetchQuote(
        tokenIn: Token,
        tokenOut: Token,
        amountIn: BigDecimal,
    ): SwapQuote {
        val sender = SwapHelper.getSendingAddressForToken(tokenIn)
            ?: throw IllegalStateException("Relay requires a source address")
        val response = quote(
            tokenIn,
            tokenOut,
            amountIn,
            sender,
            recipient = null,
            slippage = IMultiSwapProvider.DEFAULT_SLIPPAGE,
        )
        val deposit = response.validatedDepositTransaction(tokenIn, amountIn, sender)

        val actionRequired: ISwapProviderAction? = if (tokenIn.type is TokenType.Eip20) {
            val plugin = ChainRegistry[tokenIn.blockchainType]
            val spender = deposit.to
            val allowance = plugin?.eip20Allowance(tokenIn, spender)
            plugin?.eip20ApproveAction(allowance, amountIn, spender, tokenIn)
        } else {
            null
        }

        return SwapQuote(
            amountOut = response.outputAmount(tokenOut),
            tokenIn = tokenIn,
            tokenOut = tokenOut,
            amountIn = amountIn,
            actionRequired = actionRequired,
            estimationTime = response.details.timeEstimate,
            extraData = RelayQuoteExtraData(response.requestId),
        )
    }

    override suspend fun fetchFinalQuote(
        tokenIn: Token,
        tokenOut: Token,
        amountIn: BigDecimal,
        sendTransactionSettings: SendTransactionSettings?,
        swapQuote: SwapQuote,
        recipient: Address?,
        slippage: BigDecimal,
    ): SwapFinalQuote {
        val sender = SwapHelper.getSendingAddressForToken(tokenIn)
            ?: throw IllegalStateException("Relay requires a source address")
        val recipientAddress = recipient?.hex ?: SwapHelper.getReceiveAddressForToken(tokenOut)
        val response = quote(tokenIn, tokenOut, amountIn, sender, recipientAddress, slippage)
        val deposit = response.validatedDepositTransaction(tokenIn, amountIn, sender)
        val approvalSpender = deposit.to

        val fields = buildList {
            recipient?.let { add(DataFieldRecipient(it)) }
            DataFieldSlippage.getField(slippage)?.let { add(it) }
        }

        return SwapFinalQuote(
            tokenIn = tokenIn,
            tokenOut = tokenOut,
            amountIn = amountIn,
            amountOut = response.outputAmount(tokenOut),
            amountOutMin = response.minimumOutputAmount(tokenOut),
            sendTransactionData = SendTransactionData.Evm(
                transactionData = deposit,
                gasLimit = null,
            ),
            priceImpact = null,
            fields = fields,
            estimatedTime = response.details.timeEstimate,
            slippage = slippage,
            providerSwapId = response.requestId,
            approvalSpender = approvalSpender,
        )
    }

    suspend fun status(requestId: String): RelayApi.StatusResponse = api.status(requestId)

    private suspend fun quote(
        tokenIn: Token,
        tokenOut: Token,
        amountIn: BigDecimal,
        sender: String,
        recipient: String?,
        slippage: BigDecimal,
    ): RelayApi.QuoteResponse {
        val request = RelayApi.QuoteRequest(
            user = sender,
            originChainId = chainIds.getValue(tokenIn.blockchainType),
            destinationChainId = chainIds.getValue(tokenOut.blockchainType),
            originCurrency = tokenIn.relayCurrency,
            destinationCurrency = tokenOut.relayCurrency,
            amount = amountIn.movePointRight(tokenIn.decimals).toBigIntegerExact().toString(),
            recipient = recipient ?: requireNotNull(SwapHelper.getReceiveAddressForToken(tokenOut)),
            refundTo = sender,
            slippageTolerance = slippage.movePointRight(2).toBigIntegerExact().toString(),
            explicitDeposit = true,
            usePermit = false,
        )
        val response = api.quote(request)
        RelayQuoteValidation.validate(response, request, tokenIn.decimals, tokenOut.decimals)
        return response
    }

    private val Token.relayCurrency: String
        get() = when (val tokenType = type) {
            TokenType.Native -> nativeCurrency
            is TokenType.Eip20 -> tokenType.address
            else -> throw IllegalArgumentException("Relay does not support $tokenType")
        }

    private val TokenType.isRelayCurrency: Boolean
        get() = this is TokenType.Native || this is TokenType.Eip20

    private fun RelayApi.QuoteResponse.validatedDepositTransaction(
        tokenIn: Token,
        amountIn: BigDecimal,
        sender: String,
    ): EvmTransactionData = RelayQuoteValidation.deposit(
        this, chainIds.getValue(tokenIn.blockchainType), tokenIn.relayCurrency,
        amountIn.movePointRight(tokenIn.decimals).toBigIntegerExact(), sender,
    )

    private fun RelayApi.QuoteResponse.outputAmount(token: Token): BigDecimal =
        requireNotNull(details.currencyOut.amount).toBigDecimal().movePointLeft(token.decimals)

    private fun RelayApi.QuoteResponse.minimumOutputAmount(token: Token): BigDecimal =
        requireNotNull(details.currencyOut.minimumAmount).toBigDecimal().movePointLeft(token.decimals)

    data class RelayQuoteExtraData(val requestId: String) : SwapQuote.ExtraData
}

interface RelayApi {
    @POST("/quote/v2")
    suspend fun quote(@Body request: QuoteRequest): QuoteResponse

    @GET("/intents/status/v3")
    suspend fun status(@Query("requestId") requestId: String): StatusResponse

    data class QuoteRequest(
        val user: String,
        val originChainId: Long,
        val destinationChainId: Long,
        val originCurrency: String,
        val destinationCurrency: String,
        val amount: String,
        val tradeType: String = "EXACT_INPUT",
        val recipient: String? = null,
        val refundTo: String,
        val slippageTolerance: String,
        val explicitDeposit: Boolean,
        val usePermit: Boolean,
    )

    data class QuoteResponse(
        val requestId: String,
        val steps: List<Step>,
        val details: Details,
        val fees: Fees? = null,
    )

    data class Fees(val app: CurrencyAmount? = null)

    data class Step(val id: String, val items: List<StepItem>, val kind: String? = null, val requestId: String? = null)
    data class StepItem(val data: TransactionData?)
    data class TransactionData(
        val from: String,
        val to: String,
        val data: String,
        val value: String,
        val chainId: Long,
        val gas: String? = null,
    )

    data class Details(
        val currencyOut: CurrencyAmount,
        val timeEstimate: Long? = null,
        val sender: String? = null,
        val recipient: String? = null,
        val currencyIn: CurrencyAmount? = null,
        val refundCurrency: CurrencyAmount? = null,
    )

    data class Currency(val chainId: Long, val address: String, val decimals: Int)

    data class CurrencyAmount(
        val amount: String? = null,
        val amountFormatted: BigDecimal? = null,
        val minimumAmount: String? = null,
        val minimumAmountFormatted: BigDecimal? = null,
        val currency: Currency? = null,
    )

    data class StatusResponse(
        val status: String,
        val txHashes: List<String>? = null,
    )
}

package io.horizontalsystems.walletkit.modules.multiswap.providers

import io.horizontalsystems.walletkit.core.App
import io.horizontalsystems.walletkit.core.chain.ChainRegistry
import io.horizontalsystems.walletkit.core.hexStringToByteArray
import io.horizontalsystems.walletkit.core.managers.APIClient
import io.horizontalsystems.walletkit.core.tor.torcore.TorConstants
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
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Proxy

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
    override val riskLevel = RiskLevel.FAIR

    private const val nativeCurrency = "0x0000000000000000000000000000000000000000"

    private val directApi = APIClient
        .retrofit("https://api.relay.link", 60)
        .create(RelayApi::class.java)

    private val torApi by lazy {
        val proxy = Proxy(
            Proxy.Type.HTTP,
            InetSocketAddress(
                TorConstants.IP_LOCALHOST,
                TorConstants.HTTP_PROXY_PORT_DEFAULT.toInt(),
            )
        )
        APIClient
            .retrofit("https://api.relay.link", 60, proxy)
            .create(RelayApi::class.java)
    }

    private val api: RelayApi
        get() {
            if (!App.torKitManager.isTorEnabled) return directApi
            if (App.torKitManager.torStatusFlow.value != TorStatus.Connected) {
                throw RetryableSwapError()
            }
            return torApi
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
            val spender = response.approvalSpender(tokenIn) ?: deposit.to
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
        val approvalSpender = response.approvalSpender(tokenIn) ?: deposit.to

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
                transactionData = EvmTransactionData(
                    to = deposit.to,
                    value = deposit.value.toBigIntegerFlexible(),
                    input = deposit.data.hexStringToByteArray(),
                ),
                gasLimit = deposit.gas?.toLongFlexible(),
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
        val response = api.quote(
            RelayApi.QuoteRequest(
                user = sender,
                originChainId = chainIds.getValue(tokenIn.blockchainType),
                destinationChainId = chainIds.getValue(tokenOut.blockchainType),
                originCurrency = tokenIn.relayCurrency,
                destinationCurrency = tokenOut.relayCurrency,
                amount = amountIn.movePointRight(tokenIn.decimals).toBigInteger().toString(),
                recipient = recipient,
                refundTo = sender,
                slippageTolerance = slippage.movePointRight(2).toBigInteger().toString(),
                explicitDeposit = true,
                usePermit = false,
            )
        )
        response.fees?.app?.amount?.let { appFee ->
            require(appFee.toBigInteger() == BigInteger.ZERO) {
                "Relay returned an unexpected application fee"
            }
        }
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

    private fun RelayApi.QuoteResponse.depositTransaction(): RelayApi.TransactionData =
        steps.firstOrNull { it.id == "deposit" }
            ?.items
            ?.firstNotNullOfOrNull { it.data }
            ?: throw IllegalStateException("Relay quote did not include a deposit transaction")

    private fun RelayApi.QuoteResponse.validatedDepositTransaction(
        tokenIn: Token,
        amountIn: BigDecimal,
        sender: String,
    ): RelayApi.TransactionData {
        val transaction = depositTransaction()
        val expectedChainId = chainIds.getValue(tokenIn.blockchainType)
        require(transaction.chainId == expectedChainId) { "Relay returned a transaction for another chain" }
        require(transaction.from.equals(sender, ignoreCase = true)) { "Relay returned a transaction for another sender" }
        require(transaction.to.isEvmAddress()) { "Relay returned an invalid destination contract" }
        require(transaction.data.isHexData()) { "Relay returned invalid transaction data" }

        val value = transaction.value.toBigIntegerFlexible()
        val inputAmount = amountIn.movePointRight(tokenIn.decimals).toBigInteger()
        when (tokenIn.type) {
            TokenType.Native -> require(value == inputAmount) { "Relay changed the native deposit amount" }
            is TokenType.Eip20 -> require(value == BigInteger.ZERO) { "Relay attached native value to a token deposit" }
            else -> error("Unsupported Relay input token")
        }
        return transaction
    }

    private fun RelayApi.QuoteResponse.approvalSpender(tokenIn: Token): String? {
        val tokenAddress = (tokenIn.type as? TokenType.Eip20)?.address ?: return null
        val approval = steps.firstOrNull { it.id == "approve" || it.id == "approval" }
            ?.items
            ?.firstNotNullOfOrNull { it.data }
            ?: return null
        require(approval.to.equals(tokenAddress, ignoreCase = true)) {
            "Relay returned an approval for another token"
        }

        val calldata = approval.data.removePrefix("0x")
        require(calldata.length >= 136 && calldata.take(8).equals("095ea7b3", ignoreCase = true)) {
            "Relay returned an unsupported approval transaction"
        }
        val spender = "0x${calldata.substring(8, 72).takeLast(40)}"
        require(spender.isEvmAddress()) { "Relay returned an invalid approval spender" }
        return spender
    }

    private fun RelayApi.QuoteResponse.outputAmount(token: Token): BigDecimal =
        details.currencyOut.amountFormatted
            ?: details.currencyOut.amount?.toBigDecimal()?.movePointLeft(token.decimals)
            ?: throw IllegalStateException("Relay quote did not include an output amount")

    private fun RelayApi.QuoteResponse.minimumOutputAmount(token: Token): BigDecimal? =
        details.currencyOut.minimumAmountFormatted
            ?: details.currencyOut.minimumAmount?.toBigDecimal()?.movePointLeft(token.decimals)

    private fun String.toBigIntegerFlexible(): BigInteger =
        if (startsWith("0x", ignoreCase = true)) drop(2).ifEmpty { "0" }.toBigInteger(16)
        else toBigInteger()

    private fun String.toLongFlexible(): Long =
        if (startsWith("0x", ignoreCase = true)) drop(2).ifEmpty { "0" }.toLong(16)
        else toLong()

    private fun String.isEvmAddress() = matches(Regex("^0x[0-9a-fA-F]{40}$"))

    private fun String.isHexData(): Boolean {
        if (!startsWith("0x") || length % 2 != 0) return false
        return drop(2).all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
    }

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

    data class Step(val id: String, val items: List<StepItem>)
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
    )

    data class CurrencyAmount(
        val amount: String? = null,
        val amountFormatted: BigDecimal? = null,
        val minimumAmount: String? = null,
        val minimumAmountFormatted: BigDecimal? = null,
    )

    data class StatusResponse(
        val status: String,
        val txHashes: List<String>? = null,
    )
}

package io.horizontalsystems.walletkit.modules.privatesend

import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import io.horizontalsystems.walletkit.R
import io.horizontalsystems.walletkit.core.providers.Translator
import io.horizontalsystems.walletkit.modules.multiswap.swapFeePaidFromAsset
import io.horizontalsystems.walletkit.modules.multiswap.providers.SwapHelper
import kotlinx.coroutines.withTimeout
import io.horizontalsystems.walletkit.core.App
import io.horizontalsystems.walletkit.core.WatchAccountException
import io.horizontalsystems.walletkit.core.ViewModelUiState
import io.horizontalsystems.walletkit.core.badge
import io.horizontalsystems.walletkit.core.ethereum.CautionViewItem
import io.horizontalsystems.walletkit.core.managers.CurrencyManager
import io.horizontalsystems.walletkit.entities.Currency
import io.horizontalsystems.walletkit.entities.SwapRecord
import io.horizontalsystems.walletkit.modules.multiswap.DepositNotSendableException
import io.horizontalsystems.walletkit.modules.multiswap.SwapCommitFailedException
import io.horizontalsystems.walletkit.modules.multiswap.SwapCommitFailure
import io.horizontalsystems.walletkit.modules.multiswap.TimerService
import io.horizontalsystems.walletkit.modules.multiswap.UnpricedDeposit
import io.horizontalsystems.walletkit.modules.xrate.XRateService
import io.horizontalsystems.walletkit.modules.multiswap.history.SwapOperation
import io.horizontalsystems.walletkit.modules.multiswap.history.SwapRecordManager
import io.horizontalsystems.walletkit.modules.multiswap.history.SwapStatus
import io.horizontalsystems.walletkit.modules.multiswap.sendtransaction.AbstractSendTransactionService
import io.horizontalsystems.walletkit.modules.multiswap.sendtransaction.SendTransactionData
import io.horizontalsystems.walletkit.modules.multiswap.sendtransaction.SendTransactionResult
import io.horizontalsystems.walletkit.modules.multiswap.sendtransaction.SendTransactionServiceFactory
import io.horizontalsystems.walletkit.modules.multiswap.ui.DataField
import io.horizontalsystems.walletkit.modules.send.SendModule
import io.horizontalsystems.marketkit.models.Token
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import kotlin.coroutines.cancellation.CancellationException

/**
 * The confirmation step of a private send: previews the route, then builds and estimates the
 * deposit transfer against the preview's stand-in address through the chain's own
 * send-transaction service. This ViewModel never builds a transaction itself.
 *
 * The preview has no side effects, so it is silently re-requested every [QUOTE_LIFETIME_SECONDS]
 * and after a failed send. The order is created only when the user presses Send: the commit
 * names the real deposit address and attachment, the transfer is rebuilt for them, signed and
 * broadcast, and its hash is reported to the backend. A spent preview is never committed twice.
 */
class PrivateSendConfirmViewModel(
    private val request: PrivateSendRequest,
    private val btcParams: PrivateSendBtcParams?,
    private val manager: PrivateSendManager,
    val sendTransactionService: AbstractSendTransactionService,
    currencyManager: CurrencyManager,
    private val timerService: TimerService,
    private val swapRecordManager: SwapRecordManager,
) : ViewModelUiState<PrivateSendConfirmUiState>() {

    private val token = request.token
    private val currency = currencyManager.baseCurrency

    // Same sources the per-chain confirmations use for the shared top section.
    val coinRate = XRateService(App.marketKit, currency).getRate(token.coin.uid)
    val contact = App.contactsRepository
        .getContactsFiltered(token.blockchainType, addressQuery = request.recipient)
        .firstOrNull()

    // The preview on screen: what Send commits.
    private var preview: PrivateSendPreview? = null
    private var preparingDeposit = true
    private var previewConsumed = false
    // A transport failure may follow a successful broadcast; never offer another deposit here.
    private var broadcastAttempted = false
    // The deposit estimate against the stand-in address, re-fed on fee-settings changes.
    private var depositData: SendTransactionData? = null
    // Stands in for the send service's cautions while the preview has nothing to estimate: the
    // only check possible before the order exists is the deposit against the balance.
    private var unpricedCaution: CautionViewItem? = null
    private var error: Throwable? = null
    private var initialLoading = true
    private var sendTransactionState = sendTransactionService.stateFlow.value
    private var timerState = timerService.stateFlow.value

    // From the moment Send is pressed until the send ends, nothing may replace the preview or the
    // data the send service holds: a refresh landing in between would commit another preview, or
    // put the stand-in deposit back under a transfer about to be signed. Also rejects a second
    // concurrent send (double-tap) so the same deposit is never sent twice.
    @Volatile
    private var isSending = false

    // The in-flight preview. A new preview cancels the previous one so two responses never
    // interleave their `preview`/`depositData` writes.
    private var previewJob: Job? = null

    // The chain service and its sub-services are plain objects; their collectors and every
    // deposit fed to them run on this one confined thread so their state stays consistent.
    // It is an IO thread: signing and broadcasting block on it.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val serviceDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val serviceScope = CoroutineScope(viewModelScope.coroutineContext + serviceDispatcher)

    init {
        viewModelScope.launch {
            sendTransactionService.stateFlow.collect {
                sendTransactionState = it
                if (preview != null && !preparingDeposit) {
                    initialLoading = initialLoading && it.loading
                }
                emitState()
            }
        }

        viewModelScope.launch {
            // A fee-settings change re-estimates the SAME deposit. Guarded in the body (a
            // downstream throw would kill viewModelScope and freeze the screen; Flow.catch
            // only sees upstream failures) so the collector survives for later changes.
            sendTransactionService.sendTransactionSettingsFlow.collect {
                if (isSending || broadcastAttempted) return@collect
                try {
                    withContext(serviceDispatcher) {
                        if (isSending || broadcastAttempted) return@withContext
                        val data = depositData ?: return@withContext
                        sendTransactionService.setSendTransactionData(data)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    error = e
                    emitState()
                }
            }
        }

        viewModelScope.launch {
            timerService.stateFlow.collect {
                timerState = it
                // The preview has no side effects, so an expired one is silently re-requested:
                // the deposit ceiling under the Send button is always one the server honours.
                if (it.timeout && !isSending && !broadcastAttempted) {
                    requestPreview()
                }
                emitState()
            }
        }

        sendTransactionService.start(serviceScope)

        requestPreview()
    }

    override fun createState(): PrivateSendConfirmUiState {
        val preview = preview
        // With no stand-in there is no estimate to show, and the send service's state describes
        // whatever it was last fed, not this preview.
        val unpriced = preview != null && preview.stubDepositAddress == null
        val refreshRequired = preview != null && !preparingDeposit && !sendTransactionState.loading &&
            !maximumAffordable(preview.depositAmount, sendTransactionState)

        return PrivateSendConfirmUiState(
            initialLoading = initialLoading,
            loading = preparingDeposit || (!unpriced && sendTransactionState.loading),
            token = token,
            recipient = request.recipient,
            amountOut = preview?.amountOut ?: request.amountOut,
            privateFee = preview?.privateFee,
            reservedAmount = preview?.refundableBuffer?.takeIf { it > BigDecimal.ZERO },
            bufferUnknown = preview != null && preview.minSellAmount == null,
            estimatedTime = preview?.estimatedTime,
            currency = currency,
            networkFee = if (unpriced) null else sendTransactionState.networkFee,
            cautions = (if (unpriced) listOfNotNull(unpricedCaution) else sendTransactionState.cautions) +
                if (refreshRequired) listOf(CautionViewItem(
                    Translator.getString(R.string.EthereumTransaction_Error_CannotEstimate_Title),
                    Translator.getString(R.string.PrivateSend_Caution_MaxUnavailable), CautionViewItem.Type.Error,
                )) else emptyList(),
            transactionFields = if (unpriced) emptyList() else sendTransactionState.fields,
            canSend = preview != null && !preparingDeposit && !previewConsumed && !broadcastAttempted &&
                !isSending && !refreshRequired && error == null &&
                (if (unpriced) unpricedCaution == null else !sendTransactionState.loading && sendTransactionState.sendable),
            refreshRequired = refreshRequired && !isSending && !broadcastAttempted,
            hasSettings = sendTransactionService.hasSettings,
            hasNonceSettings = sendTransactionService.hasNonceSettings,
            error = error,
        )
    }

    /**
     * The error screen's action: previews again from the just-opened state. The old preview and
     * deposit data are dropped first, so a stale preview can never sit under a live Send button.
     */
    fun refresh() {
        if (isSending || broadcastAttempted) return

        preview = null
        depositData = null
        initialLoading = true
        emitState()

        // `initialLoading` only clears when the service's StateFlow emits after the new preview
        // is set, and StateFlow drops a value equal to the current one. A re-estimate of a
        // like-for-like deposit (same amount, fee and fields) lands on an identical state, so
        // without a fresh uuid nothing is emitted and the screen spins forever.
        sendTransactionService.refreshUuid()

        requestPreview()
    }

    private fun requestPreview() {
        if (isSending || broadcastAttempted) return

        previewJob?.cancel()
        preparingDeposit = true
        emitState()
        previewJob = viewModelScope.launch(serviceDispatcher) {
            try {
                error = null

                val activeAccount = App.accountManager.activeAccount
                if (activeAccount == null ||
                    activeAccount.isWatchAccount ||
                    activeAccount.id != request.accountId
                ) {
                    throw WatchAccountException()
                }

                val effectiveRequest = if (request.sendMax) withTimeout(45_000) { prepareMaximumRequest() } else request
                ensureActive()
                checkActiveAccount()
                val preview = manager.preview(effectiveRequest)

                ensureActive()
                checkActiveAccount()
                previewConsumed = false

                // Decided before the preview is published, so the button never reads the new
                // preview against the previous preview's caution.
                unpricedCaution = if (preview.stubDepositAddress == null) {
                    UnpricedDeposit.caution(token, preview.depositAmount, availableBalance())
                } else {
                    null
                }

                this@PrivateSendConfirmViewModel.preview = preview
                emitState()

                // Estimated against the stand-in with no attachment: the deposit address and its
                // attachment only exist after the commit, and send() rebuilds against them.
                val stubDepositAddress = preview.stubDepositAddress
                if (stubDepositAddress != null) {
                    val data = PrivateSendDepositBuilder.build(
                        token = token,
                        amount = preview.depositAmount,
                        address = stubDepositAddress,
                        attachment = null,
                        btcParams = btcParams,
                    )
                    depositData = data
                    sendTransactionService.setSendTransactionData(data)
                } else {
                    // Nothing to estimate before the order names its address: the screen settles
                    // without a fee, which the service's state collector would otherwise signal.
                    depositData = null
                    initialLoading = false
                    emitState()
                }

                preparingDeposit = false
                sendTransactionState = sendTransactionService.stateFlow.value
                initialLoading = preview.stubDepositAddress != null && sendTransactionState.loading
                emitState()

                // CountDownTimer binds to the current thread's Looper, so the expiry timer
                // must start on Main — this coroutine runs on the service thread.
                withContext(Dispatchers.Main) {
                    timerService.start(QUOTE_LIFETIME_SECONDS)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                initialLoading = false
                error = e
                emitState()
            }
        }
    }

    private fun checkActiveAccount() {
        val account = App.accountManager.activeAccount
        if (account?.isWatchAccount != false || account.id != request.accountId) throw WatchAccountException()
    }

    private suspend fun prepareMaximumRequest(): PrivateSendRequest {
        val available = availableBalance() ?: throw PrivateSendError.MaxUnavailable
        var budget = request.amountOut.min(available)
        if (budget <= BigDecimal.ZERO) throw PrivateSendError.MaxUnavailable
        if (swapFeePaidFromAsset(token.type)) {
            val ownAddress = SwapHelper.getReceiveAddressForToken(token)
            val probe = PrivateSendDepositBuilder.build(token, budget, ownAddress, attachment = null, btcParams = btcParams)
            sendTransactionService.refreshUuid()
            sendTransactionService.setSendTransactionData(probe)
            val settled = sendTransactionService.awaitSettled(30_000)
            val fee = settled.networkFee?.primary?.coinValue?.takeIf { it.coin.uid == token.coin.uid }?.value
            val spendable = sendTransactionService.maxSendableAmount() ?: fee?.let { available - it }
                ?: throw PrivateSendError.MaxUnavailable
            budget = budget.min(spendable)
        }
        if (budget <= BigDecimal.ZERO) throw PrivateSendError.MaxUnavailable
        return manager.maximumRequest(request, budget)
    }

    private fun maximumAffordable(
        depositAmount: BigDecimal,
        state: io.horizontalsystems.walletkit.modules.multiswap.sendtransaction.SendTransactionServiceState,
        service: AbstractSendTransactionService = sendTransactionService,
    ): Boolean {
        if (!request.sendMax) return true
        val available = availableBalance() ?: return false
        var budget = request.amountOut.min(available)
        if (swapFeePaidFromAsset(token.type)) {
            val fee = state.networkFee?.primary?.coinValue?.takeIf { it.coin.uid == token.coin.uid }?.value
            val spendable = service.maxSendableAmount() ?: fee?.let { available - it } ?: return false
            budget = budget.min(spendable)
        }
        return depositAmount <= budget
    }

    // What the deposit can draw on: the outputs chosen on the send screen when there are any,
    // otherwise what the chain lets a deposit spend.
    private fun availableBalance(): BigDecimal? {
        btcParams?.unspentOutputs?.let { outputs ->
            return outputs.sumOf { it.value }.toBigDecimal().movePointLeft(token.decimals)
        }
        return UnpricedDeposit.availableBalance(token, null)
    }

    override fun onCleared() {
        timerService.stop()
        sendTransactionService.clear()
    }

    /**
     * Creates the order for the preview on screen, builds the deposit for its address, signs and
     * broadcasts it, records the send and reports the hash. A [SwapCommitFailedException] means
     * the order could not be created or its deposit could not be built: a fresh preview is already
     * being loaded, and the screen only shows the reason briefly.
     */
    suspend fun send(): SendTransactionResult = withContext(serviceDispatcher) {
        val preview = preview ?: throw PrivateSendError.CommitFailed()

        synchronized(this@PrivateSendConfirmViewModel) {
            if (isSending || previewConsumed || broadcastAttempted || preparingDeposit) throw PrivateSendError.CommitFailed()
            val state = sendTransactionService.stateFlow.value
            val cannotSend = if (preview.stubDepositAddress == null) {
                UnpricedDeposit.caution(token, preview.depositAmount, availableBalance()) != null
            } else state.loading || !state.sendable
            if (error != null || cannotSend || !maximumAffordable(preview.depositAmount, state))
                throw PrivateSendError.MaxUnavailable
            isSending = true
        }

        var requiresPreview = false
        try {
            previewJob?.cancelAndJoin()
            checkActiveAccount()
            ensureActive()
            timerService.stop()
            previewConsumed = true
            emitState()

            // The only step with side effects. The token is spent either way, so a fresh preview
            // replaces the one on screen; the reason is surfaced by the caller.
            val order = try {
                manager.commit(preview)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                requiresPreview = true
                throw SwapCommitFailedException(commitFailure(e), e)
            }

            // The order exists from here on. A deposit that cannot be built or sent leaves it to
            // lapse unfunded, and the screen re-previews so a retry never reuses it.
            val service = try {
                ensureActive()
                checkActiveAccount()
                val data = PrivateSendDepositBuilder.build(
                    token = token,
                    amount = order.depositAmount,
                    address = order.depositAddress,
                    attachment = order.attachment,
                    btcParams = btcParams,
                )
                sendServiceFor(data)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                requiresPreview = true
                throw SwapCommitFailedException(SwapCommitFailure.Failed, e)
            }

            // Pre-saved before broadcasting so the record survives the app dying between the
            // two: the committed order is already real server-side, and tracking resolves it by
            // uuid alone even before a tx hash lands. A send that fails here may still have
            // reached the node, and the kits do not say which side of the broadcast an error
            // came from, so the record is never marked failed and no fresh preview is offered;
            // the Send button stays disabled after a broadcast attempt.
            val recordId: Int
            val result = try {
                ensureActive()
                checkActiveAccount()
                if (!maximumAffordable(order.depositAmount, service.stateFlow.value, service)) {
                    requiresPreview = true
                    throw SwapCommitFailedException(SwapCommitFailure.Failed, PrivateSendError.MaxUnavailable)
                }
                recordId = swapRecordManager.save(swapRecord(order))
                ensureActive()
                checkActiveAccount()
                broadcastAttempted = true
                service.sendTransaction()
            } finally {
                if (service !== sendTransactionService) service.clear()
            }

            transactionHash(result)?.let { txHash ->
                swapRecordManager.updateTransactionHash(recordId, txHash)
                // Best-effort and off the critical path: funds have moved, so the screen must
                // not wait on the report, and tracking resolves the send without it.
                App.swapSyncService.reportInboundTxHash(preview.provider, order.providerSwapId, txHash)
            }

            // The recipient is a wallet-external address worth suggesting next time.
            App.recentAddressManager.setRecentAddress(
                io.horizontalsystems.walletkit.entities.Address(request.recipient),
                token.blockchainType,
            )

            result
        } finally {
            synchronized(this@PrivateSendConfirmViewModel) { isSending = false }
            if (requiresPreview) requestPreview() else emitState()
        }
    }

    /**
     * The service that signs the committed deposit. The on-screen one when it reflects new data as
     * soon as it is set; otherwise a fresh instance, so no state built for the stand-in can be
     * signed. The fee choices carry over; the estimate is redone for the real destination.
     */
    private suspend fun sendServiceFor(data: SendTransactionData): AbstractSendTransactionService {
        val service = sendTransactionService.fresh()?.also { it.start(serviceScope) } ?: sendTransactionService
        try {
            service.setSendTransactionData(data)
            val state = service.awaitSettled()
            if (!state.sendable) {
                // A caution's text can be blank while its title carries the reason.
                val caution = state.cautions.firstOrNull { it.type == CautionViewItem.Type.Error }
                throw DepositNotSendableException(caution?.text?.ifBlank { null } ?: caution?.title ?: "Deposit cannot be sent")
            }
            return service
        } catch (e: Throwable) {
            if (service !== sendTransactionService) service.clear()
            throw e
        }
    }

    // What the notice says when the order could not be created: the server's discriminator when
    // it names one, a generic line otherwise.
    private fun commitFailure(error: Throwable): SwapCommitFailure = when (error) {
        PrivateSendError.RateChanged -> SwapCommitFailure.RateChanged
        PrivateSendError.PreviewExpired -> SwapCommitFailure.RefreshRequired
        else -> SwapCommitFailure.Failed
    }

    private fun swapRecord(order: PrivateSendOrder) = SwapRecord(
        accountId = request.accountId,
        timestamp = System.currentTimeMillis(),
        providerId = order.providerId,
        providerName = "Private send",
        tokenInUid = token.tokenQuery.id,
        tokenInCoinCode = token.coin.code,
        tokenInCoinUid = token.coin.uid,
        tokenInBadge = token.badge,
        tokenInBlockchainTypeUid = token.blockchainType.uid,
        tokenOutUid = token.tokenQuery.id,
        tokenOutCoinCode = token.coin.code,
        tokenOutCoinUid = token.coin.uid,
        tokenOutBadge = token.badge,
        tokenOutBlockchainTypeUid = token.blockchainType.uid,
        amountIn = order.depositAmount.toPlainString(),
        amountOut = order.amountOut.toPlainString(),
        amountOutMin = order.amountOut.toPlainString(),
        recipientAddress = order.request.recipient,
        customRecipientAddress = true,
        // Doubles as the refund address — the buffer refund lands here. The provider itself
        // is never told the sender's address.
        sourceAddress = order.refundAddress,
        transactionHash = null,
        providerSwapId = order.providerSwapId,
        fromAsset = null,
        toAsset = null,
        depositAddress = order.depositAddress,
        status = SwapStatus.Depositing.name,
        operation = SwapOperation.PrivateSend.name,
        estimatedTime = order.estimatedTime,
    )

    private fun transactionHash(result: SendTransactionResult): String? = when (result) {
        is SendTransactionResult.Evm -> result.transactionHash
        is SendTransactionResult.Btc -> result.transactionRecord?.transactionHash
        is SendTransactionResult.Zcash -> result.transactionHash
        is SendTransactionResult.Solana -> result.txHash
        is SendTransactionResult.Tron -> result.txHash
        is SendTransactionResult.Thorchain -> result.txHash
        is SendTransactionResult.Stellar -> result.txHash
        is SendTransactionResult.Xrp -> result.txHash
        is SendTransactionResult.Near -> result.txHash
        is SendTransactionResult.Monero -> result.txHash
        is SendTransactionResult.Zano -> result.txHash
        SendTransactionResult.Ton -> null
    }

    companion object {
        // How long a preview is shown before it is silently re-requested. The preview has no
        // side effects, so a refresh costs nothing; the order is created only on Send.
        const val QUOTE_LIFETIME_SECONDS = 15L

        fun init(
            request: PrivateSendRequest,
            btcParams: PrivateSendBtcParams?,
        ): CreationExtras.() -> PrivateSendConfirmViewModel = {
            PrivateSendConfirmViewModel(
                request = request,
                btcParams = btcParams,
                manager = App.privateSendManager,
                sendTransactionService = SendTransactionServiceFactory.create(request.token),
                currencyManager = App.currencyManager,
                timerService = TimerService(),
                swapRecordManager = App.swapRecordManager,
            )
        }
    }
}

data class PrivateSendConfirmUiState(
    val initialLoading: Boolean,
    val loading: Boolean,
    val token: Token,
    val recipient: String,
    val amountOut: BigDecimal,
    val privateFee: BigDecimal?,
    // The refundable slippage buffer — shown as "Reserved amount", per the design.
    val reservedAmount: BigDecimal?,
    val bufferUnknown: Boolean,
    val estimatedTime: Long?,
    val currency: Currency,
    val networkFee: SendModule.AmountData?,
    val cautions: List<CautionViewItem>,
    val transactionFields: List<DataField>,
    val canSend: Boolean,
    val refreshRequired: Boolean,
    val hasSettings: Boolean,
    val hasNonceSettings: Boolean,
    val error: Throwable?,
)

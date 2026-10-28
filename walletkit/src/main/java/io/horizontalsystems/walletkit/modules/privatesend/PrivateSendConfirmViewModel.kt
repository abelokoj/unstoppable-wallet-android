package io.horizontalsystems.walletkit.modules.privatesend

import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import io.horizontalsystems.walletkit.R
import io.horizontalsystems.walletkit.core.providers.Translator
import io.horizontalsystems.walletkit.core.IBalanceAdapter
import io.horizontalsystems.walletkit.modules.multiswap.swapFeePaidFromAsset
import io.horizontalsystems.walletkit.modules.multiswap.providers.SwapHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import io.horizontalsystems.walletkit.core.App
import io.horizontalsystems.walletkit.core.WatchAccountException
import io.horizontalsystems.walletkit.core.ViewModelUiState
import io.horizontalsystems.walletkit.core.badge
import io.horizontalsystems.walletkit.core.ethereum.CautionViewItem
import io.horizontalsystems.walletkit.core.managers.CurrencyManager
import io.horizontalsystems.walletkit.entities.Currency
import io.horizontalsystems.walletkit.entities.SwapRecord
import io.horizontalsystems.walletkit.modules.multiswap.TimerService
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
 * The confirmation step of a private send: commits the order (quote + /v2/swap in one), then
 * builds and estimates the deposit transfer through the chain's own send-transaction service.
 * This ViewModel never builds a transaction itself.
 *
 * Committing happens once per user-visible order, and that is mandatory rather than an
 * optimisation: every /v2/swap creates a REAL order. Fee-settings changes re-estimate the
 * same deposit; they never re-commit. Past [QUOTE_LIFETIME_SECONDS] the send button is
 * replaced by Refresh — an order is only ever replaced by that explicit tap, never silently
 * under a live send button. The expired order was never funded and simply lapses server-side.
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

    private var order: PrivateSendOrder? = null
    private var preparingDeposit = true
    private var depositData: SendTransactionData? = null
    private var error: Throwable? = null
    private var initialLoading = true
    private var expired = false
    private var sendTransactionState = sendTransactionService.stateFlow.value
    private var timerState = timerService.stateFlow.value

    // Reject a second concurrent broadcast (double-tap / overlapping send) so the same
    // deposit is never sent twice.
    private var isSending = false

    // The one history row for this order. A retry after a failed broadcast reuses it
    // instead of inserting a duplicate per attempt.
    private var recordId: Int? = null

    // The in-flight commit. A new commit cancels the previous one: without this a double
    // tap on Refresh starts two commits whose `order`/`depositData` writes interleave —
    // the deposit could fund one order while the screen and history describe the other.
    private var commitJob: Job? = null

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
                if (order != null && !preparingDeposit) {
                    initialLoading = initialLoading && it.loading
                }
                emitState()
            }
        }

        viewModelScope.launch {
            // A fee-settings change re-estimates the SAME deposit — the committed order and
            // its sellAmount ceiling are never silently replaced. Guarded in the body (a
            // downstream throw would kill viewModelScope and freeze the screen; Flow.catch
            // only sees upstream failures) so the collector survives for later changes.
            sendTransactionService.sendTransactionSettingsFlow.collect {
                val data = depositData ?: return@collect
                try {
                    withContext(serviceDispatcher) {
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
                if (it.timeout) {
                    expired = true
                }
                emitState()
            }
        }

        sendTransactionService.start(serviceScope)

        commit()
    }

    override fun createState(): PrivateSendConfirmUiState {
        val order = order
        val refreshRequired = order != null && !preparingDeposit && !sendTransactionState.loading &&
            !maximumAffordable(order, sendTransactionState)

        return PrivateSendConfirmUiState(
            initialLoading = initialLoading,
            loading = preparingDeposit || sendTransactionState.loading,
            token = token,
            recipient = request.recipient,
            amountOut = order?.amountOut ?: request.amountOut,
            privateFee = order?.privateFee,
            reservedAmount = order?.refundableBuffer?.takeIf { it > BigDecimal.ZERO },
            bufferUnknown = order != null && order.minSellAmount == null,
            estimatedTime = order?.estimatedTime,
            currency = currency,
            networkFee = sendTransactionState.networkFee,
            cautions = sendTransactionState.cautions + if (refreshRequired) listOf(CautionViewItem(
                Translator.getString(R.string.EthereumTransaction_Error_CannotEstimate_Title),
                Translator.getString(R.string.PrivateSend_Caution_MaxUnavailable), CautionViewItem.Type.Error)) else emptyList(),
            transactionFields = sendTransactionState.fields,
            canSend = order != null && !preparingDeposit && sendTransactionState.sendable && !sendTransactionState.loading &&
                maximumAffordable(order, sendTransactionState) && !expired && error == null,
            expired = expired,
            refreshRequired = refreshRequired,
            hasSettings = sendTransactionService.hasSettings,
            hasNonceSettings = sendTransactionService.hasNonceSettings,
            error = error,
        )
    }

    /**
     * The expired-state action: commits a brand-new order in place. The screen returns to
     * the exact just-opened state — old order, deposit data and history-row binding are
     * dropped first, so a stale order can never sit under a live send button and the new
     * order gets its own history row (a Failed row from the old order's broadcast stays).
     */
    fun refresh() {
        synchronized(this) {
            if (isSending) return
        }

        expired = false
        preparingDeposit = true
        order = null
        depositData = null
        recordId = null
        initialLoading = true
        emitState()

        // `initialLoading` only clears when the service's StateFlow emits after the new order
        // is set, and StateFlow drops a value equal to the current one. A re-estimate of a
        // like-for-like deposit (same amount, fee and fields) lands on an identical state, so
        // without a fresh uuid nothing is emitted and the screen spins forever.
        sendTransactionService.refreshUuid()

        commit()
    }

    private fun commit() {
        commitJob?.cancel()
        commitJob = viewModelScope.launch(serviceDispatcher) {
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
                val confirmingAccount = App.accountManager.activeAccount
                if (confirmingAccount?.isWatchAccount != false || confirmingAccount.id != request.accountId)
                    throw WatchAccountException()
                val order = manager.commit(effectiveRequest)

                ensureActive()

                this@PrivateSendConfirmViewModel.order = order
                emitState()

                val data = PrivateSendDepositBuilder.build(order, btcParams)
                depositData = data
                sendTransactionService.setSendTransactionData(data)
                preparingDeposit = false
                sendTransactionState = sendTransactionService.stateFlow.value
                initialLoading = sendTransactionState.loading
                emitState()

                // CountDownTimer binds to the current thread's Looper, so the expiry timer
                // must start on Main — this coroutine runs on Default.
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

    private suspend fun prepareMaximumRequest(): PrivateSendRequest {
        val available = App.adapterManager.getAdapterForToken<IBalanceAdapter>(token)?.balanceData?.available
            ?: throw PrivateSendError.MaxUnavailable
        var budget = request.amountOut.min(available)
        if (budget <= BigDecimal.ZERO) throw PrivateSendError.MaxUnavailable
        if (swapFeePaidFromAsset(token.type)) {
            val ownAddress = SwapHelper.getReceiveAddressForToken(token)
            val probe = PrivateSendDepositBuilder.transfer(token, budget, ownAddress, btcParams = btcParams)
            sendTransactionService.refreshUuid()
            sendTransactionService.setSendTransactionData(probe)
            val settled = withTimeout(30_000) { sendTransactionService.stateFlow.first { !it.loading } }
            val fee = settled.networkFee?.primary?.coinValue?.takeIf { it.coin.uid == token.coin.uid }?.value
            val spendable = sendTransactionService.maxSendableAmount() ?: fee?.let { available - it }
                ?: throw PrivateSendError.MaxUnavailable
            budget = budget.min(spendable)
        }
        if (budget <= BigDecimal.ZERO) throw PrivateSendError.MaxUnavailable
        return manager.maximumRequest(request, budget)
    }

    private fun maximumAffordable(order: PrivateSendOrder,
        state: io.horizontalsystems.walletkit.modules.multiswap.sendtransaction.SendTransactionServiceState): Boolean {
        if (!request.sendMax) return true
        val available = App.adapterManager.getAdapterForToken<IBalanceAdapter>(token)?.balanceData?.available ?: return false
        var budget = request.amountOut.min(available)
        if (swapFeePaidFromAsset(token.type)) {
            val fee = state.networkFee?.primary?.coinValue?.takeIf { it.coin.uid == token.coin.uid }?.value
            val spendable = sendTransactionService.maxSendableAmount() ?: fee?.let { available - it } ?: return false
            budget = budget.min(spendable)
        }
        return order.depositAmount <= budget
    }

    override fun onCleared() {
        timerService.stop()
        sendTransactionService.clear()
    }

    suspend fun send(): SendTransactionResult = withContext(serviceDispatcher) {
        val order = order ?: throw PrivateSendError.CommitFailed()

        synchronized(this@PrivateSendConfirmViewModel) {
            if (isSending) throw PrivateSendError.CommitFailed()
            isSending = true
        }

        try {
            // Checked before the timer is stopped: throwing after that would silence the
            // expiry watchdog without rearming it, leaving a lapsed order looking sendable.
            val activeAccount = App.accountManager.activeAccount
            if (activeAccount == null ||
                activeAccount.isWatchAccount ||
                activeAccount.id != request.accountId
            ) {
                throw WatchAccountException()
            }

            val state = sendTransactionService.stateFlow.value
            if (preparingDeposit || state.loading || !state.sendable || expired || error != null || !maximumAffordable(order, state))
                throw PrivateSendError.MaxUnavailable
            timerService.stop()

            // Pre-saved before broadcasting so the record survives the app dying between the
            // two: the committed order is already real server-side, and /v2/track resolves it
            // by uuid alone even before a tx hash lands. A retry reuses the row (flipping a
            // previous Failed back to Depositing) rather than inserting one per attempt.
            val savedRecordId = recordId?.also {
                swapRecordManager.updateStatus(it, SwapStatus.Depositing, null)
            } ?: swapRecordManager.save(swapRecord(order)).also { recordId = it }

            val result = try {
                sendTransactionService.sendTransaction()
            } catch (e: Throwable) {
                // A failed broadcast moved no funds; the unfunded order simply expires.
                swapRecordManager.updateStatus(savedRecordId, SwapStatus.Failed, null)

                // Stopping the timer above silenced the expiry watchdog; a retry is only
                // legitimate while the committed quote is still alive, so rearm it with the
                // remaining lifetime — or dead-end the screen if the deadline already passed.
                val remainingSeconds =
                    (order.committedAt + QUOTE_LIFETIME_SECONDS * 1000 - System.currentTimeMillis()) / 1000
                withContext(Dispatchers.Main) {
                    if (remainingSeconds > 0) {
                        timerService.start(remainingSeconds)
                    } else {
                        expired = true
                        emitState()
                    }
                }

                throw e
            }

            transactionHash(result)?.let {
                swapRecordManager.updateTransactionHash(savedRecordId, it)
            }

            // The recipient is a wallet-external address worth suggesting next time.
            App.recentAddressManager.setRecentAddress(
                io.horizontalsystems.walletkit.entities.Address(request.recipient),
                token.blockchainType,
            )

            result
        } finally {
            synchronized(this@PrivateSendConfirmViewModel) { isSending = false }
        }
    }

    private fun swapRecord(order: PrivateSendOrder) = SwapRecord(
        accountId = App.accountManager.activeAccount?.id ?: "",
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
        else -> null
    }

    companion object {
        // A committed quote is only honoured for this long. Past it the screen dead-ends
        // rather than pretending to be current — the deposit ceiling is what the send button
        // authorizes.
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
    val expired: Boolean,
    val refreshRequired: Boolean,
    val hasSettings: Boolean,
    val hasNonceSettings: Boolean,
    val error: Throwable?,
)

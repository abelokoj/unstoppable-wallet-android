package io.horizontalsystems.walletkit.core.adapters

import io.horizontalsystems.nearkit.NearKit
import io.horizontalsystems.nearkit.models.NearAmount
import io.horizontalsystems.walletkit.core.AdapterState
import io.horizontalsystems.walletkit.core.BalanceData
import io.horizontalsystems.walletkit.core.NearSendEstimate
import io.horizontalsystems.walletkit.R
import io.horizontalsystems.walletkit.core.LocalizedException
import io.horizontalsystems.walletkit.core.App
import io.horizontalsystems.walletkit.core.BackgroundManagerState
import io.horizontalsystems.walletkit.core.managers.NearKitWrapper
import io.horizontalsystems.walletkit.core.managers.toAdapterState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import java.math.BigDecimal

/**
 * Adapter for a NEP-141 token. No activation is needed to receive: whoever sends the token pays
 * the receiver's storage registration, and so does this adapter when it sends to an
 * unregistered account.
 */
class NearTokenAdapter(
    kitWrapper: NearKitWrapper,
    val contractId: String,
    private val decimals: Int,
) : BaseNearAdapter(kitWrapper) {
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    private var tokenBalance: BigDecimal = BigDecimal.ZERO

    private val _balanceUpdatedFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val _balanceStateUpdatedFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override var balanceState: AdapterState = AdapterState.Syncing()
    override val balanceStateUpdatedFlow: Flow<Unit> get() = _balanceStateUpdatedFlow
    override val balanceData: BalanceData get() = BalanceData(tokenBalance)
    override val balanceUpdatedFlow: Flow<Unit> get() = _balanceUpdatedFlow

    override var fee: BigDecimal = DEFAULT_FEE
        private set

    override fun start() {
        kitWrapper.watchToken(contractId)
        coroutineScope.launch {
            combine(kit.ftBalancesFlow, kit.syncStateFlow, App.backgroundManager.stateFlow) { _, state, background ->
                state to background
            }.collectLatest { (state, background) ->
                balanceState = if (state is NearKit.SyncState.Synced) AdapterState.Syncing() else state.toAdapterState()
                _balanceStateUpdatedFlow.tryEmit(Unit)
                if (state !is NearKit.SyncState.Synced || background != BackgroundManagerState.EnterForeground) return@collectLatest
                while (isActive) {
                    try {
                        tokenBalance = BigDecimal(kitWrapper.verifiedTokenBalance(contractId)).movePointLeft(decimals)
                        balanceState = AdapterState.Synced
                    } catch (e: TimeoutCancellationException) {
                        balanceState = AdapterState.NotSynced(e)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        balanceState = AdapterState.NotSynced(e)
                    }
                    _balanceUpdatedFlow.tryEmit(Unit)
                    _balanceStateUpdatedFlow.tryEmit(Unit)
                    delay(30_000L)
                }
            }
        }
    }

    override fun stop() {
        kitWrapper.unwatchToken(contractId)
        coroutineScope.cancel()
    }

    override fun refresh() {
        kit.refresh()
    }

    // ISendNearAdapter

    override val maxSendableBalance: BigDecimal
        get() = if (balanceState == AdapterState.Synced) tokenBalance else BigDecimal.ZERO

    override suspend fun estimate(address: String, amount: BigDecimal, memo: String?): NearSendEstimate {
        if (amount <= BigDecimal.ZERO || amount > verifiedBalance()) throw LocalizedException(R.string.Swap_ErrorInsufficientBalance)
        val estimate = kit.estimateFtTransfer(contractId, address, toUnits(amount), memo)
        fee = NearAmount.toNear(estimate.fee.fee)
        val storageDeposit = estimate.storageDeposit?.let { NearAmount.toNear(it) }
        return FtSendEstimate(
            fee = fee,
            storageDeposit = storageDeposit,
            // NEP-141 transfers attach one yoctoNEAR, which is below display precision
            requiredNear = NearAmount.toNear(estimate.fee.requiredBalance) + (storageDeposit ?: BigDecimal.ZERO),
            kitEstimate = estimate,
        )
    }

    override suspend fun send(amount: BigDecimal, address: String, memo: String?, estimate: NearSendEstimate): String {
        require(estimate is FtSendEstimate) { "Not a token transfer estimate" }
        if (amount <= BigDecimal.ZERO || amount > verifiedBalance()) throw LocalizedException(R.string.Swap_ErrorInsufficientBalance)
        return kit.sendFt(contractId, address, toUnits(amount), memo, estimate = estimate.kitEstimate).hash
    }

    private suspend fun verifiedBalance(): BigDecimal =
        BigDecimal(kitWrapper.verifiedTokenBalance(contractId)).movePointLeft(decimals)

    private fun toUnits(amount: BigDecimal) = amount.movePointRight(decimals).toBigIntegerExact()

    private class FtSendEstimate(
        fee: BigDecimal,
        storageDeposit: BigDecimal?,
        requiredNear: BigDecimal,
        val kitEstimate: NearKit.FtTransferEstimate,
    ) : NearSendEstimate(fee, storageDeposit, requiredNear)
}

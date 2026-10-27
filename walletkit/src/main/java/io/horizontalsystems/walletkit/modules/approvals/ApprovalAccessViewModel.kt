package io.horizontalsystems.walletkit.modules.approvals

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.horizontalsystems.walletkit.R
import io.horizontalsystems.walletkit.core.App
import io.horizontalsystems.walletkit.core.chain.ChainRegistry
import io.horizontalsystems.walletkit.entities.Account
import io.horizontalsystems.marketkit.models.Blockchain
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ApprovalAccessState(
    val network: Blockchain? = null,
    val owner: String? = null,
    val approvals: List<ActiveApproval> = emptyList(),
    val busy: Boolean = false,
    val scanned: Boolean = false,
    val nextBlock: Long? = null,
    val scannedFrom: Long? = null,
    val head: Long? = null,
    val latestHead: Long? = null,
    val failedChecks: Int = 0,
    val message: Int? = null,
    val accountChanged: Boolean = false,
)

class ApprovalAccessViewModel(val account: Account?) : ViewModel() {
    val networks = App.evmBlockchainManager.allBlockchains
    var state by mutableStateOf(ApprovalAccessState(network = networks.firstOrNull()))
        private set
    private val services = mutableMapOf<String, ApprovalAccessService>()
    private var job: Job? = null
    private var generation = 0

    init {
        select(networks.firstOrNull())
        viewModelScope.launch {
            App.accountManager.activeAccountStateFlow.collect {
                if (App.accountManager.activeAccount?.id != account?.id) {
                    generation++
                    job?.cancel()
                    state = state.copy(busy = false, accountChanged = true,
                        approvals = emptyList(), message = R.string.ApprovalAccess_WalletChanged)
                }
            }
        }
    }

    private fun service(): ApprovalAccessService? {
        val activeAccount = account ?: return null
        val network = state.network ?: return null
        return services.getOrPut(network.uid) {
            ChainRegistry[network.type]?.approvalAccessService(activeAccount) ?: return null
        }
    }

    fun select(network: Blockchain?) {
        generation++
        job?.cancel()
        state = ApprovalAccessState(network = network,
            accountChanged = App.accountManager.activeAccount?.id != account?.id)
        try {
            state = state.copy(owner = service()?.owner)
        } catch (_: Exception) {
            state = state.copy(message = R.string.ApprovalAccess_UnsupportedAccount)
        }
    }

    fun scan() = runOperation {
        val scanGeneration = generation
        val service = service() ?: error("Unsupported wallet")
        val result = withContext(Dispatchers.IO) {
            service.scan { from -> viewModelScope.launch {
                if (generation == scanGeneration) state = state.copy(scannedFrom = minOf(state.scannedFrom ?: from, from))
            } }
        }
        state = state.copy(approvals = result.approvals, scanned = true,
            nextBlock = result.nextBlock, head = result.head, latestHead = result.latestHead, failedChecks = result.failedChecks)
    }

    fun check(target: ApprovalTarget) = runOperation {
        val service = requireNotNull(service())
        val result = withContext(Dispatchers.IO) { service.check(target) }
        state = state.copy(
            approvals = state.approvals.filterNot { it.target.key == target.key } + listOfNotNull(result),
            message = if (result == null) R.string.ApprovalAccess_NotActive else null,
        )
    }

    fun cancel() {
        generation++
        job?.cancel()
        state = state.copy(busy = false, message = R.string.ApprovalAccess_Stopped)
    }

    private fun runOperation(block: suspend () -> Unit) {
        if (state.busy || state.accountChanged || state.owner == null) return
        state = state.copy(busy = true, message = null)
        val operationGeneration = generation
        job = viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (generation == operationGeneration) state = state.copy(message = R.string.ApprovalAccess_ScanError)
            }
            finally {
                if (generation == operationGeneration) state = state.copy(busy = false)
            }
        }
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ApprovalAccessViewModel(App.accountManager.activeAccount) as T
    }
}

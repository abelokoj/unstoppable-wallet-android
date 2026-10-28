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
import io.horizontalsystems.marketkit.models.BlockchainType
import io.horizontalsystems.marketkit.models.Coin
import io.horizontalsystems.marketkit.models.Token
import io.horizontalsystems.marketkit.models.TokenQuery
import io.horizontalsystems.marketkit.models.TokenType
import io.horizontalsystems.walletkit.core.customCoinUid
import io.horizontalsystems.walletkit.core.nativeTokenContractAddress
import io.horizontalsystems.walletkit.entities.nft.NftKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ApprovalAccessState(
    val network: Blockchain? = null,
    val owner: String? = null,
    val approvals: List<ActiveApproval> = emptyList(),
    val items: List<ApprovalAccessItem> = emptyList(),
    val selectedContracts: Set<String> = emptySet(),
    val busy: Boolean = false,
    val scanned: Boolean = false,
    val nextBlock: Long? = null,
    val scannedFrom: Long? = null,
    val head: Long? = null,
    val latestHead: Long? = null,
    val failedChecks: Int = 0,
    val message: Int? = null,
    val accountChanged: Boolean = false,
) {
    val selectedApprovals: List<ActiveApproval>
        get() = items.filter { it.contract in selectedContracts }.flatMap { it.approvals }
}

internal fun defaultApprovalNetwork(networks: List<Blockchain>): Blockchain? =
    networks.firstOrNull { it.type == BlockchainType.BinanceSmartChain } ?: networks.firstOrNull()

class ApprovalAccessViewModel(val account: Account?) : ViewModel() {
    val networks = App.evmBlockchainManager.allBlockchains
    var state by mutableStateOf(ApprovalAccessState(network = defaultApprovalNetwork(networks)))
        private set
    private val services = mutableMapOf<String, ApprovalAccessService>()
    private var job: Job? = null
    private var generation = 0
    private val metadataJobs = mutableMapOf<String, Job>()
    private val metadataCache = mutableMapOf<String, ApprovalAccessItem>()

    val selectedApprovals: List<ActiveApproval> get() = state.selectedApprovals

    fun toggleSelection(contract: String) {
        if (state.busy || state.accountChanged || state.items.none { it.contract == contract }) return
        state = state.copy(selectedContracts = if (contract in state.selectedContracts)
            state.selectedContracts - contract else state.selectedContracts + contract)
    }

    fun clearSelection() { state = state.copy(selectedContracts = emptySet()) }

    fun selectAll() {
        if (!state.busy && !state.accountChanged) state = state.copy(selectedContracts = state.items.map { it.contract }.toSet())
    }

    init {
        select(defaultApprovalNetwork(networks))
        viewModelScope.launch {
            App.accountManager.activeAccountStateFlow.collect {
                if (App.accountManager.activeAccount?.id != account?.id) {
                    generation++
                    job?.cancel()
                    metadataJobs.values.forEach(Job::cancel)
                    state = state.copy(busy = false, accountChanged = true,
                        approvals = emptyList(), items = emptyList(), selectedContracts = emptySet(),
                        message = R.string.ApprovalAccess_WalletChanged)
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
        metadataJobs.values.forEach(Job::cancel)
        metadataJobs.clear()
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
        val items = resolveKnownItems(result.approvals)
        if (generation != scanGeneration) return@runOperation
        state = state.copy(approvals = result.approvals, items = items, scanned = true,
            selectedContracts = state.selectedContracts.intersect(items.map { it.contract }.toSet()),
            nextBlock = result.nextBlock, head = result.head, latestHead = result.latestHead, failedChecks = result.failedChecks)
    }

    fun check(target: ApprovalTarget) = runOperation {
        val checkGeneration = generation
        val service = requireNotNull(service())
        val result = withContext(Dispatchers.IO) { service.check(target) }
        val approvals = state.approvals.filterNot { it.target.key == target.key } + listOfNotNull(result)
        val items = resolveKnownItems(approvals)
        if (generation != checkGeneration) return@runOperation
        state = state.copy(
            approvals = approvals, items = items,
            selectedContracts = state.selectedContracts.intersect(items.map { it.contract }.toSet()),
            message = if (result == null) R.string.ApprovalAccess_NotActive else null,
        )
    }

    private suspend fun resolveKnownItems(approvals: List<ActiveApproval>): List<ApprovalAccessItem> {
        val network = state.network ?: return emptyList()
        val activeAccount = account ?: return emptyList()
        val cached = metadataCache.toMap()
        return withContext(Dispatchers.IO) {
            val nftMetadata = App.nftMetadataManager.addressMetadata(NftKey(activeAccount, network.type))
            groupApprovalItems(approvals).map { item ->
                val key = "${network.uid}:${item.contract}"
                cached[key]?.copy(approvals = item.approvals) ?: run {
                    val type = if (item.contract.equals(network.type.nativeTokenContractAddress, true))
                        TokenType.Native else TokenType.Eip20(item.contract)
                    val token = App.coinManager.getToken(TokenQuery(network.type, type))
                    val nft = nftMetadata?.assets?.firstOrNull { it.nftUid.contractAddress.equals(item.contract, true) }
                    val collection = nftMetadata?.collections?.firstOrNull { it.providerUid == nft?.providerCollectionUid }
                    item.copy(token = token, name = collection?.name, imageUrl = collection?.thumbnailImageUrl)
                }
            }
        }
    }

    fun loadMetadata(contract: String) {
        val network = state.network ?: return
        val item = state.items.firstOrNull { it.contract == contract } ?: return
        val key = "${network.uid}:$contract"
        if (state.busy || state.accountChanged || item.token != null || item.name != null || key in metadataJobs) return
        val operationGeneration = generation
        val permissions = service() ?: return
        metadataJobs[key] = viewModelScope.launch {
            try {
                val metadata = withContext(Dispatchers.IO) { permissions.metadata(item.approvals.first().target) }
                    ?: return@launch
                if (generation != operationGeneration || state.accountChanged) return@launch
                val query = TokenQuery(network.type, TokenType.Eip20(contract))
                val token = metadata.decimals?.let {
                    Token(Coin(query.customCoinUid, metadata.name, metadata.symbol), network, query.tokenType, it)
                }
                val enriched = item.copy(token = token, name = metadata.name)
                metadataCache[key] = enriched
                state = state.copy(items = state.items.map {
                    if (it.contract == contract) enriched.copy(approvals = it.approvals) else it
                })
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Unknown contracts retain the balance list's placeholder. */ }
        }
    }

    fun cancel() {
        generation++
        job?.cancel()
        metadataJobs.values.forEach(Job::cancel)
        metadataJobs.clear()
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

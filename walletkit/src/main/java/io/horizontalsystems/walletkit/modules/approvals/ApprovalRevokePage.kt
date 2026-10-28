package io.horizontalsystems.walletkit.modules.approvals

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.horizontalsystems.walletkit.R
import io.horizontalsystems.walletkit.core.App
import io.horizontalsystems.walletkit.core.chain.ChainRegistry
import io.horizontalsystems.walletkit.modules.confirm.ConfirmTransactionScreen
import io.horizontalsystems.walletkit.modules.evmfee.Cautions
import io.horizontalsystems.walletkit.modules.multiswap.sendtransaction.AbstractSendTransactionService
import io.horizontalsystems.walletkit.modules.multiswap.sendtransaction.SendTransactionData
import io.horizontalsystems.walletkit.modules.multiswap.sendtransaction.SendTransactionResult
import io.horizontalsystems.walletkit.modules.multiswap.sendtransaction.SendTransactionServiceFactory
import io.horizontalsystems.walletkit.modules.multiswap.sendtransaction.SendTransactionServiceState
import io.horizontalsystems.walletkit.modules.multiswap.ui.DataFieldFeeTemplate
import io.horizontalsystems.walletkit.modules.nav3.HSNavigation
import io.horizontalsystems.walletkit.modules.nav3.HSPage
import io.horizontalsystems.walletkit.modules.nav3.LocalResultEventBus
import io.horizontalsystems.walletkit.uiv3.components.controls.HSButton
import io.horizontalsystems.walletkit.ui.compose.components.VSpacer
import io.horizontalsystems.walletkit.uiv3.components.HSScaffold
import io.horizontalsystems.walletkit.uiv3.components.info.TextBlock
import io.horizontalsystems.marketkit.models.BlockchainType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data class ApprovalRevokePage(val accountId: String, val blockchainType: BlockchainType, val target: ApprovalTarget) : HSPage() {
    override fun contentKey() = "ApprovalRevokePage:$accountId:${blockchainType.uid}:${target.key}"

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val model = viewModel<ApprovalRevokeViewModel>(factory = ApprovalRevokeViewModel.Factory(this))
        val results = LocalResultEventBus.current
        LaunchedEffect(model.hash) {
            val hash = model.hash
            if (hash != null && resultKey != null) results.sendResult(Result(target.key, hash))
        }
        val network = App.evmBlockchainManager.getBlockchain(blockchainType)?.name ?: blockchainType.uid
        val authorize = navigation.authorizedAction(model::submit)
        ConfirmTransactionScreen(
            title = stringResource(R.string.ApprovalAccess_Confirm),
            onClickBack = navigation::removeLastOrNull,
            onClickFeeSettings = { navigation.slideFromRight(ApprovalRevokeFeePage(this)) },
            buttonsSlot = {
                HSButton(
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(if (model.submitted) R.string.Button_Done else R.string.Swap_Revoke),
                    enabled = model.submitted || (model.ready && !model.submitting && model.feeState?.sendable == true),
                    loadingIndicator = model.submitting,
                    onClick = { if (model.submitted) navigation.removeLastOrNull() else authorize() },
                )
            },
        ) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                AccessText(network)
                VSpacer(12.dp)
                AccessText(stringResource(target.kind.titleRes))
                VSpacer(12.dp)
                AccessText(stringResource(R.string.ApprovalAccess_Contract, target.contract))
                VSpacer(12.dp)
                AccessText(stringResource(R.string.ApprovalAccess_Spender, target.spender))
                target.tokenId?.let { VSpacer(12.dp); AccessText(stringResource(R.string.ApprovalAccess_TokenIdValue, it)) }
                VSpacer(12.dp)
                AccessText(stringResource(R.string.ApprovalAccess_Owner, model.owner ?: ""))
            }
            TextBlock(stringResource(R.string.ApprovalAccess_ConfirmInfo))
            if (model.preparing) TextBlock(stringResource(R.string.ApprovalAccess_Preparing))
            DataFieldFeeTemplate(navigation,
                primary = model.feeState?.networkFee?.primary?.getFormattedPlain() ?: "---",
                secondary = model.feeState?.networkFee?.secondary?.getFormattedPlain(),
                title = stringResource(R.string.FeeSettings_NetworkFee),
                infoText = stringResource(R.string.FeeSettings_NetworkFee_Info))
            model.feeState?.cautions?.takeIf { it.isNotEmpty() }?.let { Cautions(it) }
            if (model.error) TextBlock(stringResource(R.string.ApprovalAccess_RevokeError))
            if (model.submitted) {
                TextBlock(stringResource(R.string.ApprovalAccess_Submitted))
                model.hash?.let { TextBlock(it) }
            }
        }
    }

    @Serializable
    data class Result(val targetKey: String, val transactionHash: String)
}

class ApprovalRevokeViewModel(private val page: ApprovalRevokePage) : ViewModel() {
    var feeState by mutableStateOf<SendTransactionServiceState?>(null)
        private set
    var ready by mutableStateOf(false)
        private set
    var preparing by mutableStateOf(true)
        private set
    var submitting by mutableStateOf(false)
        private set
    var submitted by mutableStateOf(false)
        private set
    var error by mutableStateOf(false)
        private set
    var hash by mutableStateOf<String?>(null)
        private set
    var owner by mutableStateOf<String?>(null)
        private set
    var sendService: AbstractSendTransactionService? = null
        private set
    private var approvalService: ApprovalAccessService? = null

    init {
        viewModelScope.launch {
            try {
                val account = requireNotNull(App.accountManager.activeAccount)
                require(account.id == page.accountId && !account.isWatchAccount)
                ApprovalAbi.validate(page.target)
                val permissions = requireNotNull(ChainRegistry[page.blockchainType]?.approvalAccessService(account))
                approvalService = permissions
                owner = permissions.owner
                val revoke = withContext(Dispatchers.IO) { permissions.revokeData(page.target) }
                require(App.accountManager.activeAccount?.id == page.accountId)
                val token = requireNotNull(App.evmBlockchainManager.getBaseToken(page.blockchainType))
                val service = SendTransactionServiceFactory.create(token)
                sendService = service
                viewModelScope.launch { service.stateFlow.collect { feeState = it } }
                service.start(viewModelScope)
                service.setSendTransactionData(SendTransactionData.Evm(revoke, null))
                ready = true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
            finally { preparing = false }
        }
        viewModelScope.launch {
            App.accountManager.activeAccountStateFlow.collect {
                if (App.accountManager.activeAccount?.id != page.accountId) { ready = false; error = true }
            }
        }
    }

    fun submit() {
        if (!ready || submitting || submitted || feeState?.sendable != true) return
        submitting = true
        error = false
        viewModelScope.launch {
            try {
                // Re-check at signing time, not just when the review page was opened.
                val data = withContext(Dispatchers.IO) { requireNotNull(approvalService).revokeData(page.target) }
                val expected = ApprovalAbi.revoke(page.target)
                require(data.to == expected.to && data.value == expected.value && data.input.contentEquals(expected.input))
                require(App.accountManager.activeAccount?.id == page.accountId)
                val result = requireNotNull(sendService).sendTransaction() as SendTransactionResult.Evm
                hash = result.transactionHash
                submitted = true
                ready = false
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true; ready = false }
            finally { submitting = false }
        }
    }

    class Factory(private val page: ApprovalRevokePage) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ApprovalRevokeViewModel(page) as T
    }
}

@Serializable
data class ApprovalRevokeFeePage(val confirmation: ApprovalRevokePage) : HSPage() {
    override fun contentKey() = "ApprovalRevokeFeePage:${confirmation.contentKey()}"

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val model = navigation.viewModelForScreen<ApprovalRevokeViewModel>(confirmation.contentKey(),
            factory = ApprovalRevokeViewModel.Factory(confirmation))
        val service = model.sendService
        if (service != null) {
            service.GetSettingsContent(navigation)
        } else {
            HSScaffold(title = stringResource(R.string.FeeSettings_NetworkFee), onBack = navigation::removeLastOrNull) {
                TextBlock(stringResource(if (model.preparing) R.string.ApprovalAccess_Preparing else R.string.ApprovalAccess_RevokeError))
            }
        }
    }
}

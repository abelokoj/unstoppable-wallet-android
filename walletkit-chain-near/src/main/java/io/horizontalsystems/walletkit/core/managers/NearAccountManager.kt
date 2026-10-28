package io.horizontalsystems.walletkit.core.managers

import io.horizontalsystems.marketkit.models.BlockchainType
import io.horizontalsystems.marketkit.models.TokenQuery
import io.horizontalsystems.marketkit.models.TokenType
import io.horizontalsystems.nearkit.models.FtBalance
import io.horizontalsystems.walletkit.core.AppLogger
import io.horizontalsystems.walletkit.core.IAccountManager
import io.horizontalsystems.walletkit.entities.Account
import io.horizontalsystems.walletkit.entities.AccountOrigin
import io.horizontalsystems.walletkit.entities.EnabledWallet
import io.horizontalsystems.walletkit.entities.Wallet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * Enables a wallet for every NEP-141 token the account holds and the coin list knows, so a
 * restored or freshly credited account shows its tokens without manual adding. Unknown tokens
 * are left out: anyone can send any contract's token to any account, and most are spam.
 */
class NearAccountManager(
    private val accountManager: IAccountManager,
    private val walletManager: WalletManager,
    private val kitManager: NearKitManager,
    private val tokenAutoEnableManager: TokenAutoEnableManager,
    private val coinManager: io.horizontalsystems.walletkit.core.ICoinManager,
) {
    private val blockchainType: BlockchainType = BlockchainType.Near
    private val logger = AppLogger("near-account-manager")
    private val singleDispatcherCoroutineScope = CoroutineScope(Executors.newSingleThreadExecutor().asCoroutineDispatcher())
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var subscriptionJob: Job? = null

    fun start() {
        singleDispatcherCoroutineScope.launch {
            kitManager.kitBindingFlow.collect { binding ->
                try {
                    if (binding != null) subscribe(binding) else subscriptionJob?.cancel()
                } catch (e: Exception) {
                    logger.warning("error", e)
                }
            }
        }
    }

    private fun subscribe(binding: NearKitManager.KitBinding) {
        val (wrapper, account) = binding
        // what the kit had in storage before any sync: the restore-time baseline
        val persisted = wrapper.kit.ftBalances

        // A replacement binding can arrive without a separately observed stopped state.
        subscriptionJob?.cancel()
        subscriptionJob = coroutineScope.launch {
            wrapper.kit.ftBalancesFlow.collect { balances ->
                // coroutineScope has a plain Job: a throw here would cancel it for good
                try {
                    synchronized(kitManager) {
                        if (kitManager.kitBindingFlow.value !== binding) return@synchronized
                        handle(balances, account, persisted)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    logger.warning("error", e)
                }
            }
        }
    }

    /**
     * Tokens held in [persisted] are enabled only when the user opted into NEAR on the restore
     * screen; anything synced since is auto-enabled as on every other chain.
     */
    private fun handle(balances: List<FtBalance>, account: Account, persisted: List<FtBalance>) {
        if (!tokenAutoEnableManager.autoEnableTokensOnReceive) return

        val gated = account.origin == AccountOrigin.Restored &&
            !account.isWatchAccount &&
            !tokenAutoEnableManager.isAutoEnabled(account, blockchainType)
        val persistedHeld = if (gated) persisted.filter { it.isHeld }.map { it.contractId }.toSet() else emptySet()

        val held = balances.filter { it.isHeld && it.contractId !in persistedHeld }
        if (held.isEmpty()) return

        val enabledWallets = nearTokenQueriesToEnable(
            account, accountManager.activeAccount, walletManager.activeWallets, held.map { it.contractId },
        )
            .mapNotNull { query -> coinManager.getToken(query) }
            .map { token ->
                EnabledWallet(
                    tokenQueryId = token.tokenQuery.id,
                    accountId = account.id,
                    coinName = null,
                    coinCode = null,
                    coinDecimals = null,
                    coinImage = null,
                )
            }
        if (enabledWallets.isEmpty() || accountManager.activeAccount != account) return

        walletManager.saveEnabledWallets(enabledWallets)
    }

    private val FtBalance.isHeld: Boolean get() = balance.signum() > 0
}

/** Select only the owning account's missing token wallets; another account's wallets are unrelated. */
internal fun nearTokenQueriesToEnable(
    account: Account,
    activeAccount: Account?,
    existingWallets: List<Wallet>,
    contractIds: List<String>,
): List<TokenQuery> {
    if (activeAccount != account) return emptyList()
    val existingQueries = existingWallets.filter { it.account == account }.map { it.token.tokenQuery }.toSet()
    return contractIds.distinct()
        .map { TokenQuery(BlockchainType.Near, TokenType.Nep141(it)) }
        .filter { it !in existingQueries }
}

package io.horizontalsystems.walletkit.modules.approvals

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.horizontalsystems.walletkit.core.App
import io.horizontalsystems.walletkit.core.managers.EvmKitManagerRegistry
import io.horizontalsystems.walletkit.core.managers.rpcSource
import io.horizontalsystems.walletkit.core.tor.torcore.TorConstants
import io.horizontalsystems.walletkit.entities.Account
import io.horizontalsystems.walletkit.entities.AccountType
import io.horizontalsystems.walletkit.entities.evmAddress
import io.horizontalsystems.walletkit.modules.multiswap.sendtransaction.EvmTransactionData
import io.horizontalsystems.walletkit.modules.settings.privacy.tor.TorStatus
import io.horizontalsystems.ethereumkit.models.RpcSource
import io.horizontalsystems.marketkit.models.BlockchainType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Credentials
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/** Only the configured RPC is queried. No indexer, remote ABI, signing or broadcast API. */
class EvmApprovalAccessService private constructor(
    private val account: Account,
    private val blockchainType: BlockchainType,
    override val owner: String,
) : ApprovalAccessService {
    private val mutex = Mutex()
    // shortcut: session-only progress; persist per account if scans need to survive closing this page.
    private val candidates = linkedMapOf<String, ApprovalTarget>()
    private val history = ApprovalLogScan()

    override suspend fun scan(onProgress: (Long) -> Unit): ApprovalScan = mutex.withLock {
        val rpc = openRpc()
        val head = quantity(rpc.call("eth_blockNumber")).longValueExact()
        history.scan(head, { from, to -> remember(fetchRange(rpc, from, to)) }, onProgress)
        val active = mutableListOf<ActiveApproval>()
        var failed = 0
        for (candidate in candidates.values) {
            try {
                check(rpc, candidate)?.let(active::add)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failed++
            }
        }
        ApprovalScan(active, history.nextBlock, history.scannedHead, failed, head)
    }

    override suspend fun check(target: ApprovalTarget): ActiveApproval? = mutex.withLock {
        ApprovalAbi.validate(target)
        val checked = check(openRpc(), target)
        remember(listOf(target))
        checked
    }

    override suspend fun revokeData(target: ApprovalTarget): EvmTransactionData = mutex.withLock {
        require(!account.isWatchAccount) { "Watch-only wallet cannot sign" }
        requireNotNull(check(openRpc(), target)) { "This approval is no longer active" }
        // No remote calldata is ever accepted. All revocations are zero-value allowlisted calls.
        ApprovalAbi.revoke(target)
    }

    private fun remember(targets: List<ApprovalTarget>) {
        require((candidates.keys + targets.map { it.key }).size <= 5000) {
            "Too many permissions to scan in one session"
        }
        targets.forEach { candidates[it.key] = it }
    }

    private suspend fun check(rpc: Rpc, target: ApprovalTarget): ActiveApproval? {
        ApprovalAbi.validate(target)
        val ownerWord = ApprovalAbi.addressWord(owner)
        val spenderWord = ApprovalAbi.addressWord(target.spender)
        val activeAmount = when (target.kind) {
            ApprovalKind.Token -> {
                val amount = word(rpc.contractCall(target.contract, "dd62ed3e$ownerWord$spenderWord"))
                if (amount.signum() > 0) amount.toString() else null
            }
            ApprovalKind.NftCollection -> {
                val approved = word(rpc.contractCall(target.contract, "e985e9c5$ownerWord$spenderWord"))
                require(approved == BigInteger.ZERO || approved == BigInteger.ONE)
                if (approved == BigInteger.ONE) "1" else null
            }
            ApprovalKind.Nft -> {
                val tokenWord = ApprovalAbi.uintWord(target.tokenId!!)
                // Transfers invalidate token-specific NFT approvals; check both current owner and spender.
                val currentOwner = word(rpc.contractCall(target.contract, "6352211e$tokenWord"))
                val approved = word(rpc.contractCall(target.contract, "081812fc$tokenWord"))
                if (currentOwner == BigInteger(ownerWord, 16) && approved == BigInteger(spenderWord, 16)) "1" else null
            }
        }
        return activeAmount?.let { ActiveApproval(target, it) }
    }

    private suspend fun fetchRange(rpc: Rpc, from: Long, to: Long): List<ApprovalTarget> {
        val ownerTopic = "0x" + ApprovalAbi.addressWord(owner)
        return listOf(APPROVAL_TOPIC, APPROVAL_ALL_TOPIC).flatMap { signature ->
            val filter = JsonObject().apply {
                addProperty("fromBlock", "0x${from.toString(16)}")
                addProperty("toBlock", "0x${to.toString(16)}")
                add("topics", JsonArray().apply { add(signature); add(ownerTopic) })
            }
            val logs = rpc.call("eth_getLogs", JsonArray().apply { add(filter) }).asJsonArray
            if (logs.size() >= 10000) throw ApprovalLogRangeError()
            logs.mapNotNull { log -> parseLog(log.asJsonObject, ownerTopic, signature, from, to) }
        }
    }

    private suspend fun openRpc(): Rpc {
        require(App.accountManager.activeAccount?.id == account.id) { "Wallet changed. Reopen Revoke Access." }
        val expectedChain = requireNotNull(App.evmBlockchainManager.getChainId(blockchainType))
        val source = App.evmSyncSourceManager.getHttpSyncSource(blockchainType)?.rpcSource as? RpcSource.Http
            ?: error("This network has no HTTP RPC source")
        val url = source.uris.firstOrNull()?.toString() ?: error("No RPC source")
        val rpc = Rpc(url, source.auth?.let { Credentials.basic("", it) }, account.id)
        require(quantity(rpc.call("eth_chainId")) == expectedChain.toBigInteger()) { "RPC network does not match the selected network" }
        return rpc
    }

    private class Rpc(private val url: String, private val auth: String?, private val accountId: String) {
        suspend fun call(method: String, params: JsonArray = JsonArray()): JsonElement {
            require(App.accountManager.activeAccount?.id == accountId) { "Wallet changed. Reopen Revoke Access." }
            val torEnabled = App.torKitManager.isTorEnabled
            if (torEnabled) check(App.torKitManager.torStatusFlow.value == TorStatus.Connected) { "Wait for Tor to connect" }
            val api = if (torEnabled) torApi else directApi
            val response = api.call(url, auth, JsonObject().apply {
                addProperty("jsonrpc", "2.0"); addProperty("id", 1)
                addProperty("method", method); add("params", params)
            })
            // Discard a response if privacy/account settings changed while it was in flight.
            require(App.accountManager.activeAccount?.id == accountId && App.torKitManager.isTorEnabled == torEnabled)
            if (response.has("error")) throw RpcFailure()
            return response.get("result")?.takeUnless { it.isJsonNull } ?: throw RpcFailure()
        }

        suspend fun contractCall(contract: String, data: String) = call("eth_call", JsonArray().apply {
            add(JsonObject().apply { addProperty("to", ApprovalAbi.address(contract)); addProperty("data", "0x$data") })
            add("latest")
        })
    }

    private class RpcFailure : ApprovalLogRangeError()

    private interface Api {
        @POST suspend fun call(@Url url: String, @Header("Authorization") auth: String?, @Body body: JsonObject): JsonObject
    }

    companion object {
        internal const val APPROVAL_TOPIC = "0x8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b925"
        internal const val APPROVAL_ALL_TOPIC = "0x17307eab39ab6107e8899845ad3d59bd9653f200f220920489ca2b5937696c31"

        private fun api(proxy: Proxy? = null): Api {
            // Deliberately no request/body logging. Reject redirects instead of disclosing address queries.
            val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS)
                .followRedirects(false).followSslRedirects(false).apply { proxy?.let(::proxy) }.build()
            return Retrofit.Builder().baseUrl("https://localhost/").client(client)
                .addConverterFactory(GsonConverterFactory.create()).build().create(Api::class.java)
        }
        private val directApi by lazy { api() }
        private val torApi by lazy { api(Proxy(Proxy.Type.HTTP, InetSocketAddress(
            TorConstants.IP_LOCALHOST, TorConstants.HTTP_PROXY_PORT_DEFAULT.toInt()))) }

        fun create(account: Account, blockchainType: BlockchainType): EvmApprovalAccessService? {
            val owner = when (val type = account.type) {
                is AccountType.EvmAddress -> type.address
                else -> type.evmAddress(EvmKitManagerRegistry.getChain(blockchainType))?.hex
            } ?: return null
            return EvmApprovalAccessService(account, blockchainType, ApprovalAbi.address(owner))
        }

        private fun quantity(value: JsonElement): BigInteger {
            val text = value.asString
            require(text.matches(Regex("^0x[0-9a-fA-F]{1,64}$")))
            return text.removePrefix("0x").toBigInteger(16)
        }

        private fun word(value: JsonElement): BigInteger {
            require(value.asString.matches(Regex("^0x[0-9a-fA-F]{64}$"))) { "Contract returned invalid approval data" }
            return quantity(value)
        }

        internal fun parseLog(log: JsonObject, ownerTopic: String, signature: String, from: Long, to: Long): ApprovalTarget? {
            if (log.get("removed")?.asBoolean == true) return null
            require(quantity(log.get("blockNumber")).longValueExact() in from..to)
            val topics = log.getAsJsonArray("topics").map { it.asString.lowercase() }
            require(topics.size in 3..4 && topics[0] == signature && topics[1] == ownerTopic)
            require(topics[2].matches(Regex("^0x0{24}[0-9a-f]{40}$")))
            val spender = "0x" + topics[2].takeLast(40)
            if (spender == "0x" + "0".repeat(40)) return null
            val contract = ApprovalAbi.address(log.get("address").asString)
            return when {
                signature == APPROVAL_ALL_TOPIC -> {
                    require(topics.size == 3)
                    word(log.get("data"))
                    ApprovalTarget(contract, spender, ApprovalKind.NftCollection)
                }
                topics.size == 4 -> {
                    require(log.get("data").asString == "0x")
                    val id = word(com.google.gson.JsonPrimitive(topics[3])).toString()
                    ApprovalTarget(contract, spender, ApprovalKind.Nft, id)
                }
                else -> {
                    word(log.get("data"))
                    ApprovalTarget(contract, spender, ApprovalKind.Token)
                }
            }
        }
    }
}

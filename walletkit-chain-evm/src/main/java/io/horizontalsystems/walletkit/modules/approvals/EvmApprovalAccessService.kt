package io.horizontalsystems.walletkit.modules.approvals

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.horizontalsystems.walletkit.core.App
import io.horizontalsystems.walletkit.core.managers.EvmKitManagerRegistry
import io.horizontalsystems.walletkit.core.managers.rpcSource
import io.horizontalsystems.walletkit.core.managers.requireSocksProxy
import io.horizontalsystems.walletkit.entities.Account
import io.horizontalsystems.walletkit.entities.AccountType
import io.horizontalsystems.walletkit.entities.evmAddress
import io.horizontalsystems.walletkit.modules.multiswap.sendtransaction.EvmTransactionData
import io.horizontalsystems.walletkit.modules.settings.privacy.tor.TorStatus
import io.horizontalsystems.ethereumkit.core.toHexString
import io.horizontalsystems.ethereumkit.models.TransactionTag
import io.horizontalsystems.ethereumkit.models.RpcSource
import io.horizontalsystems.marketkit.models.BlockchainType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Credentials
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url
import java.math.BigInteger
import java.net.Proxy
import java.util.concurrent.TimeUnit

/** Uses existing local wallet history and the configured RPC. No indexer or remote transaction calldata. */
class EvmApprovalAccessService private constructor(
    private val account: Account,
    private val blockchainType: BlockchainType,
    override val owner: String,
) : ApprovalAccessService {
    private val mutex = Mutex()
    // shortcut: session-only progress; persist per account if scans need to survive closing this page.
    private val candidates = linkedMapOf<String, ApprovalTarget>()
    private val history = ApprovalLogScan()
    private class CachedPage(val tags: List<List<String>>) {
        var cursor: ByteArray? = null
        var complete = false
    }
    private val cachedPages = listOf(
        CachedPage(listOf(listOf(TransactionTag.EIP20_APPROVE))), CachedPage(emptyList()),
    )
    private val seenReceipts = linkedSetOf<String>()

    override suspend fun scan(onProgress: (Long) -> Unit): ApprovalScan = mutex.withLock {
        val rpc = openRpc()
        val head = quantity(rpc.call("eth_blockNumber")).longValueExact()
        var failed = 0
        var historyError = false
        try {
            history.scan(head, { from, to -> remember(fetchRange(rpc, from, to)) }, onProgress)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            historyError = true
        }
        if (historyError || history.nextBlock != null || history.scannedHead < head) {
            failed += readCachedApprovals(rpc)
        }
        val active = mutableListOf<ActiveApproval>()
        for (candidate in candidates.values) {
            try {
                check(rpc, candidate)?.let(active::add)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failed++
            }
        }
        ApprovalScan(active, history.nextBlock, history.scannedHead, failed, head, historyError)
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

    override suspend fun metadata(target: ApprovalTarget): ApprovalAssetMetadata? = mutex.withLock {
        ApprovalAbi.validate(target)
        val rpc = openRpc()
        suspend fun text(selector: String, maxLength: Int): String? = try {
            ApprovalMetadataParser.text(rpc.contractCall(target.contract, selector).asString, maxLength)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
        val name = text("06fdde03", 128)
        val symbol = text("95d89b41", 32)
        if (name == null && symbol == null) return@withLock null
        val decimals = if (target.kind == ApprovalKind.Token) try {
            word(rpc.contractCall(target.contract, "313ce567")).intValueExact().takeIf { it in 0..255 }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null } else null
        ApprovalAssetMetadata(name ?: symbol!!, symbol ?: "", decimals)
    }

    private suspend fun readCachedApprovals(rpc: Rpc): Int {
        val wrapper = EvmKitManagerRegistry.existingEvmKitWrapper(account, blockchainType) ?: return 0
        if (!wrapper.evmKit.receiveAddress.hex.equals(owner, true)) return 0
        var failures = 0
        for (page in cachedPages) {
            currentCoroutineContext().ensureActive()
            if (page.complete) { page.cursor = null; page.complete = false }
            val transactions = try {
                wrapper.evmKit.getFullTransactionsAsync(page.tags, page.cursor, 50)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failures++; continue }
            var accepted = 0
            for (full in transactions) {
                currentCoroutineContext().ensureActive()
                val tx = full.transaction
                val block = tx.blockNumber
                val rawHash = tx.hash.toHexString()
                val hash = if (rawHash.startsWith("0x")) rawHash else "0x$rawHash"
                try {
                    if (block != null && block >= 0 && !tx.isFailed && hash !in seenReceipts) {
                        require(tx.hash.size == 32)
                        val receipt = rpc.call("eth_getTransactionReceipt", JsonArray().apply { add(hash) }).asJsonObject
                        remember(parseReceipt(receipt, hash, "0x" + ApprovalAbi.addressWord(owner), block))
                        seenReceipts.add(hash)
                        if (seenReceipts.size > 5000) seenReceipts.remove(seenReceipts.first())
                    }
                    // A failed/malformed receipt stays at this cursor for a later retry.
                    page.cursor = tx.hash
                    accepted++
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { failures++; break }
            }
            page.complete = accepted == transactions.size && transactions.size < 50
        }
        return failures
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
            currentCoroutineContext().ensureActive()
            require(App.accountManager.activeAccount?.id == accountId) { "Wallet changed. Reopen Revoke Access." }
            val torEnabled = App.torKitManager.isTorEnabled
            if (torEnabled) check(App.torKitManager.torStatusFlow.value == TorStatus.Connected) { "Wait for Tor to connect" }
            val api = if (torEnabled) api(App.torKitManager.requireSocksProxy()) else directApi
            val response = try {
                api.call(url, auth, JsonObject().apply {
                    addProperty("jsonrpc", "2.0"); addProperty("id", 1)
                    addProperty("method", method); add("params", params)
                })
            } catch (error: HttpException) {
                if (method == "eth_getLogs" && error.code() in listOf(400, 413, 504)) throw ApprovalLogRangeError()
                throw error
            } catch (error: java.io.InterruptedIOException) {
                currentCoroutineContext().ensureActive()
                if (method == "eth_getLogs") throw ApprovalLogRangeError()
                throw error
            }
            // Discard a response if privacy/account settings changed while it was in flight.
            require(App.accountManager.activeAccount?.id == accountId && App.torKitManager.isTorEnabled == torEnabled)
            if (response.has("error")) {
                if (method == "eth_getLogs") throw ApprovalLogRangeError()
                throw RpcFailure()
            }
            return response.get("result")?.takeUnless { it.isJsonNull } ?: throw RpcFailure()
        }

        suspend fun contractCall(contract: String, data: String) = call("eth_call", JsonArray().apply {
            add(JsonObject().apply { addProperty("to", ApprovalAbi.address(contract)); addProperty("data", "0x$data") })
            add("latest")
        })
    }

    private class RpcFailure : Exception("The selected RPC could not complete the request")

    private interface Api {
        @POST suspend fun call(@Url url: String, @Header("Authorization") auth: String?, @Body body: JsonObject): JsonObject
    }

    companion object {
        internal const val APPROVAL_TOPIC = "0x8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b925"
        internal const val APPROVAL_ALL_TOPIC = "0x17307eab39ab6107e8899845ad3d59bd9653f200f220920489ca2b5937696c31"

        // Reuse the pool during large scans, including clients bound to the current Tor port.
        private val rpcClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).build()

        private fun api(proxy: Proxy? = null): Api {
            // Deliberately no request/body logging. Reject redirects instead of disclosing address queries.
            val client = rpcClient.newBuilder().apply { proxy?.let(::proxy) }.build()
            return Retrofit.Builder().baseUrl("https://localhost/").client(client)
                .addConverterFactory(GsonConverterFactory.create()).build().create(Api::class.java)
        }
        private val directApi by lazy { api() }

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

        internal fun parseReceipt(receipt: JsonObject, hash: String, ownerTopic: String, block: Long): List<ApprovalTarget> {
            require(receipt.get("transactionHash")?.asString?.equals(hash, true) == true)
            require(quantity(requireNotNull(receipt.get("blockNumber"))).longValueExact() == block)
            receipt.get("status")?.takeUnless { it.isJsonNull }?.let {
                val status = quantity(it)
                require(status == BigInteger.ZERO || status == BigInteger.ONE)
                if (status == BigInteger.ZERO) return emptyList()
            }
            val logs = requireNotNull(receipt.getAsJsonArray("logs"))
            require(logs.size() < 10000)
            return logs.mapNotNull { entry ->
                val log = entry.asJsonObject
                val topics = log.getAsJsonArray("topics") ?: return@mapNotNull null
                if (topics.size() < 2) return@mapNotNull null
                val signature = topics[0].asString.lowercase()
                if (signature != APPROVAL_TOPIC && signature != APPROVAL_ALL_TOPIC) return@mapNotNull null
                if (!topics[1].asString.equals(ownerTopic, true)) return@mapNotNull null
                if (log.get("removed")?.asBoolean == true) return@mapNotNull null
                require(log.get("transactionHash")?.asString?.equals(hash, true) == true)
                parseLog(log, ownerTopic, signature, block, block)
            }
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

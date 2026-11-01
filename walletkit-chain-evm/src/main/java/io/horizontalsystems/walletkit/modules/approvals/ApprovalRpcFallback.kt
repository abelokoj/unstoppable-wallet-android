package io.horizontalsystems.walletkit.modules.approvals

import com.google.gson.JsonElement
import io.horizontalsystems.walletkit.entities.EvmSyncSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class ApprovalRpcEndpoint(val url: String, val auth: String?)

internal fun approvalRpcEndpoints(selected: EvmSyncSource, available: List<EvmSyncSource>): List<ApprovalRpcEndpoint> =
    (listOf(selected) + available).flatMap { source ->
        source.uris.map { ApprovalRpcEndpoint(it.toString(), source.auth) }
    }.distinct()

internal fun isEmptyApprovalRpcResult(method: String, result: JsonElement): Boolean =
    result.isJsonNull || (method == "eth_getLogs" && result.asJsonArray.size() == 0) ||
        (method == "eth_call" && result.asString.matches(Regex("^0x0{64}$")))

/** A negative result is final only after every configured endpoint has been checked. */
internal suspend fun <S, T : Any> readApprovalRpcSources(
    sources: List<S>,
    guard: () -> Unit,
    isEmpty: (T) -> Boolean,
    read: suspend (S) -> T,
): T {
    var empty: T? = null
    var failure: Exception? = null
    for (source in sources) {
        currentCoroutineContext().ensureActive()
        guard()
        val result = try { read(source) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            guard()
            failure = error
            continue
        }
        guard()
        if (!isEmpty(result)) return result
        empty = result
    }
    failure?.let { throw it }
    return requireNotNull(empty) { "No approval RPC is available" }
}

package io.horizontalsystems.walletkit.modules.multiswap.providers

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.horizontalsystems.marketkit.models.BlockchainType
import io.horizontalsystems.marketkit.models.Token
import io.horizontalsystems.marketkit.models.TokenQuery
import io.horizontalsystems.marketkit.models.TokenType
import io.horizontalsystems.walletkit.R
import io.horizontalsystems.walletkit.core.App
import io.horizontalsystems.walletkit.core.chain.ChainRegistry
import io.horizontalsystems.walletkit.entities.Address
import io.horizontalsystems.walletkit.entities.SwapRecord
import io.horizontalsystems.walletkit.modules.multiswap.SwapFinalQuote
import io.horizontalsystems.walletkit.modules.multiswap.SwapQuote
import io.horizontalsystems.walletkit.modules.multiswap.history.SwapStatus
import io.horizontalsystems.walletkit.modules.multiswap.sendtransaction.SendTransactionData
import io.horizontalsystems.walletkit.modules.multiswap.sendtransaction.SendTransactionSettings
import io.horizontalsystems.walletkit.modules.multiswap.sendtransaction.SolanaInstruction
import io.horizontalsystems.walletkit.modules.multiswap.ui.DataFieldRecipient
import io.horizontalsystems.walletkit.modules.multiswap.ui.DataFieldSlippage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap

object PancakeCrossChainProvider : IMultiSwapProvider {
    override val id = "pancake_crosschain"
    override val title = "PancakeSwap Cross-chain"
    override val icon = R.drawable.swap_provider_pancake
    override val type = SwapProviderType.DEX
    override val requireTerms = true
    override val quoteTimeoutMillis = 30_000L
    override val riskLevel = RiskLevel.EXCELLENT
    private val gson = Gson()
    @Volatile private var routes: List<PancakeCrossChainApi.Route> = emptyList()
    private const val dummyEvm = "0x1111111111111111111111111111111111111111"
    private const val dummySolana = "6uzPkC5fsHgBwZLjpDjMgryfnioXo6uGEbMbEGVNk2GR"

    override suspend fun start() {
        routes = emptyList()
        routes = PancakeCrossChainNetwork.request { it.routes() }.routes.filter {
            it.originChainId != PancakeNetworks.SOLANA && it.destinationChainId != PancakeNetworks.SOLANA &&
                PancakeNetworks.ids.containsValue(it.originChainId) && PancakeNetworks.ids.containsValue(it.destinationChainId)
        }.onEach {
            require(it.originChainId != it.destinationChainId)
            PancakeCrossChainTransaction.address(it.originToken); PancakeCrossChainTransaction.address(it.destinationToken)
        }
    }

    override fun supports(blockchainType: BlockchainType) = PancakeNetworks.ids.containsKey(blockchainType)
    override fun isSingleTransactionSwap(tokenInBlockchainTypeUid: String, tokenOutBlockchainTypeUid: String) = false
    override fun supports(tokenFrom: Token, tokenTo: Token): Boolean {
        val origin = PancakeNetworks.ids[tokenFrom.blockchainType] ?: return false
        val destination = PancakeNetworks.ids[tokenTo.blockchainType] ?: return false
        if (origin == destination || tokenFrom.currency == null || tokenTo.currency == null) return false
        if (origin == PancakeNetworks.SOLANA || destination == PancakeNetworks.SOLANA) return true
        return routes.any { it.originChainId == origin && it.destinationChainId == destination }
    }

    override suspend fun fetchQuote(tokenIn: Token, tokenOut: Token, amountIn: BigDecimal): SwapQuote {
        require(supports(tokenIn, tokenOut))
        val plan = plan(tokenIn, tokenOut, amountIn, IMultiSwapProvider.DEFAULT_SLIPPAGE)
        val spender = if (plan is NonEvmPlan) PancakeRelayEvmTransaction.DEPOSITORY
            else PancakeCrossChainTransaction.chain(PancakeNetworks.ids.getValue(tokenIn.blockchainType)).sender
        val action = if (tokenIn.type is TokenType.Eip20) {
            val plugin = requireNotNull(ChainRegistry[tokenIn.blockchainType])
            plugin.eip20ApproveAction(plugin.eip20Allowance(tokenIn, spender), amountIn, spender, tokenIn)
        } else null
        return SwapQuote(plan.output.toBigDecimal().movePointLeft(tokenOut.decimals), tokenIn, tokenOut, amountIn,
            action, plan.seconds)
    }

    override suspend fun fetchFinalQuote(tokenIn: Token, tokenOut: Token, amountIn: BigDecimal,
        sendTransactionSettings: SendTransactionSettings?, swapQuote: SwapQuote, recipient: Address?, slippage: BigDecimal): SwapFinalQuote {
        require(supports(tokenIn, tokenOut))
        val sender = requireNotNull(SwapHelper.getSendingAddressForToken(tokenIn))
        val receiver = recipient?.hex ?: SwapHelper.getReceiveAddressForToken(tokenOut)
        val plan = plan(tokenIn, tokenOut, amountIn, slippage, sender, receiver)
        val origin = PancakeNetworks.ids.getValue(tokenIn.blockchainType)
        val destination = PancakeNetworks.ids.getValue(tokenOut.blockchainType)
        val amount = PancakeCrossChainTransaction.rawAmount(amountIn, tokenIn.decimals)
        val sendData: SendTransactionData
        val spender: String?
        var requestId: String? = null
        when (plan) {
            is EvmPlan -> {
                val quoted = plan.value
                spender = PancakeCrossChainTransaction.chain(origin).sender
                if (quoted.source == null && quoted.destination == null) {
                    sendData = SendTransactionData.Evm(PancakeCrossChainTransaction.build(origin, destination,
                        tokenIn.currency!!, tokenOut.currency!!, amount, quoted.bridge.bridgeTransactionData!!, sender, receiver), null)
                } else {
                    val commands = JsonArray()
                    quoted.source?.let { commands.add(swapCommand(it, quoted.slippageBps, plan.rpcs)) }
                    commands.add(bridgeCommand(origin, destination, quoted.bridgeInput, quoted.bridgeOutput, quoted.bridgeAmount, quoted.bridge, sender))
                    quoted.destination?.let { commands.add(swapCommand(it, quoted.slippageBps, plan.rpcs)) }
                    val response = PancakeCrossChainNetwork.request { api -> api.calldata(JsonObject().apply {
                        addProperty("inputToken", tokenIn.currency); addProperty("outputToken", tokenOut.currency)
                        addProperty("inputAmount", amount.toString()); addProperty("originChainId", origin)
                        addProperty("destinationChainId", destination); addProperty("recipientOnDestChain", receiver)
                        addProperty("type", "EVM"); add("commands", commands)
                    }) }
                    val transaction = requireNotNull(response.getAsJsonObject("transactionData"))
                    require(transaction.get("router")?.asString.equals(spender, true))
                    val data = requireNotNull(transaction.get("calldata")?.asString)
                    val order = PancakeSwapOrder(origin, destination, tokenIn.currency!!, tokenOut.currency!!, amount,
                        quoted.bridgeInput, quoted.bridgeOutput, quoted.bridgeAmount, quoted.bridge.bridgeTransactionData!!,
                        quoted.source?.expectation(), quoted.destination?.expectation(), sender, receiver, System.currentTimeMillis() / 1000)
                    val checks = PancakeCrossChainTransaction.routerChecks(order, data)
                    for (check in checks) {
                        val result = plan.rpcs.computeIfAbsent(check.chainId) { PancakeCrossChainNetwork.Rpc(it) }
                            .read(PancakePoolCall(check.custodian, check.calldata))
                        require(result.matches(Regex("^0x[0-9a-fA-F]{64}$")) && result.drop(2).toBigInteger(16) == BigInteger.ONE) {
                            "PancakeSwap router is not authorized by the bridge"
                        }
                    }
                    sendData = SendTransactionData.Evm(PancakeCrossChainTransaction.buildWithSwaps(order, data, checks), null)
                }
            }
            is NonEvmPlan -> {
                val response = PancakeCrossChainNetwork.request { api -> api.calldata(JsonObject().apply {
                    addProperty("requestId", plan.quote.requestId); addProperty("inputToken", tokenIn.currency)
                    addProperty("outputToken", tokenOut.currency); addProperty("inputAmount", amount.toString())
                    addProperty("originChainId", origin); addProperty("destinationChainId", destination)
                    addProperty("recipientOnDestChain", receiver); addProperty("user", sender)
                    addProperty("type", "NON-EVM"); addProperty("slippageTolerance", plan.slippageBps)
                }) }
                val committed = PancakeNonEvmQuote.parse(response, origin, destination, tokenIn.currency!!,
                    tokenOut.currency!!, amount, plan.slippageBps, plan.quote)
                requestId = committed.requestId
                if (origin == PancakeNetworks.SOLANA) {
                    spender = null
                    val steps = requireNotNull(response.getAsJsonArray("steps")).map { it.asJsonObject }
                    require(steps.size == 1 && steps.single().get("id")?.asString == "deposit" && steps.single().get("kind")?.asString == "transaction")
                    val items = steps.single().getAsJsonArray("items"); require(items.size() == 1)
                    val data = items.single().asJsonObject.getAsJsonObject("data")
                    val instructions = gson.fromJson(data.get("instructions"), Array<SolanaInstruction>::class.java).toList()
                    val tables = data.getAsJsonArray("addressLookupTableAddresses")?.map { it.asString } ?: emptyList()
                    sendData = SendTransactionData.Solana.WithRelayInstructions(instructions, tables, sender, tokenIn.currency!!,
                        amount, committed.requestId, plan.slippageBps)
                } else {
                    spender = PancakeRelayEvmTransaction.DEPOSITORY
                    sendData = SendTransactionData.Evm(PancakeRelayEvmTransaction.build(response, origin, tokenIn.currency!!, amount, sender), null)
                }
            }
        }
        return SwapFinalQuote(tokenIn, tokenOut, amountIn, plan.output.toBigDecimal().movePointLeft(tokenOut.decimals),
            plan.minimum.toBigDecimal().movePointLeft(tokenOut.decimals), sendData, null,
            buildList { recipient?.let { add(DataFieldRecipient(it)) }; if (plan.hasSwap) DataFieldSlippage.getField(slippage)?.let { add(it) } },
            plan.seconds, if (plan.hasSwap) slippage else null, providerSwapId = requestId, approvalSpender = spender,
            fromAsset = tokenIn.currency, toAsset = tokenOut.currency)
    }

    private sealed interface Plan {
        val output: BigInteger
        val minimum: BigInteger
        val seconds: Long?
        val hasSwap: Boolean
    }
    private fun PancakeEvmPlanner.Leg.expectation() = PancakeSwapExpectation(quote.chainId,
        quote.inputToken, quote.outputToken, quote.amountIn, minimum, quote.raw)
    private data class EvmPlan(val value: PancakeEvmPlanner.Plan,
        val rpcs: ConcurrentHashMap<Long, PancakeCrossChainNetwork.Rpc>) : Plan {
        override val output get() = value.expectedOutput
        override val minimum get() = value.minimumOutput
        override val seconds get() = value.bridge.expectedFillTimeSec?.toLongOrNull()?.takeIf { it > 0 }
        override val hasSwap get() = value.source != null || value.destination != null
    }
    private data class NonEvmPlan(val quote: PancakeNonEvmQuote, val slippageBps: Int) : Plan {
        override val output get() = quote.amountOut
        override val minimum get() = quote.minimumOut
        override val seconds get() = quote.estimatedTime
        override val hasSwap = true
    }

    private suspend fun plan(input: Token, output: Token, amount: BigDecimal, slippage: BigDecimal,
                             sender: String? = null, recipient: String? = null): Plan {
        val origin = PancakeNetworks.ids.getValue(input.blockchainType)
        val destination = PancakeNetworks.ids.getValue(output.blockchainType)
        val raw = PancakeCrossChainTransaction.rawAmount(amount, input.decimals)
        if (origin == PancakeNetworks.SOLANA || destination == PancakeNetworks.SOLANA) {
            val bps = PancakeRoutePlanner.slippageBps(slippage, 1)
            val response = PancakeCrossChainNetwork.request { api -> api.nonEvmQuote(origin, destination,
                input.currency!!, output.currency!!, raw.toString(), JsonObject().apply {
                    addProperty("type", "NON-EVM"); addProperty("slippageTolerance", bps)
                    addProperty("user", sender ?: if (origin == PancakeNetworks.SOLANA) dummySolana else dummyEvm)
                    addProperty("recipientOnDestChain", recipient ?: if (destination == PancakeNetworks.SOLANA) dummySolana else dummyEvm)
                }) }
            return NonEvmPlan(PancakeNonEvmQuote.parse(response, origin, destination, input.currency!!, output.currency!!, raw, bps), bps)
        }
        val rpcs = ConcurrentHashMap<Long, PancakeCrossChainNetwork.Rpc>()
        val decimals = ConcurrentHashMap<Pair<Long, String>, Int>().apply {
            put(origin to input.currency!!.lowercase(), input.decimals)
            put(destination to output.currency!!.lowercase(), output.decimals)
        }
        val planner = PancakeEvmPlanner(routes,
            { chain, from, to, value -> ammQuote(chain, from, to, value, rpcs) },
            { fromChain, toChain, from, to, value, body -> PancakeCrossChainNetwork.request {
                it.quote(fromChain, toChain, from, to, value.toString(), body)
            } },
            { leg, bps -> swapCommand(leg, bps, rpcs) },
            { chain, token ->
                val key = chain to token.lowercase()
                decimals[key] ?: if (token == PancakeCrossChainTransaction.NATIVE) 18 else {
                    val result = rpcs.computeIfAbsent(chain) { PancakeCrossChainNetwork.Rpc(it) }
                        .read(PancakePoolCall(token, "0x313ce567"))
                    require(result.matches(Regex("^0x[0-9a-fA-F]{64}$"))) { "Invalid bridge token decimals" }
                    result.drop(2).toBigInteger(16).intValueExact().also {
                        require(it in 0..255); decimals[key] = it
                    }
                }
            })
        return EvmPlan(planner.quote(origin, destination, input.currency!!, output.currency!!,
            raw, slippage, recipient ?: dummyEvm), rpcs)
    }

    private suspend fun ammQuote(chain: Long, input: String, output: String, amount: BigInteger,
                                 rpcs: ConcurrentHashMap<Long, PancakeCrossChainNetwork.Rpc>): PancakeAmmQuote {
        return PancakeAmmRouting.quote(chain, input, output, amount,
            rpcs.computeIfAbsent(chain) { PancakeCrossChainNetwork.Rpc(it) })
    }

    private suspend fun swapCommand(leg: PancakeEvmPlanner.Leg, bps: Int,
                                    rpcs: ConcurrentHashMap<Long, PancakeCrossChainNetwork.Rpc>): JsonObject {
        val rpc = rpcs.computeIfAbsent(leg.quote.chainId) { PancakeCrossChainNetwork.Rpc(it) }
        val states = mutableMapOf<PancakePoolCall, String>()
        suspend fun reads(calls: List<PancakePoolCall>) {
            for (batch in calls.filterNot { states.containsKey(it) }.chunked(6)) coroutineScope {
                batch.map { call -> async {
                    try { call to rpc.read(call) }
                    catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                } }.awaitAll().filterNotNull().forEach { (call, result) -> states[call] = result }
            }
        }
        reads(leg.quote.stateCalls)
        reads(leg.quote.additionalStateCalls(states))
        return JsonObject().apply {
            addProperty("command", "SWAP")
            add("data", JsonObject().apply { addProperty("originChainId", leg.quote.chainId)
                add("trade", leg.quote.asTrade(leg.minimum, states)); addProperty("slippageTolerance", bps) })
        }
    }

    private fun bridgeCommand(origin: Long, destination: Long, input: String, output: String, amount: BigInteger,
                               bridge: PancakeBridgeQuote, refund: String) = JsonObject().apply {
        addProperty("command", "BRIDGE"); add("data", JsonObject().apply {
            addProperty("inputToken", input); addProperty("outputToken", output); addProperty("inputAmount", amount.toString())
            addProperty("originChainId", origin); addProperty("destinationChainId", destination)
            addProperty("originChainRecipient", refund); addProperty("minOutputAmount", bridge.bridgeTransactionData!!.outputAmount)
            add("bridgeTransactionData", gson.toJsonTree(bridge.bridgeTransactionData))
        })
    }

    internal suspend fun status(record: SwapRecord): Pair<SwapStatus, String?>? {
        val hash = record.transactionHash ?: return null
        val origin = PancakeNetworks.ids.entries.find { it.key.uid == record.tokenInBlockchainTypeUid }?.value ?: return null
        val destination = PancakeNetworks.ids.entries.find { it.key.uid == record.tokenOutBlockchainTypeUid }?.value ?: return null
        val input = record.fromAsset ?: return null
        val output = record.toAsset ?: return null
        val tokenIn = TokenQuery.fromId(record.tokenInUid)?.let { App.coinManager.getToken(it) } ?: return null
        val tokenOut = TokenQuery.fromId(record.tokenOutUid)?.let { App.coinManager.getToken(it) } ?: return null
        if (origin != PancakeNetworks.SOLANA) {
            val receipt = PancakeCrossChainNetwork.Rpc(origin).receipt(hash) ?: return null
            require(receipt.get("transactionHash")?.asString.equals(hash, true))
            record.sourceAddress?.let { require(receipt.get("from")?.asString.equals(it, true)) }
            val target = if (destination == PancakeNetworks.SOLANA) PancakeRelayEvmTransaction.DEPOSITORY
                else PancakeCrossChainTransaction.chain(origin).sender
            require(receipt.get("to")?.asString.equals(target, true))
            when (receipt.get("status")?.asString?.lowercase()) {
                "0x0" -> return SwapStatus.Failed to null
                "0x1" -> Unit
                else -> return null
            }
        }
        val type = if (origin == PancakeNetworks.SOLANA || destination == PancakeNetworks.SOLANA) "NON-EVM" else "EVM"
        val response = PancakeCrossChainNetwork.request { it.rawStatus(PancakeNetworks.apiName(origin), hash, type) }
        val parsed = gson.fromJson(response, PancakeBridgeStatus::class.java)
        val status = parsed.validatedStatus(hash, origin, destination, input, output,
            PancakeCrossChainTransaction.rawAmount(record.amountIn.toBigDecimal(), tokenIn.decimals),
            PancakeCrossChainTransaction.rawAmount(requireNotNull(record.amountOutMin).toBigDecimal(), tokenOut.decimals)) ?: return null
        return status to parsed.outboundHash
    }

    private val Token.currency: String?
        get() = when (val tokenType = type) {
            TokenType.Native -> if (blockchainType == BlockchainType.Solana) PancakeNetworks.SOL else PancakeCrossChainTransaction.NATIVE
            is TokenType.Eip20 -> if (blockchainType != BlockchainType.Solana) PancakeCrossChainTransaction.address(tokenType.address)
                .takeUnless { it == PancakeCrossChainTransaction.NATIVE } else null
            is TokenType.Spl -> if (blockchainType == BlockchainType.Solana) tokenType.address else null
            else -> null
        }
}

package io.horizontalsystems.walletkit.modules.multiswap.providers

import io.horizontalsystems.walletkit.modules.multiswap.sendtransaction.EvmTransactionData
import java.math.BigInteger

internal object RelayQuoteValidation {
    fun deposit(response: RelayApi.QuoteResponse, chain: Long, token: String, amount: BigInteger, sender: String): EvmTransactionData {
        require(response.requestId.matches(Regex("^0x[0-9a-fA-F]{64}$"))) { "Invalid Relay request identifier" }
        require(response.steps.isNotEmpty() && response.steps.all { it.kind == "transaction" && it.id in setOf("deposit", "approve", "approval") && it.requestId.equals(response.requestId, true) }) { "Unsupported Relay execution steps" }
        val deposits = response.steps.filter { it.id == "deposit" }
        require(deposits.size == 1 && deposits.single().items.size == 1) { "Relay must return exactly one deposit" }
        val approvals = response.steps.filter { it.id != "deposit" }
        require(approvals.size <= 1 && (token != RelayDepositTransaction.NATIVE || approvals.isEmpty()))
        for (approval in approvals) {
            require(approval.items.size == 1)
            val tx = requireNotNull(approval.items.single().data)
            require(tx.chainId == chain && RelayDepositTransaction.address(tx.from) == RelayDepositTransaction.address(sender))
            require(RelayDepositTransaction.address(tx.to) == RelayDepositTransaction.address(token) && RelayDepositTransaction.uint(tx.value) == BigInteger.ZERO)
            val data = tx.data.lowercase()
            require(data.matches(Regex("^0x095ea7b3[0-9a-f]{128}$"))) { "Unsupported Relay approval" }
            require(data.substring(10, 74) == RelayDepositTransaction.DEPOSITORY.drop(2).padStart(64, '0')) { "Relay changed the approval spender" }
            require(data.substring(74).toBigInteger(16) >= amount)
            // No provider approval calldata is signed; the existing wallet action approves the exact amount.
        }
        return RelayDepositTransaction.build(chain, token, amount, sender, requireNotNull(deposits.single().items.single().data))
    }

    fun validate(response: RelayApi.QuoteResponse, request: RelayApi.QuoteRequest, inputDecimals: Int, outputDecimals: Int) {
        require(request.originChainId != request.destinationChainId && request.explicitDeposit && !request.usePermit && request.tradeType == "EXACT_INPUT")
        require(inputDecimals in 0..255 && outputDecimals in 0..255)
        require(RelayDepositTransaction.address(request.refundTo) == RelayDepositTransaction.address(request.user))
        val details = response.details
        require(RelayDepositTransaction.address(requireNotNull(details.sender)) == RelayDepositTransaction.address(request.user)) { "Relay changed the sender" }
        require(RelayDepositTransaction.address(requireNotNull(details.recipient)) == RelayDepositTransaction.address(requireNotNull(request.recipient))) { "Relay changed the recipient" }
        fun currency(amount: RelayApi.CurrencyAmount, chain: Long, token: String, decimals: Int) {
            val currency = requireNotNull(amount.currency) { "Relay currency metadata is missing" }
            require(currency.chainId == chain && RelayDepositTransaction.address(currency.address) == RelayDepositTransaction.address(token) && currency.decimals == decimals) { "Relay changed the quoted asset" }
        }
        val input = requireNotNull(details.currencyIn)
        currency(input, request.originChainId, request.originCurrency, inputDecimals)
        currency(details.currencyOut, request.destinationChainId, request.destinationCurrency, outputDecimals)
        details.refundCurrency?.let { currency(it, request.originChainId, request.originCurrency, inputDecimals) }
        val amount = RelayDepositTransaction.uint(request.amount).also { require(it.signum() > 0) }
        require(RelayDepositTransaction.uint(requireNotNull(input.amount)) == amount) { "Relay changed the input amount" }
        val output = RelayDepositTransaction.uint(requireNotNull(details.currencyOut.amount))
        val minimum = RelayDepositTransaction.uint(requireNotNull(details.currencyOut.minimumAmount))
        val slippage = RelayDepositTransaction.uint(request.slippageTolerance).also { require(it <= BigInteger.valueOf(5000)) }
        require(output.signum() > 0 && minimum.signum() > 0 && minimum <= output)
        require(minimum >= output * (BigInteger.valueOf(10000) - slippage) / BigInteger.valueOf(10000)) { "Relay exceeded the requested slippage" }
        response.fees?.app?.amount?.let { require(RelayDepositTransaction.uint(it) == BigInteger.ZERO) { "Unexpected Relay application fee" } }
        deposit(response, request.originChainId, request.originCurrency, amount, request.user)
    }
}

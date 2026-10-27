package io.horizontalsystems.walletkit.modules.multiswap.providers

import io.horizontalsystems.ethereumkit.contracts.ContractMethodFactories
import io.horizontalsystems.ethereumkit.contracts.ContractMethodFactory
import io.horizontalsystems.ethereumkit.core.IMethodDecorator
import io.horizontalsystems.ethereumkit.core.hexStringToByteArray
import io.horizontalsystems.ethereumkit.models.TransactionData
import io.horizontalsystems.uniswapkit.contract.SwapExactETHForTokensMethodFactory
import io.horizontalsystems.uniswapkit.contract.SwapExactTokensForETHMethodFactory
import io.horizontalsystems.uniswapkit.contract.SwapExactTokensForTokensMethodFactory
import io.horizontalsystems.uniswapkit.models.TradeData
import io.horizontalsystems.uniswapkit.models.TradeType
import java.math.BigInteger

// shortcut: SDK a887150 ignores the flag and miskeys supporting decoders; remove when both are fixed upstream.
internal object FeeOnTransferCompatibility : IMethodDecorator {
    private val methods = listOf(
        "b6f9de95".hexStringToByteArray() to SwapExactETHForTokensMethodFactory,
        "791ac947".hexStringToByteArray() to SwapExactTokensForETHMethodFactory,
        "5c11d795".hexStringToByteArray() to SwapExactTokensForTokensMethodFactory
    )

    private val supportingFactories = ContractMethodFactories().apply {
        registerMethodFactories(methods.map { (selector, factory) ->
            object : ContractMethodFactory by factory {
                override val methodId = selector
            }
        })
    }

    fun transactionData(transactionData: TransactionData, tradeData: TradeData): TransactionData {
        if (!tradeData.options.feeOnTransfer) return transactionData

        require(tradeData.type == TradeType.ExactIn) { "Fee-on-transfer swaps require exact input" }
        val amountOutMin = tradeData.tokenAmountOutMin.rawAmount
        require(amountOutMin > BigInteger.ZERO) { "Fee-on-transfer swaps require a positive minimum output" }

        val input = transactionData.input
        val nativeInput = tradeData.trade.tokenAmountIn.token.isEther
        val argumentCount = if (nativeInput) 4 else 5
        require(input.size == 4 + 32 * (argumentCount + 1 + tradeData.path.size)) { "Unexpected V2 swap arguments" }
        val minimumOffset = if (nativeInput) 4 else 36
        require(BigInteger(1, input.copyOfRange(minimumOffset, minimumOffset + 32)) == amountOutMin) { "Unexpected V2 minimum output" }

        val selector = input.copyOfRange(0, 4)
        val supportingSelector = methods.firstOrNull { (_, factory) -> factory.methodId.contentEquals(selector) }?.first
            ?: throw IllegalArgumentException("Unsupported V2 exact-input selector")

        // Both functions have identical arguments; preserve every byte of the SDK's bounded trade.
        return transactionData.copy(input = supportingSelector + input.copyOfRange(4, input.size))
    }

    // Existing SDK swap decorators recognize these plain method classes and the same ABI arguments.
    override fun contractMethod(input: ByteArray) = supportingFactories.createMethodFromInput(input)
}

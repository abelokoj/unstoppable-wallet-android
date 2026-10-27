package io.horizontalsystems.walletkit.modules.multiswap.providers

import io.horizontalsystems.ethereumkit.contracts.ContractMethodHelper
import io.horizontalsystems.ethereumkit.core.hexStringToByteArray
import io.horizontalsystems.ethereumkit.crypto.InternalBouncyCastleProvider
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.Chain
import io.horizontalsystems.uniswapkit.SwapTransactionDecorator
import io.horizontalsystems.uniswapkit.TradeManager
import io.horizontalsystems.uniswapkit.contract.SwapExactETHForTokensMethod
import io.horizontalsystems.uniswapkit.contract.SwapExactTokensForETHMethod
import io.horizontalsystems.uniswapkit.contract.SwapExactTokensForTokensMethod
import io.horizontalsystems.uniswapkit.decorations.SwapDecoration
import io.horizontalsystems.uniswapkit.models.Pair
import io.horizontalsystems.uniswapkit.models.Route
import io.horizontalsystems.uniswapkit.models.Token
import io.horizontalsystems.uniswapkit.models.TokenAmount
import io.horizontalsystems.uniswapkit.models.Trade
import io.horizontalsystems.uniswapkit.models.TradeData
import io.horizontalsystems.uniswapkit.models.TradeOptions
import io.horizontalsystems.uniswapkit.models.TradeType
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger
import java.security.Security

class FeeOnTransferCompatibilityTest {
    companion object {
        init {
            Security.addProvider(InternalBouncyCastleProvider.getInstance())
        }
    }

    private val tokenIn = Token.Erc20(address("11"), 18)
    private val tokenOut = Token.Erc20(address("22"), 18)
    private val intermediate = Token.Erc20(address("33"), 18)
    private val ether = Token.Ether(address("44"))
    private val receiveAddress = address("55")
    private val recipient = address("66")
    private val variants = listOf(
        Variant(ether, tokenOut, "7ff36ab5", "b6f9de95", "swapExactETHForTokensSupportingFeeOnTransferTokens(uint256,address[],address,uint256)"),
        Variant(tokenIn, ether, "18cbafe5", "791ac947", "swapExactTokensForETHSupportingFeeOnTransferTokens(uint256,uint256,address[],address,uint256)"),
        Variant(tokenIn, tokenOut, "38ed1739", "5c11d795", "swapExactTokensForTokensSupportingFeeOnTransferTokens(uint256,uint256,address[],address,uint256)")
    )

    @Test
    fun `supporting selectors match canonical Keccak signatures`() {
        variants.forEach {
            assertArrayEquals(it.supportingSelector.hexStringToByteArray(), ContractMethodHelper.getMethodId(it.supportingSignature))
        }
    }

    @Test
    fun `all exact input variants preserve ABI arguments router value and source input`() {
        variants.forEach { variant ->
            val tradeData = tradeData(variant)
            val original = TradeManager().transactionData(receiveAddress, Chain.Ethereum, tradeData)
            val originalInput = original.input.copyOf()
            assertArrayEquals(variant.plainSelector.hexStringToByteArray(), original.input.copyOfRange(0, 4))

            val converted = FeeOnTransferCompatibility.transactionData(original, tradeData)

            assertArrayEquals(variant.supportingSelector.hexStringToByteArray(), converted.input.copyOfRange(0, 4))
            assertArrayEquals(originalInput.copyOfRange(4, originalInput.size), converted.input.copyOfRange(4, converted.input.size))
            assertArrayEquals(originalInput, original.input)
            assertEquals(original.to, converted.to)
            assertEquals(original.value, converted.value)
            assertEquals(if (variant.tokenIn.isEther) tradeData.trade.tokenAmountIn.rawAmount else BigInteger.ZERO, converted.value)

            val method = FeeOnTransferCompatibility.contractMethod(converted.input)
            val expectedArguments = if (variant.tokenIn.isEther) {
                listOf(tradeData.tokenAmountOutMin.rawAmount, tradeData.path.map { it.address }, recipient, deadline(original.input, 3))
            } else {
                listOf(tradeData.trade.tokenAmountIn.rawAmount, tradeData.tokenAmountOutMin.rawAmount, tradeData.path.map { it.address }, recipient, deadline(original.input, 4))
            }
            val decodedArguments = when (method) {
                is SwapExactETHForTokensMethod -> listOf(method.amountOutMin, method.path, method.to, method.deadline)
                is SwapExactTokensForETHMethod -> listOf(method.amountIn, method.amountOutMin, method.path, method.to, method.deadline)
                is SwapExactTokensForTokensMethod -> listOf(method.amountIn, method.amountOutMin, method.path, method.to, method.deadline)
                else -> error("Expected an SDK V2 swap method")
            }
            assertEquals(expectedArguments, decodedArguments)
        }
    }

    @Test
    fun `conversion preserves every supported V2 router`() {
        val tradeData = tradeData(variants.last())
        listOf(Chain.Ethereum, Chain.BinanceSmartChain, Chain.Polygon, Chain.Avalanche, Chain.Base).forEach { chain ->
            val original = TradeManager().transactionData(receiveAddress, chain, tradeData)
            val converted = FeeOnTransferCompatibility.transactionData(original, tradeData)
            assertEquals(TradeManager.getRouterAddress(chain), converted.to)
            assertEquals(original.value, converted.value)
        }
    }

    @Test
    fun `disabled policy leaves all exact input variants untouched`() {
        variants.forEach { variant ->
            val tradeData = tradeData(variant, feeOnTransfer = false)
            val original = TradeManager().transactionData(receiveAddress, Chain.Ethereum, tradeData)
            assertSame(original, FeeOnTransferCompatibility.transactionData(original, tradeData))
        }
    }

    @Test
    fun `disabled policy leaves even exact out transaction untouched`() {
        val tradeData = tradeData(variants.last(), type = TradeType.ExactOut, feeOnTransfer = false)
        val original = TradeManager().transactionData(receiveAddress, Chain.Ethereum, tradeData)
        assertSame(original, FeeOnTransferCompatibility.transactionData(original, tradeData))
    }

    @Test
    fun `exact output fee on transfer is rejected`() {
        val tradeData = tradeData(variants.last(), type = TradeType.ExactOut)
        val original = TradeManager().transactionData(receiveAddress, Chain.Ethereum, tradeData)
        assertThrows(IllegalArgumentException::class.java) { FeeOnTransferCompatibility.transactionData(original, tradeData) }
    }

    @Test
    fun `minimum output rounded to zero is rejected`() {
        val tradeData = tradeData(variants.last(), amountOut = BigInteger.ONE)
        assertEquals(BigInteger.ZERO, tradeData.tokenAmountOutMin.rawAmount)
        val original = TradeManager().transactionData(receiveAddress, Chain.Ethereum, tradeData)
        assertThrows(IllegalArgumentException::class.java) { FeeOnTransferCompatibility.transactionData(original, tradeData) }
    }

    @Test
    fun `encoded minimum cannot bypass the quoted minimum`() {
        variants.forEach { variant ->
            val tradeData = tradeData(variant)
            val original = TradeManager().transactionData(receiveAddress, Chain.Ethereum, tradeData)
            val input = original.input.copyOf()
            val minimumOffset = if (variant.tokenIn.isEther) 4 else 36
            input.fill(0, minimumOffset, minimumOffset + 32)
            assertThrows(IllegalArgumentException::class.java) {
                FeeOnTransferCompatibility.transactionData(original.copy(input = input), tradeData)
            }
        }
    }

    @Test
    fun `unknown and truncated selectors are rejected`() {
        val tradeData = tradeData(variants.last())
        val original = TradeManager().transactionData(receiveAddress, Chain.Ethereum, tradeData)
        listOf(byteArrayOf(), byteArrayOf(1, 2, 3), original.input.copyOf(4), "deadbeef".hexStringToByteArray() + original.input.drop(4)).forEach { input ->
            assertThrows(IllegalArgumentException::class.java) {
                FeeOnTransferCompatibility.transactionData(original.copy(input = input), tradeData)
            }
        }
    }

    @Test
    fun `supporting calls retain SDK swap decoration and recipient`() {
        variants.forEach { variant ->
            val tradeData = tradeData(variant)
            val original = TradeManager().transactionData(receiveAddress, Chain.Ethereum, tradeData)
            val converted = FeeOnTransferCompatibility.transactionData(original, tradeData)
            val method = FeeOnTransferCompatibility.contractMethod(converted.input)
            assertTrue(when {
                variant.tokenIn.isEther -> method is SwapExactETHForTokensMethod
                variant.tokenOut.isEther -> method is SwapExactTokensForETHMethod
                else -> method is SwapExactTokensForTokensMethod
            })
            val decoration = SwapTransactionDecorator().decoration(
                receiveAddress, converted.to, converted.value, method, emptyList(), emptyList()
            ) as SwapDecoration
            assertEquals(converted.to, decoration.contractAddress)
            assertEquals(tradeData.trade.tokenAmountIn.rawAmount, decoration.amountIn.value)
            assertEquals(tradeData.tokenAmountOutMin.rawAmount, decoration.amountOut.value)
            assertEquals(recipient, decoration.recipient)
            assertEquals(deadline(original.input, if (variant.tokenIn.isEther) 3 else 4), decoration.deadline)
        }
    }

    @Test
    fun `decoder ignores plain unknown and truncated calls`() {
        val original = TradeManager().transactionData(receiveAddress, Chain.Ethereum, tradeData(variants.last()))
        assertNull(FeeOnTransferCompatibility.contractMethod(original.input))
        assertNull(FeeOnTransferCompatibility.contractMethod("deadbeef".hexStringToByteArray()))
        assertNull(FeeOnTransferCompatibility.contractMethod(byteArrayOf(1, 2, 3)))
    }

    private fun tradeData(
        variant: Variant,
        type: TradeType = TradeType.ExactIn,
        feeOnTransfer: Boolean = true,
        amountOut: BigInteger = BigInteger.valueOf(9_000)
    ): TradeData {
        val reserve = BigInteger.valueOf(1_000_000)
        val pairs = listOf(
            Pair(TokenAmount(variant.tokenIn, reserve), TokenAmount(intermediate, reserve)),
            Pair(TokenAmount(intermediate, reserve), TokenAmount(variant.tokenOut, reserve))
        )
        return TradeData(
            Trade(type, Route(pairs, variant.tokenIn, variant.tokenOut), TokenAmount(variant.tokenIn, BigInteger.valueOf(10_000)), TokenAmount(variant.tokenOut, amountOut)),
            TradeOptions(allowedSlippagePercent = BigDecimal.ONE, recipient = recipient, feeOnTransfer = feeOnTransfer)
        )
    }

    private fun address(byte: String) = Address("0x" + byte.repeat(20))
    private fun deadline(input: ByteArray, argument: Int) = BigInteger(1, input.copyOfRange(4 + argument * 32, 4 + (argument + 1) * 32))

    private data class Variant(val tokenIn: Token, val tokenOut: Token, val plainSelector: String, val supportingSelector: String, val supportingSignature: String)
}

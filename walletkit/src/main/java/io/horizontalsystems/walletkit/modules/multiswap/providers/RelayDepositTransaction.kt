package io.horizontalsystems.walletkit.modules.multiswap.providers

import io.horizontalsystems.walletkit.core.hexStringToByteArray
import io.horizontalsystems.walletkit.modules.multiswap.sendtransaction.EvmTransactionData
import org.web3j.crypto.Hash
import java.math.BigInteger

/** Locally rebuild the only Relay deposit calls this wallet permits signing. */
internal object RelayDepositTransaction {
    // relayprotocol/relay-depository@458a64c310a3504aaf37e112af9a8707c9eb11ad,
    // packages/ethereum-vm/deployments/addresses.prod.json. Never trust a quote's contract registry.
    const val DEPOSITORY = "0x4cd00e387622c35bddb9b4c962c136462338bc31"
    const val NATIVE = "0x0000000000000000000000000000000000000000"
    val chains = setOf(1L, 10L, 56L, 100L, 137L, 324L, 4663L, 8453L, 42161L, 43114L)
    private val nativeSelector = Hash.sha3String("depositNative(address,bytes32)").take(10)
    private val tokenSelector = Hash.sha3String("depositErc20(address,address,uint256,bytes32)").take(10)
    private val allowanceSelector = Hash.sha3String("depositErc20(address,address,bytes32)").take(10)

    fun address(value: String): String {
        require(value.matches(Regex("^0x[0-9a-fA-F]{40}$"))) { "Invalid Relay address" }
        return value.lowercase()
    }

    fun uint(value: String): BigInteger {
        require(value.matches(Regex("^(0x[0-9a-fA-F]{1,64}|[0-9]{1,78})$"))) { "Invalid Relay quantity" }
        return (if (value.startsWith("0x")) value.drop(2).toBigInteger(16) else value.toBigInteger())
            .also { require(it.bitLength() <= 256) { "Relay quantity overflow" } }
    }

    fun build(chain: Long, input: String, amount: BigInteger, sender: String, tx: RelayApi.TransactionData): EvmTransactionData {
        require(chain in chains) { "Relay deposit contract is not verified on this network" }
        require(amount.signum() > 0 && amount.bitLength() <= 256)
        val owner = address(sender).also { require(it.drop(2).toBigInteger(16) > BigInteger.valueOf(2)) }
        val token = address(input)
        require(tx.chainId == chain && address(tx.from) == owner) { "Relay changed the chain or sender" }
        require(address(tx.to) == DEPOSITORY) { "Unrecognized Relay deposit contract" }
        val data = tx.data.lowercase()
        require(data.length in setOf(138, 202, 266) && data.matches(Regex("^0x[0-9a-f]+$"))) { "Unsupported Relay deposit data" }
        fun word(index: Int) = data.substring(10 + index * 64, 74 + index * 64)
        val ownerWord = owner.drop(2).padStart(64, '0')
        require(word(0) == "0".repeat(64) || word(0) == ownerWord) { "Relay changed the depositor" }
        val value = uint(tx.value)
        val intent: String
        val rebuilt: String
        if (token == NATIVE) {
            require(data.length == 138 && data.startsWith(nativeSelector) && value == amount)
            intent = word(1)
            rebuilt = nativeSelector + ownerWord + intent
        } else {
            require(value == BigInteger.ZERO && word(1) == token.drop(2).padStart(64, '0')) { "Relay changed the deposit token" }
            when {
                data.startsWith(tokenSelector) -> {
                    require(data.length == 266 && word(2).toBigInteger(16) == amount) { "Relay changed the deposit amount" }
                    intent = word(3)
                }
                data.startsWith(allowanceSelector) -> { require(data.length == 202); intent = word(2) }
                else -> throw IllegalArgumentException("Unsupported Relay deposit method")
            }
            // The allowance overload can consume an old larger allowance; always encode an exact amount.
            rebuilt = tokenSelector + ownerWord + token.drop(2).padStart(64, '0') + amount.toString(16).padStart(64, '0') + intent
        }
        require(intent.toBigInteger(16) > BigInteger.ZERO) { "Missing Relay deposit identifier" }
        return EvmTransactionData(DEPOSITORY, if (token == NATIVE) amount else BigInteger.ZERO, rebuilt.hexStringToByteArray())
    }
}

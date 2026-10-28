package io.horizontalsystems.walletkit.modules.multiswap.sendtransaction

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.horizontalsystems.walletkit.core.App
import io.horizontalsystems.walletkit.core.managers.requireSocksProxy
import io.horizontalsystems.walletkit.modules.settings.privacy.tor.TorStatus
import io.horizontalsystems.solanakit.Signer
import io.horizontalsystems.solanakit.database.transaction.TransactionDatabase
import io.horizontalsystems.solanakit.database.transaction.TransactionStorage
import io.horizontalsystems.solanakit.models.FullTransaction
import io.horizontalsystems.solanakit.models.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit
import org.sol4k.AccountMeta
import org.sol4k.AddressLookupTableAccount
import org.sol4k.Base58
import org.sol4k.PublicKey
import org.sol4k.TransactionMessage
import org.sol4k.VersionedTransaction
import org.sol4k.instruction.BaseInstruction
import java.math.BigInteger

/** Only the requested input may fund a Relay deposit; remote instructions never choose another signer. */
internal object SolanaRelayInstructions {
    const val NATIVE = "11111111111111111111111111111111"
    const val WSOL = "So11111111111111111111111111111111111111112"
    const val TOKEN = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
    const val TOKEN_2022 = "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb"
    const val ATA = "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL"
    const val COMPUTE = "ComputeBudget111111111111111111111111111111"
    const val MEMO = "MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr"
    const val RELAY = "99vQwtBwYtrqqD9YSXbdum3KBdxPAVxYTaQ3cfnJSrN2"
    const val FORWARDER = "DPArtTLbEqa6EuXHfL5UFLBZhFjiEXWRudhvXDrjwXUr"
    const val JUPITER = "JUP6LkbZbjS1jKKwapdHNy74zcZ3tLUZoi5QNyVTaV4"
    val depository = pda(RELAY, "relay_depository")
    val vault = pda(RELAY, "vault")
    val forwarder = pda(FORWARDER, "relay_forwarder")
    private val nativeDeposit = hex("0d9e0ddf5fd51c06")
    private val tokenDeposit = discriminator("deposit_token")
    private val tokenForward = discriminator("forward_token")

    data class Validation(val writable: List<String>, val createdAccounts: Set<String>, val inputAccount: String?, val tokenPrograms: Map<String, String>)
    data class Account(val owner: String, val lamports: Long, val data: ByteArray, val executable: Boolean = false)

    fun validate(plan: SendTransactionData.Solana.WithRelayInstructions): Validation {
        val owner = address(plan.senderAddress)
        address(plan.inputMint)
        require(plan.inputAmount.signum() > 0 && plan.inputAmount.bitLength() <= 64)
        require(plan.slippageBps in 0..5000 && plan.requestId.matches(Regex("^0x[0-9a-fA-F]{64}$")))
        require(plan.instructions.size in 1..40 && plan.lookupTables.size <= 8)
        plan.lookupTables.forEach(::address)
        val instructions = plan.instructions
        instructions.forEach { instruction ->
            address(instruction.programId)
            require(instruction.keys.size <= 128 && hex(instruction.data).size <= 2048)
            instruction.keys.forEach { key ->
                address(key.pubkey)
                require(!key.isSigner || key.pubkey == owner) { "Relay requested another signer" }
            }
        }
        val deposits = instructions.filter { it.programId == RELAY || it.programId == FORWARDER }
        require(deposits.size == 1) { "Expected one Relay deposit" }
        val deposit = deposits.single()
        val d = hex(deposit.data)
        val jupiter = instructions.filter { it.programId == JUPITER }
        require(jupiter.size <= 1)
        var bridgeMint = plan.inputMint
        var inputAccount: String? = null
        val programs = mutableMapOf<String, String>()
        if (deposit.programId == RELAY) {
            require(jupiter.isEmpty() && d.size == 48)
            require(deposit.keys.size >= 5)
            require(deposit.keys[0].pubkey == depository && deposit.keys[1].pubkey == owner && deposit.keys[1].isSigner &&
                deposit.keys[2].pubkey == owner && deposit.keys[3].pubkey == vault)
            require(u64(d, 8) == plan.inputAmount) { "Relay changed the deposit amount" }
            if (plan.inputMint == NATIVE) {
                require(d.copyOfRange(0, 8).contentEquals(nativeDeposit) && deposit.keys.size == 5 && deposit.keys[4].pubkey == NATIVE)
            } else {
                require(d.copyOfRange(0, 8).contentEquals(tokenDeposit) && deposit.keys.size == 10)
                val tokenProgram = deposit.keys[7].pubkey
                require(tokenProgram in setOf(TOKEN, TOKEN_2022) && deposit.keys[4].pubkey == plan.inputMint)
                inputAccount = associated(owner, plan.inputMint, tokenProgram)
                require(deposit.keys[5].pubkey == inputAccount && deposit.keys[6].pubkey == associated(vault, plan.inputMint, tokenProgram))
                require(deposit.keys[8].pubkey == ATA && deposit.keys[9].pubkey == NATIVE)
                programs[plan.inputMint] = tokenProgram
            }
        } else {
            require(jupiter.size == 1 && d.size == 40 && d.copyOfRange(0, 8).contentEquals(tokenForward))
            require(deposit.keys.size == 12 && deposit.keys[0].pubkey == owner && deposit.keys[0].isSigner && deposit.keys[1].pubkey == owner)
            require(deposit.keys[2].pubkey == forwarder && deposit.keys[3].pubkey == depository && deposit.keys[4].pubkey == vault)
            bridgeMint = deposit.keys[5].pubkey
            val bridgeProgram = deposit.keys[9].pubkey
            require(bridgeProgram in setOf(TOKEN, TOKEN_2022))
            require(deposit.keys[6].pubkey == associated(forwarder, bridgeMint, bridgeProgram) && deposit.keys[7].pubkey == associated(vault, bridgeMint, bridgeProgram))
            require(deposit.keys[8].pubkey == RELAY && deposit.keys[10].pubkey == ATA && deposit.keys[11].pubkey == NATIVE)
            programs[bridgeMint] = bridgeProgram
            val swap = jupiter.single()
            val shared = hex(swap.data).take(8).toByteArray().contentEquals(discriminator("shared_accounts_route"))
            require(shared || hex(swap.data).take(8).toByteArray().contentEquals(discriminator("route"))) { "Unsupported Jupiter instruction" }
            val authIndex = if (shared) 2 else 1
            val sourceIndex = if (shared) 3 else 2
            val destinationIndex = if (shared) 6 else 4
            val mintIndex = if (shared) 8 else 5
            val feeIndex = if (shared) 9 else 6
            require(swap.keys.size > feeIndex && swap.keys[authIndex].pubkey == owner && swap.keys[authIndex].isSigner)
            require(swap.keys[destinationIndex].pubkey == deposit.keys[6].pubkey && swap.keys[mintIndex].pubkey == bridgeMint)
            require(swap.keys[feeIndex].pubkey == JUPITER) { "Unexpected Jupiter platform fee recipient" }
            val tokenProgram = swap.keys[0].pubkey
            require(tokenProgram in setOf(TOKEN, TOKEN_2022))
            val sourceMint = if (plan.inputMint == NATIVE) WSOL else plan.inputMint
            inputAccount = associated(owner, sourceMint, tokenProgram)
            require(swap.keys[sourceIndex].pubkey == inputAccount)
            if (shared) require(swap.keys[7].pubkey == sourceMint)
            programs[sourceMint] = tokenProgram
            val swapData = hex(swap.data)
            require(swapData.size >= 27)
            // Current Jupiter AMM variants share this tail; account-delta simulation below is the spend authority.
            require(u64(swapData, swapData.size - 19) == plan.inputAmount)
            require(u64(swapData, swapData.size - 11).signum() > 0)
            require(u16(swapData, swapData.size - 3) <= plan.slippageBps && swapData.last() == 0.toByte())
        }
        val created = mutableSetOf<String>()
        var transferredSol = BigInteger.ZERO
        var limitCount = 0
        var priceCount = 0
        instructions.forEach { instruction ->
            val b = hex(instruction.data)
            fun key(i: Int) = instruction.keys[i].pubkey
            when (instruction.programId) {
                RELAY, FORWARDER, JUPITER -> Unit
                COMPUTE -> {
                    require(instruction.keys.isEmpty())
                    when (b.firstOrNull()?.toInt()) {
                        2 -> { require(b.size == 5 && u32(b, 1) in 1..1_400_000); limitCount++ }
                        3 -> { require(b.size == 9 && u64(b, 1) <= BigInteger.valueOf(100_000)); priceCount++ }
                        else -> error("Unsupported compute budget instruction")
                    }
                }
                MEMO -> require(instruction.keys.isEmpty() && b.toString(Charsets.UTF_8).equals(plan.requestId, true))
                ATA -> {
                    require(b.isEmpty() || b.contentEquals(byteArrayOf(0)) || b.contentEquals(byteArrayOf(1)))
                    require(instruction.keys.size == 6 && key(0) == owner && instruction.keys[0].isSigner && key(4) == NATIVE)
                    val mint = key(3)
                    val tokenProgram = key(5)
                    require(tokenProgram in setOf(TOKEN, TOKEN_2022) && key(2) in setOf(owner, forwarder))
                    require(mint in setOf(plan.inputMint, bridgeMint, if (plan.inputMint == NATIVE) WSOL else plan.inputMint))
                    require(key(1) == associated(key(2), mint, tokenProgram))
                    programs[mint] = tokenProgram
                    created += key(1)
                }
                NATIVE -> {
                    require(jupiter.isNotEmpty() && plan.inputMint == NATIVE && b.size == 12 && u32(b, 0) == 2L)
                    require(instruction.keys.size == 2 && key(0) == owner && instruction.keys[0].isSigner && key(1) == inputAccount)
                    transferredSol += u64(b, 4)
                }
                TOKEN, TOKEN_2022 -> {
                    require(b.isNotEmpty())
                    when (b[0].toInt() and 255) {
                        17 -> require(b.size == 1 && key(0) == inputAccount && plan.inputMint == NATIVE)
                        9 -> require(b.size == 1 && instruction.keys.size == 3 && key(0) == inputAccount && key(1) == owner && key(2) == owner && instruction.keys[2].isSigner)
                        else -> error("Unexpected token transfer, approval or authority change")
                    }
                }
                else -> error("Unsupported Relay transaction program")
            }
        }
        require(limitCount <= 1 && priceCount <= 1 && transferredSol <= plan.inputAmount)
        if (plan.inputMint == NATIVE && jupiter.isNotEmpty()) require(transferredSol == plan.inputAmount)
        return Validation(instructions.flatMap { it.keys }.filter { it.isWritable }.map { it.pubkey }.plus(owner).distinct(), created, inputAccount, programs)
    }

    fun compile(plan: SendTransactionData.Solana.WithRelayInstructions, blockhash: String, tables: List<AddressLookupTableAccount>): ByteArray {
        validate(plan)
        address(blockhash)
        require(tables.map { it.key.toBase58() }.toSet() == plan.lookupTables.toSet())
        val instructions = plan.instructions.toMutableList()
        fun compute(op: Int, number: Long, bytes: Int) = SolanaInstruction(COMPUTE, emptyList(), (byteArrayOf(op.toByte()) + little(number, bytes)).toHex())
        if (instructions.none { it.programId == COMPUTE && hex(it.data).firstOrNull() == 2.toByte() }) instructions.add(0, compute(2, 1_400_000, 4))
        if (instructions.none { it.programId == COMPUTE && hex(it.data).firstOrNull() == 3.toByte() }) instructions.add(0, compute(3, 100_000, 8))
        val compiled = instructions.map { instruction -> BaseInstruction(hex(instruction.data), instruction.keys.map {
            AccountMeta(PublicKey(it.pubkey), it.isSigner, it.isWritable)
        }, PublicKey(instruction.programId)) }
        val message = TransactionMessage.newMessage(PublicKey(plan.senderAddress), blockhash, compiled, tables)
        return VersionedTransaction(message).serialize().also { require(it.size <= 1232) { "Relay transaction exceeds Solana packet limit" } }
    }

    fun validateChanges(plan: SendTransactionData.Solana.WithRelayInstructions, validation: Validation,
                        before: Map<String, Account?>, after: Map<String, Account?>, networkFee: BigInteger): BigInteger {
        val ownerBefore = requireNotNull(before[plan.senderAddress])
        val ownerAfter = requireNotNull(after[plan.senderAddress])
        require(ownerBefore.owner == NATIVE && ownerAfter.owner == NATIVE && !ownerBefore.executable && !ownerAfter.executable)
        val rent = validation.createdAccounts.fold(BigInteger.ZERO) { acc, key ->
            val old = before[key]
            val new = after[key]
            if (old == null && new != null) {
                require(!new.executable && new.owner in setOf(TOKEN, TOKEN_2022) && new.data.size >= 165)
                acc + new.lamports.toBigInteger()
            } else acc
        }
        val nativeInput = if (plan.inputMint == NATIVE) plan.inputAmount else BigInteger.ZERO
        require(ownerBefore.lamports.toBigInteger() - ownerAfter.lamports.toBigInteger() <= nativeInput + networkFee + rent) { "Relay spent unexpected SOL" }
        val ownerBytes = Base58.decode(plan.senderAddress)
        val debits = mutableMapOf<String, BigInteger>()
        validation.writable.forEach { key ->
            val old = before[key] ?: return@forEach
            if (old.owner !in setOf(TOKEN, TOKEN_2022) || old.data.size < 165 || !old.data.copyOfRange(32, 64).contentEquals(ownerBytes)) return@forEach
            val new = after[key]
            val mint = Base58.encode(old.data.copyOfRange(0, 32))
            if (new == null) {
                require(key == validation.inputAccount && mint == WSOL && plan.instructions.any { it.programId in setOf(TOKEN, TOKEN_2022) && hex(it.data).firstOrNull() == 9.toByte() })
                debits[mint] = (debits[mint] ?: BigInteger.ZERO) + u64(old.data, 64)
            } else {
                require(new.owner == old.owner && new.data.size >= 165 && new.data.copyOfRange(0, 64).contentEquals(old.data.copyOfRange(0, 64)))
                require(new.data.size == old.data.size && new.data.copyOfRange(72, new.data.size).contentEquals(old.data.copyOfRange(72, old.data.size))) { "Relay changed token permissions" }
                val debit = u64(old.data, 64) - u64(new.data, 64)
                debits[mint] = (debits[mint] ?: BigInteger.ZERO) + debit
            }
        }
        debits.forEach { (mint, debit) ->
            val allowed = if (mint == plan.inputMint || (plan.inputMint == NATIVE && mint == WSOL)) plan.inputAmount else BigInteger.ZERO
            require(debit <= allowed) { "Relay spent another token or exceeded the source amount" }
        }
        if (plan.inputMint == NATIVE) {
            val combinedSpend = ownerBefore.lamports.toBigInteger() - ownerAfter.lamports.toBigInteger() + (debits[WSOL] ?: BigInteger.ZERO)
            require(combinedSpend <= plan.inputAmount + networkFee + rent) { "Relay exceeded the combined SOL/wrapped SOL budget" }
        }
        return networkFee + rent
    }

    fun associated(owner: String, mint: String, program: String = TOKEN): String = PublicKey.findProgramAddress(
        listOf(PublicKey(owner), PublicKey(program), PublicKey(mint)), PublicKey(ATA)).publicKey.toBase58()
    fun address(value: String): String {
        require(value.length in 32..44 && Base58.decode(value).size == 32) { "Invalid Solana address" }
        return value
    }
    fun hex(value: String): ByteArray {
        val raw = value.removePrefix("0x")
        require(raw.length % 2 == 0 && raw.matches(Regex("[0-9a-fA-F]*")))
        return raw.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
    private fun pda(program: String, seed: String) = PublicKey.findProgramAddress(listOf(PublicKey(seed.toByteArray())), PublicKey(program)).publicKey.toBase58()
    private fun discriminator(name: String) = java.security.MessageDigest.getInstance("SHA-256").digest("global:$name".toByteArray()).copyOf(8)
    private fun little(value: Long, size: Int) = ByteArray(size) { (value ushr (8 * it)).toByte() }
    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun u16(bytes: ByteArray, offset: Int) = unsigned(bytes, offset, 2).toInt()
    private fun u32(bytes: ByteArray, offset: Int) = unsigned(bytes, offset, 4).toLong()
    private fun u64(bytes: ByteArray, offset: Int) = unsigned(bytes, offset, 8)
    private fun unsigned(bytes: ByteArray, offset: Int, size: Int): BigInteger {
        require(offset >= 0 && offset + size <= bytes.size)
        return BigInteger(1, bytes.copyOfRange(offset, offset + size).reversedArray())
    }
}

/** Builds and broadcasts through the selected node, rather than SolanaKit's hardcoded raw-send endpoint. */
internal class SolanaRelayTransaction(
    private val plan: SendTransactionData.Solana.WithRelayInstructions,
    private val accountId: String,
    private val rpcUrl: String,
    private val signer: Signer,
) {
    data class Prepared(val raw: ByteArray, val blockhash: String, val lastValidBlockHeight: Long, val fee: BigInteger)
    private var prepared: Prepared? = null
    private var submitted: FullTransaction? = null
    val fee: java.math.BigDecimal get() = requireNotNull(prepared).fee.toBigDecimal().movePointLeft(9)

    suspend fun prepare() = withContext(Dispatchers.IO) {
        checkAccount()
        val validation = SolanaRelayInstructions.validate(plan)
        require(rpc("getGenesisHash").asString == "5eykt4UsFv8P8NJdTREpY1vzqKqZKvdpKuc147dw2N9d") { "Wrong Solana network" }
        validateMints(validation)
        val block = rpc("getLatestBlockhash", array(JsonObject().apply { addProperty("commitment", "confirmed") })).asJsonObject.getAsJsonObject("value")
        val blockhash = block.get("blockhash").asString
        val lastHeight = block.get("lastValidBlockHeight").asLong
        val tables = loadTables()
        val raw = SolanaRelayInstructions.compile(plan, blockhash, tables)
        val calculatedFee = simulate(raw, validation)
        prepared = Prepared(raw, blockhash, lastHeight, calculatedFee)
        checkAccount()
    }

    suspend fun send(): FullTransaction = withContext(Dispatchers.IO) {
        submitted?.let { return@withContext it }
        checkAccount()
        val candidate = requireNotNull(prepared)
        require(rpc("getBlockHeight", array(JsonObject().apply { addProperty("commitment", "confirmed") })).asLong <= candidate.lastValidBlockHeight) { "Solana quote expired; refresh the swap" }
        val validation = SolanaRelayInstructions.validate(plan)
        require(simulate(candidate.raw, validation) <= candidate.fee) { "Solana transaction cost changed; refresh the swap" }
        checkAccount()
        val signed = signer.signTransaction(candidate.raw)
        val hash = Base58.encode(signed.signature)
        val encoded = Base64.getEncoder().encodeToString(signed.serializedTransaction)
        val record = FullTransaction(Transaction(
            hash = hash, timestamp = System.currentTimeMillis() / 1000,
            fee = candidate.fee.toBigDecimal().movePointLeft(9), from = plan.senderAddress, to = null, amount = null,
            pending = true, blockHash = candidate.blockhash, lastValidBlockHeight = candidate.lastValidBlockHeight,
            base64Encoded = encoded, programIds = SolanaRelayInstructions.JUPITER.takeIf { id -> plan.instructions.any { it.programId == id } },
            createdTokenAccount = plan.instructions.any { it.programId == SolanaRelayInstructions.ATA },
        ), emptyList())
        // The SDK's public storage API preserves the existing pending/retry flow without private-field access.
        save(record)
        submitted = record
        try {
            val returned = rpc("sendTransaction", array(encoded, JsonObject().apply {
                addProperty("encoding", "base64"); addProperty("skipPreflight", false)
                addProperty("preflightCommitment", "confirmed"); addProperty("maxRetries", 3)
            })).asString
            require(returned == hash) { "Solana node returned another transaction" }
        } catch (e: RpcRejected) {
            if (!e.alreadyProcessed) {
                save(record.copy(transaction = record.transaction.copy(pending = false, error = "Relay transaction rejected")))
                submitted = null
                throw e
            }
        } catch (_: IOException) {
            // Submission may have succeeded. Keep its exact signature/payload so retries cannot pay twice.
        }
        record
    }

    private fun checkAccount() {
        require(App.accountManager.activeAccount?.id == accountId) { "Wallet changed; reopen the swap" }
        val plugin = io.horizontalsystems.walletkit.core.chain.ChainRegistry[io.horizontalsystems.marketkit.models.BlockchainType.Solana] as io.horizontalsystems.walletkit.chain.solana.SolanaChainPlugin
        require(plugin.rpcSourceManager.rpcSource.url.toString() == rpcUrl) { "Network provider changed; refresh the swap" }
        if (App.torKitManager.isTorEnabled) check(App.torKitManager.torStatusFlow.value == TorStatus.Connected) { "Wait for Tor to connect" }
    }

    private fun save(transaction: FullTransaction) {
        val db = TransactionDatabase.getInstance(App.instance, "Solana-$accountId-txs")
        try { TransactionStorage(db, plan.senderAddress).addTransactions(listOf(transaction)) } finally { db.close() }
    }

    private suspend fun loadTables(): List<AddressLookupTableAccount> {
        if (plan.lookupTables.isEmpty()) return emptyList()
        val accounts = accounts(plan.lookupTables).second
        return plan.lookupTables.map { address ->
            val account = requireNotNull(accounts[address]) { "Missing address lookup table" }
            require(account.owner == "AddressLookupTab1e1111111111111111111111111" && !account.executable)
            val bytes = account.data
            require(bytes.size in 56..8248 && (bytes.size - 56) % 32 == 0 && bytes.take(4) == listOf(1.toByte(), 0, 0, 0).map { it.toByte() })
            require(bytes.copyOfRange(4, 12).all { it == 0xff.toByte() }) { "Address lookup table is deactivated" }
            AddressLookupTableAccount(PublicKey(address), (56 until bytes.size step 32).map { PublicKey(bytes.copyOfRange(it, it + 32)) })
        }
    }

    private suspend fun validateMints(validation: SolanaRelayInstructions.Validation) {
        val mints = validation.tokenPrograms.keys.filter { it != SolanaRelayInstructions.NATIVE }
        if (mints.isEmpty()) return
        val states = accounts(mints).second
        mints.forEach { mint ->
            val state = requireNotNull(states[mint]) { "Missing swap mint" }
            require(state.owner == validation.tokenPrograms[mint] && !state.executable && state.data.size >= 82)
            if (state.owner == SolanaRelayInstructions.TOKEN_2022 && state.data.size > 165) {
                var cursor = 166
                while (cursor + 4 <= state.data.size) {
                    val type = (state.data[cursor].toInt() and 255) or ((state.data[cursor + 1].toInt() and 255) shl 8)
                    val size = (state.data[cursor + 2].toInt() and 255) or ((state.data[cursor + 3].toInt() and 255) shl 8)
                    cursor += 4
                    require(cursor + size <= state.data.size)
                    // Transfer hooks execute arbitrary external code with the swap's account privileges.
                    if (type == 14) require(size == 64 && state.data.copyOfRange(cursor + 32, cursor + 64).all { it == 0.toByte() }) { "Unsupported token transfer hook" }
                    cursor += size
                }
            }
        }
    }

    private suspend fun simulate(raw: ByteArray, validation: SolanaRelayInstructions.Validation): BigInteger {
        require(validation.writable.size <= 100)
        val (slot, before) = accounts(validation.writable)
        val feeResult = rpc("getFeeForMessage", array(Base64.getEncoder().encodeToString(
            VersionedTransaction.from(Base64.getEncoder().encodeToString(raw)).message.serialize()), JsonObject().apply { addProperty("commitment", "confirmed") }))
            .asJsonObject.get("value")
        require(!feeResult.isJsonNull) { "Solana quote expired; refresh the swap" }
        val networkFee = feeResult.asBigInteger
        require(networkFee.signum() > 0 && networkFee <= BigInteger.valueOf(10_000_000)) { "Unexpected Solana transaction fee" }
        val response = rpc("simulateTransaction", array(Base64.getEncoder().encodeToString(raw), JsonObject().apply {
            addProperty("encoding", "base64"); addProperty("sigVerify", false)
            addProperty("replaceRecentBlockhash", false); addProperty("commitment", "confirmed"); addProperty("minContextSlot", slot)
            add("accounts", JsonObject().apply { addProperty("encoding", "base64"); add("addresses", array(*validation.writable.toTypedArray())) })
        })).asJsonObject.getAsJsonObject("value")
        require(response.get("err").isJsonNull) { "Solana swap simulation failed" }
        val values = response.getAsJsonArray("accounts")
        require(values.size() == validation.writable.size)
        val after = validation.writable.mapIndexed { index, key -> key to parseAccount(values[index]) }.toMap()
        return SolanaRelayInstructions.validateChanges(plan, validation, before, after, networkFee)
    }

    private suspend fun accounts(addresses: List<String>): Pair<Long, Map<String, SolanaRelayInstructions.Account?>> {
        require(addresses.size <= 100)
        val result = rpc("getMultipleAccounts", array(array(*addresses.toTypedArray()), JsonObject().apply {
            addProperty("encoding", "base64"); addProperty("commitment", "confirmed")
        })).asJsonObject
        val values = result.getAsJsonArray("value")
        require(values.size() == addresses.size)
        return result.getAsJsonObject("context").get("slot").asLong to addresses.mapIndexed { index, key -> key to parseAccount(values[index]) }.toMap()
    }

    private fun parseAccount(value: JsonElement): SolanaRelayInstructions.Account? {
        if (value.isJsonNull) return null
        val obj = value.asJsonObject
        val data = obj.getAsJsonArray("data")
        require(data.size() == 2 && data[1].asString == "base64" && data[0].asString.length <= 32768)
        return SolanaRelayInstructions.Account(SolanaRelayInstructions.address(obj.get("owner").asString), obj.get("lamports").asLong,
            Base64.getDecoder().decode(data[0].asString), obj.get("executable").asBoolean).also { require(it.lamports >= 0) }
    }

    private suspend fun rpc(method: String, params: JsonArray = JsonArray()): JsonElement {
        checkAccount()
        val tor = App.torKitManager.isTorEnabled
        val client = if (tor) baseClient.newBuilder().proxy(App.torKitManager.requireSocksProxy()).build() else baseClient
        val request = Request.Builder().url(rpcUrl).post(JsonObject().apply {
            addProperty("jsonrpc", "2.0"); addProperty("id", 1); addProperty("method", method); add("params", params)
        }.toString().toRequestBody("application/json".toMediaType())).build()
        val result = try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Solana network unavailable")
                val parsed = com.google.gson.JsonParser.parseString(requireNotNull(response.body).string()).asJsonObject
                if (parsed.has("error")) {
                    val message = parsed.getAsJsonObject("error").get("message")?.asString.orEmpty()
                    throw RpcRejected(message.contains("already been processed", true))
                }
                requireNotNull(parsed.get("result"))
            }
        } catch (e: RpcRejected) {
            throw e
        } catch (_: IOException) {
            // Custom RPC URLs may contain credentials; do not propagate transport error details.
            throw IOException("Solana network unavailable")
        }
        checkAccount()
        require(tor == App.torKitManager.isTorEnabled) { "Privacy settings changed; refresh the swap" }
        return result
    }

    private class RpcRejected(val alreadyProcessed: Boolean) : IOException("Solana transaction request rejected")
    companion object {
        private val baseClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS)
            .callTimeout(35, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
        private fun array(vararg values: Any): JsonArray = JsonArray().apply { values.forEach { value ->
            when (value) { is JsonElement -> add(value); is Number -> add(value); is Boolean -> add(value); else -> add(value.toString()) }
        } }
    }
}

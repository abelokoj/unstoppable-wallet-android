
# PancakeSwap Relay instructions use Gson field names when preparing Solana transactions.
-keep class io.horizontalsystems.walletkit.modules.multiswap.sendtransaction.SolanaInstruction { *; }
-keep class io.horizontalsystems.walletkit.modules.multiswap.sendtransaction.SolanaAccountMeta { *; }

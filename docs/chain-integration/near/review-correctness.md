# NEAR correctness review

Recording time: 2026-10-28T16:15:11-04:00 (America/New_York). Role: Correctness. This reviewer did not implement the NEAR integration. Reviewed the local, uncommitted upstream 0.53 integration and the official `near-android-kit` source pinned at `856d2e3` in `gradle/libs.versions.toml:76`.

What this change does: NEAR adds native and NEP-141 balances, transfers, account discovery, named-account restore settings, watch accounts and transaction history. The same kit is shared by the enabled wallets of an account; swaps can enable the receiving wallet after broadcast.

This is an independent role report, not the final integration decision log. The integration owner must assign each finding a fixed, tech-debt or rejected disposition in `review.md` before merge. No application source was changed by this review.

## Findings

### NC-1 · P2 · Reject invalid NEAR backup account identifiers before saving the account

Checklist: **ADDR-1, ADDR-3, ACC-2**. Evidence: `walletkit/src/main/java/io/horizontalsystems/walletkit/modules/backuplocal/BackupLocalModule.kt:123`, `walletkit/src/main/java/io/horizontalsystems/walletkit/modules/backuplocal/fullbackup/BackupProvider.kt:165`, and `walletkit/src/main/java/io/horizontalsystems/walletkit/modules/backuplocal/fullbackup/BackupProvider.kt:187`.

Reachable case: restore a decryptable `near_address` backup whose address is `Alice.near`, or a mnemonic backup whose enabled NEAR wallet has `near_account_id` set to `not an account`. `getAccountTypeFromData` accepts the watch string without validation; the generic settings restore accepts the named-account string. Both accounts/settings are persisted. Neither `AccountFactory.account` nor `AccountType.NearAddress` validates these inputs.

`NearKitManager.nearWallet` later forwards the saved string to the SDK (`walletkit-chain-near/src/main/java/io/horizontalsystems/walletkit/core/managers/NearKitManager.kt:171`). SDK account-id validation then throws; `walletkit/src/main/java/io/horizontalsystems/walletkit/core/factories/AdapterFactory.kt:30` logs and returns null. Restore therefore succeeds into a wallet with no working adapter instead of rejecting or skipping the invalid account with a usable explanation. The SDK requires a lowercase, well-formed NEAR account id; rejection only during adapter construction is too late.

Smallest fix: validate NEAR watch addresses and NEAR restore-setting values at the backup boundary before saving them. Keep malformed-account handling isolated so other accounts in the full backup can still restore. Do not silently lowercase and accept a malformed chain id without an explicit normalization rule.

### NC-2 · P2 · Keep the issuing contract on unknown-token history values

Checklist: **HIST-6, HIST-7**. Evidence: `walletkit-chain-near/src/main/java/io/horizontalsystems/walletkit/core/factories/NearTransactionConverter.kt:184` and `walletkit/src/main/java/io/horizontalsystems/walletkit/entities/TransactionValue.kt:109`.

Reachable case: a successful transfer from an unlisted contract such as `example-token.near` returns metadata with symbol `USDT` and a normal token name. `ftValue` uses the contract id for the lookup, then drops it when constructing `TransactionValue.TokenValue`. With metadata present, neither the stored token name nor token code contains the issuer; `TokenValue` itself has no contract field. Two unlisted contracts with the same metadata produce indistinguishable asset identities in the app's history value.

For a plain receive, `walletkit/src/main/java/io/horizontalsystems/walletkit/modules/transactionInfo/TransactionInfoViewItemFactory.kt:228` renders the amount and sender, without an issuing-contract row. Contract-call history only shows the top-level call target, which also need not be the token issuer. Unknown-token spam scoring helps hide unsolicited rows but does not preserve identity, and the row remains reachable through the spam view or the trusted-sender exemption in `SpamManager.kt:134`.

Smallest fix: retain the NEP-141 contract id with the unknown asset and expose it in transaction details. Continue resolving known tokens by the full `TokenQuery`; do not resolve unknown tokens by ticker.

### NC-3 · P2 · Do not display raw units as token quantities after metadata failure

Checklist: **AMT-4, HIST-3, NET-4**. Evidence: `walletkit-chain-near/src/main/java/io/horizontalsystems/walletkit/core/factories/NearTransactionConverter.kt:179` and `walletkit-chain-near/src/main/java/io/horizontalsystems/walletkit/core/factories/NearTransactionConverter.kt:182`.

Reachable case: an unlisted six-decimal token has a receipt amount of `1000000` base units, meaning one token. When `ftMetadata(contractId)` fails, for example while its RPC endpoint is unavailable, the converter catches the error, substitutes decimals `0`, and emits an ordinary `TokenValue` containing `1000000`. The history renderer formats `decimalValue` as a coin quantity (`walletkit/src/main/java/io/horizontalsystems/walletkit/modules/transactions/TransactionViewItemFactory.kt:1676`), so the app shows one million units as if they were one million whole tokens. There is no raw-unit/unknown-precision marker. This also affects legitimate transfers shown through the spam view or a trusted sender.

Smallest fix: when precision is unavailable, retain an explicitly unscaled/unknown amount and do not render it as a token quantity. A cached verified precision can be used; `0` cannot stand for an unknown precision. Keep a failed metadata fetch distinct from genuine zero-decimal metadata.

### NC-4 · P2 · Include internal native-transfer counterparties in contact history filters

Checklist: **HIST-1, HIST-3**. Evidence: `walletkit-chain-near/src/main/java/io/horizontalsystems/walletkit/core/adapters/NearTransactionsAdapter.kt:138`; compare the actual counterparty selection in `walletkit-chain-near/src/main/java/io/horizontalsystems/walletkit/core/factories/NearTransactionConverter.kt:132`.

Reachable case: a transaction has signer `alice.near`, top-level receiver `router.near`, and a successful internal receipt transferring one NEAR from `vault.near` to the watched account. The converter correctly produces a native receipt from `vault.near`. If the user filters history by a contact whose address is `vault.near`, `addressMatches` rejects it: it checks only the top-level signer/receiver and `ftTransfers`, never `nearTransfers`. Both initial paging and incremental updates use this same filter, so the transaction disappears from the contact view although the row identifies that contact as the sender.

Smallest fix: also match successful native receipt counterparties in the address filter, using the same account-side/direction rules as the converter. Include a receipt-based native payout in the focused history vectors.

### NC-5 · P2 · Bind automatic token enabling to the kit's account, not a separately read active account

Checklist: **ACC-3**; also the approved integration requirement to preserve receiving-wallet account isolation. Evidence: `walletkit-chain-near/src/main/java/io/horizontalsystems/walletkit/core/managers/NearAccountManager.kt:51`, `walletkit-chain-near/src/main/java/io/horizontalsystems/walletkit/core/managers/NearAccountManager.kt:88`, and `walletkit-chain-near/src/main/java/io/horizontalsystems/walletkit/core/managers/NearAccountManager.kt:96`.

Reachable schedule: account A's NEAR kit is created and its started notification is queued. The user switches to B before the independent subscription thread handles that notification, while adapter rebuilding has not yet replaced A's kit. `subscribe()` reads A's `kitWrapper`, then independently reads B from `accountManager.activeAccount`. A's held-token snapshot is subsequently passed to `handle` with account B, and `EnabledWallet(accountId = B.id)` is saved for A's discovered tokens. There is also a narrower switch between the two reads. `NearKitManager.getKitWrapper` publishes its wrapper before setting `currentAccount` (`NearKitManager.kt:73`); the subscription does not acquire that manager's monitor or verify a matching wrapper/account pair. The asynchronous active-wallet/adapter consumers make this an ordinary account-switch schedule, not a requirement for two user actions in the same UI frame.

Even a correctly captured old account uses the current account's `activeWallets` to decide which token types already exist. A late A emission while B is active can therefore suppress A's legitimate token enable because B already enabled it. The current app config inherits `autoEnableTokensOnReceive = true`, so this path is active.

Smallest fix: publish/capture the kit and owning account together, and discard work if that binding is no longer current before persistence. Query existing wallets for that same owner, or restrict enabling to the owner while it is active. Do not merely replace the account read with an unsynchronized nullable `currentAccount` read, since wrapper/account publication is currently separate.

## Source checks and boundaries

- **AMT-1 / TX-7:** app amounts use `BigDecimal`/`BigInteger`; NEAR scaling uses 24 decimals and token scaling calls `toBigIntegerExact`. Send-service precision checks reject excess decimals. The SDK serializes the chain's integer fields; no transaction bytes were built or signed in this review.
- **AMT-2 / AMT-3 / HIST-5:** this integration exposes native/NEP-141 transfers, not a token allowance/approve flow. No unlimited-allowance value needs conversion in these app paths. This is a scope determination, not a test result for arbitrary contract calls.
- **ADDR-1 / ADDR-4:** the ordinary send path validates account-id syntax, rejects self/token-contract destinations, and checks named-account existence. The SDK repeats receiver validation when sending. Backup validation is the separate gap NC-1. Native NEAR has no memo; NEP-141 memos are the public optional transfer memo.
- **TX-1 / TX-3:** native estimates add the transfer amount to the fee's required balance. NEP-141 estimates add receiver storage registration and preserve the kit's confirmed storage estimate for `sendFt`; a changed storage quote is refused before sending. The SDK's fee `requiredBalance` covers gas purchase rather than only the displayed expected fee. The app omits the attached one yoctoNEAR from its explicit NEP-141 `requiredNear` sum; a concrete underfunding result was not demonstrated because the kit also reserves a substantial send-gas margin. Exact boundary behavior therefore remains unverified, not passed.
- **ACC-1:** `AccountType.NearAddress` is a watch account, maps to `NearWallet.WatchOnly`, and the pinned kit's `requireSigner` rejects it. The app's send/swap account checks and SDK signer refusal were inspected; no device attempt was made.
- **ACC-2 / ACC-3:** named-account settings have a serialized `near_account_id` key and are written/read through per-account restore settings. Account cleanup calls the NEAR kit clear API. Full backup round-trip, alternate iOS type naming, deletion/recreation and account-switch execution were not run.
- **ACC-4:** receiving-wallet enabling uses the explicitly supplied account in `SwapWalletEnabler`; the independent discovery subscription has the account-binding defect NC-5. Private Send/CrossPay changes implemented earlier by this reviewer are outside this independent NEAR role review and were not treated as independently validated here.

## External evidence inspected

The pinned official SDK was read directly, without installing a different release:

- [Account-id syntax and classification](https://github.com/horizontalsystems/near-android-kit/blob/856d2e3/nearkit/src/main/java/io/horizontalsystems/nearkit/crypto/AccountId.kt#L15).
- [Native and NEP-141 estimation](https://github.com/horizontalsystems/near-android-kit/blob/856d2e3/nearkit/src/main/java/io/horizontalsystems/nearkit/NearKit.kt#L174), [committed storage-deposit check](https://github.com/horizontalsystems/near-android-kit/blob/856d2e3/nearkit/src/main/java/io/horizontalsystems/nearkit/NearKit.kt#L235), and [watch-only signer refusal](https://github.com/horizontalsystems/near-android-kit/blob/856d2e3/nearkit/src/main/java/io/horizontalsystems/nearkit/NearKit.kt#L318).
- [Fee calculation and required gas balance](https://github.com/horizontalsystems/near-android-kit/blob/856d2e3/nearkit/src/main/java/io/horizontalsystems/nearkit/transaction/FeeCalculator.kt#L146), [the one-yocto transfer attachment](https://github.com/horizontalsystems/near-android-kit/blob/856d2e3/nearkit/src/main/java/io/horizontalsystems/nearkit/transaction/FtActions.kt#L67), and [integer amount scaling](https://github.com/horizontalsystems/near-android-kit/blob/856d2e3/nearkit/src/main/java/io/horizontalsystems/nearkit/models/NearAmount.kt#L10).
- [Receipt movements and native net changes](https://github.com/horizontalsystems/near-android-kit/blob/856d2e3/nearkit/src/main/java/io/horizontalsystems/nearkit/models/Transaction.kt#L40), [receipt/event conversion and tags](https://github.com/horizontalsystems/near-android-kit/blob/856d2e3/nearkit/src/main/java/io/horizontalsystems/nearkit/sync/TransactionConverter.kt#L51), and [pending-before-submit ordering](https://github.com/horizontalsystems/near-android-kit/blob/856d2e3/nearkit/src/main/java/io/horizontalsystems/nearkit/transaction/TransactionSender.kt#L69).

## Limitations and verdict

No Gradle build, unit tests, emulator/device UI, live watch-address history vectors, funded transaction, balance-boundary send, or iOS parity check was run. The derivation of the first 20 addresses (**PAR-1**), external backup/vector comparison (**PAR-2**) and cross-platform finding closure (**PAR-3**) remain unverified. The chain design/vector documents were not yet present at the start of this role review; their live results and rationale were not assumed. Security, resilience and adversarial-role reviews remain separate.

Verdict: resolve or explicitly disposition **NC-1 through NC-5** before merge. The evidence is a source trace with concrete inputs/schedules, not a claim of executed device reproductions.

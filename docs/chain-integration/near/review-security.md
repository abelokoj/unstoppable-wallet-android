# NEAR security review

Recording time: 2026-10-28T16:12:27-04:00 (America/New_York).

Role: Security only. Reviewed the local, uncommitted 0.53.0 integration, `walletkit-chain-near`, NEAR account/restore/history wiring, and the exact pinned public SDK commit `856d2e3148cad1b031cba9247ddcbda3f26382b8`. Application sources were not changed by this review. The original pending findings below are preserved as the review-time record. Final fixed dispositions and test results are in `review.md` and `test-vectors.md`.

SDK references below are relative to `nearkit/src/main/java/io/horizontalsystems/nearkit/` at that commit, not the wallet repository. The source was retrieved from the upstream GitHub repository into a temporary review directory; no SDK artifact was built or substituted.

## Findings

### SEC-1 [P1] A fake token can impersonate a trusted contact and bypass the spam filter

Checklist: **HIST-6, HIST-7**. Status: **pending**.

Evidence:

- `walletkit-chain-near/src/main/java/io/horizontalsystems/walletkit/core/factories/NearTransactionConverter.kt:96`, `:111`, `:145`, and `:184`: an FT log's claimed sender becomes the receive counterparty; unknown contract metadata supplies its displayed symbol/name, while the contract ID is discarded from `TokenValue`.
- `walletkit/src/main/java/io/horizontalsystems/walletkit/core/managers/SpamManager.kt:133`: any event claiming a contact address returns non-spam before the unknown-token rule is evaluated.
- `walletkit/src/main/java/io/horizontalsystems/walletkit/modules/transactionInfo/TransactionInfoViewItemFactory.kt:228`: ordinary NEAR receive details show the amount and sender, with no issuer field. `TokenValue` has no contract identifier at `walletkit/src/main/java/io/horizontalsystems/walletkit/entities/TransactionValue.kt:102`.
- Pinned SDK `sync/TransactionConverter.kt:277`: a successful contract's `ft_transfer` event accepts `old_owner_id` and `new_owner_id` from the log. `contractId` is correctly bound to the outcome executor, but that identity is lost in the app's unknown-token record.

Reachable input: an attacker deploys a token contract whose metadata says `USDC`, then successfully calls it to emit an event claiming `old_owner_id = trusted-contact.near`, `new_owner_id = victim.near`, and a positive amount. No access to the contact's key or genuine USDC contract is required. The indexer discovers account history from such token logs. Once synced, the wallet can show a non-spam USDC receive attributed to the trusted contact, with no token issuer available in its ordinary transfer details. These names are synthetic examples.

Required behavior: a contact label cannot authenticate a token contract's arbitrary sender claim. Unknown issuer transfers must retain their unknown-token protection even when the log names a contact, and their contract identity must remain inspectable. This does not imply counterfeit tokens merge into the real USDC wallet balance; the demonstrated failure is a trusted-looking history entry.

Smallest fix: prevent the contact exemption from overriding unknown-token classification, and preserve/show the NEP-141 contract identity using the existing details components. Do not silently add a new screen or redesign the approved interface.

### SEC-2 [P2] Incoming spam can evict the genuine recipient used for lookalike detection

Checklist: **HIST-6**. Status: **pending**.

Evidence: `walletkit-chain-near/src/main/java/io/horizontalsystems/walletkit/core/adapters/NearTransactionsAdapter.kt:98` applies `limit` to all transactions before selecting counterparties; `:107` and `:111` explicitly accept incoming counterparties, and `:106` accepts an unsigned contract's claimed outgoing FT log. `walletkit/src/main/java/io/horizontalsystems/walletkit/core/managers/SpamManager.kt:170` uses this as outgoing history, and `:190` caches a sub-threshold verdict whenever that returned list is nonempty.

Reachable input: after the victim's genuine outgoing payment, an attacker sends 20 tiny incoming NEAR transfers from an unrelated address. Those rows remain in SDK history even when hidden as spam. The attacker then sends `0.02 NEAR` from a new lookalike of the genuine recipient, with different leading/trailing characters from the flooding address. The genuine outgoing recipient has fallen outside the 20-row query. With the current NEAR risk limit of `0.01` (`app/src/main/java/io/horizontalsystems/bankwallet/core/providers/AppConfigProvider.kt:159`), the final transfer scores 2 for low value plus at most 4 for timing: 6, below the spam threshold of 7. Comparing it with the genuine recipient would add the missing lookalike points. Its clean verdict can then persist in the spam cache.

Required behavior: attacker-controlled incoming rows and forged token logs must not replace the user's genuine outgoing-recipient baseline. Page through bounded history until genuine outgoing candidates are collected; authenticate/filter candidates before applying the context limit. If legitimate indirect outgoing transfers are included, verify their origin and token identity rather than accepting arbitrary `from` fields.

### SEC-3 [P2] Failed contract reads publish unverified indexer balances as fully synced

Checklist: **NET-4, AMT-4**. Status: **pending**. The cause is in the pinned SDK.

Evidence:

- Pinned SDK `sync/Syncer.kt:173` initializes token balances from FastNEAR indexer amounts. At `:181-193`, a failed or malformed `ft_balance_of` response for a watched token leaves that indexer amount in place. `:196-199` persists/emits the resulting balance; `:144` reports `Synced` regardless of that read failure.
- `walletkit-chain-near/src/main/java/io/horizontalsystems/walletkit/core/adapters/NearTokenAdapter.kt:48` exposes this number, `:54` adopts the kit's global synced state, and `:72` returns it as the maximum spendable balance.

Reachable input: an enabled token has a stale or falsified positive indexer balance while all RPC attempts for that token's balance fail, or return valid JSON of the wrong shape. Native-account reads can still succeed. The wallet publishes the indexer's number as spendable with no token sync error, even if the authoritative contract balance is zero. An ordinary indexer delay can cause the stale version; exploitation of the falsified version requires control of the indexer response and failure of the independent read. The chain still enforces the real balance when a send executes; no signature or chain-balance bypass was found.

Required behavior: authoritative token-balance read failure must remain distinguishable from a successful read. An enabled token must not silently downgrade from contract state to unverified indexer state. Propagate the watched-token read error or retain only a previous verified value with a visible stale/error state; reject malformed balance responses. This requires an SDK correction or an explicitly reviewed local mitigation, not merely a different API key.

## Security checks without an additional finding

- **ACC-1:** `NearKitManager.kt:205` maps NEAR watch accounts to `NearWallet.WatchOnly`. SDK signing requires a signer (`NearKit.kt:318`) and fresh full-access-key membership (`transaction/TransactionSender.kt:39-42`). Watch-only signing and a function-call-only key do not gain transfer authority through these paths.
- **ADDR-1, ADDR-4, ACC-4:** app address validation and SDK send checks validate account IDs, block a token's own contract as recipient, and require named receivers to exist. NEP-141 storage deposits are capped and compared with the confirmed estimate. Amount conversion uses exact integer conversion. No application-supplied arbitrary actions or receiver substitution was found in the reviewed NEAR send bridge.
- **ACC-2, ACC-3:** local backup/storage adds the NEAR watch-address type; seed-backed selected account IDs are account-scoped restore settings. SDK database names include network, app account ID, and NEAR account ID; clear-account wiring uses the app account ID. No private-key disclosure was identified in these paths. Concurrency/deletion behavior was inspected statically only.
- **NET-5 / privacy:** the selected RPC name resolves to a built-in allowlist, and all pinned mainnet URLs use HTTPS. A stored unrecognized name falls back to the default rather than becoming an arbitrary URL. The SDK's `network/ApiClient.kt:19` constructs ordinary OkHttp clients without `Proxy.NO_PROXY`, an alternate proxy selector, or custom DNS; the app sets SOCKS JVM properties at `walletkit/src/main/java/io/horizontalsystems/walletkit/core/managers/TorManager.kt:363` and a blocked port during failure. Static inspection found no NEAR-specific Tor bypass. This is not packet-capture evidence that every Android runtime path is leak-free.
- Account discovery sends the derived public key to FastNEAR; sync sends account IDs, token contract IDs, history requests and transaction hashes to the configured services. Broadcast sends signed transaction bytes to RPC endpoints. The default fallback list includes the community operator `rpc.shitzuapes.xyz` in pinned `network/Network.kt:27`. These are real disclosures even without an API key; no seed/private key is passed to the HTTP interfaces. BASIC SDK request logging also contains account/public-key URL paths; log export/access was not exercised.
- `FASTNEAR_API_KEY` is intentionally blank. No credential was borrowed or added. The SDK key interceptor is not installed for a blank key, so no API-key leak applies to this configuration.

## Limits and disposition

No builds, automated tests, live account vectors, funded transactions, device execution, Tor packet capture, or iOS parity checks were run. Findings are source-traced cases, not claims of a reproduced mainnet exploit. The pinned SDK source was available and inspected; the actual downloaded dependency's byte-for-byte equivalence and runtime proxy behavior were not verified.

Security recommendation: resolve or explicitly disposition **SEC-1 through SEC-3** before publication. The primary arbitrary-attacker blocker is **SEC-1**. No application behavior or UI change was made by this review; any additional functionality remains subject to the user's decision.

# NEAR adversary review

Recorded: 2026-10-28T16:15:06-04:00.

Scope: account/public-key discovery and history/token identity in the imported NEAR module and pinned `near-android-kit` revision `856d2e3` (`gradle/libs.versions.toml:76`). SDK file references below are relative to its `nearkit/src/main/java/io/horizontalsystems/nearkit/` directory; the exact pinned source was inspected in a temporary checkout.

This reviewer did not implement NEAR. Signing, pending/retry handling, fees and Tor are outside this role; the other reviewers own those areas. No application code was changed during the original review; the later ADV-2 remediation is recorded below. No delta matrix existed under `docs/chain-integration/near/` at the start of this review, so the assumptions below come from the actual implementation and its comments.

## ADV-1: A hostile token can impersonate a trusted contact in normal history

Priority: P2. Checklist: **HIST-6, HIST-7**. Disposition: reported for the integration owner to decide; not fixed by this document.

Reachable input: an attacker calls its own contract, `fake-token.near`, which returns metadata `name = "Tether USD"`, `symbol = "USDT"`, `decimals = 6` and successfully emits:

```text
EVENT_JSON:{"standard":"nep141","version":"1.0.0","event":"ft_transfer","data":[{"old_owner_id":"friend.near","new_owner_id":"victim.near","amount":"100000000"}]}
```

`friend.near` is a saved NEAR contact of the victim. No signature from that contact, no real USDT and no actual balance transfer in the hostile contract are needed to emit that log. The SDK's FastNEAR history interface explicitly includes transactions naming the account in event logs (`network/FastNearProvider.kt:71`); once indexed, the ordinary history-sync path supplies this transaction to the app.

Evidence and result:

- SDK `sync/TransactionConverter.kt:262-284` correctly binds token identity to the executing contract, but accepts `old_owner_id` and `new_owner_id` from that contract's event. The contract therefore controls the displayed transfer counterparty, not merely its ticker.
- App `walletkit-chain-near/src/main/java/io/horizontalsystems/walletkit/core/factories/NearTransactionConverter.kt:118-127,139-150` turns the event into a positive net movement with `friend.near` as counterparty. Lines 100-111 classify a single such movement as `Type.Receive`.
- The same converter's lines 182-190 create a generic `TransactionValue.TokenValue` from the attacker's metadata without retaining `fake-token.near`. `walletkit/src/main/java/io/horizontalsystems/walletkit/entities/TransactionValue.kt:102` defines that value without a contract/issuer field.
- `walletkit/src/main/java/io/horizontalsystems/walletkit/core/managers/SpamManager.kt:133-135` returns `false` for spam as soon as any event address is a saved contact. This happens before unknown-token auto-spam scoring (`PoisoningScorer.kt:275-280`). The attacker-controlled event sender therefore defeats the protection that would otherwise hide this unknown token.
- `walletkit/src/main/java/io/horizontalsystems/walletkit/modules/transactionInfo/TransactionInfoViewItemFactory.kt:228-238` displays only the received value, sender and optional memo for this type. The emitting token contract is absent even from its transaction details. The user sees an ordinary `+100 USDT` receipt attributed to a trusted contact, with no token issuer that distinguishes it from real USDT.

The problem is presentation and counterparty trust, not crediting the canonical USDT wallet balance. Token lookup and wallet discovery still use the exact `Nep141(contractId)` identity; a hostile contract cannot populate that canonical token's balance solely by reusing its ticker.

Smallest complete remedy: never apply the contacts exemption to a counterparty asserted only by an unknown token contract. Keep the emitting token contract on NEAR token movements and display it on send/receive details, including unknown-token and suspicious-history views. A regression vector should use a real saved contact name in a hostile contract's event and assert both suspicious classification and visible contract identity.

Validation: static source-to-display trace with the concrete input above. This fixture was not executed through an Android UI or posted to a live chain; its indexing by FastNEAR was not exercised.

## ADV-2: An untrusted account-lookup list amplifies into unbounded RPC work

Priority: P2. Checklist: **NET-2** (bound work driven by an untrusted enumeration). Disposition: fixed in the local, uncommitted wallet-layer integration; build/test execution remains with the integration owner. The evidence below records the original implementation.

Reachable input: during mnemonic restore, the FastNEAR public-key endpoint returns an `account_ids` array containing N copies of one syntactically valid named account that really has the phrase's full-access key. For example, N = 10000 duplicates of an already controlled `owned.near`. A faulty or hostile lookup service can produce this reply without compromising the independent RPC service.

Evidence and result:

- SDK `network/FastNearProvider.kt:65-68` accepts the entire response list without a count bound or deduplication.
- SDK `NearKit.kt:453-456` then calls `viewAccessKey` serially for every copy, with no `distinct`, candidate cap or overall discovery deadline. The legitimate full-access answer preserves every duplicate.
- App `walletkit-chain-near/src/main/java/io/horizontalsystems/walletkit/modules/nearaccount/NearAccountPickerViewModel.kt:135-139` creates a child coroutine per returned candidate. `loadAccount` at lines 153-155 starts two additional RPC requests for each candidate. This reply therefore causes 10000 sequential ownership RPCs, followed by up to 20000 detail queries, and a duplicated account list.
- The restore screen remains in `Searching()` until all of this completes. The manual-entry/default-account escape controls are shown only in `SearchFailed`, not in the searching branch (`NearAccountPickerPage.kt:103-108`). Back can cancel the overall flow, but cannot continue the same restore with the already derivable implicit account while the work is running.

The correct full-access verification prevents a false account from being accepted; it does not bound work caused by a poisoned discovery response. This is a restore availability/request-amplification issue, not a signing-authority bypass.

Smallest remedy: deduplicate before making ownership queries, cap the number of candidates processed per discovery, and bound concurrent detail requests. An overall discovery timeout or an available manual/default path during lookup keeps a slow indexer from holding restore hostage. Test a duplicated lookup response and a large unique response; preserve independent full-access verification for every account actually offered.

Validation: static call-count trace. No synthetic request flood or network load test was performed.

Remediation recorded 2026-10-28T16:21:53-04:00: `NearAccountPickerViewModel.kt` now uses the public raw discovery interface rather than the SDK's unbounded `findAccounts`. Before any ownership RPC it discards invalid IDs and the derived implicit ID, deduplicates and takes at most 20 named candidates; every offered named candidate still has to pass an exact full-access-key query. Detail reads run sequentially, retaining the existing two concurrent RPCs per account. A 20-second limit covers lookup, ownership checks and details; expiry becomes an ordinary failure so the existing manual/default-account controls appear, while external cancellation still propagates. The lookup and RPC factories use explicit clients without HTTP logging or redirects and resolve the global Tor proxy fail-closed whenever enabled. No new API credentials are used; the configured tier is keyless. Account-state conversion and other-key/contract warnings are preserved.

`NearAccountDiscoveryTest.kt` adds five focused cases for duplicate/invalid candidates, the 20-candidate cap and full-access requirement, lookup timeout, ownership/detail timeout and external cancellation. These tests were source-checked but not run by this role, as the integration owner controls builds. The cap deliberately leaves excess discovered accounts to the existing manual-name entry path; it never treats an omitted candidate as controlled.

## Assumptions challenged without an additional confirmed defect

- **Account discovery is advisory, not proof of control.** `NearKit.kt:453-455` validates account syntax and independently queries each candidate's full-access key on the RPC. Manually entered names repeat account existence and full-access checks in `NearAccountNameViewModel.kt:61-72`. The picker blocks unknown/lost access (`NearAccountPickerViewModel.kt:235`) and renders other-key/contract warnings (`NearAccountPickerPage.kt:241-279`). A malicious indexer alone cannot offer an arbitrary foreign account that the honest RPC says the key does not control.
- **A contract cannot become the canonical token by copying its symbol.** The SDK uses `outcome.executor_id` as `contractId`, and the app resolves known tokens by `TokenQuery(Near, Nep141(contractId))` (`NearTransactionConverter.kt:167`). `NearAccountManager.kt:90-93` auto-enables only token identities known to the coin manager. The remaining identity loss is the history representation described in ADV-1.
- **Settled history remains an indexer trust boundary.** `sync/TransactionSyncer.kt:183-195` accepts fetched items only for requested transaction hashes, but does not independently verify their full receipt tree against the RPC. SDK `TransactionConverter` also accepts both RPC and FastNEAR shapes. This review did not establish a chain-proof guarantee, a compromised-indexer defense or an independent attestation scheme, and does not claim one.
- **No live validation claim.** No watch-account vectors, Android rendering, indexer inclusion, funded transfers, builds or test suites were run by this role. The checked evidence is the pinned SDK and imported source. Other review roles and the integration owner still need to record decisions and the required live-vector results before marking the chain-integration review complete.

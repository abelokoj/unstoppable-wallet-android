# NEAR resilience review

Role: Resilience. Source review of the imported wallet module and pinned NearKit `856d2e3148cad1b031cba9247ddcbda3f26382b8`; no funded or device failure injection performed.

- **TX-4, TX-5:** SDK `transaction/TransactionSender.kt` saves the pending record before `sendTransaction`, preserves it after cancellation/ambiguous network errors and queries reached nodes before classifying a rejection. `SendTransactionServiceNear` returns a newly pending hash after an ambiguous outcome. This prevents a network timeout alone from being presented as proof that nothing was sent. Raw signed-byte rebroadcast after process restart was not exercised.
- **TX-6:** The SDK sender uses an account-kit mutex to serialize nonce acquisition and submission. Wallet/account/kit replacement under simultaneous requests was inspected statically only.
- **NET-2, NET-3:** SDK `sync/TransactionSyncer.kt` bounds incremental pagination and backfill, retries pending resolution independently of indexer progress and uses rate-limit backoff. A transient history outage is tolerated only within its configured failure threshold; no live provider-limit test was run.
- **NET-4:** SEC-3 applies: a watched-token contract read can fail or return malformed data while the SDK retains an indexer-derived value and marks global balances synced. This is a confirmed trust downgrade, not merely missing test coverage. It remains pending the user's repair/defer decision and must be resolved before enabling NEAR in a published build.
- **ACC-3:** `NearKitManager` tears down the previous kit on account or selected NEAR-account changes and reloads after RPC changes. Delayed token-auto-enable events must be pinned to the kit's account; the independent correctness review covers that race.

No additional distinct source-confirmed resilience finding beyond the balance trust downgrade is claimed. Network packet routing, real node failure, storage corruption, process death, pending expiry and runtime history recovery remain untested. This review does not establish production readiness.

Disposition update recorded at 2026-10-28T16:52:32-04:00: the user approved repair. SEC-3 is mitigated locally through independent contract-balance verification with an unsynced/error state on failure; account binding is also repaired. See review.md for the final decisions and test-vectors.md for the executed and unrun checks. The observations above describe the original imported code.

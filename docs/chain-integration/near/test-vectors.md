# NEAR integration test vectors

Recording time: 2026-10-28T16:44:18-04:00 (America/New_York). Target: local Open Swap 0.53.0 (185), importing upstream `d6661c8d0dc02d259767f58b7cd73cabb877da18` and NearKit `856d2e3148cad1b031cba9247ddcbda3f26382b8`.

## Automated fixtures

The following fixtures are synthetic regression inputs, not claimed mainnet transactions. Validation recorded at 2026-10-28T16:52:32-04:00: all 596 selected tests passed with zero failures, errors or skipped tests, including all 26 NEAR tests. Base Debug and Base Release subsequently passed, including release shrinking and vital lint. Both APKs report 0.53.0 (185), and the release certificate matches the established Open Swap certificate. The development APK was subsequently installed/opened on a connected device and the existing Balances/swap controls plus an unsigned PancakeSwap Cross-chain quote were checked. These are general integration checks; the NEAR runtime vectors below remain unrun. The user is continuing manual review, and the separate emulator remains open for later tests.

- `NearAddressAndTokenTest`: named/implicit account recognition, rejected foreign inputs, token-contract parsing, configured RPC fallback ordering and backup-validator parity with the pinned SDK.
- `NearAccountPickerUiStateTest`: unknown/lost access cannot be accepted as verified ownership.
- `NearAccountDiscoveryTest`: invalid and duplicate account IDs, more than 20 named candidates, full-access checks, bounded timeout and cancellation.
- `NearTokenBalanceTest`: exact zero and U128 maximum; reject numbers instead of strings, null, containers, negative/fractional/exponential values, leading-zero encodings and overflow.
- `NearHistorySafetyTest`: a fake USDC issuer naming a trusted contact stays spam and retains its contract; metadata failure remains explicitly raw; 250 incoming spam rows cannot consume the outgoing-recipient limit; unsigned/unknown-token and failed transfers are excluded; internal native counterparties match; repeating cursors and the 20-page cap terminate.
- `NearAccountManagerTest`: stale-account results cannot enable tokens for the new active account, and another account's enabled wallets cannot suppress the owner's tokens.
- Shared `BackupRestoreSettingsTest`: NEAR account-ID serialization, valid restore defaults, malformed watch/name settings and explicit null rejection.
- App `ChainBehaviorParityTest`: registered-chain behavior fixture includes NEAR.

## Live Android vectors

No watch-account history snapshot or explorer transaction set was captured in this offline merge. No mainnet hash, balance, transaction count or success result is invented below. Use disposable test accounts and record explorer evidence before treating these runtime cases as verified.

- Native incoming and outgoing: **not run**, the connected device has only been checked for general app/swap behavior. Check exact 24-decimal scaling, signer fee and incoming/outgoing/All filters without duplicate hashes.
- Known and unknown NEP-141 transfers: **not run**, NEAR runtime testing was not completed in this batch. Check exact contract identity and scale; unknown issuers must remain suspicious even with a familiar ticker/contact. Metadata failure must show raw units and the contract.
- Fee-only contract call: **not run**, NEAR runtime testing was not completed in this batch. Show the signer fee separately and no invented received amount.
- Unlimited approval: **not applicable** to the imported native/NEP-141 send interface; it does not implement ERC-20 allowances.
- Internal transfers and wrapping: **not run**, NEAR runtime testing was not completed in this batch. Verify account net movement, successful internal counterparty filtering and absence of duplicate native value.
- Dust and lookalike sender: **not run**, NEAR runtime testing was not completed in this batch. Compare a real outgoing recipient with subsequent dust/lookalike receipts, including a flood preceding the lookalike. Unit fixtures cover the bounded context selection, not live indexing/display.
- Named-account ownership and another full-access key: **not run**, NEAR runtime testing was not completed in this batch. The account picker must distinguish a verified full-access key, an inaccessible account and an unavailable check. Manual entry must remain possible after lookup failure/candidate limits.
- Fresh receiver registration and native reserve boundary: **not run**, requires a disposable funded transfer. Confirm storage-deposit cap, prepaid gas, one-yocto attachment and final amount before signing.
- Ambiguous broadcast, pending expiry and restart: **not run**, requires disposable funds and controlled network failure. Confirm pending persistence and no second payment after an uncertain response.
- Token RPC failure/recovery and foreground/background: **not run**, no controlled Android network test. A failed contract read must retain only prior verified display data with an unsynced/error state and a zero maximum; recovery must resume verified updates.
- Rapid account switching and encrypted backup restore: **not run**, NEAR runtime testing was not completed in this batch. Confirm that delayed discovery/auto-enable results never bind to another wallet and that valid sibling accounts survive a malformed NEAR backup entry.
- Tor fail-closed routing and packet capture: **not run**, no runtime capture. Static client/proxy inspection is not a leak test.

## iOS parity

First-20-address derivation comparison, cross-platform backups, all live history vectors and cross-platform finding closure: **not run**, no iOS counterpart/runtime is connected to this task. No external developer message was sent. The role reports, design and decision record are available for a later authorized handoff.

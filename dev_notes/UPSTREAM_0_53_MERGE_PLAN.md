# Upstream 0.53 Integration

Recording time: 2026-10-28T15:32:53-04:00 (America/New_York).

## Baseline and scope

- Open Swap baseline: `66c13609895e888775ad505ad9ee2bafd36f5ba7`, including the published 0.52.3 application and later documentation updates.
- Upstream target: release 0.53.0, commit `d6661c8d0dc02d259767f58b7cd73cabb877da18`.
- Integration branch: `codex/merge-upstream-0.53.0`.
- The merge preview identified 35 conflicted paths. They are now resolved in the local integration. Required upstream state handling was adapted to the current Open Swap screens instead of replacing their layouts.
- Complete and test the integration locally before pushing it. Preserve existing release tags and signing identity. Show the proposed commit message before publication.

## User decisions

- Approved: preserve current Open Swap direct providers, Relay, PancakeSwap Cross-chain and current ratings. Do not adopt upstream's removal of direct Uniswap/PancakeSwap/QuickSwap/1inch/THORChain/Maya/Stellar routes or server-controlled rating replacement.
- Approved by the user's subsequent "proceed": add NEAR account setup, conditional Zcash shielding steps and automatic receiving-token activation after swaps, using the current visual design.
- Approved by the user's subsequent "proceed": adopt USwap v3 preview/commit/signed execution and route-specific MEV eligibility while preserving signing, PIN and Tor controls.
- Approved by the same decision: add XSwap and expose NEAR Confidential in ordinary Swap, retaining all current direct providers and ratings.
- Approved: repair the NEAR review findings before publication. See docs/chain-integration/near/review.md for dispositions and runtime limits.
- Current appearance must be preserved. Any additional functional impact discovered during implementation must be presented to the user before applying it.

## Required preservation

- Keep amounts on the left, token selectors on the right, percentage shortcuts at the bottom, the active native-token 100% control and the fiat cursor fix.
- Keep the provider icon/name dropdown, provider icons and current ratings, including the configured PancakeSwap Cross-chain Excellent label.
- Keep the wallet switcher's fixed search and circled checkmark, fonts/scaling, selected app/splash icons, token discovery and persistence, shared EVM contacts and NGN handling.
- Keep Revoke Access, its Balances shortcut, BNB default, metadata/details, selection review and the existing signing flow with separate revocation transactions.
- Keep Standard and Private Send maximum-amount controls and the final provider/gas/buffer reserves.
- Keep exact-amount approvals, disabled wallet-added fee behavior, validated PancakeSwap command rebuilding, Token-2022 simulation checks, configured Solana RPC submission and pending-payload reuse.
- Keep the current Tor preference, fail-closed bridge requests, signing/PIN controls, branding and signing certificate. Do not borrow credentials or enable direct Across as part of this merge.

## Integration areas to resolve

1. Shared chain interfaces and new SDK versions: preserve direct-provider extension points and merge chain fixes without dropping Open Swap fields.
2. Provider registry and history: retain direct route IDs and history resolution; reconcile upstream retired-provider tracking without retiring active Open Swap providers.
3. Hosted swap execution: after approval, adapt USwap, confirmation, Private Send, CrossPay and transaction settings together. Preserve exact committed amounts, retry/cancellation safety and the current maximum-send behavior.
4. New chain and Zcash flows: after approval, integrate NEAR and the shielding/balance updates using current design components. Follow the repository's chain-integration checklist, evidence and role-separated review requirements for NEAR. Do not claim live vectors passed unless exercised.
5. UI bindings: port only required new state/action handling into current screens. Reject upstream layout changes that conflict with the preservation list.
6. Strings and fixtures: merge all supported locales, provider-ID fixtures and chain behavior snapshots while preserving Open Swap additions.
7. Verification: run the affected wallet/chain and app suites, translation coverage and development/release builds. Inspect the current interface where a device or emulator is available. No funded transaction is authorized by this merge request.

## Additional behavior to disclose

- The user approved XSwap and NEAR Confidential in ordinary Swap. They are included while existing providers remain active.
- NEAR introduces RPC and account-discovery service access. Inspect the pinned kit's Tor handling and data disclosure before enabling the chain.
- Automatic receiving-token activation changes the enabled coin list; it must preserve account isolation and restore/birthday settings.
- MEV protection must not be advertised for bridge deposits or routes that cannot use it. The requested rating labels are display configuration, not security guarantees.

## Status

The local merge is in progress on the integration branch. Open Swap version metadata is 0.53.0 (185) so the build number advances beyond the installed 0.52.3 (184). All 596 selected tests, translation coverage, Base Debug and Base Release now pass. APK version and the unchanged release certificate are verified. The development update was installed/opened on the connected test device and the existing Balances/swap layout, bottom percentages and an unsigned PancakeSwap Cross-chain ETH-to-BNB quote were checked. The user is continuing manual review; the separate emulator is left open. The validated source merge is ready for its authorized commit/push, with NEAR/funded/Tor/iOS limits recorded. No new release tag or APK publication is part of this source merge. FastNEAR uses its supported keyless tier; no borrowed FastNEAR API key is added.

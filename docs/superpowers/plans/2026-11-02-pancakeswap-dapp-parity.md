> Superseded runtime decision: the user subsequently requested Kotlin only. The plan below records the earlier JavaScript implementation and test APKs. The active design is `2026-11-02-pancake-kotlin-routing.md`; current architecture and limits are in `docs/pancake-native-routing.md`. No JavaScript engine is intended for the final requested installers.

# PancakeSwap dapp parity implementation plan

**Goal:** Match the current PancakeSwap cross-chain planner on Open Swap's existing supported networks, integrate the direct-PNG Original animation, and deliver F-Droid development and signed installer APKs at 0.53.2 (189), without publishing.

**Architecture:** Use the pinned official SmartRouter/InfinityRouter solvers locally, with native-controlled read-only network transport. Mirror the observed dapp's four EVM planning strategies and NON-EVM gateway semantics. Keep expected quotes separate from protected execution plans and retain the current local approval/signing and calldata validation boundaries.

**Tech stack:** Kotlin/coroutines, existing Retrofit/OkHttp/Tor/RPC transport, bundled pinned PancakeSwap JavaScript SDK, Android's JavaScript runtime, Node-based solver regressions, existing Gradle/JUnit checks.

**Spec:** User goal and docs/audits/2026-11-02-pancakeswap-crosschain.md, plus the recorded official dapp build and package evidence. A public full-planner endpoint was not found, so an endpoint substitution is not sufficient.

## Global constraints

- No UI redesign, developer fee, signing-flow replacement, production service/key purchase or new server.
- No GitHub push, release upload or remote-tag change.
- Version 0.53.2, build 189; F-Droid Debug and F-Droid Release only; preserve production signing identity.
- Preserve global Tor behavior and fail closed when enabled Tor is unavailable. No JavaScript networking or wallet signing capabilities.
- Keep the crypto scanner and other cartoons unchanged. Use the direct-PNG Original MP4s already verified locally.
- Preserve user edits, pre-existing untracked installers and cached build outputs; do not run a blanket clean.

## Tasks

- [ ] Pin the actual current dapp strategies, solver configuration, source-surplus behavior and pool-discovery contract. Correct audit assumptions when deeper evidence contradicts them.
- [ ] Write failing native planner tests for all four patterns, directed bridge-token mappings, expected/protected separation, user slippage and invalid/expired plans. Implement a testable native planner using injectable quote/command functions.
- [ ] Bundle the official AMM solvers reproducibly, with lockfile, provenance/licenses and deterministic fixtures. Prove native/wrapped assets, V2/V3/Stable/Infinity paths, split selection and large integer behavior using the exact bundled asset.
- [ ] Add the smallest isolated local runtime bridge. Restrict native requests to the intended chains, read-only RPC methods and approved public pool data. Test cancellation, timeouts, Tor changes, oversized/malformed messages and attempts to request arbitrary URLs or signing methods.
- [ ] Connect the provider to the new planner and official solvers. Preserve exact approval targets/input amounts, recipient/refund checks, router whitelists, protected output and transaction reconstruction; test quote and calldata agreement, including source-surplus semantics.
- [ ] Compare unsigned native-provider and reference-dapp plans for source/destination tokens, route selection, expected amounts, protected amounts, bridge fees and generated command intent, across EVM patterns and both Solana directions. Account explicitly for different observation blocks/times.
- [ ] Verify the Original asset hashes in both checkouts and final APKs. Update changelog, TODO and integration documentation with actual outcomes and limitations.
- [ ] Run the relevant Kotlin/JavaScript tests and release checks, then build both F-Droid APKs sequentially using the existing cache profile. Verify package/flavor/version/signatures, packaged routing/media assets, release reflection rules and archive layout. Place distinct APKs/checksums in computer Downloads; do not publish or install.

## Review focus

- A visually larger quote must not conceal an unprotected or underfunded bridge/destination command.
- Fallbacks must not silently substitute a different planner and claim parity.
- The local JS runtime must never bypass Tor, access a wallet secret or expose a signing method.
- Decimal conversion, native/wrapped tokens, chain case-sensitivity and graph serialization must preserve integer amounts.
- Keep every allowed payment and approval bounded to the reviewed order, including hook and split routes.

## Execution notes

The user explicitly authorized implementation after the audit/recommendation, so work proceeds inline without another approval ceremony. Source/contract findings, test failures, implementation rulings and final artifact evidence will be recorded as they occur. Completion requires the requested deliverables and meaningful planner/solver coverage; current unit tests alone do not establish full dapp parity.

## Delivery result, November 2, 2026

Implementation and local installer delivery are finished: 39 native tests, nine JavaScript tests, five live anonymous EVM AMM probes, seven frozen dapp-core comparisons, real source/destination/three-command gateway fixtures, release vital lint and both final signed F-Droid artifacts pass. The exact packaged asset replays every supported EVM chain with direct networking disabled. APK verification records and checksums are saved separately; version 0.53.2 (189), current certificates and the unchanged scanner are retained. Source and release have not been pushed.

The broad original task checklist above is retained as its initial acceptance plan, not a claim of hardware/funded-test completion. Native WebView lifecycle/renderer/Tor-cancellation tests and synchronized complete app/dapp EVM/Solana settlement comparisons remain device follow-up. Preview addresses stay private until confirmation under the user's policy decision. The gateway's trade output is the encoded minimum, active Infinity StableSwap is disabled, and exact destination prefunding is intentionally retained for safe bridge-surplus refunds. See dev_tools/pancake-routing/README.md and the root changelog for actual verified scope and limitations.

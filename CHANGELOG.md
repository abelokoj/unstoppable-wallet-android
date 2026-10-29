# Open Swap 0.53.1 release preparation - 2026-10-28T20:28:14-04:00

- Prepared the completed startup hotfix for version 0.53.1 (186). The public notes describe startup stability and continued loading of valid custom tokens without internal test/device details. Previous releases and release-note sections are preserved.
- Validation remains the successful 311-test wallet unit suite, the verified signed Fdroid Release APK, and the user's confirmation that the updated regular app opens and stays running. APK SHA-256: 5eae9f086892cc7fab124e5a0c5f89698c06e544e5e8341c7cb64b8436e75bbd. The optional optimized instrumentation did not complete, as recorded below; no funded transaction or recovery operation was tested.
- The release will use this already-tested APK rather than another local build. Source commit, a new v0.53.1 tag and GitHub publication are pending at this recording point. No previous tag or asset is being replaced. Recording time in America/New_York before this commit.

---
# Startup hotfix handoff and test cleanup - 2026-10-28T19:47:29-04:00

- The signed 0.53.1 (186) hotfix fixes the reported regular-app startup crash. The user installed it as an update and confirmed it opens and stays running; Android also reported the new installed version and a running process. The APK retains the established release signing certificate and SHA-256 5eae9f086892cc7fab124e5a0c5f89698c06e544e5e8341c7cb64b8436e75bbd.
- Retained the seven custom-token persistence unit regressions. All 311 wallet unit tests passed with no failures, errors or skips. No production code changed after that successful suite or the user's signed-APK check.
- The optional optimized instrumentation attempts did not execute their test cases: the first runner lacked androidx.tracing.Trace and the second lacked kotlin.LazyKt after shrinking. Stopped the subsequent test-only rebuild rather than delaying the completed startup fix further, removed both newly created disposable test packages, and removed the unexecuted Android harness from this source batch. Its diagnostic copy and logs remain outside the repository. No optimized instrumentation pass is claimed.
- Regular and development wallet packages and their storage were preserved during test cleanup. No funded transaction or recovery operation was performed. Release notes describe the startup/storage fix without test-device details. This is local, uncommitted work, not a GitHub publication; the timestamp records this handoff in America/New_York. Earlier progress entries below remain intact.

---
# Startup crash hotfix validated - 2026-10-28T19:12:49-04:00

- Fixed release-only custom-token catalog loading by writing explicit stable JSON keys and accepting the verified field aliases used by earlier optimized APKs. Invalid entries are isolated before token lookup, without rewriting the existing preference on read or changing account, key, seed or wallet databases.
- Passed all 311 wallet unit tests with no failures, errors or skips, including seven persistence regression cases. Before the fix, the old reflective decoder failed four of those seven cases. The separate optimized Android persistence tests are still in progress at this recording point.
- Built and verified the signed Fdroid Release APK for 0.53.1 (186), including its package/version and compatibility with the established release certificate. APK SHA-256: 5eae9f086892cc7fab124e5a0c5f89698c06e544e5e8341c7cb64b8436e75bbd. The user installed this hotfix as an update and confirmed that the previously crashing regular app now opens and stays running. No wallet storage was cleared, and no funded transaction or recovery operation was performed.
- Local, uncommitted and not published to GitHub. This entry records the validation results now, in America/New_York; earlier in-progress notes below remain as historical recording points.

---
# Startup crash hotfix in progress - 2026-10-28T18:37:34-04:00

- Captured repeated on-device release crashes in TokenQuery.fromId, reached through CoinManager.customTokens during EVM history loading. Verified that CustomTokenRecord's five JSON-backed fields were renamed to a/b/c/d/e in both the current optimized build and the prior published 0.52.3 APK. This explains a release-only saved-catalog compatibility failure; the previous development-only launch check did not exercise it.
- Replaced reflective custom-token catalog persistence with explicit stable JSON keys and read support for the verified older optimized aliases. Each record is validated before it reaches token lookup; malformed entries cannot abort the whole catalog, and conflicting canonical/legacy identities are rejected. Reads do not rewrite or clear the existing preference. Account, seed, key and wallet databases are unchanged.
- Prepared hotfix version 0.53.1 (186). Added seven focused unit cases and an Android persistence regression using its own disposable preferences. The baseline decoder failed four of the seven unit cases before the fix; the fixed wallet suite and optimized-device validation are still in progress at this recording point. No fixed APK or publication is claimed yet.
- Local, uncommitted work recorded in America/New_York. Crash logs remain outside the repository; no private wallet inventory or credentials are included. Existing regular-wallet data will not be cleared or replaced with test fixtures.

---
# Release 0.53.0 published - 2026-10-28T18:09:59-04:00

- Published the separate GitHub release v0.53.0 from d4f63de9da963607c53bbe1d114272f6d5dd694f after the user's successful manual development review and explicit publication request. GitHub Actions run 40775692405 completed successfully and attached the signed Fdroid Release APK open-swap-v0.53.0.apk (99,896,026 bytes). Earlier releases and tags remain intact.
- Downloaded the actual public APK and verified package money.openswap.wallet, version 0.53.0 (185), a valid signature matching the established Open Swap release certificate, the exact uploaded size and the GitHub SHA-256 digest 085026678306077a563f8ddbc083591ac5cf232655a60b5d740463d224e62523. No release APK was installed over the user's regular wallet by automation; the previously validated local Base Release APK was also provided for optional manual installation while the hosted build ran.
- Restored the agreed Markdown release body after Git's default annotated-tag cleanup stripped its headings, then checked it exactly against the prepared notes. The release tag still points to the original release commit; no tag was moved. Added the verbatim-cleanup instruction to the developer guide so future tags preserve Markdown headings. The published notes retain the financial-testing, provider/fee, preview, Tor and iOS limits.
- This follow-up records publication and the separately requested test-emulator cleanup. Application source remains unchanged from the tested merge, so no application test rerun is needed. The prior 596 selected tests and Base builds, user feedback and successful Fdroid Release build are the actual validation evidence; no funded transaction, recovery exercise or packet capture is claimed. Recording time in America/New_York before this documentation commit/push.

---
# Test emulator removal - 2026-10-28T18:01:46-04:00

- Closed the test emulator and removed only the added OpenSwap_053_Test Pixel 5 virtual device at the user's request because it was slow. Preserved the original Pixel 10 Pro XL virtual-device definition and the shared Android system image.
- Verified the exact AVD directory and configuration before deletion, used the Android AVD manager to remove it, confirmed the added device files are absent, and confirmed no emulator engine remains running. No app source, wallet data or APK changed; no application test rerun is needed for this host-only cleanup.
- Recording time in America/New_York for local documentation awaiting the publication-record commit. The signed GitHub APK build continues independently.

---
# Release 0.53.0 preparation - 2026-10-28T17:52:36-04:00

- The user reported that manual development testing is working and requested the APK build and GitHub publication. Prepared the separate v0.53.0 release with version 0.53.0 (185); application source is unchanged from the tested upstream merge cdba97d76. Existing releases and signing identity are preserved.
- Finalized the reader-facing release notes, removed the unreleased heading and source-only homepage notice, and updated the APK verification example. Kept the material NEAR, provider, fee, bridge-preview, funded-recovery, Tor and iOS limitations explicit. General user feedback does not establish that each unrun financial or cross-platform vector passed.
- Validation already completed for this unchanged application source: 596 selected tests, all supported translations, Base Debug and optimized Base Release passed; the development update was installed and reviewed. This batch checks documentation and delegates the distributable Fdroid Release build to the existing signed GitHub Actions workflow triggered by the new annotated tag. No new source code or funded transaction is part of publication preparation.
- Recording time in America/New_York before the release commit/tag push and before the hosted build. Completion, attached asset and final APK verification will be recorded after they actually succeed. Unrelated installers and private development notes remain excluded.

---
# Upstream 0.53 integration - 2026-10-28T17:22:32-04:00

- Integrated upstream Unstoppable Wallet 0.53.0 at `d6661c8d0dc02d259767f58b7cd73cabb877da18`, adapting the merge to preserve Open Swap's current layouts, direct providers, Relay, PancakeSwap Cross-chain, configured route ratings, bottom percentages, maximum-amount controls, fiat cursor, wallet switching, appearance, token/contact features and Revoke Access. Version metadata is 0.53.0 (185), advancing beyond 0.52.3 (184).
- Added the approved NEAR/native and NEP-141 account flows, XSwap, ordinary-swap NEAR Confidential, conditional Zcash shielding and receiving-token activation. Updated hosted Swap, Private Send and CrossPay to the v3 preview/commit/signed lifecycle while retaining account guards, exact committed amounts, fee reserves, existing signing and uncertain-broadcast tracking. Retained direct Maya shielded delivery and migrated the retained Stellar provider to the new lifecycle. Existing 1inch configuration remains intact; FastNEAR is keyless and no borrowed credential is configured.
- Repaired the source-confirmed NEAR review findings before publication: unknown-token issuer identity and spam classification, genuine outgoing poisoning context, independent contract-balance verification with visible failure states, raw units after metadata failure, backup account-ID validation, atomic account/kit ownership and bounded discovery. Added the design, four role reports, final finding dispositions and explicit executed/unrun vector records under `docs/chain-integration/near/`.
- Prepared reader-focused, unreleased 0.53.0 notes and updated the source homepage without claiming a new downloadable release. Added WMKeyboard alongside LeanType as the standing release/homepage formatting reference. Preserved previous release notes, tags and changelog entries.
- Validation: all 596 selected tests passed with zero failures, errors or skips: walletkit 304, EVM 33, Solana 19, Stellar 16, THORChain 1, NEAR 26, Zcash 75, Tron 7, XRP 4, Zano 8 and app 103. Bitcoin and Monero test tasks contain no tests; their modules compiled with the app. Translation coverage passed for 1,849 strings across nine locales. Base Debug and Base Release packaging passed, including release optimization and vital lint. Both APKs report 0.53.0 (185); the release certificate matches the existing Open Swap fingerprint. Fixed a stale NEAR fallback-order assertion against the pinned SDK rather than changing its configured order. Existing compiler/Room/Gradle warnings remain.
- Recording time in America/New_York for local, staged merge preparation before committing or pushing. The user subsequently connected a test device: installed and opened only the development update in place, then checked the Balances Revoke Access shortcut, amount-left/token-right layout, bottom 25/50/75/100% controls and an unsigned ETH-to-BNB PancakeSwap Cross-chain quote with the retained Excellent label. No approval, signature or transaction was submitted. The user took over further manual testing; the separate emulator was left running for later use. This device-check update was recorded at 2026-10-28T17:40:18-04:00. Funded transfers, bridge settlement/refunds, controlled pending/recovery failures, observed Tor traffic and iOS parity remain untested. Configured RPC/indexer/provider services still receive the public request data described in the integration records. No new release tag or GitHub APK is published by this source-merge batch.

---
# Documentation reference update - 2026-10-28T16:05:48-04:00

- Added WMKeyboard's v0.6.4 release and repository homepage alongside LeanType in both `dev_notes/RELEASE_NOTES_STYLE.md` and `dev_notes/README_STYLE.md`, as requested. The guides now record concise release summaries, suitable change or feature-area groupings, prominent download links and reader-focused descriptions.
- Reviewed both supplied public pages and the existing guides. These guide edits are local and uncommitted within the ongoing upstream 0.53 merge; they do not change the app, current published homepage or existing release notes. No publication is claimed by this entry. Recording time in America/New_York.

---

# Upstream 0.53 merge preflight - 2026-10-28T15:32:53-04:00

- Prepared local integration branch `codex/merge-upstream-0.53.0` from the current published Open Swap baseline and compared upstream release 0.53.0 at `d6661c8d0dc02d259767f58b7cd73cabb877da18`. A merge-tree preview identified 35 conflicted paths without changing checked-out application files.
- Recorded the user's decision to preserve existing direct swap routes, Relay, PancakeSwap Cross-chain and current ratings. The user requires existing UI preservation and a decision before changes that affect current functionality; NEAR/Zcash flows and USwap execution/MEV changes remain pending user decisions.
- Added the implementation/preservation plan to `dev_notes/UPSTREAM_0_53_MERGE_PLAN.md` and tracked unfinished integration work in TODO. No application code, version, APK, signing settings or released tag was changed. No actual upstream merge, build, test run, commit or push was performed in this preflight batch.
- Corrected an accidental control character preceding the v0.52.3 tag in the earlier publication entry. That typo came from escaped prose during release documentation; the actual released tag remains v0.52.3. Recording time for local, uncommitted preflight documentation in America/New_York.

---

# Release published - 2026-10-28T15:23:45-04:00

- Published Open Swap 0.52.3 (184) as GitHub release `v0.52.3`, based on the preceding release commit and tag. GitHub Actions completed the signed APK build successfully and attached `open-swap-v0.52.3.apk` to the release.
- Updated the live release body to match the published LeanType-style source notes. The accompanying README and developer-note guides remain local until their documentation commit is pushed. No post-build application code or APK changed.

---
# README landing-page guide - 2026-10-28T15:22:23-04:00

- Reformatted the Open Swap GitHub landing page around the reader-facing structure used by LeanType: short product introduction, key links, overview, grouped features, download, certificate verification, financial-security disclosures and project links. Removed the duplicated upstream README from the landing page while preserving the upstream project link and fork disclaimer.
- Added dev_notes/README_STYLE.md so future README revisions follow the same pattern. The guide keeps public text focused on user outcomes and requires visible fee, trust and untested-flow disclosures for financial features.
- Documentation-only change. No application source, build metadata, tag or APK changed. Validation: reviewed the new links, statement of scope and staged documentation patch; no application rebuild or test rerun is needed.

---

# Release-note format refinement - 2026-10-28T15:20:00-04:00

- Adopted the clearer reader-facing pattern observed in LeanType releases for Open Swap 0.52.3: concise headings for Updated, Added and Fixed, with a separate visible cross-chain preview limitation. The feature scope, upstream 0.52.2 attribution and risk disclosures remain unchanged.
- This is a release-note and changelog-only follow-up. No source code, version metadata, tag or APK changes are included. Validation: checked the staged documentation patch and whitespace only; no application rebuild or test rerun is required.

---

# Developer release-note guide - 2026-10-28T15:16:44-04:00

- Added `dev_notes/RELEASE_NOTES_STYLE.md` as the standing guide for LeanType-style public Open Swap release notes: concise Updated, Added and Fixed sections, with visible financial-feature limitations when needed. The existing untracked `dev notes/` folder remains untouched.
- Documentation-only change. No application source, build metadata, tag or APK changed. Validation: reviewed the new guide and staged documentation patch; no application rebuild or test rerun is needed.

---

# Release notes clarification - 2026-10-28T15:07:00-04:00

- Reorganized the 0.52.3 public notes into New Features, Improvements and Fixes, and Cross-chain Preview. Explicitly identified upstream Unstoppable Wallet 0.52.2 as the integrated base and separated approval management from full-balance sending and other fixes, at the user's request.
- Preserved all previously listed release features and material preview limits. Kept the existing v0.52.3 tag and application sources unchanged; this is a documentation-only follow-up to the published release commit feb0a4484. Earlier notes and changelog entries remain intact.
- Validation: checked release-note section/history preservation and patch whitespace; no app rebuild or test rerun is needed for these prose-only changes. The signed GitHub APK build for the unchanged release tag was still in progress at recording time. Recording time in America/New_York, before this documentation commit and release-body update.


---
# Release preparation - 2026-10-28T14:59:06-04:00

- Prepared OpenSwap 0.52.3 (184) for publication as a new release after v0.51.2, including the upstream 0.52.2 integration and all subsequent OpenSwap approval, send, swap, wallet-selection and provider-icon work. The upstream 0.53.0 investigation is complete, but none of that release is merged into this version.
- Consolidated public release notes since the previous release, including the circled wallet-selection check, the requested PancakeSwap Cross-chain Excellent label and the remaining funded-settlement/refund limits. Updated the integration document to identify its preview status in 0.52.3. Historical notes and existing releases are preserved; unrelated installers and private development notes are excluded.
- Validation performed earlier for the unchanged source in this batch: 392 selected tests passed; Base Debug and Base Release packaging passed, including release shrinking/lint and signing-certificate verification; translation coverage passed for 1,811 strings across nine locales. Publication preparation checked the staged patch, APK version metadata, existing GitHub releases, signing-secret names and release-workflow configuration. No funded transaction was tested.
- Recording time in America/New_York for local publication preparation, before the release commit and push. Remote publication status is recorded by the GitHub commit, tag, Actions run and release; this entry does not claim those future steps have already succeeded.


---
# Unreleased - 2026-10-28T13:53:13-04:00

- Incremented the app from 0.52.2 (183) to 0.52.3 (184) and set the PancakeSwap Cross-chain route score to Excellent at the user's request. This is a configured display rating, not an independent security audit or a guarantee of bridge safety.
- Fixed the empty fiat amount field so the cursor sits after the currency symbol and before the shadowed zero. The shared visual transformation preserves currency prefixes, including multi-character symbols, without inserting the placeholder into the entered amount. Existing amount parsing, alignment and placeholder colors remain in place. Regression coverage includes empty/typed values and offset mapping.
- Reviewed the cross-chain quote/build/status, approvals, recipient/input limits, AMM command, configured-RPC/Tor and Solana signing/simulation paths. Found a missing Token-2022 simulation guard: permission checks covered the base account layout but omitted extension data and appended bytes. Preserved account size and all non-balance bytes so unexpected extension/permission changes now fail closed. The regression reproduced the missing rejection before the fix and passes afterward. No funded on-chain exploit was attempted or demonstrated.
- Added narrow library consumer rules preserving the two JSON-read Solana instruction/account models in optimized builds. The first mapping showed model renaming, which did not alone prove field removal; direct inspection of the final release APK verified all six expected JSON field names. This is release hardening, not evidence of an observed release crash. Existing provider/RPC checks and the current signing flow are retained.
- Validation: all 392 selected tests passed with zero failures, errors or skipped tests: 238 walletkit, 33 EVM, 19 Solana and 102 app tests. Both Base Debug and Base Release built successfully, including release optimization and lint; rebuilt release packaging after the consumer-rule addition. Both APKs report 0.52.3 (184), and the release signing certificate matches the documented OpenSwap certificate. Translation coverage passed for 1,811 strings across nine locales, and patch whitespace checks passed. Existing compiler/Room/Gradle warnings remain.
- Installed and opened only the development package in place, preserving app data. The user confirmed the fiat cursor now works correctly. Automated screenshot and on-phone rating checks were interrupted by repeated USB debugging disconnections; the requested Excellent value is verified in source. No approval, funded transaction, signature or PIN bypass occurred.
- Remaining limits: Relay destination intent and hosted settlement/refund status still require provider trust. Production gateway support/terms, funded settlement, every-network/pair coverage, recovery and observed Tor traffic remain unverified. This focused wallet-code review is not an independent audit of the bridge protocols or their infrastructure. See integration details for disclosures and bounds.
- Correction to the 2026-10-28T12:45:26-04:00 entry: its component totals were 236 + 33 + 18 + 1, which equal 288 selected tests, not the reported aggregate of 287. Preserved that historical entry; corrected the current documentation/TODO aggregate transparently.
- Updated pending public release notes, TODO and integration details, and staged the requested source/docs changes for review. No commit, push, tag, release publication or release-package installation was performed. Unrelated local installers and development notes remain excluded. Timestamp is the recording time in America/New_York for local, uncommitted work.


---

# Unreleased - 2026-10-28T12:45:26-04:00

- Extended the earlier PancakeSwap Bridge preview into PancakeSwap Cross-chain: native/ERC20 routes can include AMM trades before and after bridging across Ethereum, BNB Smart Chain, Base, Arbitrum and zkSync. Added native/SPL Solana-to-EVM and EVM-to-Solana routes through PancakeSwap's Relay-backed interface. Linea remains excluded by the user's choice. Existing same-chain V2/V3 providers and the deferred standalone Across adapter are retained.
- Added bounded catalog route planning, split-leg slippage, V2/V3/classic StableSwap/Infinity CL/Bin quote codecs and registered BNB Infinity StableSwap discovery. Pool state and router authorization are checked through configured, chain-verified RPCs. Unknown/custom hooks are rejected, with alternate classic routes attempted where possible. The planner does not guarantee identical website route selection or the lowest total gas cost. Standalone Infinity and Uniswap v4 providers remain planned.
- EVM AMM transactions decode and validate the gateway's full command tree before locally rebuilding the outer call with fixed assets, exact input, canonical adapters, recipients/refund owner, minimum output, bounded deadlines and empty Permit2 data. Source prefunding uses the exact approved input. Relay EVM deposits require the canonical depository and exact token/native amount. Existing approvals, confirmation and PIN signing are reused; no wallet fee or extra payout command was added. Normal pool, provider/bridge and network costs apply.
- Added Solana instruction/program/account validation, native/wrapped SOL distinction, lookup-table loading, configured-RPC simulation and submission, current-signer reuse and exact pending-payload preservation after an ambiguous broadcast. Active-wallet, RPC, Tor, blockhash and fee checks remain required before signing. Sanitized Solana transport errors so custom RPC URLs are not exposed through network error details. The SDK's global Tor handling remains in place; this does not replace its general retry transport.
- Initial quotes use public placeholders where an address is required. Final transaction preparation shares the actual sender/recipient and swap intent with the gateway; tracking shares the public transaction hash. Tor-enabled requests fail closed without a connected SOCKS proxy, and redirects/body logging remain disabled. Increased only PancakeSwap's quote budget to 30 seconds so multi-step quotes are not discarded by the default five-second limit.
- Added public unsigned fixtures for ETH/BNB AMM routes, pool variants, registered stable-pool state and Solana/Relay intents, plus malformed/malicious payload, slippage and instruction tests. All 287 selected tests passed: 236 walletkit, 33 EVM, 18 Solana and one navigation-serialization test, with zero failures, errors or skipped tests. The final Base development APK built as 0.52.2 (183); after the transport-error change, the Solana suite's 18 tests and development packaging passed again. Patch whitespace checks passed. Existing compiler/Room/Gradle warnings remain.
- Installed and opened the development app in place, preserving data. Physical quote checks showed PancakeSwap for ETH to BNB, BNB to ETH, BNB to native SOL and native SOL to BNB; BNB to ETH was also selectable when another provider won automatic routing. Stopped at quote/provider selection with insufficient balance or token-not-enabled controls. No funded swap, approval, signature or PIN bypass occurred. These observations supersede the earlier USB-disconnection limitation for quote testing only.
- Production gateway access/support remains unconfirmed. Final funded signing, every-network/token coverage, destination receipt verification, live NON-EVM settlement status, Tor traffic capture and refund/recovery remain untested. The opaque Relay intent and hosted settlement status remain provider assertions; recovery is not automated. No blanket support for arbitrary hooks or future website routes is claimed.
- Local, uncommitted changes only. Updated TODO, pending public release notes and integration documentation. No release build, commit, push, tag or publication was performed. Timestamp is the recording time in America/New_York.


---

# Unreleased - 2026-10-28T07:22:33-04:00

- Added a local PancakeSwap Bridge preview for live-catalog bridge-token pairs across Ethereum, BNB Smart Chain, Base, Arbitrum and zkSync. This advances the earlier feasibility work; source/destination AMM trades, Solana and full website routing remain unimplemented. Existing PancakeSwap V2/V3 routes are retained, and direct Across stays deferred.
- Uses PancakeSwap's website gateway without a borrowed key or wallet address for quotes. Transactions are constructed locally against published XChainSender/Across adapters, with one bridge and one delivery/unwrap command, empty Permit2 data, ordinary wallet approvals, fixed destination/minimum output/refund ownership and no added wallet fee or payout command. Bridge/relayer cost and network gas still apply. Requests use the existing Tor preference with fail-closed SOCKS handling, no request/body logging and no redirects.
- Added input/chain/token/deposit-limit/fee/deadline checks and dedicated history tracking. A successful source submission alone cannot complete history: matching destination fill data and sufficient output are required. Unknown responses remain pending; partial/failed execution requires attention and refunds require a reported refund hash. Hosted tracking is not independent destination receipt verification.
- Added public unsigned dummy-address ERC20/native fixtures and regression tests for exact encoding, native value, Permit2 omission, mismatched/stale/invalid quotes and tracking identity. Live anonymous checks returned quotes for Base USDC to Arbitrum USDC, Base USDC to BNB USDC with different decimals, and native ETH between Base and Arbitrum. Public contract transactions verified the status response format. No order or funded transaction was submitted.
- Validation: the Base development APK built successfully as version 0.52.2 (183); all 254 selected tests passed (220 walletkit, 33 EVM and one navigation-serialization test), with zero failures, errors or skipped tests. Corrected the initial transaction encoding against the fixture before packaging. A mistyped navigation-test filter was rerun with the correct test name. Translation coverage passed for 1,811 strings across nine locales, and patch whitespace checks passed. Existing compiler/Room and Gradle warnings remain.
- Installed and opened only the development package on the connected phone, preserving existing data. The USB connection dropped at the update screen before route selection or signing confirmation could be exercised; physical route testing remains pending. The public website gateway is undocumented, so third-party production access/support and future availability remain unconfirmed. Funded settlement, Tor traffic observation and refund/recovery testing remain required before production release; recovery transactions are not automated.
- Local, uncommitted changes only. Updated TODO, pending public release notes and [integration details](docs/PancakeSwap-CrossChain.md). No release build, commit or push was performed. Timestamp is the recording time.


---

# Open Swap — change log

A personal fork of [unstoppable-wallet-android](https://github.com/horizontalsystems/unstoppable-wallet-android) (MIT). Not affiliated with or endorsed by Horizontal Systems.

**App name:** Open Swap · **Package:** `money.openswap.wallet` · **Signing SHA-256:** `277c10bbed425c98b8df4aa7372dfe483daeefdc6ddbc19b462ac83df8d8b295`

## Repositories

| Repo | Branch / pin | Base | Notes |
|---|---|---|---|
| [unstoppable-wallet-android](https://github.com/abelokoj/unstoppable-wallet-android) | `master` | upstream `0.52.2` | Open Swap |
| [ethereum-kit-android](https://github.com/horizontalsystems/ethereum-kit-android) | pinned `a887150` | upstream | Arc-capable kit; taxed-token compatibility lives in the wallet |
| [market-kit-android](https://github.com/horizontalsystems/market-kit-android) | pinned `aa91028` | upstream | NGN rate conversion remains in the wallet |

Use the kit revisions pinned in `gradle/libs.versions.toml`, not their moving `master` branches. The earlier custom kit forks are retained in their repositories but are no longer build dependencies as of this 0.52.2 merge.


---

# Unreleased - 2026-10-28T06:21:19-04:00

- Replaced the native-token 100% restriction recorded in earlier entries with active full-balance selection and bounded, fee-aware swap confirmation. The chain service supplies the spendable budget; EVM uses its full gas-limit fee reserve and accounts for extra native value required by the route. Adjusted inputs receive fresh quotes/transaction data, round down to token precision and remain blocked from signing until ready. Unknown or unaffordable estimates fail closed instead of assuming zero fees.
- EVM preparation now clears the preceding transaction and rejects settled transaction data whose destination, value or calldata do not match the latest request. Added regression coverage for fee adjustments, probe recovery, tiny amounts, unavailable estimates and bounded retries; corrected a probe-equality edge case exposed during the initial test run.
- Added 100% to Standard and Private Send. Standard Send retains the existing maximum-transfer fee deduction. Private Send records an explicit maximum selection, estimates the native deposit fee through the existing chain service and uses read-only exact-output rate quotes to reserve provider cost and the refundable buffer before committing one order. Manually typed amounts retain exact-output semantics. Final deposit affordability and the active wallet are rechecked, and changed fees/balances expose an explicit Refresh action without silently replacing an order. The balance/shortcut row wraps when needed for narrow screens or larger text.
- Revoke Access now starts on BNB Smart Chain independently of alphabetical network ordering, with the existing first-supported-network fallback if BNB metadata is unavailable. Added a regression test for this selection.
- Replaced Lizex's placeholder with its official [favicon](https://lizex.io/favicon.ico), converted to PNG without altering the artwork and bundled locally. The existing provider icon sizing applies; no runtime image request was added. Inspected the asset and confirmed its presence in the final APK.
- Reviewed Private Send's fee path: the client displays quoted route/provider cost, a separate network fee and, when known, a refundable buffer. No explicit client-side developer fee, affiliate/bps request field or separate developer payout was found. The [underlying provider's policy](https://docs.near-intents.org/resources/fees) permits partner fees/revenue sharing, so the deployed USwap/backend split remains unverified. No fee parameters were removed or changed by this investigation.
- Confirmed both Uniswap V3 and PancakeSwap V3 remain registered for normal swaps, including Ethereum and BNB Smart Chain. Reviewed Uniswap v4, PancakeSwap Infinity and PancakeSwap cross-chain integration and updated TODO. A public, unsigned PancakeSwap same-chain aggregator quote returned HTTP 200 without an API key or wallet address and echoed an added swap fee of zero. That does not establish third-party cross-chain access: the documented Unified API is same-chain, while the website's bridge routes use Across/Relay. Direct Across remains deferred at the user's request; PancakeSwap cross-chain, Infinity and Uniswap v4 were not implemented in this batch.
- Validation: the final Base development APK built successfully as version 0.52.2 (183), and all 247 tests passed (213 walletkit, 33 EVM and one navigation-serialization test), with zero failures, errors or skipped tests. Translation coverage passed for 1,811 strings across nine locales, and patch whitespace checks passed. Existing Room/compiler and Gradle warnings remain.
- Local, uncommitted work only; pending public release notes and TODO were updated. No phone is connected, so no installation, native visual check or funded transaction was performed. Live maximum-send/provider behavior, RPC/Tor end-to-end checks, backend fee beneficiaries and native-speaker translations remain unverified. No release build, commit or push was performed. Timestamp is the recording time.


---

# Unreleased - 2026-10-28T05:00:12-04:00

- Enlarged Continue Scanning/Stop and Manual Check from compact controls to the app's standard 56dp buttons and headline text. Stacked them at full width so their labels have more room across languages and font sizes.
- Added token names, full token contract addresses, known spender names and full spender contract addresses to Review Revocations and approval details. Reused the existing saved EVM address-label database, queried off the UI thread, with Unknown for absent or unreadable labels. No additional network lookup, approval indexer or dependency was added. Help clarifies that names can differ across networks and are not proof of safety.
- Approval details show infinity for the standard maximum ERC-20 allowance, the exact decimal-scaled allowance for limited approvals, and a compact token amount using the app's existing number formatter. Decimal conversion uses bounded integer arithmetic without rounding; unknown decimals keep the raw allowance and an explicit unavailable-conversion message instead of guessing. Existing NFT permission types and token IDs remain visible, and signing continues to use the original permission target rather than display metadata.
- Moved token selectors to the right of both swap rows and amounts/fiat values to the left, including empty-input placeholders. The shared input's defaults retain the existing Send and CrossPay layouts. Bottom percentage controls and the provider-name/icon selector remain in place.
- Changed the long-press wallet switcher's selected indicator to the same circled checkmark component used by Manage Wallets, retaining accessible single-selection semantics, row tapping and the existing fixed-search/scroll behavior.
- Validation: all 239 tests passed (205 walletkit, 33 EVM and one navigation-serialization test), with zero failures, errors or skipped tests. Coverage includes exact allowance scaling, malformed/overflow values, unknown decimals and metadata-independent review identity. The final Base development APK built successfully as version 0.52.2 (183); translation coverage passed for 1,810 strings across nine locales, and patch whitespace checks passed. An initial test compile used an unavailable JSON helper; removed that test-only dependency assumption and reused the existing serialization checks. Existing compiler/Room and Gradle warnings remain.
- Local, uncommitted changes only. Updated the pending public release notes. No release build, installation, commit or push was performed. No phone is connected; native visual review and live RPC/Tor/funded revocation checks remain untested, and translations have not had native-speaker review. Timestamp is the recording time.


---

# Unreleased - 2026-10-27T03:39:23-04:00

- Added Revoke Access beside Manage Tokens in the Balances header, including when no tokens are enabled, while retaining the Security Center entry. Grouped approvals by token contract and reused balance-list token icons and names, cached NFT metadata and an unknown-token fallback. Optional visible-row metadata reads use the existing configured RPC only, with bounded, validated display strings and session caching; metadata does not authenticate a contract.
- Added long-press multi-selection, Select All and Clear Selection. Selected approvals use the existing confirmation, network-fee and PIN flow through separate reviewed transactions, with wallet/network/watch-only guards. Each transaction requires its own review and network fee; no atomic batch or account delegation was introduced. Submitted transactions remain distinct from confirmed revocations.
- Simplified the approval screen with fixed scan/manual-check controls, a bottom selection action, themed network and approval-type menus, contract details with full selectable addresses, and an Info page for explanatory text. Kept incomplete-history, failed-check and account-change warnings visible where relevant. Adjusted keyboard insets and cancel behavior for the scan/metadata jobs.
- Validation: all 238 tests in the walletkit, EVM and navigation-serialization suites passed with zero failures, errors or skipped tests. The final Base development APK built successfully as version 0.52.2 (183). Translation coverage passed for 1,801 strings across nine locales, and patch whitespace checks passed. Existing Gradle/Kotlin and native-library warnings remain.
- Local, uncommitted changes only. No release build, device installation, native visual review, live RPC/Tor end-to-end check, funded transaction, commit or push was performed for this batch. No phone is connected, and the previously recorded emulator startup failure remains unresolved. Historical scan coverage depends on the configured RPC provider, and translations have not had native-speaker review. Timestamp is the recording time.


---

# Unreleased - 2026-10-27T02:38:06-04:00

- Moved swap percentage shortcuts below the main action button, inside the existing keyboard-inset footer, so they stay at the bottom and above the keyboard. Kept fixed/custom percentages and the existing native-fee guard; Send and CrossPay's shared balance-row behavior is unchanged.
- Replaced the swap header's Routes label with the selected provider's 32dp icon, name and dropdown. The icon/name area opens provider selection, the exchange rate remains separately tappable on the right, and long names use ellipsis within a bounded column. Removed the duplicate Provider row and its obsolete debug-only visibility toggle.
- Validation: the Base development APK built successfully and all three focused percentage regression tests passed. Patch whitespace checks passed. No phone is connected and the emulator failure recorded below remains unresolved, so visual/device validation was not performed. No release build, installation, commit or push was performed for this local, uncommitted UI change. Timestamp is the recording time.


---

# Unreleased - 2026-10-27T02:00:21-04:00

- Investigated a direct Across integration at the user's request. Across's current [credential documentation](https://docs.across.to/introduction/api-keys) requires a production API key and integrator ID; upstream Unstoppable 0.52.2 has no direct Across provider or credentials to reuse. Public legacy route/fee probes returned HTTP 200, but that does not establish production access or remove the documented requirement. The user then chose to skip the integration. No Across implementation or configuration was added; the unfinished optional adapter is unchanged. Recorded the deferral in `TODO.md` without marking it complete.
- Attempted the requested emulator testing using a fresh isolated API 36 AVD. This bypassed the earlier instance-limit error, but hardware-accelerated boot stalled before adb became available. One software fallback crashed in emulator 37.1.11's `qemu-system-x86_64-headless.exe` with access violation `0xC0000005`, before APK installation. No app screen, live quote, signature or transaction was exercised. The personal AVD was not changed, and no emulator process remains running.
- Validation: checked the upstream release's provider sources, current official Across documentation, public quote responses and emulator crash evidence. Updated documentation only; the previously validated 0.52.2 (183) app code/APKs remain unchanged. No new app build, commit or push was performed. Timestamp is the recording time for this local, uncommitted work.


---

# Unreleased - 2026-10-27T01:48:47-04:00

- Imported upstream release `0.52.2` (`4b0488cf42b66ca514c5ae5c30267df46bdae63a`) into the local OpenSwap integration branch. This brings XRP with trust-line assets, Arc/native USDC, CrossPay, unified sending, Zcash Names and Slipstream synchronization, watch-only THORChain/MayaChain, improved operation history, Tor routing/recovery, signing and swap-recipient checks, WalletConnect session cleanup, private-key backup encoding fixes and upstream minified-build fixes. Version metadata is now `0.52.2 (183)`.
- Preserved OpenSwap branding/signing, restored V2 swap providers and fee settings, Relay/Across entries and privacy preferences, provider icons, fixed wallet-switch search, manually added token persistence/discovery, shared EVM contacts, NGN handling, font choices/scaling, splash selection and custom swap percentages. The shared upstream amount input replaces the old swap-only layout; 100% stays visible while retaining upstream's native-fee guard. Across remains an incomplete optional backend adapter, not a newly completed direct integration.
- Switched to upstream's Arc-capable Ethereum kit `a887150`; retained fee-on-transfer V2 encoding and history decoding through a small wallet-side adapter. It rejects exact-output/zero-minimum calls and preserves the SDK-built router, value, amount, path, recipient and deadline. The earlier fork-only EIP-7702 SDK extension had no wallet callers and is no longer a dependency; this merge does not add EIP-7702 signing support to the app. Historical custom-kit references above were corrected to the dependencies now used.
- Adapted Relay and Revoke Access to the connected Tor SOCKS endpoint, refusing requests when unavailable and reading the current port after restarts. Approval scans share their connection pool instead of creating a separate pool for every query. The old fixed HTTP proxy no longer exists upstream. Added regression tests for proxy readiness/ports, taxed-token calldata/decoding and percentage input. Preserved the approval feature in local checkpoint `94bf54eb6` before the merge; the October 26 entries below describe its original pre-commit state.
- Completed localization coverage for 14 existing fork appearance/manual-token strings and 45 Revoke Access strings in all nine supported locales. Moved the unchanged English approval strings into the main strings resource so upstream's translation check covers them. Updated public release notes and dependency documentation while preserving previous release notes and changelog entries.
- Broader app tests exposed a Windows CRLF-versus-LF snapshot mismatch and legacy upstream number-format assertions that did not match the existing OpenSwap precision policy. Normalized fixture line endings in the assertion and updated the stale expectations; production formatting and chain behavior were not changed for those tests.
- Validation: all 411 unit tests passed (309 across nine wallet/chain modules and 102 app tests), with zero failures, errors or skipped tests. Both `:app:assembleBaseDebug` and `:app:assembleBaseRelease` succeeded, including release minification, resource shrinking and release lint. The final development APK is `money.openswap.wallet.dev`, `0.52.2 (183)`; the release APK retains the documented OpenSwap signing certificate. The translation check passed for 1,788 strings across nine locales, and patch whitespace checks passed. Existing Gradle/Kotlin, Room-index and native-library warnings remain. This validation record is prepared for the local merge commit and fast-forward to `master`; it is not a published release.
- No phone is connected. Device rendering, live RPC/Tor reconnection, external bridge availability, upgrades with real wallet data and funded transaction flows have not been tested. Translations have not had native-speaker review. No installation, remote push, tag or release publication was performed. Timestamp is the recording time for local integration work, not a release time.


---

# Unreleased - 2026-10-26T22:55:18-04:00

- Fixed `SwapProviderIdMappingTest` by supplementing the captured upstream provider fixture with explicitly synthetic Across and Relay metadata. The upstream snapshot is preserved, and Relay is checked using its actual direct-provider ID, `RELAY`, rather than `u_RELAY`.
- Added regression coverage confirming that suspension metadata for both fork-specific bridges applies to their runtime provider IDs without suspending unrelated providers. Production provider behavior is unchanged; this resolves the test failure recorded in the preceding entry.
- Validation: reproduced the original failure, then passed all 137 walletkit and 11 EVM unit tests with zero failures, errors or skipped tests. Test-only change; no APK rebuild, device installation or live bridge swap performed. Synthetic metadata does not establish live backend availability. Recording time for local, uncommitted work; nothing from this batch was published.


---

# Unreleased - 2026-10-26T22:45:00-04:00

- Added a Security Center entry for Revoke Access covering standard ERC-20 allowances, ERC-721 single-NFT approvals and ERC-721/ERC-1155 collection operators. The user selected EVM-only support and existing network providers only; no third-party approval indexer was added.
- Scanning uses owner-filtered RPC logs and live contract reads, with a manual contract/spender check, explicit incomplete-scan and failed-check states, wallet-change guards, watch-only read access and session-only scan progress. Discovery and permission re-check RPC queries use Tor when enabled, wait for its connection, omit request/body logging and reject redirects. Bounded range retries preserve both older progress and newer blocks after interruptions or RPC limits.
- Revoke calls are constructed locally with zero native-currency value and only the standard zero-allowance/zero-address/disabled-operator calldata. Confirmation rechecks the permission, uses the existing network-fee and signing services, requests the configured PIN and distinguishes submission from confirmation. Non-standard or malicious contracts can behave differently; the review screen warns users to verify contract addresses.
- Confirmation and fee-setting state is isolated per wallet, network and permission, including restoration; the manual form uses the existing keyboard-inset pattern and permits copying full addresses for comparison.
- Validation: all 19 approval tests passed (ABI construction, event parsing, bounded scan continuation/cancellation and confirmation-state isolation), and `:app:assembleBaseDebug` succeeded in the renamed `C:\dev\openswap` repository. A stale dex cache still referenced the old folder; cleaned only the generated app/EVM module outputs and rebuilt without build-cache reuse. The artifact is `money.openswap.wallet.dev`, version `0.51.2 (182)`; version metadata and published releases were not changed. Existing Gradle, native-library and unrelated resource warnings remain.
- Broader validation found an existing `SwapProviderIdMappingTest` failure: the committed provider fixture does not include `u_ACROSS` and `u_RELAY`. The unrelated provider code/fixture was not changed or the test suppressed. The approval tests and development APK build pass independently of that failure.
- The Android emulator failed during startup and no phone was connected, so native visual, live-RPC end-to-end and real-device checks remain incomplete. No funded transaction was signed, no device installation was performed, and nothing from this batch was committed or published.
- Limitations: historical RPC range limits can make discovery slow or incomplete; a scan is not proof that a wallet is safe. This does not invalidate every off-chain permit, smart-account delegation or WalletConnect session, and cannot recover lost funds. Only standard EVM approvals are in scope, not TRON or other chain-specific permissions.
- Timestamp is the final recording time for local, uncommitted work begun on October 24 and first recorded on October 26 at `22:18:52-04:00`; earlier edit timestamps are not reconstructed.


---

# Release notes updated - 2026-09-30T08:54:00-04:00

- Updated the existing `0.51.2` public release notes' appearance section to include Source Sans 3 and Public Sans and identify Default as Manrope. Kept the other release notes and historical changelog entries unchanged.
- Recording time for local publication preparation. The new fonts and notes are being committed for GitHub publication; build and test results are recorded below. Validation: patch whitespace check passed. No new build or device installation needed for this documentation-only update.


---

# Unreleased - 2026-09-30T08:51:13-04:00

- Added Source Sans 3 and Public Sans to Appearance > App Font, each with bundled Regular (400), Medium (500), SemiBold (600) and Bold (700) static TTFs. They use the existing theme, persistence and font-size controls; existing font choices and saved keys are unchanged. Fonts work offline without external font requests.
- Bundled unmodified fonts and redistribution licenses from pinned official upstream commits: `adobe-fonts/source-sans@87b37a2daaed80fcb8e8ccb0085c4d72ddade12e` and `uswds/public-sans@62058987ce57f64e39a30adc8a512998a3110c70`. Preserved full character sets rather than introducing language subsets.
- Size measurement: eight TTFs total 2,048,592 bytes uncompressed; packaged fonts and licenses occupy 982,347 compressed bytes (about 0.98 MB, 0.7% of the development APK, excluding ZIP entry overhead). The whole Base development APK changed from 140,996,132 to 140,997,882 bytes, a net increase of 1,750 bytes; other generated content offset most of the added font assets, so this net difference should not be treated as their standalone overhead. Release/store download and installed size were not measured.
- Validation: all eight focused `AppFontTest` tests passed; `:app:assembleBaseDebug` succeeded; checked TTF headers, embedded weight metadata, packaged resources/licenses and patch whitespace. Existing Gradle deprecation and Room index warnings remain. The mobile audit script matched no Kotlin files and provides no coverage for this change. No device font-rendering review, installation or GitHub push performed in this batch.
- Timestamp is the recording time for local, uncommitted work.


---

# Unreleased - 2026-09-30T08:22:08-04:00

- Renamed the Default app-font option to `Default (Manrope)` so users can identify the bundled typeface. The selected-value label and font picker share this resource; font rendering and saved preferences are unchanged.
- Recording time for this local, uncommitted change. Validation: confirmed the shared resource mapping and checked patch whitespace. No build or device installation performed for this text-only edit.
- Update recorded at `2026-09-30T08:29:30-04:00`: the Base development APK built successfully, retaining existing Gradle deprecation warnings. This change is being folded into the previous release commit with its original message; earlier changelog entries and public release notes are preserved. No device installation performed in this batch.


---

# Release notes prepared - 2026-09-30T08:04:52-04:00

- Added the approved public notes for version `0.51.2` in `RELEASE_NOTES.md`, covering user-facing improvements and the remaining Across limitation.
- Removed em dashes from the public notes and kept development validation details in this changelog.
- Recording time for local release preparation; commit and publication are pending. Validation: staged patch whitespace check passed; the application build and device checks are recorded below.


---

# Unreleased — 2026-09-30T07:50:50-04:00

Switch Wallet scrolling fix prepared for the next release.

- Replaced the Switch Wallet sheet's regular scrolling column with the same lazy-list and fixed bottom-search overlay structure used by Manage Wallets. The search control no longer participates in scroll layout or shifts while the wallet list is moving.
- Added a list-boundary nested-scroll guard so high-velocity repeated flings are consumed by the wallet list instead of being handed to the draggable modal sheet and making the sheet jerk.
- Kept the original Switch Wallet selection behavior and dismisses search focus when list scrolling begins.
- Validation: the Base development APK compiled, packaged and installed successfully on the connected Samsung SM-S928U. On-device review confirmed stable scrolling, including repeated high-velocity upward swipes.


---

# Unreleased — 2026-09-30T07:09:00-04:00

Release metadata and font-size controls prepared for the next release.

- Changed the Appearance font-size stepper from 5% jumps to 1% increments while preserving the existing 85%–140% range and 110% reset value.
- Made the percentage field directly editable with a numeric keyboard, live updates for valid values, and safe clamping when editing ends.
- Added a fixed bottom search field to the long-press **Switch Wallet** sheet while preserving that sheet's original wallet-switching behavior.
- Increased the app version from `0.51.0 (180)` to `0.51.2 (182)` because `0.51.1` is already present in the GitHub repository.
- Passed the focused font-size persistence, migration and bounds tests, assembled the Base development APK, and installed `money.openswap.wallet.dev` successfully on the connected Samsung SM-S928U. The build retained existing Android/Gradle deprecation and Room index warnings; no release build or store artifact was produced.


---

# Unreleased — 2026-09-29T23:33:34-04:00

Changes prepared for the next release.

## Wallet switching and appearance

- Made the search field in the long-press **Switch Wallet** sheet a fixed bottom control, matching Coin/Wallet Manager behavior; only the wallet list now scrolls behind it.
- Replaced the three-option font-size picker with a compact minus/editable-percentage/plus stepper using 1% increments from 85% to 140%, plus a reset button that restores the previous 110% default. Users may type a percentage directly; incomplete input is retained while editing and out-of-range values are clamped when editing finishes. Existing Small/Default/Large preferences migrate without changing their effective scale.
- Enlarged the Relay provider artwork within the same 32dp provider-icon container so its visible mark is consistent with other route icons.

## Relay risk correction

- Corrected Relay's displayed risk level from **Excellent** to **Fair**. The original value was assigned manually by analogy with other non-custodial bridge providers, not by a formal scoring engine. Relay's current published API behavior and terms permit sanctions/risk screening, blocked routes or wallets, delayed/frozen funds and non-guaranteed recovery, which conflicts with this app's **Excellent** description of no route checks/freezes and automatic refunds.

## Validation

- Passed the wallet and Base development Kotlin compilation, focused font-size persistence/migration/bounds tests, and `:app:assembleBaseDebug`.
- Installed the updated `money.openswap.wallet.dev` APK successfully on the connected Samsung SM-S928U (`R5CX105K2CV`); no release APK was installed or replaced.


---

# Unreleased — 2026-09-29T23:08:17-04:00

Local validation performed for the next release.

- Built the `baseDebug` development variant and verified its application ID as `money.openswap.wallet.dev`.
- Installed that development APK successfully on the newly connected Samsung SM-S928U (`R5CX105K2CV`). No release APK was installed or replaced.


---

# Unreleased — 2026-09-29T23:06:15-04:00

Documentation prepared for the next release.

- Added a root `TODO.md` implementation backlog for Uniswap v4 and PancakeSwap Infinity, including routing architecture, hook and dynamic-fee restrictions, exact approvals and Permit2 safeguards, calldata validation, simulation, Tor fail-closed privacy, UI expectations and release criteria.
- Recorded that existing v2/v3 integrations must remain available and that no Uniswap v5 or PancakeSwap v5 provider should be created without an official production protocol.
- Documentation only; no swap-provider behavior changed in this batch.


---

# Unreleased — 2026-09-29T22:45:40-04:00

Changes prepared for the next release.

## Relay bridge and security

- Replaced Relay's backend-only registration with a direct Relay Quote API v2 provider for supported EVM-to-EVM routes. The provider builds approval and deposit transactions through the wallet's existing EVM approval/signing flow and tracks bridge completion through Relay's status API.
- Added defensive validation for every remote Relay transaction: expected origin chain and sender, valid EVM target and calldata, exact native deposit value or zero ERC-20 call value, decoded standard ERC-20 approval token/spender, and a hard rejection of any non-zero application fee. The client deliberately omits `appFees`, referrer and integrator-fee fields and never sends keys or signing material.
- Live unsigned production quotes succeeded for Base ETH to Optimism ETH and Arbitrum USDC to Base USDC. Both returned ready deposit transactions, the ERC-20 route returned the expected approval step, and Relay reported a zero application fee. No transaction was signed or submitted.
- Across remains registered through the existing backend adapter but is not marked complete: Across's current production Swap API requires a Bearer API key and two-byte integrator ID, neither of which is configured in this repository. Embedding an API secret in the Android client would be unsafe; a direct Across route needs a protected backend credential or another approved architecture.
- Relay now explicitly honors the wallet's existing Tor option. When Tor is enabled and connected, Relay quote and status traffic uses the local Tor HTTP proxy, so Relay sees a Tor exit address instead of the device's direct IP; while Tor is connecting or unavailable, Relay fails closed without a direct-network fallback. Even over Tor, Relay necessarily receives the public wallet address, selected chains/tokens and amount. HTTP logging remains BASIC (method/URL/status only), so request bodies containing wallet and quote data are not logged by the app.

## Wallet and appearance corrections

- Restored the bottom Wallet-tab long press to the original **Switch Wallet** bottom sheet and added the requested search bar inside that sheet. It no longer opens **Manage Wallets**.
- Renamed **APP FONT** to **App Font** and added a reversible persisted **Font Size** selector with Small, Default and Large choices; Default preserves the prior 110% app scale.
- Changed the swap token picker's action to **+ Add token manually**, placed it at the bottom of search results and reused the same full-width primary button component and typography as the swap page's **Enter Amount** button.

## Validation and limitations

- Passed `:walletkit:compileDebugKotlin`, the focused font preference tests and `:app:compileBaseDebugKotlin`; the Base debug build was installed successfully on the connected Samsung SM-A716U1 running Android 13 after the Relay/Tor changes.
- Across cannot return direct routes until production credentials are supplied through a secure mechanism. Relay's API is a third-party trust boundary; the wallet validates the transaction envelope and approval target, but users should still review the confirmation screen and destination before signing.


---

# Unreleased — 2026-09-29T21:59:14-04:00

Changes prepared for the next release.

## Token and wallet selection

- Added contract-address discovery to Coin Manager's bottom search. When the normal catalog has no match, the app searches supported custom-token networks, shows the discovered token and lets the user enable it in place; the existing manual Add Token flow remains available.
- Made the long-press wallet shortcut on the selected bottom Wallet tab open the same searchable wallet switcher used by the top-right wallet button.
- Tightened the swap picker's **Add token manually** control to 12 dp horizontal padding and increased its label to 18 sp while retaining a 48 dp minimum touch target.

## Bridge route test and correction

- Live-tested canonical EVM bridge pairs on the connected device: Ethereum USDC to Base USDC and Ethereum ETH to Base ETH. The configured backend returned Near, LI.FI, Circle CCTP and THORChain routes, but did not return Across or Relay.
- This corrects the earlier entry that described the Across and Relay client registration as a completed integration. Their provider metadata, artwork and uSwap adapters are present locally, but usable integration remains incomplete until the configured backend advertises `ACROSS` and `RELAY`, or dedicated direct provider clients are implemented. No bridge transaction was submitted.

## Validation and limitations

- Passed `:walletkit:compileDebugKotlin` and the focused swap-provider, provider-icon and percentage-selector unit tests.
- Automatic contract discovery requires network access to the relevant chain services. Across and Relay remain unavailable in live route selection with the current backend configuration.


---

# Unreleased — 2026-09-29T21:09:31-04:00

Changes prepared for the next release.

## Token discovery and custom-token catalog

- Added cross-network contract discovery to the swap token picker. When a non-native token is not already enabled, the picker checks the existing custom-token services for supported EVM networks, Solana and the other custom-token-capable chains, presents each matching network, and lets the user toggle the token on without leaving the swap flow.
- Kept the existing manual Add Token flow available as the fallback when automatic discovery returns no match.
- Added an app-wide, offline custom-token catalog. A manually added custom token's query, name, symbol, decimals and image reference are persisted independently of the active wallet, reconstructed locally, and merged into Coin Manager after an account switch. Enabling remains account-specific.
- Refresh the swap selector from wallet updates so tokens enabled automatically or through the manual screen become available immediately on return.

## Appearance

- Added **Default**, **System** and **Pretendard** font choices under Appearance. Default keeps the existing Manrope typography; System uses Android's default family; Pretendard uses the supplied Pretendard Std regular, medium, semibold and bold assets.
- Centralized the selection at the root Compose theme and persisted only a stable font key. The feature can be reversed without touching individual text components by removing the font enum/resources and the single theme parameter. Included the Pretendard license alongside the bundled assets.

## Validation and limitations

- Passed the focused font, contact, swap-percentage and provider-icon unit tests, `:app:compileBaseDebugKotlin`, and Android resource processing.
- Built and installed `app-base-debug.apk` successfully on the connected Samsung SM-A716U1 running Android 13.
- Initial contract discovery requires network access to the relevant chain services; tokens already saved in the custom-token catalog remain available offline. Clearing the app's data also clears this local catalog and the font preference.


---

# Unreleased — 2026-09-29T19:56:50-04:00

Changes prepared for the next release.

## Swap and bridge providers

- Audited Jupiter's quote and commit requests and confirmed they carry no app BPS, platform-fee, referral or fee-account fields. Removed the unused `SWAP_FEE_BPS` build/configuration hook so an application-level BPS fee cannot be reintroduced through that dead path.
- Added Jupiter's official provider artwork so its logo is shown in the route picker.
- Registered Across and Relay as cross-chain providers through the wallet's existing uSwap execution and UUID tracking contract, including provider metadata, supported-source filtering, same-chain Relay handling and official provider artwork.
- Kept the fixed `25% / 50% / 75% / 100%` amount selectors visible for every input token, including native assets that also pay network gas. Selecting the full native balance can still fail at confirmation when the network fee is not covered; the selector now remains available as requested.

## Appearance and contacts

- Removed the fixed splash drawable so Android derives the launch splash icon from the currently enabled launcher alias, matching the icon selected under Appearance.
- Made a saved EVM contact address available across every supported EVM chain. Exact per-chain entries still win when present; filtering, autocomplete, contact selection, transaction lookup, duplicate checks and replacement warnings all use the compatible EVM address otherwise.

## Validation and limitations

- Passed the focused contact, Jupiter-icon and percentage-option unit tests, `:app:compileBaseDebugKotlin`, `:app:processBaseDebugResources`, and `git diff --check`.
- Across and Relay client integration is complete, but live route availability was not exercised with real funds. It depends on the configured uSwap backend advertising the `ACROSS` and `RELAY` adapters for the selected asset pair.


---

# 0.50.2 — 2026-09-06 to 2026-09-08

`versionCode 178` · based on upstream `0.50.1`

## Swap providers

**Restored Uniswap V2, PancakeSwap V2 and QuickSwap** — 2026-08-25 · `c69f59d0a`

Upstream commented these out of the registry in `45d60a43b` and never deleted the classes; `acfd98cb3` restored only the V3 variants. Re-added to `EvmChainPlugin.swapProviders()`. Tax and meme tokens overwhelmingly have liquidity in V2 pairs rather than V3 pools.

**Fixed fee-on-transfer (tax) token swaps** — 2026-08-25 · kit `11b92fc`

Selling a taxed token failed with `PancakeSwap: K` / `UniswapV2: K`. The pair contract receives less than the nominal `amountIn`, so the constant-product check fails. Root cause was in `ethereum-kit`, not the wallet: `uniswapkit` had never implemented the encoding side of the router's `...SupportingFeeOnTransferTokens` functions.

Added three method encoders — `0x5c11d795`, `0xb6f9de95`, `0x791ac947` — each subclassing its plain counterpart so `SwapTransactionDecorator`'s `is` checks still decorate these transactions. Also fixed three existing decoder factories that derived `methodId` from the *plain* variant's signature, so they could never match a real fee-on-transfer transaction and silently overwrote the plain factories in the registry map. Verified against canonical selectors and hand-computed calldata.

**Provider icons restored** — 2026-09-06 · `acb90644d`

Upstream's `82f4fbc0d` ("Remove provider names in Swap") deleted the `icon` field, every override and 18 drawables. Restored across 18 providers, including `UProvider`'s sub-providers. Drawables recovered from upstream tag `0.48.5` and commit `82f4fbc0d^`.

**Two-row route picker** — 2026-09-06 · `acb90644d`

Provider logo, name and amount on row one; clock, time, risk badge and fiat value on row two. Restored from `82f4fbc0d^`, the last commit before the redesign.

**Provider names shown by default** — 2026-08-25 · `5e2444c67`

`showSwapProviderName` defaulted to `false` and every display site was additionally gated behind `BuildConfig.DEBUG`. Both removed.

## Fees and privacy

**All integrator fees removed** — 2026-09-06 · `acb90644d`

`SWAP_FEE_BPS` was **100 bps (1%) in release builds**, 25 in debug. Now 0. Three providers consumed it:

- **THORChain** and **Maya** — `affiliate` and `affiliateBps` set to `null`. This mattered beyond the fee: these providers return a memo built from those values, and the memo is broadcast on-chain in `OP_RETURN`, transaction data or a shielded memo field. Every swap was permanently tagged.
- **1inch** — `referrer` and `fee` parameters dropped entirely rather than zeroed. Per 1inch's own troubleshooting docs, setting them makes fee-on-transfer swaps *always fail*, so this also fixed tax tokens on that provider.

Audited every other provider: Uniswap, PancakeSwap V2/V3, QuickSwap, AllBridge, the uSwap sub-providers, Stellar and Solana never took a cut.

## Currency

**NGN (Nigerian Naira)** — 2026-09-07 · `6e2442103` · kit `966a222`

The Horizontal Systems backend whitelists currencies server-side and does not serve NGN, so requesting it returned nothing. Prices are now fetched in USD and converted with a single FX rate from `open.er-api.com`.

Converted in `CoinPriceSchedulerProvider`, the only path into `CoinPriceManager.handleUpdated` — so the Room cache, every observable and all 27 price call sites get NGN with no other changes.

**Not CoinGecko directly**, despite coins already carrying a `coinGeckoId`. A `/simple/price` call sends the list of coins the user holds plus their IP to a third party, fingerprinting their portfolio and building a durable profile over repeated refreshes. Routing crypto prices through the HS backend is precisely what prevents that. The FX endpoint is asked one identical question by every user and reveals nothing about holdings.

Stale rates are served up to 24h rather than blanking; past that, prices are hidden rather than shown wrong.

**Nigeria flag drawable** — 2026-09-07 · `6e2442103`

Drawn from the official specification (`#008751`, equal thirds). The web repo's `ng.svg` is a stylised icon with angular notches, not the flag.

## Number formatting — 2026-09-08

**Subscript compression for small values.** `0.0000012345` renders as `0.0₅12345`. Applied in `NumberFormatter.formatRounded`, the single path every format method routes through.

Fixed 7-character budget after the decimal point, shared between the subscript and the digits — so a one-digit subscript gets 5 digits and a two-digit subscript gets 4, and compressed values line up in a column. Triggers above 2 leading zeros; `0.001234` is left alone.

**Trailing zeros kept only where real.** `0.0559803` → `0.055980` (the sixth digit is a genuine zero from truncation) but `0.5` → `0.5` (padding to `0.500000` would invent five zeros). Upstream stripped unconditionally, losing the first case.

**Uncompressed sub-1 values capped at 6 decimals.** Upstream's `zeros + maxFractionNonZeroDigits` gave 5 to 7 depending on the value.

**Values ≥ 1 show at least 2 decimals**, as currency conventionally does: `1.00`, `20.00`, `999.50`. Real precision beyond 2 is preserved.

**Abbreviated values fixed at 2 decimals** — `1.72 T`, `304.70 B`, `250.00 M`. Upstream used 2/1/0 decimals by magnitude and stripped trailing zeros, giving ragged widths.

**Scientific notation above quadrillion** — `1.23E18`. The named scale stops at Q, and nothing beyond has a suffix anyone would recognise. Reachable via token balances, not fiat values.

## Interface

**Six launcher icons** — 2026-09-06 · `acb90644d`

BTC, BCH, ETH, BNB, Polygon and THORChain, as adaptive icon vectors. `minSdk 28` means no PNG fallbacks are needed — four XML files each. ETH and THORChain use real gradients matching their source SVGs. THORChain's symbol is black, per the app's own `swap_provider_thorchain.xml` rather than the web repo's `networks/thor.svg`, which says white.

**THORChain set as the default icon** — 2026-09-07

Manifest aliases and `AppIconService` fallbacks both changed; a mismatch between them fails silently.

**Plflag and Sinwar removed from the picker** — 2026-09-06

Marked `isDeprecated` rather than deleted, so the manifest aliases still resolve for anyone with one active. `setAppIcon` migrates them to the default. Deleting the aliases outright would make the launcher icon vanish until reinstall.

**`IconSizes`: central icon size constants** — 2026-09-06, expanded 2026-09-08

Split by context — `Token`, `MarketRow`, `TransactionRow`, `TransactionRowContainer`, `TransactionRowPaired`, `TransactionInfo`, `CoinInfoRow`, `Provider`, `Compact`, `Badge`. Upstream hardcoded these at every call site.

**Token icons 32 → 48dp** across balance, swap, market lists, transaction rows, transaction info and confirmations. The transaction row's container grew 42 → 58dp, since a fixed container clips larger icons.

**Text 10% larger.** `ComposeAppTheme` pins `fontScale`, which is why the app ignores the system font size setting. Raised from `1f` to `1.1f`.

**`TransactionCell` fixed height → `heightIn`.** A fixed `72.dp` doesn't grow with the font scale, so text was clipped by the row divider.

**Secondary text contrast raised.** `andy` was `Steel` (#B3B3B3) on white and `Smoke` (#4B4B4B) on near-black, both near the legibility threshold. Now `GreyDark` (#63636A) and `Steel` respectively.

**Swap page: amount left, token selector right** — matching upstream `12f7f3843`, before the redesign. Included removing a `CenterEnd` Box that pinned the placeholder "0" to the right.

**Fee row defaults to fiat and persists the choice.** Upstream used `remember(valueFiat)` with a `false` initial value, so it started on the token and reset on every re-quote. Now backed by `ILocalStorage.feeDisplayInFiat`.

**Fixed: unverified tokens showed a blank icon.** The swap token selector and EIP-20 approve screen called `CoinImage`'s *coin* overload while holding a `Token`, discarding `token.iconPlaceholder` — the blockchain badge (BNB/BEP20, ETH/ERC20). Three call sites in `SelectSwapCoinDialogScreen`, two in `Eip20ApproveConfirmPage`.

## Build and distribution

**WalletConnect enabled in the F-Droid flavour** — 2026-08-25 · `5e2444c67`

Not gated by `FDROID_BUILD` as it appeared, but by module wiring: `:dapp-wallet-connect` was listed for `baseDebug`, `baseRelease` and `ci` only. Without it, `DAppServiceInitializer` never runs and the settings entry hides itself.

**Renamed to Open Swap, applicationId `money.openswap.wallet`** — 2026-08-25 · `9a4418218`, `09840a679`

The applicationId change matters for distribution: signing with a different key under upstream's package name means Android refuses to install alongside a real Unstoppable Wallet.

**Release signing config** — 2026-08-25 · `09840a679`

Reads `keystore.properties` locally, environment variables in CI. Both gitignored. Upstream's committed `test.keystore` has its password in `build.gradle.kts`, so anyone cloning the repo could sign an APK that Android installs as a silent update — unacceptable for public distribution.

**GitHub Actions release workflow** — 2026-08-28 · `9c8f3460a`, `259972863`, `18e79ca3b`

Builds, signs and attaches the APK on `v*` tags. Upstream's six inherited workflows were removed; they referenced secrets that don't exist here and failed on every push.

**Upstream sync `0.50.1`** — 2026-08-28 · `37575b974`

Five commits, all navigation and send-flow stability fixes.


---

# Outstanding

- **Release notes formatting.** `git tag -l --format='%(contents)'` isn't reaching the runner. `fetch-depth: 0` fetches history but may not fetch tag objects — try `git fetch --tags --force` before that step, or a committed `CHANGELOG.md` via `body_path`.
- **Tor and the FX request.** `RetrofitUtils.buildClient` constructs its own `OkHttpClient` rather than taking the app's shared one, so the NGN rate call may bypass `TorManager`. This matters: avoiding third-party exposure is the whole reason FX conversion was chosen over CoinGecko.
- **Two APK variants per release** — `openswap_wallet_fdroid_wc_v<N>.apk` and `openswap_wallet_fdroid_v<N>.apk`. Needs a design decision: a new product flavour, or a Gradle property with two CI runs. Both would share an applicationId, so users couldn't install both side by side.
- **Tax-aware swap quotes.** Quotes use plain constant-product math and ignore transfer taxes, so users must manually raise slippage above a token's sell tax or the swap reverts with `INSUFFICIENT_OUTPUT_AMOUNT`. Fix: simulate via `eth_call` to derive the real expected output. If a tax percentage is ever displayed, present it as a timestamped estimate — taxes vary by direction, size, wallet and time, and honeypots are built to pass simulation and fail on the real transaction.
- **Across and Relay live-route verification.** The client providers are now registered through the existing uSwap execution/tracking contract. Confirm the production backend advertises both provider IDs and exercise successful, failed and refunded routes before release.
- **Batched approve + swap (EIP-7702).** Foundation committed and verified in the ethereum-kit fork: `Authorization`, `AuthorizationSigner`, `RawTransaction.authorizationList`, type-`0x04` signing and encoding. Remaining: choose a delegate contract and confirm deployment per chain (BSC matters most), confirm EIP-7702 activation per chain, widen `SendTransactionData.Evm` to carry multiple calls, include the authorization list in gas estimation, sign the authorization after the nonce is fixed (the `+1` self-delegation rule), and surface the delegation in the UI. A wrapper contract is **not** viable: it makes `msg.sender` a contract, and many tax tokens revert on contract callers.

# Gotchas

- **`compile*` produces no APK.** `assembleFdroidDebug` is required before installing; `adb install` after a `compile` task silently installs the previous build. Cost us several rounds of "the change didn't work".
- **CRLF churn between WSL and PowerShell.** Editing one working tree from both shows thousands of phantom modifications. Fix: `git config core.autocrlf true` in the repo.
- **WSL git has its own identity** — `user.name` and `user.email` must be set separately from Windows git.
- **`base64` in CI needs whitespace stripped.** Secrets pick up stray whitespace and `base64 -d` mistranslates it *silently*, producing a corrupt keystore and a "Tag number over 30 is not supported" failure 13 minutes into the build. Use `tr -d '[:space:]'`, then verify with `keytool -list` so it fails fast.
- **`apksigner` glob** — `$ANDROID_HOME/build-tools/*/apksigner` expands to several paths on CI runners. Use `ls -d ... | sort -V | tail -1`.
- **Validate workflow YAML before pushing.** A 12-minute build is a slow way to find an indentation error. Notepad breaks YAML indentation on paste.
- **JitPack builds on first request.** Gradle often times out waiting. Check `https://jitpack.io/#<user>/<repo>/<hash>` and retry once it's green.
- **`swap.unstoppable/public/` is not authoritative for assets.** Its `networks/thor.svg` has the symbol white where the brand is black, and `flags/ng.svg` is a stylised icon rather than the flag. The app's own drawables are the better reference.
- **The Fee row is a toggle**, not a stacked display — tap the value to switch between coin and fiat.

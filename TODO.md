## Release 0.53.3 (190) preparation, 2026-11-02

- [x] Prepare version/build 0.53.3 (190), the requested Together → Original → Scanner → Tom → Jerry rotation and the release-note draft. Preserve the 48dp home icons and all prior native routing/media work.
- [x] Pass the updated animation regression, all 64 focused native tests and automated F-Droid publication/version/signature guard checks. Confirm the GitHub workflow/signing configuration is available and the new tag is unused.
- [ ] Obtain the user's release-note/commit-message review before publishing; commit the relevant source, create a new annotated v0.53.3 tag with verbatim approved notes and push to trigger GitHub's signed F-Droid build.
- [ ] Monitor the automated release, then verify the attached APK's F-Droid flavor, version 0.53.3 (190), production certificate and published notes. Do not replace earlier release tags or manually upload the old local APK.

## Upstream UI carryovers reviewed, 2026-11-02

- [x] Restore home/Balances token icons to the shared 48dp size, retaining badges and other contexts. Both F-Droid 0.53.2 (189) APKs with 48dp home icons and checksum sidecars are verified in Downloads under distinct Pancake-Kotlin-48dp filenames. All 64 focused tests, release vital lint, signatures, metadata and exact media checks pass. No device installation or publication performed.
- [ ] User decision: swap-token icons also changed from 48dp to 40dp in upstream 0.52.2, and their labels changed from a 16sp base style to 20sp. These remain unchanged by the home-icon fix; do not silently revert additional UI.

## Kotlin-only PancakeSwap rewrite, 2026-11-02

- [x] Replace the new app JavaScript/WebView quote engine with Kotlin, retaining V2/V3/Stable/Infinity math, adaptive splitting, native contract verification and full fallback selection. Archive development-only JavaScript outside the repository; remove Node/JavaScript build requirements.
- [x] Pass exact pool/graph/ABI and captured five-EVM quote regressions. Preserve signing, Tor, anonymous previews, input/refund safeguards, scanner and direct-PNG Original. Require user approval before future app language/runtime changes.
- [x] Both Kotlin-only F-Droid 0.53.2 (189) APKs and checksum sidecars are verified in Downloads. All 64 focused native tests and release vital lint pass; retained signatures, native engine, removed JavaScript and all 18 media assets are verified. Earlier JavaScript APKs below are superseded local test artifacts. Source/release remain uncommitted and unpublished.
- [ ] Obtain device lifecycle, live Tor cancellation and funded EVM/Solana settlement feedback. Absolute different-time app/dapp price equality is not established. Do not push or install as part of this batch.

## PancakeSwap cross-chain quote parity audit, recorded 2026-11-02

- [x] Check the official public API/SDK options: keyless Unified API same-chain quote works; no supported complete cross-chain planner was found. Preserve the client-planner implementation requirement and document unconfirmed partner-only access.
- [x] Audit the supplied BSC-to-Ethereum quote discrepancy against current official dapp behavior, public quotes, wallet code and 23 focused passing tests. See docs/audits/2026-11-02-pancakeswap-crosschain.md. Confirm the same bridge infrastructure without claiming identical quote selection or an added developer fee.
- [x] Separate expected/protected plans, verify the gateway minimum semantics and bridge surplus scaling, preserve exact destination funding/refunds and the current signing/safety checks. Local implementation; both-swap calldata is covered by a real unsigned fixture.
- [x] Align the inspected dapp strategies: official primary single-hop/full SDK plus adaptive routing, active V2/V3/Stable/Infinity settings, and all allowed bridge candidates for the both-swap pattern. Correction: active Infinity StableSwap is disabled in the reference dapp; enabling it or comparing every pattern would not match that configuration.
- [x] Add expected/minimum and all-pattern native regressions, seven exact dapp-core vectors, all-active-pool directional checks and no-network packaged-asset replays across all five EVM chains. Existing Solana direction fixtures remain passing; previews retain placeholders under the wallet privacy policy.
- [x] Produce and verify the Development F-Droid 0.53.2 (189) APK with the sharper Original and unchanged scanner; 39 native and nine JavaScript tests pass.
- [x] Both final F-Droid 0.53.2 (189) APKs are verified in computer Downloads with checksums, retained certificates, exact assets and compact archives. The signed regular installer is 103,248,727 bytes; 39 native and nine JavaScript tests pass. This batch remains local/uncommitted; do not push yet.
- [ ] Obtain native Android lifecycle, Tor-cancellation and EVM/Solana funded-test feedback. Synchronized live dapp-versus-wallet complete settlements are not claimed by offline core vectors or unsigned quote tests.

## Direct-PNG Original source correction

- [x] Export both Original themes directly from the preserved PNG frames and update local app assets, stills, fixtures and provenance. Preserve the scanner, other cartoons and saved 60-fps archive.
- [x] All 21 focused tests and the F-Droid development build passed. Verified signing, version 0.53.2 (189), packaged media and unchanged scanner; saved the distinct Original-PNG development APK in computer Downloads.
- [ ] Review the new Original before/after player; GitHub publication and a regular installer refresh are not part of this local batch yet.

## Compact scan-video integration and release refresh

- [x] Integrate the four selected themed Baseline MP4 cartoons and reduced-motion stills; remove the large animated cartoon WebPs. Preserve the crypto scanner and archived 60-fps alternative.
- [x] Set Relay to Good consistently in both provider declarations and update the existing public release-note section.
- [x] All 21 focused checks and both F-Droid builds passed. Final signatures, 18 media resources, scanner preservation, compact ZIP layout and packaged size verified. Both version 0.53.2 (189) APKs are in computer Downloads; regular installer is 102,738,088 bytes.
- [x] Published source be069a375 and refreshed v0.53.2 (189) notes/APK after showing the commit message. The 102,738,088-byte public F-Droid APK matches the signed local checksum; old assets/tag metadata are backed up, the retag workflow was skipped and v0.51.1 is unchanged.
- [ ] Obtain native device playback feedback; unsupported decoder errors use the existing scanner fallback.

## Five scan animations and F-Droid builds, updated 2026-11-01

Historical preview milestones below are retained for reference. The current shipped MP4 state is recorded in the compact-video release section above.

- [x] Add the original ground-search animation as the fifth scan style using its smaller lossless WebP and restore the NEAR Confidential logo.
- [x] Require F-Droid distribution locally and in GitHub release checks.
- [x] Apply 1.4x timing to all three published cartoon loops, preserving all 325 frames and image bytes; save a playable preview. Both final F-Droid APK checks passed.
- [x] All 19 focused checks and both F-Droid installers pass; signatures, variant flags, six media resources and licenses verified. Animation media total 25,572,137 bytes. Version 0.53.2 (189) testing APKs and checksums are in computer Downloads; no installation or publication performed.
- [x] Save a separate 1.7x preview; the user selected this speed for the compressed version. FFmpeg quality-70 comparison copies retain all 325 frames and alpha, saving 1,867,046 bytes (8.24%). Current app APKs remain at 1.4x; compressed-asset review and integration are pending.
- [x] Show the FFmpeg commands before encoding and save stronger lossy quality-50 previews at 1.7x. All 325 frames and alpha retained; the three files total 19,317,938 bytes, saving 14.72%. Preview review/integration remain pending; no app build changed.
- [x] Prepare aggressive reduced-resolution/frame-rate trials for all four cartoons: smaller WebP 1,026,080 bytes, minimum WebP 471,618 bytes, or both-theme MP4 files 70,206 bytes. Save comparisons and preserve originals. These are preview media only; native playback and integration are pending.
- [x] Prepare higher-quality cartoon previews around the revised 3 MB budget: full-resolution 60-fps MP4 set plus unchanged scanner totals 2,501,235 bytes; 16-fps transparent WebP alternative plus scanner totals 3,084,777 bytes. Crypto scanner files verified unchanged against the current APK. Native playback/integration remain pending.
- [x] Archive the exact sharper 60-fps MP4 set in the named Downloads backup folder, with player and checksums. Prepare a separate Constrained Baseline Level 3.0 / 480-pixel / 30-fps comparison: 2,377,985 bytes including the unchanged scanner. Preview review and native playback/integration remain pending.
- [ ] Obtain personal feedback on the fifth animation and NEAR Confidential icon.

## Existing release refresh, updated 2026-10-31

- [x] Preserve the original Tom and Jerry ground-search preview as a separate unchanged developer-note alternative and offline snapshot/ZIP.
- [x] Published the completed wallet changes, refreshed the existing v0.53.2 release notes and replaced its APK with the verified signed 0.53.2 (189) installer. Previous APK/tag metadata is backed up; the public download checksum, release body and source tag are verified. Older releases remain unchanged.

## Four-animation scan rotation, updated 2026-10-31

- [x] Integrate the existing scanner, Tom and Jerry together, Tom alone and Jerry alone as a persistent round-robin sequence selected once per accepted scan. Offer four choices in either appearance with five local assets. Preserve approved motion, theme surfaces, offscreen pause and disabled-motion handling.
- [x] Build development and signed regular 0.53.2 (189) APKs with the rotation. All 16 focused approval tests pass; versions, signatures, ZIP integrity, five animation resources and licenses/provenance are verified. Both Animation-Rotation APKs are in phone Downloads with matching checksums. No installation or GitHub publication was performed.
- [ ] Receive personal feedback for the four-scan sequence, reopening, repeat scans with existing results, both appearances, cancellation and background/disabled-motion behavior. Do not infer installation or funded transaction testing.

## Swap display and approval fallback testing, updated 2026-10-31

- [x] Reduce unfocused main swap amounts to 10 characters and reveal the full amount inline on focus for both sides; remove the received-amount popup and keep output read-only.
- [x] Complete and verify both development and signed production APKs at 0.53.2 (189). All 389 affected tests pass; APKs, checksums and the production mapping are saved under the 2026-10-31 release-backup folder.
- [ ] Receive personal feedback for Blockrazor/custom-RPC scanning and inline amount expansion. No funded transaction testing is required for this handoff.
- [x] Prepare the final scanner animation: Faaiza's Searching animation with Ethereum/wallet symbols, outlined sample crypto addresses and matching light/dark variants. Retained 1.5x speed and the 2.4-second loop. Saved native JSON/dotLottie, themed GIF/PNG previews, source/font licenses, provenance and checksums. Earlier versions remain saved.
- [x] Integrate the final themed crypto-address Searching animation below Checking permissions while scanning with no results. Local light/dark vector assets follow the selected app appearance, retain the 2.4-second loop and outlined sample addresses, pause offscreen, and respect disabled system animations. Bundle animation and renderer licenses. Correct edge fades to the actual scanning surface. All 14 focused approval checks and development packaging pass.
- [ ] Receive personal device feedback for the integrated scanner in light/dark appearance, loading/results/no-results and cancellation. Development and signed regular 0.53.2 (189) APKs are in Downloads with verified transfer checksums for manual installation. Both builds passed; all 14 focused approval tests passed. No installation or signing test is inferred.

# Open Swap TODO

## Build 189 publication, updated 2026-10-29

- [x] Publish the completed scanner and swap formatting source and replace the existing v0.53.2 release with the exact signed 0.53.2 (189) APK at the user's request. Verify the public download, checksum, certificate and updated notes; preserve the old public APK/tag metadata locally. No release above build 189 was created.
- [ ] Receive personal device feedback on the latest main-swap layout, enlarged fonts, edit/cursor behavior and exact-value details. No new device test or funded revocation is inferred from publication.

## Main swap formatting, updated 2026-10-29

- [x] Implement and build the approved 14-character unfocused input/received displays with exact-value editing/details, natural provider-rate wrapping and existing upstream typography/colors/spacing. Keep version 0.53.2 (189); all 334 wallet tests pass and the signed APK is preserved locally.
- [ ] Obtain personal device feedback for the changed amount displays, edit/cursor behavior, complete received-amount details and provider-rate wrapping at the user's chosen font size. No current device was visible; no installation or transaction was performed.

## Approval flow and provider decisions, updated 2026-10-29

- [x] User confirmed main build 189 finds BSC approvals. Retain the working fresh-history/receipt fallback.
- [x] Match Revoke Access to the wallet's existing approval/signing route by removing its extra PIN-only wrapper; leave shared PIN/biometrics and ordinary approvals unchanged. Candidate build 190 completed signed packaging; user testing of signing remains pending. The route-layout candidate is version 0.53.2 (189), following the user's explicit request to match installed build 189 without increasing it further; signed packaging is complete.
- [x] User authorized selected-first approval RPC fallback on 2026-10-31. Implemented fallback across built-in/custom endpoints on missing, empty or failed reads with unchanged signing and Tor guards; packaging and device validation are recorded in the latest changelog. This supersedes the preceding single historical BSC reader.

## In-place rollback builds, planned 2026-10-29

- [ ] Define which older versions should be supported and check wallet database, account/token serialization, encrypted storage and chain-kit data compatibility before offering rollback.
- [ ] Consider separately signed rollback releases made from selected older code with the same application ID/certificate and a new increasing build number. Label them clearly, test preservation of disposable wallet data and keep the ordinary release-security settings.
- Android blocks the original lower-versionCode production APKs from replacing a newer installation. This is not an app setting; no universal downgrade capability is implemented. Never enable destructive downgrade migration or make production debuggable to bypass this restriction. See [Android versioning](https://developer.android.com/studio/publish/versioning).

## BSC history follow-up for 0.53.3 (189)

- [x] User confirmed the 0.53.2 development fresh-history scan works. Make Tor optional while preserving fail-closed routing when selected, and move privacy/history explanations under the details icon.
- [x] Pass all 373 supported wallet/EVM unit regressions and build/inspect the corrected signed production APK as 0.53.3 (189). The earlier development build 188 is already installed; no separate release-unit-test task exists.
- [x] Obtain the user's regular-app scan confirmation: the user confirmed main build 189 finds BSC approvals. Preserve the current public 0.53.2 release and show the commit message before any push.

## Fresh BSC approval discovery, development update 2026-10-29

- [x] Implement fresh address-history requests through the existing provider and Tor, independently of stored wallet transactions; validate receipt events and check current token/NFT permissions through the selected RPC.
- [x] Align development BSC history credentials with the already-shipped regular-app pool, add accurate history progress/limits, pass 369 affected tests and all locale checks, and build/install the development update. See [implementation and validation](dev_notes/2026-10-29-bsc-live-approval-history.md).
- [x] Receive the user's BSC scan feedback: main build 189 finds approvals. Observed Tor traffic and relayed-approval coverage remain unverified.
- [x] Confirm the corrected main scanner and publish after review. The user confirmed main build 189 works, then authorized replacing v0.53.2 in place with signed 0.53.2 (189), superseding the separate-0.53.3 publication plan. The public APK now contains the fresh-history scanner and receipt fallback.

## Broader token information coverage, planned 2026-10-29

Keep Unstoppable's hosted market service as the preferred source. The user does not want to operate a separate server. These are follow-up tasks, not features included in 0.53.2.

- [ ] Prepare a request for upstream to lower or configure the automatic new-token admission threshold. The reviewed markets-server source filters new imports below $5 million market capitalization and requires reported volume; it does not apply that threshold to every existing token on each information request.
- [ ] Collect exact network and contract/mint identifiers for missing tokens and prepare manual catalogue-addition requests. The upstream import tool supports explicit coin IDs, but only the hosted service's maintainers can change its catalogue.
- [ ] Investigate an upstream contribution for on-demand import by exact network and contract/mint identity, with source verification, bounded requests and caching. It would require upstream acceptance and deployment before Open Swap could use it through their service.
- [ ] Check catalogue refresh and recognition of saved custom tokens after an exact identity match becomes available. Retain the existing market page for recognized tokens and the basic Coin Info fallback otherwise; never match by symbol alone.
- [ ] Revisit a separately hosted fork only if the user changes the no-server preference. The [user's markets-server fork](https://github.com/abelokoj/markets-server) is a source copy and does not change Unstoppable's running service. Wallet-supplied market-cap values cannot bypass a server-side import filter.

Reference: [upstream markets-server](https://github.com/horizontalsystems/markets-server). No upstream request, issue, pull request or deployment has been made. Any new external data provider would require a separate privacy and availability decision.

## Version 0.53.2 wallet fixes

- [x] Improve BSC/EVM approval discovery through existing network providers and validated synchronized transaction receipts, with provider selection available from Revoke Access.
- [x] Fix large/unlimited approval formatting crashes and the recorded text-selection toolbar crash.
- [x] Fix Coin Info for unlisted tokens and show basic metadata, copyable token identity, explorer navigation and optional verified EVM/SPL total supply using the existing UI.
- [x] Pass 383 affected unit tests and translation coverage for 1,853 strings across nine additional locales; build/install the development APK. The user confirmed BSC discovery and the formerly crashing token information page work.
- [ ] Complete wider theme/text-size/RTL, live optional supply and old-history coverage checks. No funded transaction or revocation signing was performed for this batch.

## Upstream 0.53 integration, started 2026-10-28

- [x] Compared upstream 0.53.0 with the published Open Swap baseline and resolved the local merge on `codex/merge-upstream-0.53.0`.
- [x] Preserved current layouts, direct providers, Relay, PancakeSwap Cross-chain, configured ratings, bottom swap percentages, maximum controls, fiat cursor, wallet switching, fonts, token/contact features and Revoke Access.
- [x] Integrated the approved NEAR account flows, conditional Zcash shielding, receiving-token activation, hosted-swap v3 lifecycle, route-specific MEV eligibility, XSwap and ordinary-swap NEAR Confidential.
- [x] Repaired the NEAR review findings before publication: issuer/spam identity, genuine outgoing poisoning context, independently verified token balances, metadata failures, backup validation, account isolation and bounded discovery. See [NEAR decisions](docs/chain-integration/near/review.md).
- [x] Passed all 596 selected walletkit/chain/app tests, including 26 NEAR tests, and translation coverage for 1,849 strings across nine locales.
- [x] Built Base Debug and Base Release, including shrinking/vital lint, and verified 0.53.0 (185) plus the unchanged release certificate. Prepared the changelog and reviewed commit message for the authorized source merge/push. See [merge plan](dev_notes/UPSTREAM_0_53_MERGE_PLAN.md).
- [x] Installed/opened the development update in place on the connected test device and checked the Balances shortcut, retained swap layout, bottom percentages and an unsigned PancakeSwap Cross-chain ETH-to-BNB quote with the configured Excellent label. No approval, signature or send occurred.
- [x] The user reported that manual development testing is working and authorized building/publishing the APK on 2026-10-28. No individual funded or controlled-failure vector is inferred from that general report.
- [ ] Exercise the remaining NEAR watch-history, backup, account-switch, RPC failure/recovery and observed Tor vectors recorded in the integration documents. The added Pixel 5 test emulator was closed and removed at the user's request because it was slow; the original Pixel 10 Pro XL definition remains installed.
- [ ] Test disposable funded NEAR sends, registration/reserve boundaries, ambiguous submission, pending expiry and restart recovery. No funded transaction is authorized by the merge task.
- [ ] Compare NEAR derivation/backup/history with iOS through an authorized paired-platform handoff. No external developer message has been sent.
- [x] Published the separate v0.53.0 release and signed APK with the prepared formatted notes. The workflow passed; the downloaded public APK has the expected version, signing certificate, size and digest. Prior releases/tags remain intact.
## Deferred Across integration

- Across direct integration is deferred at the user's request on 2026-10-27 because Across requires a production API key and integrator ID. Upstream Unstoppable 0.52.2 provides neither a direct Across provider nor reusable Across credentials. Do not borrow another project's credentials.
- The existing unfinished optional backend adapter remains unchanged. Do not describe it as a completed direct Across integration.
- Revisit only if the user chooses to obtain production credentials or Across officially changes its integration requirements. Public legacy endpoint responses do not remove the documented production requirement.

## PancakeSwap cross-chain development, updated 2026-10-28

- The user chose existing wallet networks, including Solana; Linea is excluded. Direct Across remains deferred. PancakeSwap still depends on Across for EVM bridging and Relay for Solana routes.
- [x] Retained the existing Uniswap V3 and PancakeSwap V3 providers for ordinary swaps.
- [x] Extended PancakeSwap Cross-chain beyond bridge-token pairs with source/destination AMM trades across Ethereum, BNB Smart Chain, Base, Arbitrum and zkSync, plus EVM-to-Solana and Solana-to-EVM route handling. See [coverage, validation and privacy details](docs/PancakeSwap-CrossChain.md).
- [x] Added V2/V3/classic StableSwap/Infinity CL/Bin codecs and registered BNB Infinity StableSwap discovery, with canonical contract and on-chain router authorization checks. Unknown/custom hooks remain rejected, with a classic-route fallback where available. Standalone Infinity and Uniswap v4 providers remain planned below.
- [x] Kept initial quotes anonymous and routed requests through the existing Tor preference with fail-closed behavior. Final transaction preparation necessarily shares sender/recipient with the gateway; no new wallet fee, referral command or borrowed key is added.
- [x] Added exact EVM transaction rebuilding and Relay deposit validation, plus Solana instruction/lookup-table validation, configured-RPC simulation/submission, existing signer use and pending-payload preservation.
- [x] Captured unsigned public fixtures for ETH to BNB, BNB to ETH and Solana routes. Development build and all 288 selected tests passed (correcting the earlier aggregate of 287), including 25 focused PancakeSwap/Solana tests.
- [x] Installed/opened the development update and confirmed PancakeSwap quotes for ETH to BNB, BNB to ETH, BNB to native SOL and native SOL to BNB. Confirmed manual selection when another provider won automatic routing. No approvals or signatures were submitted; this does not validate funded settlement.
- [ ] Confirm supported third-party gateway use, production access conditions, fees and support. Current keyless website requests do not establish permission or future availability.
- [ ] Run disposable funded swaps for EVM/EVM and both Solana directions, with destination receipt verification, actual NON-EVM status responses, Tor traffic observation and refund/recovery exercises before production release.
- [ ] Test every enabled network and representative native/ERC20/SPL pairs against live providers. Current routing uses bounded candidate batches and rejects unrecognized hooks; do not claim identical website path selection, optimal net price or blanket support for arbitrary tokens.
- Recommendation: retain V2/V3 when adding standalone PancakeSwap Infinity and Uniswap v4. Newer pools can improve individual quotes but do not guarantee a better net price.

## Release 0.52.3 preparation

- [x] Set the requested PancakeSwap Cross-chain route label to Excellent. Keep this manually configured label separate from security-review claims.
- [x] Corrected the empty fiat placeholder's cursor mapping so it sits after the currency symbol, retaining the shadowed zero and existing input parsing.
- [x] Reviewed cross-chain transaction, approval, RPC/Tor and signing boundaries. Fixed the Token-2022 extension-data validation gap with a regression covering modified and appended account data; see integration details for evidence and remaining trust assumptions.
- [x] Added narrow release rules preserving the two JSON-read Solana instruction/account models through optimization.
- [x] Incremented app version/build to 0.52.3 (184) and updated the pending public release notes.
- [x] Passed all 392 selected walletkit/EVM/Solana/app tests for 0.52.3, including the new regressions; translation coverage passed for 1,811 strings across nine locales.
- [x] Built Base Debug and Base Release as 0.52.3 (184), verified the existing signing certificate and inspected all six preserved JSON fields in release bytecode. Installed/opened only the development package; the user confirmed the corrected fiat cursor. Automated screenshot/rating checks were interrupted by USB disconnections.
- [x] Prepared/staged the source and a proposed commit message for review.
- [x] Published the separately authorized v0.52.3 commit, tag and signed APK on 2026-10-28; the publication entry in CHANGELOG.md records the completed release. Earlier releases remain intact.

## Private Send fee transparency

- Client review found quoted provider/route cost plus the normal network fee and, when applicable, a refundable deposit buffer. No explicit client-side developer fee, affiliate basis-point parameter or separate developer payout was found in the Private Send request/deposit path.
- [ ] Obtain an itemized backend/provider fee policy before claiming that no developer or partner receives any share. The app authenticates to USwap, and [NEAR's published policy](https://docs.near-intents.org/resources/fees) permits partner fees/revenue sharing; the deployed backend split was not available for this review. No fee parameters were changed by the investigation.

## Device validation

- [x] Installed/opened development version 0.52.2 (183) in place and checked PancakeSwap quotes/provider selection for ETH/BNB and BNB/SOL in both directions on 2026-10-28. This supersedes the earlier disconnection before quote testing.
- [ ] Complete funded swap/approval confirmation, device rendering across themes/text sizes, all supported networks, settlement and recovery testing. No funded transaction or signature was submitted in the quote checks; installation and quoting do not establish end-to-end operation.

## Revoke Access follow-up

- Added a Balances shortcut, grouped token/contract rows, themed network selection, long-press multi-selection and separate per-approval review using the existing signing flow. The user chose separate transactions rather than EIP-7702 delegation; do not describe this as a single on-chain batch.
- [ ] Check dark/light rendering, keyboard layout, long-press selection and navigation on a working device or emulator.
- [ ] Verify scan continuation, token/NFT metadata, per-approval fees, cancellation, submission and eventual on-chain confirmation with a disposable funded test wallet before publishing.

## Next-generation EVM swap protocols

Priority: implement after active cross-chain swap work is complete and stable. Across is not a prerequisite while its integration is deferred.

### Uniswap v4

- [ ] Add Uniswap v4 as a same-chain EVM liquidity source without removing the existing Uniswap v2 and v3 integrations.
- [ ] Extend or replace the current Android Uniswap quote kit with v4 support for PoolManager pools, pool keys, native currencies, Quoter simulation and Universal Router calldata.
- [ ] Support mixed-version routing when it produces the best result, while identifying every v2, v3 and v4 leg in route details.
- [ ] Compare routes by net user outcome: expected output minus pool fees and estimated network gas, rather than by gross quote alone.
- [ ] Use only canonical Uniswap deployments for each supported chain and keep the address list independently reviewable and updateable.
- [ ] Decode v4 Universal Router commands and show the actual recipient, minimum output, deadline, tokens, pools, fees and hooks on the confirmation screen.
- [ ] Add transaction decoration/history support for v4 swaps.

### PancakeSwap Infinity

- [ ] Add PancakeSwap Infinity as a same-chain EVM liquidity source without removing PancakeSwap v2 and v3.
- [ ] Support Infinity CLAMM and LBAMM pools; add Infinity StableSwap only on chains where it is officially deployed and useful.
- [ ] Implement canonical PoolManager, Quoter, Universal Router and Permit2 deployments per supported chain.
- [ ] Support mixed v2/v3/Infinity routing when it improves the net result and identify each pool type in route details.
- [ ] Decode Infinity router commands and display pool type, recipient, minimum output, deadline, fees and hook information before signing.
- [ ] Add transaction decoration/history support for Infinity swaps.

### Hooks and fee safety

- [ ] Initially allow only no-hook pools and explicitly approved official hooks. Reject unknown hook contracts instead of silently routing through them.
- [ ] Maintain a per-chain hook allowlist sourced from canonical protocol documentation, with an explicit review process for additions.
- [ ] Detect and display static pool fees, dynamic fees and hook-level fees. Exclude routes whose maximum fee cannot be bounded before signing.
- [ ] Do not add an Open Swap integrator fee, application fee or basis-point fee.
- [ ] Reject transaction data containing an unexpected fee recipient, affiliate/referral payment or value transfer.
- [ ] Validate router, PoolManager, Permit2, hook, token, recipient and chain addresses locally; never trust hosted quote calldata without validation.
- [ ] Simulate every proposed swap before displaying it for signature and reject failed or materially different simulations.

### Approvals and transaction protection

- [ ] Preserve exact-amount ERC-20 approvals and the existing USDT-style reset-to-zero handling; do not introduce unlimited approvals.
- [ ] Implement Permit2 nonce, amount, spender and deadline validation if Permit2 is used.
- [ ] Enforce locally computed minimum output, slippage, recipient and deadline constraints.
- [ ] Protect native-token value, wrapped-native conversions, refunds and leftover-token recipients against route-calldata substitution.
- [ ] Verify fee-on-transfer and rebasing-token behavior per protocol and exclude unsupported routes safely.

### Privacy

- [ ] Prefer local/RPC quoting where practical instead of sending wallet addresses, balances or complete swap intent to a hosted routing service.
- [ ] Route all required hosted quote and RPC traffic through the wallet's existing Tor configuration when Tor is enabled.
- [ ] Fail closed while Tor is connecting or unavailable; never silently fall back to the direct network.
- [ ] Keep HTTP logging free of wallet addresses, token amounts, balances, calldata and signatures.
- [ ] Document unavoidable disclosures separately for RPC nodes, quote services and on-chain transactions.

### User experience and routing

- [ ] Present a single Uniswap or PancakeSwap provider entry where practical, with the selected protocol version and every route leg visible in details.
- [ ] Keep v2 and v3 available because older pools may still offer better prices or liquidity.
- [ ] Clearly label dynamic-fee and approved-hook routes before confirmation.
- [ ] Preserve provider icons, route selection, swap history, percentage selectors and external-recipient behavior.

### Validation and release criteria

- [ ] Test native/native, native/ERC-20, ERC-20/native and ERC-20/ERC-20 swaps, including multi-hop and mixed-version routes.
- [ ] Test exact approvals, Permit2 expiry/nonces, deadlines, minimum output, refunds and malicious or malformed quote responses.
- [ ] Test approved hooks, unknown hooks, dynamic fees and routes with unusually high fees.
- [ ] Test every supported chain against its canonical contracts using unsigned production quotes and fork/simulation tests before permitting real signatures.
- [ ] Confirm Tor fail-closed behavior and that no sensitive request bodies appear in application logs.
- [ ] Install the completed debug build on the connected test device and perform small-value end-to-end tests before release.

### Version decision

- [ ] Do not create Uniswap v5 or PancakeSwap v5 providers unless those protocols are officially released, documented and deployed in production.
- [ ] Re-evaluate this decision only from canonical protocol documentation. PancakeSwap's current next-generation integration target is **Infinity**, not a product called v4 or v5.

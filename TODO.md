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
- [ ] Await the user's decision on a scanner-only preferred RPC followed by attempts across built-in providers. Current behavior uses selected-provider reads with the internal BSC missing-receipt fallback. Failures or incomplete discovery must never become a claim that no approvals exist. Do not implement the proposed provider sweep without that decision.

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

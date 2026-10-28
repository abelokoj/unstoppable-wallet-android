# PancakeSwap Cross-chain

Integration included in OpenSwap 0.52.3, recorded 2026-10-28. This replaces the earlier bridge-token-only preview. It does not guarantee identical routing and prices to the PancakeSwap website.

## Coverage

PancakeSwap Cross-chain accepts native/ERC20 tokens on Ethereum, BNB Smart Chain, Base, Arbitrum and zkSync, and native/SPL tokens on Solana. It covers EVM-to-EVM and both Solana/EVM directions when a supported executable route is available. Linea remains excluded by the user's choice because it is not an existing wallet network. Same-chain PancakeSwap V2/V3 providers remain available.

EVM planning considers every directed bridge pair in the live catalog and adds source and destination AMM trades as needed. It tries candidates in batches, preferring fewer trades and choosing the highest output in the first successful batch. It does not search every possible website plan or optimize total gas cost. The AMM codec supports V2, V3, classic StableSwap, hook-free Infinity CL/Bin, and registered BNB-chain Infinity StableSwap pools. A factory-backed fallback discovers stable pools and can combine them with ordinary AMM legs. Unknown hooks and custom hook payloads are rejected; an alternate classic route is attempted when possible. This does not add standalone Infinity or Uniswap v4 providers.

Routes involving Solana use PancakeSwap's Relay-backed NON-EVM interface. Native SOL and wrapped SOL remain separate asset identifiers. Availability depends on liquidity, provider limits, the configured RPC and successful validation. PancakeSwap alone has a 30-second quote budget; existing providers retain their previous five-second budget.

## Network access and disclosure

The website gateway is `https://cross-chain.pancakeswap.com/gateway/api/`: GET `v1/routes`, POST `v1/metadata`, POST `v1/calldata`, and GET `v1/status/{network}?txHash=...&type=EVM|NON-EVM`. EVM AMM quotes use `https://hub-gateway.pancakeswap.com/v2/quote`. These requests worked without an API key on the recording date. The cross-chain gateway is an undocumented website interface; third-party production support, permitted use, availability guarantees and future access conditions remain unconfirmed. No borrowed credentials or direct Across API key are used.

Initial quotes disclose networks, token contracts and amounts, with fixed public placeholder addresses where the service requires an address. Preparing the final transaction discloses the actual sender/recipient and swap intent to the gateway. Tracking discloses the public source transaction hash. The configured RPC receives contract/pool reads, allowance queries containing the wallet address, and transaction simulation/submission data. Requests honor the app's Tor preference and fail closed if an enabled Tor connection is unavailable. Request/body logging and redirects are disabled. Without Tor, these services see the connection IP.

## EVM construction and validation

Direct bridge transactions are built locally against published [XChainSender and AcrossAdapter deployments](https://developer.pancakeswap.finance/contracts/crosschain/addresses). For AMM routes the gateway supplies candidate calldata; the wallet decodes and validates the complete command tree, then rebuilds the outer transaction locally. It fixes chains, input and output assets, exact input, canonical bridge adapters, recipient/refund ownership, minimum output, deadlines and native value. Every AMM router must be authorized by the canonical bridge contract's on-chain whitelist. AMM pool state comes from the wallet's configured, chain-verified RPC.

Only the expected swap, bridge, sweep and unwrap operations are permitted. Router paths, pools, fees, amounts and recipients must agree with the accepted quote. Unexpected payment commands, Permit2 spending, unknown hooks and arbitrary payloads are rejected. The wallet removes the service's unused Permit2 payload and uses the existing exact-amount ERC20 approval and PIN/signing flow. Source prefunding is fixed to the approved input rather than the contract's whole balance. Router deadlines are normalized to on-chain seconds. Split-leg slippage is bounded across both AMM legs.

Quotes validate amount ranges, token/chain identity, deposit limits, fees and bridge deadlines. Existing transaction preparation estimates/simulates before signing. No wallet developer fee, referral command or extra payout is added. AMM pool fees, bridge/relayer costs, any provider-quoted costs and network gas still apply; zero added wallet fee does not mean a free swap or establish the provider's revenue split.

## Solana/Relay construction and signing

EVM-to-Solana deposits are restricted to Relay's canonical depository, with exact native value or exact ERC20 amount and depositor. Remote approval steps are not signed; ordinary wallet approvals are used. The destination recipient and minimum-output commitment remain a hosted Relay/PancakeSwap intent assertion: the opaque deposit intent ID alone does not prove destination execution.

Solana-source transactions reuse the current local signer and approval/PIN flow. The wallet validates canonical Relay/Jupiter programs, exact source input, signer/account privileges and supported instruction layouts; loads lookup tables through the selected RPC; compiles the versioned transaction; and requires a fresh simulation with bounded wallet debits, rent and fees. Active transfer hooks are rejected. It rechecks the active wallet, selected RPC, Tor status and transaction freshness before signing and sending.

This route submits through the configured RPC rather than the SDK raw-send method's hardcoded mainnet endpoint. It retains the existing SDK pending-transaction database and stores the exact signed payload/signature before broadcasting. An ambiguous network failure keeps that payload pending rather than creating a second payment. The existing global Tor configuration still applies to SDK retry clients; this change does not claim to replace the SDK's general retry transport.

## Tracking and evidence

EVM tracking first requires a matching successful source receipt from the configured RPC, then validates hosted status against chains, assets, input and minimum output. Solana hashes and mints retain their case-sensitive encoding. Completion requires all expected commands to succeed and a destination transaction hash with sufficient output. Unknown responses remain pending, partial/failed execution requires attention, and refunds require a reported refund hash. Hosted status is not independent destination receipt verification. Live NON-EVM settlement response compatibility remains unverified without a funded swap.

Unsigned, dummy-address live responses cover ETH-to-BNB and BNB-to-ETH AMM-plus-bridge transactions, V2/V3/StableSwap/Infinity command formats, native/wrapped SOL, and EVM ERC20-to-Solana intents. A real public BNB stable-pool snapshot covers all 16 registered pools for the fixture's pair and checks selection of the best returned pool. No wallet keys, private inventories or signed orders are fixtures.

Earlier validation of the development integration passed 288 selected tests (correcting the previously reported aggregate of 287): 236 walletkit, 33 EVM, 18 Solana and one navigation-serialization test. This includes 25 focused PancakeSwap/Solana tests for exact construction, malformed and malicious payloads, route/slippage logic, pool-state handling, intent identity and instruction validation. Device quote observations are recorded in CHANGELOG.md and TODO.md.

Funded swaps in each direction, destination verification, Tor traffic observation and refund/recovery remain untested; this integration is released as a preview. Recovery transactions are not automated. Contracts, relayers, administration and hosted quote/status services remain external dependencies. An expired or unfilled deposit does not imply an automatic refund. The implementation does not claim support for arbitrary token hooks, identical website route selection or every future gateway response.

## Source security review for 0.52.3, recorded 2026-10-28

Reviewed the quote/build/status boundary, exact source-spend and recipient checks, AMM router commands, approval targets, Tor/RPC handling and the Solana signing/simulation path. This was a focused wallet-code review, not an independent audit of PancakeSwap, Relay, Across, their contracts or service infrastructure. Published bridge deployment addresses were checked against the [PancakeSwap deployment list](https://developer.pancakeswap.finance/contracts/crosschain/addresses).

Found and fixed a Solana simulation-validation gap: the original guard compared token permission bytes in the base 165-byte account layout but ignored Token-2022 extension data and appended bytes. A changed extension could therefore pass this local guard. The check now preserves account size and every byte outside the token-balance field, including extensions. The regression reproduces acceptance of changed/appended data before the fix and covers an unchanged account. This is evidence of a missing validation guard, not a demonstrated live on-chain exploit. Transactions requiring unrecognized token-account extension changes now fail closed.

The route's Excellent label is explicitly configured at the user's request. It is not a computed audit result or a guarantee of bridge safety. Relay's destination intent and hosted completion/refund status still require provider trust. A compromised service can misrepresent intent details that are not encoded in the source deposit. Final wallet addresses/intent are disclosed during transaction preparation; without Tor, the service also sees the connection IP. Production gateway terms, funded settlement, recovery and observed Tor traffic remain release risks described above.

For 0.52.3 (184), all 392 selected tests passed: 238 walletkit, 33 EVM, 19 Solana and 102 app tests, with zero failures, errors or skipped tests. This includes the reproduced-and-fixed fiat caret and Token-2022 extension regressions. Translation coverage passed for 1,811 strings across nine locales. Release packaging and physical-device validation are recorded in the changelog when complete.

The release review also found missing shrinker protection for the two Gson-read Solana instruction/account models. Added narrow library consumer rules preserving those models and their JSON field names across release flavors. The first optimized mapping showed model renaming; the mapping alone did not establish field removal or a runtime decoding failure. The new rules explicitly preserve the reflective JSON contract. Direct inspection of final APK bytecode verified all six fields across both models.

Final 0.52.3 packaging passed for Base Debug and Base Release, including release shrinking and lint. The release signing certificate matches the existing documented OpenSwap certificate. The development update was installed in place and opened; the user confirmed the dollar caret now behaves correctly. Automated screenshot/rating inspection was interrupted by repeated USB disconnections. No funded transaction was signed. The Excellent rating is configured in source, independently of these test results.

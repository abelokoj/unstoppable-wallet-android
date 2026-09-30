# Open Swap TODO

## Next-generation EVM swap protocols

Priority: implement after the PancakeSwap cross-chain/Across work is complete and stable.

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

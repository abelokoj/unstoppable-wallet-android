# Security follow-ups

The Relay transaction checks and release-signing workflow improvements are included in 0.53.4 (191). Further work remains open. This list tracks engineering work; it is not a security certification or a claim that all risks have been resolved. Detailed assessment evidence is retained locally while unresolved findings are reviewed.

## Priority follow-ups

- [ ] H-01: Review TON signing and confirmation sequencing. Require negative tests covering cancellation and fee estimation.
- [ ] H-03: Strengthen transaction-intent validation for generic USwap execution paths. Add per-chain negative tests before expanding accepted payloads.

## Additional follow-ups

- [ ] M-01: Strengthen backup integrity and authenticated metadata while preserving legacy recovery compatibility.
- [ ] M-02: Preserve recovery passphrases exactly through backup/restore; test boundary values against derived addresses.
- [ ] M-03: Apply consistent sensitive-screen protection, including navigation transitions and overlays.
- [ ] M-04: Verify Tor routing consistently across network clients, system-proxy settings and connection failures.
- [ ] M-05: Expand dependency/build artifact verification and reproducibility checks.
- [ ] M-06: Triage dependency advisories against reachable optimized code, then test compatible updates.
- [ ] L-01: Remove development preview tooling from production entry points.
- [ ] L-02: Bound imported-backup resource usage before expensive processing.

## Validation still needed

- [ ] Physical-device confirmation and funded settlement/refund testing on supported Relay routes.
- [ ] Live Tor cancellation and fail-closed routing checks using synthetic accounts.
- [ ] Independent review of subsequent security fixes before closing these follow-ups.

Use synthetic accounts and disposable signing keys for negative tests. Preserve original backups and never migrate or delete recovery data automatically.

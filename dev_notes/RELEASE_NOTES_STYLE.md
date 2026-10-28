# Release Notes Style

Use the LeanType release-note style for every Open Swap release.

Start with a version heading and a short one-sentence summary when it helps readers understand the release. Organize user-visible changes under these headings, including only headings that have content:

- `## 🔄 Updated` for upstream updates, behavior changes and reorganized flows.
- `## ✨ Added` for new features.
- `## 🐛 Fixed` for corrected user-facing problems.
- `## ⚠️ Notes` for material limits, compatibility requirements, fees or financial-risk disclosures that readers need before using a feature.

Write concise bullets with a clear feature name followed by what changed for the user. Lead with benefits and observable behavior. Keep implementation details, test counts, device details, internal tools, commit history and developer-only explanations out of public notes.

For swaps, bridges, approvals and other financial features, state material fees, availability limits, trust assumptions and untested flows plainly. Do not present a route rating as a security guarantee. Include APK variants or sizes only when the release contains a meaningful choice between artifacts.

Preserve previous release sections in `RELEASE_NOTES.md`. Keep public notes free of model attribution, private data and em dashes.

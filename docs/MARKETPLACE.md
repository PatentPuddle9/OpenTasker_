# OpenTasker marketplace integration

The app's Marketplace destination reads the signed catalogue from the official GitHub repository. It does not use a separate marketplace server.

## Current trust state

The app intentionally ships with an empty official public-key pin in MarketplaceTrust.kt. Marketplace browsing and installation remain disabled until the owner completes signing-key setup in the marketplace repository's docs/SIGNING_KEYS.md and commits the matching public key to Experimental.

Do not remove this fail-closed behavior or trust a public key fetched from the catalogue itself.

## Install flow

1. The app downloads catalog/v1/index.json and catalog/v1/index.sig from GitHub.
2. It verifies the ECDSA P-256 signature with the public key pinned in the app.
3. The app checks the catalogue marketplace ID and schema version.
4. On install, it downloads the declared bundle path and verifies its SHA-256 against the signed catalogue entry.
5. It prompts for declared required inputs. Values are substituted locally and are not uploaded to GitHub.
6. It adds the signed package ID to bundle metadata and sends the bundle through OpenTasker's existing import-review path. Capability checks and permission acknowledgements remain in force.

A valid signature proves catalogue origin and integrity, not that a package is harmless. Risk and moderator-review indicators stay visible after approval.

## External services and privacy

Package cards display the publisher-declared destination domain, operator, hosting country, data fields, purpose, and privacy assessment. These are publisher declarations; unknown or unverified values must not be presented as independently confirmed.

Network, SMS, webhook, MQTT, and other sensitive actions remain subject to OpenTasker's own action capabilities and Android permission model. Marketplace metadata cannot grant permissions or bypass import review.

## Rapid-execution protection

Marketplace-installed profiles carry a local-only package ID tag. The engine monitors package entry executions using a rolling window. By default, ten executions in sixty seconds disables every profile belonging to that package, cancels its active task slots, and posts a warning notification. The lock persists until explicitly cleared.

Users can change the global threshold/window or turn protection off in Settings. Each marketplace package also has an individual protection toggle. Clearing the lock does not automatically re-enable disabled profiles.

## Contributions and reports

Contributors can submit an enabled Action or an app-style Shortcut Widget directly from the Marketplace screen. Widget Displays and Scene layouts are not contribution types. A Shortcut Widget submission includes its linked enabled Action plus the widget's icon, colors, gradient, and shape; the Android app-widget ID is never exported. Disabled Actions and widgets whose linked Action is disabled are excluded and are checked again immediately before upload.

GitHub sign-in uses the OAuth Device Flow. The app displays a one-time code and GitHub verification page; the contributor approves the requested public-repository access on GitHub. The access token is encrypted with a device-bound Android Keystore AES-GCM key and is never written into package files, logs, or source code. Only the OAuth application's public client ID is supplied to the build as the optional `GITHUB_OAUTH_CLIENT_ID` Actions secret. No OAuth client secret is used in the Android app.

To configure sign-in, create a GitHub OAuth App under Settings → Developer settings → OAuth Apps, enable Device Flow in the app settings, and copy its Client ID into the OpenTasker_ repository's Actions secret named `GITHUB_OAUTH_CLIENT_ID`. No client secret is needed for this flow. Re-run the Build OpenTasker APK workflow after adding the secret. The app requests only `public_repo` (write access to public repositories, needed to upload package files and open pull requests) and `read:user` (to show the signed-in account). Review this consent before approving it.

After sign-in, the app creates a branch in the contributor's Marketplace repository (or a fork when needed), uploads `manifest.json` and the data-only `bundle.json`, then opens a pull request against the official Marketplace repository. The existing GitHub Actions validator and moderator review remain authoritative.

## Offline behavior

The app caches only a catalogue that has already passed signature verification. Previously imported automations remain stored locally; the marketplace is not required for them to run.

# OpenTasker

[![Version](https://img.shields.io/badge/version-0.2.94-blue.svg)](https://github.com/PatentPuddle9/OpenTasker_/releases)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-brightgreen.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/kotlin-2.4.20-7f52ff.svg)](https://kotlinlang.org)
[![Obtainium](https://img.shields.io/badge/Obtainium-add%20app-1c1c1c.svg)](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.opentasker.app%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2FPatentPuddle9%2FOpenTasker_%22%2C%22author%22%3A%22PatentPuddle9%22%2C%22name%22%3A%22OpenTasker_%22%7D)

**OpenTasker** is an open-source, on-device Android automation app. Build **Actions** by connecting triggers to ordered steps, then run them automatically or from a home-screen shortcut. Automations and their data stay on your device; no account or cloud service is required.

The app is developed on the [Experimental branch](https://github.com/PatentPuddle9/OpenTasker_/tree/Experimental). See the [changelog](CHANGELOG.md) for recent changes.

---

## Screenshots

| Actions | Action editor | Run log |
|---|---|---|
| ![Actions](fastlane/metadata/android/en-US/images/phoneScreenshots/01-profiles.png) | ![Action editor](fastlane/metadata/android/en-US/images/phoneScreenshots/02-tasks.png) | ![Run log](fastlane/metadata/android/en-US/images/phoneScreenshots/03-run-log.png) |

| Flow | Setup | Settings |
|---|---|---|
| ![Flow](fastlane/metadata/android/en-US/images/phoneScreenshots/04-flow.png) | ![Setup](fastlane/metadata/android/en-US/images/phoneScreenshots/05-setup.png) | ![Settings](fastlane/metadata/android/en-US/images/phoneScreenshots/06-settings.png) |

## Features

### Actions

Actions are the main way to build automations in OpenTasker. An Action has a name, icon, trigger conditions, and the ordered steps that should run when those conditions match.

- Create, edit, enable, disable, group, and search Actions from the Actions area.
- Give Actions their own icons and keep related automations organized in named groups.
- Add triggers independently. The trigger picker groups related choices without silently combining them; you decide which conditions belong together.
- Use nested ALL, ANY, and NOT logic when an Action needs more than one trigger condition.
- Review an Action before enabling it. Preflight previews show expanded inputs, branch decisions, required setup, intended effects, and blockers without running the automation.
- Simulate trigger events to inspect matching, conditions, cooldowns, and admission decisions without creating a real run.
- Optional per-Action or global fallback handling can catch unhandled failures.
- Undo and redo recent edits to Actions, steps, and scenes.

The execution engine currently includes **84 registered actions** plus **10 engine-handled flow commands**. See the catalogue below.

### Triggers

The trigger picker is searchable and organized into related categories. Related events stay together where that makes them easier to find, but each trigger remains a distinct choice. For example, the Apps category keeps app-opened, app-closed, app-installed, app-updated, and app-uninstalled events together.

- **Apps:** app opened and closed events, plus app installed, updated, and uninstalled events. Foreground detection uses Android window events from OpenTasker's Accessibility service, with Usage Access polling as a fallback until the service is connected. Setup reports the service connection state. App detection observes package/activity transitions; it does not read on-screen content or generate taps.
- **Time and date:** time and day schedules, ranges, presets, aliases, and calendar windows.
- **Device and sensors:** battery, charging, headphones, screen, media playback, airplane mode, power saving, Wi-Fi name, orientation, proximity, physical activity, speed, roaming, tethering, phone-call state, USB attach/detach, and ambient light level. The ambient-light condition uses a numeric lux threshold.
- **Connectivity:** Wi-Fi and internet/data connectivity, Bluetooth connections, and Bluetooth security or bond-state changes. Related Bluetooth connection and security choices are kept together in the picker.
- **Notifications and events:** notification listener filters, NFC tags, shake events, SMS/MMS receipt, received broadcasts, screen-recording visibility, boot, Quick Settings tile taps, and Android Sharesheet events.
- **Location and presence:** GPS/geofence conditions, sunrise and sunset windows, and low-power Companion Device presence/absence events.
- **Integrations:** Locale and Tasker condition plugins, plus the UnifiedPush event bridge for compatible distributors.

App-open and app-close detection depends on the Android services and permissions shown in Setup. Android versions and device manufacturers can affect event delivery. Usage Access polling is a recovery path and may be less immediate than live window events.

### Action catalogue

| Category | Count | Examples |
|---|---:|---|
| Settings | 24 | Wi-Fi, Bluetooth, brightness, volume, airplane mode, mobile data, DND, screen timeout, torch, temporary state |
| App | 10 | Launch app or intent, publish shortcut, archive, unarchive, open URL, SMS, screenshot |
| File | 5 | Read, write, append, delete, list |
| Network | 8 | HTTP Request, Home Assistant webhook, MQTT publish, ping, download, Wake-on-LAN |
| Media | 6 | Play, stop, pause, next, previous, mute |
| System | 7 | Vibrate, clipboard set, reboot, lock, screen off, wake, log |
| Notification | 5 | Toast, Show Popup dialog, progress notification, cancel notification, text-to-speech |
| Variable | 13 | Set variable, clipboard read, contact lookup, JSON/CSV/XML/HTML data, date-time and text operations |
| Flow | 1 + 10 | Wait; engine commands for running flows, conditions, loops, try/catch, and stopping |
| Plugin | 2 | Locale setting dispatch and Locale condition query |
| Script | 1 | SHA-256-pinned Termux command with bounded result capture |
| Import | 2 | Explicit unsupported placeholders for imported Tasker and MacroDroid actions |

**Show Popup** opens a real Android dialog above other apps instead of posting a notification. It uses the Display over other apps access in Setup. Up to three buttons can be configured; a button without a selected flow simply dismisses the dialog.

**Toast** displays a short message with a selectable icon. Toast overlays follow the device's light or dark appearance and use Android's native toast-window animation. Action-result messages can also be enabled or disabled in Settings, including results from widget and shortcut runs.

Each action has an explicit capability contract. If a capability is not available or has not been reviewed, the action reports that limitation rather than pretending it succeeded. Some Android operations are restricted by the operating system and may require special access, device-admin approval, or Shizuku. Setup explains the access required for each feature.

New automations should use **HTTP Request** for GET, HEAD, POST, PUT, PATCH, DELETE, and OPTIONS. It supports structured query/header lines, inline or app-file request bodies, per-stage timeouts, status/header/body variables, and atomic file output. Redirects are off by default and can be enabled only for the same origin; TLS verification cannot be disabled, cleartext is restricted to private/local hosts, and request and response sizes are bounded.

**Intent Dispatch** supports bounded activity, explicit broadcast, and explicit service delivery. It accepts allowlisted URI/MIME data, selected activity flags, capped string/integer/boolean extras, and optional ordered-broadcast result capture. Ambiguous targets, non-exported external components, file URIs, unsupported extras, and unknown flags fail closed.

**MQTT Publish** uses an in-app MQTT 3.1.1 client. TLS is enabled by default and verifies the broker hostname. Cleartext is restricted to private/local hosts. Payloads are capped, QoS 2 and wildcard publish topics are rejected, and username/password fields are redacted.

Clipboard actions read and write bounded text without an additional permission. Clipboard-derived values are marked sensitive. Contact lookup supports bounded name, phone, and email matching into sensitive variables. On Android 17 and newer, the default contact mode uses the system picker to grant access only to selected fields; explicit permission mode is available for unattended runs.

Quick Settings tiles provide four app-owned slots. Configure a tile's label, subtitle, icon, and state, then bind it to an Action. Tile-triggered runs use the same execution and run-log path as other entry points. A tile tap on a locked device waits until the phone is unlocked before running.

The **Temporary State** action applies a bounded reversible setting, such as brightness, volume, ringer mode, or DND, and schedules restoration of the captured previous value. Pending restore work survives process death.

Keyboard actions report the current and enabled input methods. The keyboard picker opens Android's own selection UI; ordinary apps cannot silently switch the active keyboard.

### Shortcuts and widgets

The **Shortcuts** area lists home-screen shortcuts and widgets created from Actions. Create a new shortcut, choose its Action, and customize its appearance.

- Widgets are designed to look like app icons and support a **1 × 1** starting size.
- Android can resize them horizontally and vertically.
- Choose the icon, icon color, background color, optional gradient, and app-icon shape.
- Each widget is tied to its own Action configuration. Remove the widget from Shortcuts to remove that widget-owned Action.
- A widget tap runs its configured Action; it does not ask you to select a task at runtime.

### Icons and customization

The shared built-in icon picker includes the original OpenTasker bolt plus **all 442 Coolicons v4.1 icons**.

- Search and browse the built-in icons in the shared picker.
- Upload custom images from Settings. OpenTasker guides you through cropping each image into a square.
- Built-in and custom icons appear together in the picker; custom icons are not a replacement for the included library.
- Use the shared icon set for Action cards, widgets, and popup/toast headings where an icon is supported.

Icon artwork and other third-party components are documented in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

### Reliability, privacy, and diagnostics

- The automation database is encrypted at rest with SQLCipher. Its key is wrapped by Android Keystore; database migration and wrong-key handling fail closed.
- Secret variables use AES-256-GCM with Android Keystore storage. Reveal and re-entry are deliberate, and secret-derived values are redacted from action arguments, previews, traces, logs, and diagnostic reports.
- Variables are scoped to an execution or stored globally according to their names. Temporary run variables do not become permanent unless explicitly saved.
- The Context Inspector shows live source health, latest values, and why an Action did or did not match.
- Run Log provides filters, expandable action traces, variable writes, cancellation outcomes, and redacted JSON/CSV export.
- Diagnostics reports service, foreground execution, standby, exact-alarm, matcher, and watchdog health, plus execution limits and crash previews.
- OEM battery-management guidance helps identify settings that may delay background automation.
- Time/day scheduling uses alarm-backed reevaluation and a WorkManager watchdog to recover dropped work.
- Imports are reviewed before installation. Imported automations start disabled, capability requests are shown, and secrets are omitted from export.

### Interoperability

- **UnifiedPush:** connect to a distributor, receive authenticated push events, deduplicate repeated deliveries, and redact message content before matching or logging.
- **Home Assistant:** send bounded JSON webhooks using HTTPS by default. Webhook credentials are treated as secrets.
- **Locale and Tasker plugins:** dispatch Locale settings and query Locale/Tasker condition plugins.
- **External automation intents:** signature-scoped intents can run automations, toggle Actions, query status, and pass variables. Runs are asynchronous; see [docs/EXTERNAL_INTENTS.md](docs/EXTERNAL_INTENTS.md).
- **OpenTasker bundles:** schema-versioned JSON export/import with migration, conflict review, secret omission, and disabled-by-default installation.
- **Tasker XML:** import/export with mapped, unsupported, and lossy-action reporting.
- **MacroDroid:** import complete .mdr backups or individual .macro shares through the same review-first flow.
- **Received Share:** accept bounded text, URLs, MIME-typed content, and one or more shared files from Android's Sharesheet.

Untrusted imports are preflighted before database changes. OpenTasker and MacroDroid JSON are capped at 16 Mi characters, 250,000 lexical tokens, and depth 64; Tasker XML is capped at 4 Mi characters, 100,000 nodes, and depth 64. All formats share limits for top-level entities, steps, triggers, scene elements, and aggregate text size.

### UI and theming

- Five primary destinations keep the most-used areas visible, with advanced workspaces available from the app navigation.
- Dark and light palettes, high-contrast mode, and edge-to-edge system-bar styling.
- Shared installed-app picker with app labels, package search, icons, and validated manual entry.
- Metadata-driven action forms with bounded number validation, task/app/file pickers, typed output-variable chips, and summaries for configured parameters.
- Flow graphs provide zooming, panning, branch and subflow markers, and links back to the relevant editor.
- Scenes support drag-to-move, resize handles, multi-select, alignment guides, overlay launch, and tap/long-press bindings.
- Global search covers Actions, triggers, variables, and scenes.
- Editor and dialog state is retained across rotation and window resizing.

### Distribution and updates

- The app supports standard, F-Droid-compatible, and Play distribution build profiles. There is no official F-Droid or IzzyOnDroid listing yet.
- Settings can select a published update source branch other than master. The app checks that branch's update channel and can download and verify the APK before opening Android's installer. Android still asks you to confirm installation.
- Update checks can run automatically and can also be started manually from Setup.
- Every successful non-master branch build can publish a rolling prerelease update channel. Experimental and BETA also publish immutable versioned releases.
- The updater verifies the APK digest and package/version metadata before offering installation.
- Release signing material is kept outside the repository. F-Droid builds are unsigned so F-Droid can sign the APK it builds.

### Power-user backends

- **Shizuku:** permission and service status, a default-on kill switch, and a fail-closed command allowlist. Elevated actions refuse to run if Shizuku is absent, revoked, or stopped.
- **Termux:** Termux 0.109+ RUN_COMMAND integration with a user-managed SHA-256 allowlist, pre-run hash verification, timeouts, and bounded output variables.

To run a Termux script, place it below ~/.termux/tasker/, enable Termux's external-app access, and grant OpenTasker RUN_COMMAND permission from Setup. Add the script path and expected 64-character SHA-256 under Approved Termux scripts. A capture prefix such as %script writes bounded stdout, stderr, exit-code, and original-length variables; captured script output is not written to the run log.

---

## Architecture

The user-facing automation model is **Actions + triggers + ordered steps**. For compatibility with the existing storage and execution engine, some internal source names still use Profile and Task terminology.

~~~text
AutomationService (foreground)
  ↓
ProfileMatcher (matches an Action's trigger conditions)
  ↓
ContextSources (application, time, state, event, location, plugin)
  ↓
TaskRunner (executes the Action's ordered steps and flow control)
  ↓
ActionRegistry (built-ins, capability gates, and plugin dispatch)
  ↓
Room database (encrypted persistent storage and live queries)
~~~

The app uses Kotlin, Jetpack Compose, Room, coroutines, DataStore, and WorkManager. Dependencies are managed without Hilt through the app's explicit dependency wiring.

---

## Install

Download builds from the [OpenTasker_ Releases page](https://github.com/PatentPuddle9/OpenTasker_/releases).

**With [Obtainium](https://github.com/ImranR98/Obtainium):** tap the Obtainium badge above on your Android device, or add this repository as a GitHub source:

~~~text
https://github.com/PatentPuddle9/OpenTasker_
~~~

**By hand:** download the APK for the branch/version you want from Releases. Experimental builds are published as OpenTasker-vVERSION-debug.apk. BETA builds are published as OpenTasker-Beta-vVERSION-debug.apk. Other non-master branches publish prerelease update channels; use the in-app branch selector to follow one of those channels.

**Checking the signature.** Published branch builds use the repository's persistent debug signing certificate. AppVerifier reads its SHA-256 fingerprint in this form, package name first:

~~~text
com.opentasker.app
DB:A1:AA:88:E3:7B:90:15:5F:CA:31:35:CA:3B:78:1D:E9:2C:22:51:07:E4:7C:98:06:E7:5B:F8:80:55:FD:D8
~~~

To check a downloaded branch APK on a computer, run:

~~~bash
apksigner verify --print-certs OpenTasker-vVERSION-debug.apk
~~~

The command prints the same SHA-256 digest in lowercase without colons.

**Upgrading from v0.2.92 or earlier?** v0.2.93 and later use a new signing key, so Android refuses to install them over an older copy. The install fails with INSTALL_FAILED_UPDATE_INCOMPATIBLE until the old app is removed. Uninstalling also erases the automation database, so export a backup before doing anything else. In Setup, export the database or an encrypted .otbackup snapshot to a folder you control, uninstall the old build, install the current build, and restore. A backup stored only inside app storage is removed with the app. Secret variable values stay bound to the device that created them and must be re-entered after a restore.

Tags v0.1.0, v0.3.0, v0.4.1, and v0.4.2 are obsolete and cannot be upgraded in place. The active release line is v0.2.x.

---

## Build & Run

### Build an APK with GitHub Actions

The repository includes a manual GitHub Actions workflow, **Build OpenTasker APK**. This is the recommended path when you do not have an Android build environment available.

1. Open the repository's **Actions** tab.
2. Select **Build OpenTasker APK**.
3. Choose the branch to build, such as Experimental or BETA, then select **Run workflow**.
4. When the run succeeds, download the OpenTasker branch debug APK artifact from the run summary.

The workflow uses JDK 21 and the checked-in Gradle wrapper. It requires the repository's DEBUG_KEYSTORE_BASE64 secret so successive debug builds remain installable as updates. Successful Experimental and BETA builds publish versioned GitHub releases automatically; successful builds from other non-master branches publish branch update channels.

### Build locally

Use JDK 21 and the checked-in Gradle wrapper:

~~~bash
git clone https://github.com/PatentPuddle9/OpenTasker_.git
cd OpenTasker_
./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
~~~

The compileDebugAndroidTestKotlin step is intentional. Instrumented tests only run when a device is attached, so the compile step catches Android-test source errors even when no device is connected.

The external-decoder fuzzer is opt-in and is not part of the release or local quality gates. Run:

~~~bash
./gradlew :app:fuzzExternalDecoders -PfuzzSeconds=30
./gradlew :app:fuzzExternalDecoderRegression
~~~

Compose screenshot references are generated and validated headlessly. After an intentional UI change, update and validate them with:

~~~bash
./gradlew :app:updateDebugScreenshotTest
./gradlew :app:validateDebugScreenshotTest
~~~

### Signed release builds

Local signed release builds require OPEN_TASKER_RELEASE_KEYSTORE, OPEN_TASKER_RELEASE_KEYSTORE_PASSWORD, OPEN_TASKER_RELEASE_KEY_ALIAS, and OPEN_TASKER_RELEASE_KEY_PASSWORD. The key file must stay outside this repository. Android ties an app's identity to its signing key, so losing the key prevents installed copies from upgrading without uninstalling first. SigningMaterialCustodyTest fails the build if a keystore, .p12, or signing.properties file is tracked, including through git add -f.

~~~bash
./gradlew :app:assembleRelease
./gradlew :app:verifyReleaseAssetName
~~~

The local signed release staging task writes OpenTasker-vVERSION.apk to app/build/outputs/release-assets/. It fails if the staged name is still AGP's default app-release.apk, if more than one asset is staged, or if the name disagrees with tools/release-truth.json. This local signed-release asset is distinct from the debug APKs published by the branch CI workflows.

F-Droid profile:

~~~bash
./gradlew -PopenTaskerDistribution=fdroid :app:assembleRelease :app:verifyFdroidReadiness :app:verifyFdroidMetadata
~~~

Play manifest policy check:

~~~bash
./gradlew -PopenTaskerDistribution=play :app:verifyPlayManifestPolicy
~~~

### Local release quality gate

The full local release gate checks the pinned Gradle bootstrap, blocking lint, the configured JVM test floor, JaCoCo coverage floors, Room schemas, Android-test compilation, dependency/SBOM and OSV policy, configuration-cache reuse, and Play/F-Droid release builds. Release-facing version, SDK, capability-count, schema, and artifact-commit claims are generated and checked from tools/release-truth.json.

Run it on Windows with:

~~~powershell
.\tools\verify-local-release.ps1
~~~

Use .\tools\verify-local-release.ps1 -BootstrapOnly for the fast bootstrap preflight. Use .\tools\verify-dependency-verification.ps1 -UpdateOrigins only after deliberately reviewing a dependency change; the normal verifier must pass without that switch.

The repository uses Gradle's local build cache only. Do not configure a remote build cache. Kotlin 2.4.20 fixed the cache-metadata flaw in [GHSA-r937-wjx7-w2jp](https://github.com/advisories/GHSA-r937-wjx7-w2jp), and keeping the cache local avoids exposing builds to the same class of remote-cache tampering.

Treat a Gradle wrapper upgrade as one atomic change: update the wrapper distribution and checksum, verify the regenerated wrapper JAR against Gradle's published checksum, update both expected hashes in tools/verify-local-release.ps1 and ReleaseTruthContractTest, then run a clean bootstrap and the full local release gate.

---

## Development

| Property | Value |
|---|---|
| Kotlin | 2.4.20 |
| Gradle | 9.7.1 |
| Android Gradle Plugin | 9.3.2 |
| KSP | 2.3.12 |
| Build Tools | 36.0.0 |
| Macrobenchmark | 1.5.0-rc02 |
| JDK | 21 (recommended) |
| Minimum Android | 8.0 (API 26) |
| Compile SDK | 37 |
| Target SDK | 37 |
| Room | 2.8.4 |
| Compose BOM | 2026.08.00 |
| WorkManager | 2.11.2 |

Dependency versions are centralized in gradle/libs.versions.toml.

---

## Planned

- Broader device-verified background geofence reliability evidence.
- API 37 platform readiness checks, including foreground-service behavior, predictive back, and large-screen QA.
- Device-run performance evidence through the checked-in Macrobenchmark and Baseline Profile harness.

---

## Non-goals

These are deliberate project boundaries:

- **No UI automation through Accessibility.** OpenTasker uses Android accessibility window events only as an app-foreground trigger source. It does not scrape screen contents or generate synthetic taps. If that service is unavailable, Usage Access polling can be used as a fallback, with potentially slower event delivery.
- **No cloud sync, account, or remote execution.** Automations and their data stay on the device.
- **No URL or network import for shared automations.** Imported bundles are untrusted input and must pass the local review flow.
- **No Google Play Services dependency**, including for geofencing. Geofences are evaluated in-app from platform location fixes.
- **No crash reporting or analytics**, self-hosted or otherwise. The redacted diagnostic export you choose to share is the only way diagnostic data leaves the device.

---

## License

MIT. See [LICENSE](LICENSE).

## Contributing

Issues and pull requests are welcome. Open an issue to discuss a feature before building it. The non-goals above are settled; other areas are open to contribution.

[CONTRIBUTING.md](CONTRIBUTING.md) has the build and test commands, a map of the codebase, and the source guards that run during verification.

### Translations

OpenTasker supports localization. The current release ships English only; English source copy lives in app/src/main/res/values/strings.xml, action_catalog_strings.xml, and dynamic_surface_strings.xml. The app uses AGP's generated per-app language configuration with en-US as the default. An alternate locale is included in a release only after at least 80% of the default string resources are translated; incomplete or empty locale directories are rejected by the release gate.

To contribute a translation:

1. Copy the three translatable XML files from app/src/main/res/values/ to app/src/main/res/values-LOCALE/.
2. Translate only the string values, not the name attributes.
3. Omit strings that are identical to English. Android falls back automatically, but the locale must still reach the 80% translated-value threshold.
4. Run ./gradlew :app:verifyLocaleResources and submit the locale directory only when it passes.

No incomplete locale directories are kept as release placeholders. The gate reports the exact translated-string count when a locale falls below the threshold.

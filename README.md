# TV Vault 0.17.1

Offline password manager for Android TV, created by Alex Cantini.
Android 9 (API 28) or later. English UI by default, Italian on Italian systems.
Single-screen Home with Exit at top right; initial security disclosure with
Accept/Decline and optional Do not show again. Decline exits without deleting data.
TV remote/D-pad navigation. Only apps that expose compatible Android Autofill
fields can be filled. No Google Password Manager access or cloud account.

## Features
- Create, edit and delete local username/password credentials.
- Associate a credential with an app or make it available to compatible apps.
- Choose the Android Autofill service from the home screen.
- Exclude apps from Autofill suggestions.
- Optional numeric PIN: 4–12 digits, disabled by default.
- Settings to enable/change/disable the PIN and read security information.

## Security and data loss
The complete local JSON credential list is encrypted using AES-256-GCM.
The existing Android Keystore alias TVVaultLocalKeyV1 and private
SharedPreferences tvvault_secure/vault remain unchanged from 0.16.3.
The app has no INTERNET permission, analytics or cloud synchronization.
The optional PIN is an access gate, not a key-derivation password for the vault.
Its salted verifier is HMAC-SHA256 with a non-exportable Android Keystore key;
the PIN is not intentionally persisted in cleartext. PINs saved by 0.17.0 are
verified once with legacy PBKDF2 (210,000 iterations), then atomically upgraded
after a successful unlock. That first unlock may still take several seconds. Delays start after five failures,
persist across app restarts and rise from 30 seconds to a maximum of 300 seconds.
When enabled, the gate applies to both entry points and reappears after the
activity stops/backgrounds. A stopped PIN verification cannot unlock a new gate.
Screenshots, recent-app previews and ordinary screen capture are blocked by
FLAG_SECURE on vault activities. Device compromise/root is outside this protection.

Without a PIN, anyone able to use the app can access saved credentials.
The PIN cannot be recovered. Clearing app data or uninstalling deletes the vault
and PIN; there is no export, cloud backup or recovery feature. Losing the
Keystore key may permanently lose access. Read/decrypt errors block writes.
There has been no independent security audit. See SECURITY.md.

## Updating existing installations
Keep the package com.alexcantini.tvvault and sign updates with the same
certificate as the installed app. A new release key will not update an app
signed with a different key. Do not uninstall or clear data to bypass a signature
error: that loses the vault. First check the installed certificate and plan the
transition. Existing data-format compatibility is covered by regression tests;
actual Android Keystore and update behavior still require a device test.

## Building
Use a free Java 17+ JDK, Android SDK platform 35, and build-tools 34.0.0.
AGP 8.7.3 / Gradle 8.9. No third-party runtime dependencies.
Set ANDROID_HOME or create an untracked local.properties with sdk.dir.

```sh
./gradlew assembleRelease assembleDebug lintRelease --console=plain
```

Windows: use gradlew.bat. Release R8 is enabled. Dependency metadata is disabled.
The release output is app/build/outputs/apk/release/app-release-unsigned.apk.
Build from a clean, committed checkout. Tag exactly the commit used to build the
release. Do not edit source or move an already-published tag after release.
The build pins the Gradle distribution SHA-256.

## Signing and F-Droid
A definitive signing key is not embedded. The release signing helper is delivered
separately and requests secrets locally. Use a recent apksigner supporting
--alignment-preserved true. Keep private keys and passwords out of source control
and back up the key and password separately.
F-Droid preparation is in docs/FDROID.md. Fastlane metadata is under
fastlane/metadata/android/en-US and it-IT. Real device screenshots are still
required. Compilation and local tests do not imply F-Droid acceptance.

## License and attribution
GPL-3.0-only; see LICENSE, NOTICE and SOURCES.md. The supplied original project
records a derivation from GPL Bitwarden Autofill code. Its exact referenced tag
and license were verified. Original artwork is retained and fitted to 16:9 TV banners;
redistribution rights must be confirmed by the publisher before submission.

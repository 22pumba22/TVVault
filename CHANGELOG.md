# Changelog

## 0.17.2 (versionCode 20) — 2026-10-31
- Relock on pause, new launcher intents and screen-off; screen-on returns to PIN gate.
- PIN enabled/unset state clearly visible on Home; confirmation after enabling PIN.
- Keep disclaimer acknowledgement independent from access protection.
- High-contrast dark buttons, blue focus fill and bright focus borders across all screens.
- Brighter secondary labels, input hints and platform confirmation dialogs.
- No vault-format, PIN-verifier or credential changes.

## 0.17.1 (versionCode 19) — 2026-10-31
- Single-page two-column Home and top-right Exit; author credit moved to disclosure.
- Transparent icon corners; full-bleed 16:9 TV banner with TV Vault lettering.
- Fast Android-Keystore HMAC PIN verifier with one-time legacy-PIN migration.
- Hide new/confirm fields while PIN is active; require current PIN to change/disable.
- Short PIN recommendation; full-height settings background.
- First-open security disclosure, Accept/Decline and Do not show again.
- Secret inputs briefly reveal the latest typed character and then show asterisks.

## 0.17.0 (versionCode 18) — 2026-10-31
- Optional 4–12 digit PIN for the app and Autofill; disabled by default.
- Re-lock after backgrounding; persistent retry delays; PIN help/settings.
- Salted PBKDF2-HMAC-SHA256 PIN verification (210,000 iterations).
- Preserve existing AES-256-GCM/Android Keystore vault format and alias.
- Surface read/decryption/save failures and block unsafe vault replacement.
- Fill recognized login fields only; confirm cross-app credential use.
- Correct input-class handling: URI fields are not numeric passwords.
- Compatible mutable explicit Autofill PendingIntent on API 31+.
- English default UI and Italian translation; remove field diagnostic logs.
- Secure activity windows; explicit backup/device-transfer exclusions.
- Visible-app queries for TV and standard launcher apps.
- R8 release builds, dependency metadata disabled, Gradle 8.9 pinned/checksummed.
- GPL attribution, store metadata, signing workflow and device test plan.

## 0.16.3 (versionCode 17)
Original uploaded stable project; preserved unchanged as the input archive.

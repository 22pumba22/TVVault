# Changelog

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

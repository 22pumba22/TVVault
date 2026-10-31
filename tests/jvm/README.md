# JVM security regression harness

Runs the actual production PinStore, CryptoStore and field classifier using
small test doubles at Android boundaries, JCA cryptography and org.json.
LegacyCryptoStore is an exact renamed fixture from the original supplied zip.
LegacyPinStore is the exact renamed 0.17.0 verifier for migration checks.
The new PIN tests inject a test-only HMAC key at the Keystore boundary.
Checks old/new vault compatibility, AES-GCM tamper detection, blocked writes,
PIN hashing/salts/cooldown/failures and field classification.

These are JVM checks, not Android UI or hardware-backed Keystore tests.
Supply Android SDK 35, a JDK and org.json:json:20240303 from Maven Central.
The JSON jar is test-only, not bundled in the app or source tree.

Run tests/jvm/run.ps1 with -JsonJar path/to/json-20240303.jar.

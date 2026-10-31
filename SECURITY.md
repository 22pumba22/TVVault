# Security model

TV Vault is an offline local credential manager, not an audited security product.

## Protected boundaries
- At-rest credentials: AES-256-GCM, per-install Android Keystore key.
- Optional UI gate: PIN verifier, fresh random 32-byte salt, PBKDF2-HMAC-SHA256
  210,000 iterations / 256 bits, MessageDigest.isEqual comparison.
- PIN verifier/cooldown: private app preferences, not a cloud service.
- PIN failures: persisted delays from the fifth failure; max 300 seconds.
- Background: discard protected activity UI and require unlock on re-entry.
- PIN not enabled: use is deliberately ungated; clearly disclosed in settings.
- Credential selection/entry activities are not exported. Autofill service is
  protected by BIND_AUTOFILL_SERVICE. Its authentication Intent is explicit.
- Fill recognized username/password targets only; cross-package matches need
  explicit user confirmation. Universal credentials have no package restriction.
- Read/decrypt errors block writes; absent keys on decrypt are not regenerated.
- Backup and device transfer are excluded. Screen capture/recents are protected.

## Limits
The PIN does not derive or wrap the vault encryption key. A privileged attacker
who can alter app preferences/code or operate inside the app process can bypass
an application access gate. No claim is made against rooted, compromised or
physically attacked devices, malicious keyboards, hostile Autofill hosts, or
Android vulnerabilities. Cooldown uses device wall time; changing system time
can affect delays. Strings decrypted for use exist in process memory and may
remain until garbage collection. Keystore hardware-backed storage depends on
the device; hardware enforcement is not requested or guaranteed by this app.

No automatic credential capture, export, recovery, domain-origin verification,
cloud synchronization or independent security audit is provided.
Screenshots for the store should show only synthetic data and no PIN/credentials;
FLAG_SECURE intentionally prevents ordinary software screen captures.

## Reporting
Before public distribution, configure a public issue tracker and a private
security contact in this document/store metadata. Do not post credentials,
PINs, vault files or signing keys in public issues.

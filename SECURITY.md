# Security model

TV Vault is an offline local credential manager, not an audited security product.

## Protected boundaries
- At-rest credentials: AES-256-GCM, per-install Android Keystore key.
- Optional UI gate: PIN verifier, fresh random 32-byte salt, HMAC-SHA256 using
  a separate Android Keystore key (TVVaultPinKeyV2), constant-time comparison.
  Legacy PBKDF2-HMAC-SHA256 / 210,000 iterations remains solely for existing PIN
  verification and one-time migration on successful unlock. Unknown verifier
  formats or missing PIN keys fail closed; no key replacement on verification.
- Changing/disabling an enabled PIN requires a fresh current-PIN check.
  New-PIN entry is revealed only after successful verification; backgrounding
  invalidates the in-progress settings authorization.
- Secret inputs reveal only a newly typed single character for 650 ms, then show
  asterisks; stored values and multi-character pasted text remain masked.
- PIN verifier/cooldown: private app preferences, not a cloud service.
- PIN failures: persisted delays from the fifth failure; max 300 seconds.
- Pause/background, new launcher intent and screen-off: discard protected
  activity UI and require unlock on re-entry. Screen-on/user-present handlers
  restore the gate if the TV kept the activity alive while sleeping.
- Disclaimer suppression only affects the informational notice. It never
  disables an enabled PIN. A PIN remains optional until explicitly configured.
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

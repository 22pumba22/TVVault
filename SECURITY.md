# Security model

TV Vault is an offline local credential manager, not an audited security product.

## Protected boundaries
- At-rest credentials: AES-256-GCM, per-install Android Keystore key.
- Optional UI gate: PIN verifier, fresh random 32-byte salt, HMAC-SHA256 using
  a separate Android Keystore key (TVVaultPinKeyV2), constant-time comparison.
  Legacy PBKDF2-HMAC-SHA256 / 210,000 iterations remains solely for existing PIN
  verification and one-time migration on successful unlock. Unknown verifier
  formats or missing PIN keys fail closed; no key replacement on verification.
- Security and PIN requires the active PIN once per foreground visit. Changing,
  disabling and configuring Autofill use that visit authorization without caching
  the PIN. Leaving the page or backgrounding ends authorization.
- Secret inputs reveal only a newly typed single character for 1000 ms, then show
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

## Autofill choice (0.17.3)
An enabled vault PIN protects application entry. The separate Autofill PIN
requirement defaults to true. Changing it requires an authorized foreground visit to Security and PIN.
The entry PIN is checked once per visit; wrong PINs count toward the retry delay.
When unchecked, the Autofill selection activity may decrypt and return chosen
credentials without asking for the PIN. It does not grant access to MainActivity
or PIN settings. Package-selection warnings and credential selection remain.
Changing the vault PIN preserves this option; a new PIN enrollment after disabling
protection defaults to requiring the Autofill PIN again.

## Navigation and settings (0.17.4)
Home returns to the initial page without granting access. A locked Home contains
no decrypted credentials; protected actions require unlock. Security and PIN has
its own entry gate, once per visit. Leaving or backgrounding invalidates access.
Secret input reveals the last typed character for 1000 ms, then masks it automatically.

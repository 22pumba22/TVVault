# Release device test plan — pending execution

Use synthetic credentials; do not share real secrets in reports/screenshots.
Do not uninstall/clear data on the existing installation. First verify the
installed certificate versus the release certificate; a different signature
cannot update the app. If a safe upgrade is unavailable, use a separate test
device for initial testing. No migration/export feature is currently available.

Record model, Android version, release versionCode, certificate fingerprint,
input device, test result and any compatibility limits.

1. PIN off: launch, create/edit/delete credentials, restart and verify persistence.
2. App picker: launcher app names/icons, D-pad focus, universal selection.
3. Autofill: enable via system confirmation; return to Home and check status;
   disable/re-enable. Test compatible login, username-only and password-only steps.
4. Exclusions and self-exclusion: no proposal in an excluded app or TV Vault.
5. PIN: enable, leading zeroes, mismatch, change, disable; relaunch and background
   while in Home/editor/picker; repeat through Autofill. No credential or label
   should be shown before unlock. Keyboard and remote navigation must work.
6. Five wrong PINs: cooldown, restart during cooldown, correct PIN after delay.
   Verify a pending attempt cannot unlock after backgrounding.
7. Cross-app fallback: show confirmation naming source/destination packages;
   Cancel must not fill. Universal credentials are deliberately unrestricted.
8. Search/OTP/URL/non-login inputs: no password filling. Confirm useful custom-TV
   login compatibility is retained after narrowing the field selection.
9. Error tests only on disposable test data: corrupted blob/missing key produces
   an error and blocked writes, never an empty editable replacement vault.
10. Italian locale: all UI, PIN, errors, selection and Autofill in Italian.
    English and a third locale: English fallback with no clipped text.
11. Verify Back/Exit, focus and scrolling on the TV at its actual resolution.
12. Verify the R8 release, not only a debug build. Recheck the installed app's
    existing credentials only when an update with matching signature is possible.

Store screenshots are pending: provide TV screenshots/photos showing the Home
and synthetic-data vault in both languages. FLAG_SECURE blocks ordinary software
screen captures by design. Do not disable it in a public release for screenshots.

0.17.1 additions: first-open decline preserves data; Accept plus checkbox
suppresses future notices; active PIN settings hide new/confirm fields;
wrong entry PIN cannot authorize a settings visit; migration keeps the old PIN;
single typed character becomes * after 1000 ms, stored values start masked;
Home fits in one page with Exit at top right; PIN page fills background.

0.17.2: verify Home reports whether the PIN is actually configured. Enabling
requires entering/confirming a PIN and selecting Enable PIN. Hiding the security
notice must never disable PIN protection. With PIN active test repeated launcher
open, Home/background return, TV standby and a full device reboot. The automated
isolated tests cover process restart, launcher re-entry, background and injected
screen events; they do not turn off or reboot the user's television.

0.17.4: test checkbox checked/unchecked after the single settings entry PIN;
wrong PIN must leave the choice unchanged. Verify MainActivity always keeps its
PIN gate while Autofill off bypasses only the Autofill gate and retains selection.
Check option persistence after restart. Type ONE character and wait without any
other key, focus change or navigation: it must become * after 1000 ms. Compare
real rendered pixels, not just TransformationMethod.toString(). Test explicit
paste actions remain masked, including a one-character clipboard paste.

0.17.4: UiProbe174 covers Home from both unlock/settings pages without unlocking the vault, one PIN check per visit, changes without repeat checks, leaving/background invalidation, no-PIN navigation and automatic hardware-rendered masking after 1000 ms. Run only against com.alexcantini.tvvault.preview with synthetic data.

0.17.5: UiProbe175 verifies Exit closes the initial PIN gate, Home is absent there,
Home navigation from vault/editor/picker/exclusions, localized wrong-PIN and
invalid-format messages, and the authorized Android Autofill settings round trip.
Only cancel the actual system picker; never change the user's real Autofill
provider during automated tests. Check that the provider is unchanged afterward.
Inject screen-off into the preview receiver and expire the in-memory deadline to
verify those events revoke the exception; ordinary background must still lock.
Also check per-visit settings access, one-second rendered masking and process restart.

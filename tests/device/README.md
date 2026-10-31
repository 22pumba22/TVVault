# Isolated on-device UI and performance probe

UiProbe is NOT part of the production app sources or APK. It was run on an
independent debug copy whose applicationId is com.alexcantini.tvvault.preview,
whose application is testOnly=true, and whose instrumentation targets that
same .preview package. Production code/resources are copied unchanged; the
runner lives only in that separate copy. Never target the installed real app.
The runner checks its target package before accessing preferences.

Add an instrumentation declaration in the isolated copy only:
<instrumentation android:name="com.alexcantini.tvvault.UiProbe"
 android:targetPackage="com.alexcantini.tvvault.preview" />

Build/install that separate debug/testOnly APK, then:
adb shell am instrument -w com.alexcantini.tvvault.preview/com.alexcantini.tvvault.UiProbe
Use -e fast true to skip repeated legacy PBKDF2 benchmark/migration measurements.

The runner seeds only synthetic credentials and test PINs inside its own UID,
checks layout and PIN authorization, and renders its own views to local PNGs.
These images contain synthetic data; production FLAG_SECURE stays enabled.
The separate test application should be removed after testing. Logs do not
contain the real vault, actual PINs, or signing secrets.

0.17.2: the normal phase leaves a synthetic PIN enabled. Force-stop ONLY the
.preview package, then run with -e phase cold to check persistence and gate on
fresh process startup while the disclaimer remains hidden.
Screen-off/on tests directly invoke the activity's registered receiver with
synthetic screen intents. They cover a live activity without onStop; the TV
itself is not power-cycled. Full physical standby/reboot remains a manual test.

0.17.3 focused probe: UiProbe173 plus ProbeHostActivity, included ONLY in a
separate .preview build. Add the matching instrumentation target and a non-exported
ProbeHostActivity there. Normal phase tests the protected default, wrong/current
PIN authorization, both Autofill modes, selection and a real Dataset return to
the synthetic host. The cold phase verifies the saved choice after force-stop.
The synthetic host excludes descendants from real system Autofill requests.
PixelCopy captures only the owning synthetic preview window and compares an
actual rendered digit before/after timeout with a fresh masked reference. The
preview window's FLAG_SECURE is temporarily cleared for this capture and restored;
production protection is unchanged. No real clipboard data is accessed by tests.

# F-Droid preparation

This is a local release candidate. No GitHub repository, release or F-Droid
request has been created or changed by this work.

Already prepared: GPL-3.0-only attribution/license; no runtime dependencies or
INTERNET permission; R8 release; dependency metadata disabled; English/Italian
Fastlane texts, original icon and TV banner; version 0.17.0 / code 18.

Before submission:
1. Confirm rights to distribute the original artwork; set a security contact.
2. Decide/create the public source repository and issue tracker.
3. Verify the installed app signature. Create/sign using the definitive key,
   keep it private and back it up. Never uninstall to resolve a signature mismatch
   when the existing vault matters. A new key does not migrate existing data.
4. Complete release device tests and real TV screenshots (docs/DEVICE_TESTS.md).
5. Build from the exact clean commit; tag v0.17.0 on that commit. If any source
   changes, rebuild/retest before creating a final release tag.
6. Upload the signed TVVault_v0.17.0_release.apk and publish source/tag.
7. Generate the real fdroiddata metadata from fdroid/com.alexcantini.tvvault.yml.in:
   replace every placeholder with verified URLs, full commit and certificate.
   Do not submit the .in template or guessed values.
8. Validate using fdroid rewritemeta/lint and the actual F-Droid build pipeline;
   compare signatures and reproducibility using the signed release as Binaries.
   A second local unsigned build only provides local evidence, not F-Droid CI.
9. Use English/Italian Fastlane metadata, icons, TV banner and tvScreenshots/.
   Keep app descriptions/changelogs/images upstream, outside fdroiddata.
10. Keep the existing MR updated if release changes during review. Green checks
    mean technical checks passed, not acceptance or a security certification.

Lessons from BlocksTV: R8 enabled; dependency metadata disabled; signing must
preserve APK alignment; exact source commit/tag/APK; Binaries and allowed signing
key configured; LF and standard metadata field order; no unnecessary MaintainerNotes;
English fallback + Italian translation; remove verbose release logs; report
actual on-device tests and distinguish them from static/emulated checks.

Reference: https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/
Reference: https://f-droid.org/docs/Reproducible_Builds/

# F-Droid publication preparation — 0.17.6 / 24

Proposed public repository: https://github.com/22pumba22/TVVault
This document does not claim that the remote repository or submission exists.

Ready locally: GPL-3.0-only attribution, confirmed artwork redistribution,
English/Italian metadata and synthetic interface exports, R8 and disabled
dependency metadata. Application Java sources are unchanged from the approved release.

Remaining steps, in order:
1. Create the public repository and enable issues and private vulnerability reporting.
2. Finalize the security reporting contact in SECURITY.md before publication.
3. Commit the publication files and build release from that clean exact commit.
4. Sign interactively using the definitive private key outside the repository;
   verify certificate SHA-256 against the fingerprint recorded in the metadata.
5. Verify reproducibility with F-Droid tools/CI, not just a second local build.
6. Tag v0.17.6 on the verified commit and publish TVVault_v0.17.6_release.apk.
7. Replace @FULL_RELEASE_COMMIT@ in the metadata template. Validate rewritemeta/lint.
8. Submit to F-Droid and follow the maintainers' review. Acceptance is not guaranteed.

The existing TV installation is debug-signed. Do not replace its signature by
uninstalling or clearing data: its local vault would be lost.
The public release uses the separate definitive signing key.

References:
https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/
https://f-droid.org/docs/Reproducible_Builds/

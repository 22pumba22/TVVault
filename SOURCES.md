# Source and attribution record

The supplied TV Vault 0.16.3 archive declares that its Autofill implementation
was ported/reimplemented from Bitwarden mobile v2024.10.0, under GPL-3.0.
This attribution is retained; the project is distributed as GPL-3.0-only,
including the GPL text in LICENSE. No MIT licensing from BlocksTV is reused.

Verified upstream tag: v2024.10.0
Verified commit: a69420257d46fd2d6033c1b3ac05a1284de0b62a
Source: https://github.com/bitwarden/mobile/tree/a69420257d46fd2d6033c1b3ac05a1284de0b62a
License: https://github.com/bitwarden/mobile/blob/a69420257d46fd2d6033c1b3ac05a1284de0b62a/LICENSE.txt
Reference directory: src/App/Platforms/Android/Autofill/

Reference concepts: AutofillService.cs, Parser.cs, Field.cs,
FieldCollection.cs, AutofillHelpers.cs and the vault selection flow.
TV Vault Java counterparts: TvAutofillService, BwField, BwFieldCollection,
AutofillAuthActivity. The original archive did not supply a line-by-line
provenance map. The attribution above records the declared lineage and the
verified source/license, rather than claiming a complete provenance audit.

Original TV Vault extensions include custom AUTOFILL_TYPE_TEXT fields for TV,
offline package matching and AES-GCM/Android Keystore storage. No Bitwarden
networking, accounts, servers or cryptography is bundled.

Changes dated 2026-10-31: optional PIN gate and settings, strict vault error
handling, selective field filling, cross-app confirmation, Android 12+
PendingIntent mutability, localized resources, diagnostic-log removal,
backup/transfer exclusions and F-Droid release preparation.

The original icon and TV banner are preserved. Before public submission the
publisher must confirm redistribution rights for the supplied artwork.

0.17.1 changes: compact single-page Home, corrected generated artwork,
first-open disclosure, secret-character masking, current-PIN authorization
for settings changes and Android-Keystore HMAC verifier with legacy migration.

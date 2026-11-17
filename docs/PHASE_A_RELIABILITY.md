# AI Khata Phase A — Reliability work and verification plan

## Status

This document tracks the reliability-first work requested by the AI Khata Script 2 growth roadmap. It is not a claim that every Phase A item is complete.

## Implemented in this phase

- A read-only SQLite startup check runs on a background thread. It checks SQLite page integrity with PRAGMA quick_check(1) and checks foreign-key consistency with PRAGMA foreign_key_check.
- If the database check fails, AI Khata shows a warning that explicitly says it did not delete or automatically rebuild the local database. This avoids a destructive “repair” that could erase shop records.
- Backup import reads are bounded to 32 MiB, so an unexpectedly huge or hostile file is rejected before it is copied without limit into memory.
- Restore input is validated before the restore transaction starts. Invalid customer, transaction, product or expense rows are rejected before the existing shop data is cleared.
- Deleted-product sale snapshots (quantity and unit cost) are retained when a backup is restored, even where the product itself no longer exists.
- Unit tests cover healthy/corrupt foreign-key states, backup tampering, incorrect passwords, large payloads, oversized input and invalid restore protection.

## Important safety decision

The roadmap mentions an automatic repair message. AI Khata will **not** silently recreate, truncate or replace a database that fails an integrity check. SQLite corruption recovery can be destructive. The app warns the shopkeeper and preserves the source file; restoration should use a known-good encrypted backup after the current data has been copied where possible. An actual repair tool would need a separate, recoverable, tested migration/recovery design.

## Test matrix

| Environment | Automated coverage | Manual verification still required |
|---|---|---|
| Android 6 / API 23 | Robolectric database tests | Install and exercise the APK on a real API 23 device/emulator |
| Android 9 / API 28 | Robolectric database tests | Upgrade from an older install and test backup restore |
| Android 13 / API 33 | Robolectric database tests | Notification permission and background behaviour |
| Android 15 / API 35 | Robolectric database tests | Install, rotate, navigate and test with real files |
| Android 16 / API 36 | Not yet covered by this branch's SDK 35 toolchain | Add after the Android Gradle Plugin / SDK migration; verify the new Play target behaviour |
| Android 17 / API 37 (current stable) | Not yet covered by this branch's SDK 35 toolchain | Add after SDK/toolchain and Robolectric support are upgraded |
| Android 10 / API 29 (target user device) | Not a separate Robolectric matrix target in this first pass | Full manual smoke test on the Infinix X683 |
| Small and large screens | Compose/unit build coverage only | Check overflow, font scaling, landscape and tablet widths |
| Low RAM / slow network / offline | Not fully automated | Exercise core ledgers offline; scan queue/network retry; repeated background/foreground |

Robolectric tests are not a substitute for real-device testing. Current CI builds an APK and runs Python plus Android unit tests; it does not launch an emulator on every Android release.

## Monitoring plan

- Before Play launch: retain CI test results and investigate every reproducible crash before distributing a build.
- After Play launch: use Google Play Console Android vitals for crash and ANR trends, review weekly, and record the app version/device pattern. Do not put customer names, phone numbers, transaction descriptions or amounts in crash reports or analytics.
- No Firebase Crashlytics/analytics SDK is added in this phase. It would require a configured Firebase project, privacy disclosures and a deliberate data-collection decision. Core bookkeeping must not depend on it.

## Remaining Phase A work

- Manually complete the device/screen/network matrix and record the tested device models and Android versions.
- Add a consent-controlled, on-device scan-correction sample store. Keep edited row values on-device; do not upload customer details as telemetry.
- Verify scan queue behaviour with airplane mode, app restart and a slow/unstable connection.
- Repeat restore tests on a real Android 10 device with an older version-1 backup, current version-2 backup, wrong password, tampered file and a large file.
- Configure an actual signed release artifact and verify its signing certificate before treating a build as Play-ready.

## How to test this phase

1. Launch a clean install and confirm it reaches setup normally.
2. Add a customer and a credit entry; close and reopen the app; verify the same balance remains.
3. Create an encrypted backup, restore it to a test install and verify customer, transaction, inventory and expense totals.
4. Try a wrong password and a modified backup file; confirm the app refuses them and does not clear existing records.
5. Try an oversized backup and confirm a clear error appears instead of an app crash.
6. Confirm the integrity test reports a deliberately broken foreign-key relationship as unhealthy. This is a test-only database mutation, not a step to perform on real shop data.


## Google Play / platform readiness note (17 November 2026)

The current Android module uses compileSdk 35, targetSdk 35, minSdk 23 and Android Gradle Plugin 8.7.3. Android 17 (API 37) is the current stable Android release. Google Play requires new apps and app updates submitted from 31 August 2026 to target Android 16 (API 36) or higher. This branch's APK is therefore a **test build, not a Play-submission-ready build**. The next platform task is a separately tested API 36 toolchain migration; the app can keep minSdk 23 so Android 6 and the user's Android 10 device are not intentionally dropped. Do not raise targetSdk until the required Gradle / Android Gradle Plugin versions and behaviour changes have been built and tested.

Official references:
- Google Play target API requirement: https://support.google.com/googleplay/android-developer/answer/11926878
- Android 17 stable release announcement: https://android-developers.googleblog.com/2026/06/Android-17.html
- Android Gradle Plugin / Gradle compatibility table: https://developer.android.com/build/releases/about-agp

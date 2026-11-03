# ScreenWise Android — source package

**Status:** native Kotlin/Compose implementation, **not compiled in the packaging environment**. APK creation and device validation must occur through Android Studio or `.github/workflows/android.yml`. Do not publish as a verified release without running the tests below.

## Architecture

- Android 8.0+ (`minSdk=26`), Android 15 target (`targetSdk=35`), Kotlin 2.0.21, AGP 8.7.3, Gradle 8.10.2, JDK 17.
- Jetpack Compose / Material 3 and lifecycle-aware UI.
- `UsageRepository`: Android Usage Access, event-derived foreground sessions, aggregate fallback for older history.
- `UsageMath`: pure Kotlin interval calculations with midnight/window clipping, screen-off handling and coarse bounds for interrupted sessions.
- Room: limits, manual app categories, focus history, break records and optional **manual** call-minute entries.
- DataStore: persisted goals, theme, language choice, reminders, and local data retention.
- WorkManager: 15-minute minimum periodic checks, approximate delivery, quiet hours and once-per-day deduplication.
- `ReportExporter`: local Storage Access Framework CSV/PDF writing.
- Five main navigation tabs: Home, Apps, Focus, Reports, Settings.

## Important availability limitations

1. There is **no actual call-duration measurement**. The dialer foreground time is labeled separately; manual call-time entry is optional.
2. There is **no system-wide app blocking**. Limits inform optional reminders.
3. Android's UsageEvents history is not guaranteed beyond recent days. Older ranges use aggregated usage statistics without precise sessions.
4. A single foreground-application approximation is used for event sessions. Multi-window and unusual manufacturer behavior may differ from Android's Digital Wellbeing figures. The longest inferred session is bounded to four hours to reduce overcounting from interrupted event streams.
5. Android may delay WorkManager tasks; reminders are not guaranteed to the second. In-app timer displays are live only while the UI runs; battery policies can interrupt timer behavior.
6. Device-unlock counts appear only if corresponding keyguard events are available.
7. Language selection is persisted; Hindi coverage is currently **partial**. Full localization remains an open milestone.
8. A wellness score is a transparent non-medical self-defined indicator; bedtime observations are not inferred and the related points currently remain zero.
9. Category detection is a heuristic based on package labels with manual overrides. These overrides affect category display; automated category goal monitoring currently uses built-in categories.
10. The app is offline-first and has no network permission. Device OS usage history is maintained by Android; deleting ScreenWise records cannot erase that OS history.

## Build on a development computer

Requirements: Android Studio with Android SDK platform 35, build tools 35.0.0, JDK 17, internet access to Google and Maven Central repositories.

This source distribution includes Gradle build files and `gradle-wrapper.properties`. **An official `gradle-wrapper.jar` is not present**, because Gradle could not be downloaded in the packaging environment. `gradlew` and `gradlew.bat` are bootstrap scripts that will invoke a locally installed Gradle if a wrapper JAR has not been generated. With Gradle 8.10.2 installed, run:

```bash
gradle wrapper --gradle-version 8.10.2
./gradlew testDebugUnitTest assembleDebug
```

Or choose **Run workflow** in GitHub Actions; the included workflow downloads Gradle and generates the official wrapper automatically, then builds a debug APK.

The debug APK appears at `app/build/outputs/apk/debug/app-debug.apk`, but **only after successful compilation**. You can install it on your device after verifying its origin.

## Production signing

Generate a release keystore locally using `keytool` (never commit it). Define these environment variables in your local trusted build environment:

- `SCREENWISE_KEYSTORE_PATH`: path to your secure `.jks`.
- `SCREENWISE_STORE_PASSWORD`: keystore password.
- `SCREENWISE_KEY_ALIAS`: signing key alias.
- `SCREENWISE_KEY_PASSWORD`: signing key password.

Then run `./gradlew assembleRelease`. In GitHub Actions, add repository Secrets `SCREENWISE_KEYSTORE_BASE64`, `SCREENWISE_STORE_PASSWORD`, `SCREENWISE_KEY_ALIAS`, `SCREENWISE_KEY_PASSWORD`, and trigger `workflow_dispatch`. The workflow decodes the keystore only in the ephemeral runner and uploads the resulting signed APK as a protected artifact.

**Important:** Debug and release APKs have different signing identities. Do not deploy a debug APK to the public as a release.

## Tests to execute before publication

- `./gradlew testDebugUnitTest`: UsageMath window and switch tests and WellnessScore bounds.
- `./gradlew assembleDebug`: compile and package.
- On at least one Android 8–12 device and one Android 13–15 device: onboarding permission allowed/denied/revoked, dark mode, app sessions, reboots, screen-off intervals, notification permission denied, 15-minute background scheduling, Android OEM battery restrictions, CSV/PDF open/read, focus timer suspension/process recreation, record deletion, and long-range reports.
- Review user-facing permissions and Google Play policy before distribution.

## Folder guide

`app/src/main/java/com/screenwise/app/usage` usage tracking; `data` Room and DataStore; `ui` ViewModel; `worker` local alerts; `export` offline exports; `MainActivity.kt` Compose screens. The app does not connect to WordPress for analytics; WordPress only hosts product information and an administrator-supplied APK link.

### Extra pure-JVM checks without Android SDK

If the Kotlin CLI compiler is installed, run `devtools/run-pure-tests.sh`. This checks six core interval/score behaviors. It does not validate Android-specific source or full app compilation.

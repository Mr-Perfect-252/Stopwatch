# Stopwatch — ApexHub SDK sample app

A clean single-screen stopwatch with lap timing, wired to **both** official ApexHub SDKs:

| SDK | Artifact (Maven Central) | Used for |
|-----|--------------------------|----------|
| **ApexHub OTA SDK** | `io.github.mr-perfect-252:sdk:1.0.1` | In-app updates, background update checks |
| **Apex Analytics** | `io.github.mr-perfect-252:apex-analytics:1.0.0` | Sessions, screen views, event tracking, crash reports |

Registered in the ApexHub store and pre-wired with a real key:

```
Package name   com.apexhub.stopwatch
App name       Stopwatch
Public key     pk_live_W3f33erjLhdt5GId-NCmj1KJNnBvKFX7
Category       Utilities
```

CI builds a signed release APK and prints both SHAs. See
[`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml).

---

## What it does

- **Stopwatch** — Start/Pause, Reset, and Lap. Elapsed time is shown as `MM:SS.cc` (or `H:MM:SS.cc`
  past an hour). Laps are listed newest-first.
- **Version pill** — a small badge under the title shows the installed version (e.g. `v1.0.1`), so it's
  obvious which build is running.
- **OTA updates** — a periodic WorkManager check is scheduled in `StopwatchApp.onCreate()`, and the
  *Check for updates* button runs the full `checkAndPrompt()` flow (check → dialog → download →
  SHA-256 verify → Android installer).
- **Analytics** — sessions, screen views and cold-start timing are captured automatically, and each
  stopwatch action is tracked.

### Events sent (apex-analytics)

| Event | When | Properties |
|---|---|---|
| `app_opened` | `MainActivity.onCreate` | — |
| `stopwatch_started` | Start pressed | — |
| `stopwatch_paused` | Pause pressed | `elapsed_ms` |
| `lap_recorded` | Lap pressed | `lap`, `lap_ms`, `total_ms` |
| `stopwatch_reset` | Reset pressed | — |
| `screen_view` / session / `app_cold_start` | automatic | — |
| crash reports | automatic (user-prompted on next launch) | — |

---

## Where it's wired

- `app/src/main/java/com/apexhub/stopwatch/StopwatchApp.kt` — `Application`; initializes both SDKs
  (`ApexHubUpdater.schedulePeriodicCheck(...)` + `OpenAnalytics.init(...)`) and holds `PUBLIC_KEY`.
- `app/src/main/java/com/apexhub/stopwatch/MainActivity.kt` — the stopwatch screen; OTA
  `checkAndPrompt()`, and `OpenAnalytics.track(...)` on every action.
- `app/src/main/res/layout/activity_main.xml` — the screen layout.
- `app/src/main/AndroidManifest.xml` — declares `POST_NOTIFICATIONS` (the OTA SDK merges
  INTERNET / REQUEST_INSTALL_PACKAGES / RECEIVE_BOOT_COMPLETED itself).

The same `pk_live_…` **public key** activates *both* SDKs — analytics requires it too. Never ship the
`sk_live_…` secret key in an app.

---

## Signing keystore + SHA

A **demo** keystore is committed at `keystore/stopwatch.keystore` (PKCS12) and signs **both** debug and
release. A stable key is required so an in-place OTA update is allowed by Android, and ApexHub pins the
signing certificate fingerprint per app.

```
Keystore        keystore/stopwatch.keystore   (storeType PKCS12)
Store password  apexhub
Key alias       stopwatch
Key password    apexhub

Signing certificate SHA-256 (for ApexHub certificate pinning):
  03:DB:22:96:2E:1C:2A:81:BB:D7:DE:42:98:9D:7F:E5:89:57:16:60:25:5B:33:D2:57:49:28:8B:8D:39:7C:69

Signing certificate SHA-1:
  63:F5:BF:8A:1C:C4:F7:6F:26:08:4E:C6:A4:85:8E:7B:25:49:F7:AF
```

The workflow also prints the **APK's own SHA-256** after each build (that's the value ApexHub stores
and the SDK verifies on download). Replace this demo keystore with your own before shipping anything
real — never commit a production keystore.

---

## Build

CI (recommended — no local Android SDK needed): push to `main` or run the **Build APK** workflow
manually; download the `stopwatch-release` artifact.

Local:

```bash
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # signed release APK
```

Requires JDK 17 and the Android SDK (platform 34, build-tools 34.0.0).

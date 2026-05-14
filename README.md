# REACT — Rapid Emergency Alert & Contact Tap

A native Android emergency SOS application built for UP Diliman campus safety. One tap sends an SMS alert with GPS coordinates to configured emergency contacts and optionally places a phone call.

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Project Structure](#project-structure)
- [Source Files](#source-files)
  - [App Layer](#app-layer)
  - [Emergency Layer](#emergency-layer)
  - [SMS Layer](#sms-layer)
  - [Location Layer](#location-layer)
  - [Widget Layer](#widget-layer)
  - [Permissions Layer](#permissions-layer)
  - [Data Layer](#data-layer)
- [Configuration (SharedPreferences)](#configuration-sharedpreferences)
- [Permissions](#permissions)
- [Build Configuration](#build-configuration)
- [Emergency Flow](#emergency-flow)

---

## Overview

| Field | Value |
|---|---|
| Package | `com.example.campussos` |
| Min SDK | 23 (Android 6.0 Marshmallow) |
| Target SDK | 35 (Android 15) |
| Language | Kotlin |
| Key Dependency | Google Play Services Location 21.3.0 |

REACT is designed for speed and reliability. The SOS button is always one tap away — no login, no confirmation dialogs by default. An on-screen 3-second countdown gives the user a chance to cancel an accidental trigger before the alert fires.

---

## Architecture

```
User taps SOS
     │
     ▼
MainActivity
     │  checks SMS permission
     ▼
EmergencyDispatchService   ◄── Foreground Service
     │
     ├─ 3-second countdown (cancelable via notification)
     │
     ▼
executeEmergencyTrigger()
     │
     ├─── sendEmergencySms()
     │         │
     │         ├─ awaitEmergencyLocation()  ──► LocationProvider
     │         │         (4s timeout, GPS + Network)
     │         │
     │         ├─ EmergencyMessageBuilder
     │         │         (base message + coordinates)
     │         │
     │         └─ SmsDispatcher
     │                   (multipart SMS to all contacts)
     │
     └─── placeOptionalCall()
               └─ EmergencyCallHandler
                         (calls configured number if enabled)
```

---

## Project Structure

```
app/src/main/
├── AndroidManifest.xml
├── java/com/example/campussos/
│   ├── app/
│   │   ├── SplashActivity.kt
│   │   ├── MainActivity.kt
│   │   ├── SettingsActivity.kt
│   │   ├── PermissionExplanationActivity.kt
│   │   └── EmergencyConfirmActivity.kt
│   ├── emergency/
│   │   ├── EmergencyDispatchService.kt
│   │   ├── EmergencyMessageBuilder.kt
│   │   ├── EmergencyCallHandler.kt
│   │   ├── EmergencyCallResult.kt
│   │   ├── EmergencyCoordinator.kt        (placeholder)
│   │   └── DispatchRetryPolicy.kt         (placeholder)
│   ├── sms/
│   │   ├── SmsDispatcher.kt
│   │   ├── SmsSendResult.kt
│   │   └── SmsStatusReceiver.kt           (placeholder)
│   ├── location/
│   │   ├── LocationProvider.kt
│   │   ├── LocationResult.kt
│   │   └── LocationFormatter.kt
│   ├── widget/
│   │   ├── SosWidgetProvider.kt
│   │   └── SosWidgetReceiver.kt
│   ├── permissions/
│   │   └── PermissionChecker.kt
│   ├── boot/
│   │   └── BootReceiver.kt                (placeholder)
│   ├── data/
│   │   ├── EmergencyContact.kt
│   │   ├── DispatchLog.kt
│   │   ├── EmergencyContactRepository.kt  (placeholder)
│   │   ├── EmergencyPreferencesRepository.kt (placeholder)
│   │   └── DispatchLogRepository.kt       (placeholder)
│   └── notifications/
│       └── EmergencyNotificationManager.kt (placeholder)
└── res/
    ├── layout/
    │   ├── activity_splash.xml
    │   ├── activity_main.xml
    │   ├── activity_settings.xml
    │   ├── activity_permission_explanation.xml
    │   ├── activity_emergency_confirm.xml
    │   └── widget_sos.xml
    ├── drawable/         (backgrounds, icons, shapes)
    ├── anim/             (splash screen animations)
    ├── values/
    │   ├── strings.xml
    │   ├── colors.xml
    │   └── themes.xml
    └── xml/
        └── sos_widget_info.xml
```

---

## Source Files

### App Layer

#### `SplashActivity.kt`
The entry point of the app. Declared as the `LAUNCHER` activity in the manifest.

Loads `activity_splash.xml` and immediately starts 5 simultaneous animations:

| View ID | Animation | Effect |
|---|---|---|
| `splash_ring_outer` | `splash_ring_pulse` | Scale 0.86→1.06 + fade, infinite pulse |
| `splash_ring_inner` | `splash_ring_pulse_delayed` | Same pulse with 500ms offset |
| `splash_logo_container` | `splash_logo_enter` | Scale 0.55→1.0 + fade in, 700ms |
| `splash_tagline` | `splash_text_enter` | Slide up + fade in |
| `splash_bottom` | `splash_bottom_enter` | Fade in from below |

After **2400ms**, it starts `MainActivity` and calls `finish()` so the back button does not return to the splash.

---

#### `MainActivity.kt`
The main screen. Contains the SOS button, permission setup section, and navigation to Settings.

**SOS button behavior:**
- If SMS permission is missing → opens `PermissionExplanationActivity` for SMS
- If SMS permission is granted → calls `EmergencyDispatchService.start(context)`

**Permission buttons:**
- Location button → opens `PermissionExplanationActivity` for location (only if permission is not already granted)
- Call button → opens `PermissionExplanationActivity` for call permission (only if permission is not already granted)

`MainActivity` is declared `android:exported="false"` — it cannot be launched externally, only from `SplashActivity`.

---

#### `SettingsActivity.kt`
Hosts `activity_settings.xml`, which allows the user to configure:
- Emergency contact phone numbers (comma-separated or one per field)
- Custom SOS message text
- Emergency call number
- Toggle to include GPS coordinates in SMS

Settings are persisted to `SharedPreferences` under the name `"emergency_config"` and read by `EmergencyDispatchService` at trigger time.

---

#### `PermissionExplanationActivity.kt`
A reusable explanation screen shown before requesting a runtime permission. Prevents the system dialog from appearing without context.

Supports three permission types via the internal `PermissionType` enum:

| Type | Permission | Launched via |
|---|---|---|
| `Sms` | `SEND_SMS` | `startForSms(context)` |
| `Call` | `CALL_PHONE` | `startForCall(context)` |
| `Location` | `ACCESS_FINE_LOCATION` | `startForLocation(context)` |

The activity sets the explanation title and body from `strings.xml` based on the permission type, then presents two buttons:
- **Allow Permission** — calls `requestPermissions()` with the relevant permission string
- **Not Now** — calls `finish()` without requesting

When the system permission dialog closes (grant or deny), `onRequestPermissionsResult()` fires `finish()` to return to the previous screen.

---

#### `EmergencyConfirmActivity.kt`
A minimal activity that loads `activity_emergency_confirm.xml`. Intended as a confirmation screen before triggering the alert. Currently a shell — not wired into the main flow.

---

### Emergency Layer

#### `EmergencyDispatchService.kt`
The core of the app. A foreground `Service` that orchestrates the entire emergency flow.

**How it starts:**

```kotlin
EmergencyDispatchService.start(context)
// Uses startForegroundService() on API 26+, startService() below
```

**State machine:**

```
onStartCommand()
  → ACTION_CANCEL  → cancelEmergencyFlow()
  → (any other)   → startConfirmationCountdown()
```

**Countdown phase (`startConfirmationCountdown`):**
1. Calls `startForeground()` immediately with a countdown notification
2. Posts a self-rescheduling `Runnable` to `mainHandler` that fires every 1 second
3. Notification shows "Emergency alert starting — Sending in N seconds" with a **Cancel** action
4. When `remainingSeconds` reaches 0, calls `executeEmergencyTrigger()`
5. If Cancel is tapped, `ACTION_CANCEL` intent arrives → `cancelEmergencyFlow()` sets `isCanceled = true`, removes the runnable, posts a canceled notification, and stops the service

**Dispatch phase (`executeEmergencyTrigger`):**
Runs SMS and call logic on a background thread via `executor` (single-thread `ExecutorService`) so the main thread is never blocked:
1. `sendEmergencySms()` — awaits location, builds message, dispatches SMS
2. `placeOptionalCall()` — places call if configured
3. Posts a final completion notification on the main thread and stops the service

**Location await (`awaitEmergencyLocation`):**
```kotlin
// Blocks the background executor thread for up to 4500ms
val latch = CountDownLatch(1)
LocationProvider(applicationContext).getEmergencyLocation(4000ms) { result ->
    result.set(result)
    latch.countDown()
}
latch.await(4500ms, MILLISECONDS)
```
The location request runs on `applicationContext` — `FusedLocationProviderClient` handles its own threading. The latch ensures SMS is not sent until a location result (success, timeout, or unavailable) is known.

**SharedPreferences keys** (all in `"emergency_config"`):

| Key | Type | Default | Purpose |
|---|---|---|---|
| `sms_contacts` | `Set<String>` | empty | Phone numbers to SMS |
| `sms_message` | `String` | default message | Base alert text |
| `include_location` | `Boolean` | `true` | Append GPS coordinates |
| `call_enabled` | `Boolean` | `false` | Place auto-call |
| `call_number` | `String` | null | Number to call |

---

#### `EmergencyMessageBuilder.kt`
Builds the final SMS string from a base message and an optional location.

```kotlin
buildEmergencyMessage(
    baseMessage = "REACT SOS: I need help on campus.",
    includeLocation = true,
    locationResult = LocationResult.Available(14.6543, 121.0614, "...")
)
// → "REACT SOS: I need help on campus.\nLocation: 14.6543, 121.0614"
```

Rules:
- If `includeLocation` is `false` or `locationResult` is not `Available` → returns `baseMessage` only
- If location is available → appends `\nLocation: <latitude>, <longitude>`
- If `baseMessage` is blank → uses `DEFAULT_MESSAGE = "Emergency SOS: I need help on campus."`

---

#### `EmergencyCallHandler.kt`
Places an emergency phone call via `ACTION_CALL` intent.

Called with:
- `enabled: Boolean` — if `false`, returns `EmergencyCallResult.Skipped` immediately
- `phoneNumber: String?` — if null/blank, returns `EmergencyCallResult.NoNumber`

Checks `CALL_PHONE` permission before attempting the call. Returns an `EmergencyCallResult` sealed class variant.

---

#### `EmergencyCallResult.kt`

```kotlin
sealed class EmergencyCallResult {
    object Started           // Call intent fired successfully
    object Skipped           // Call not enabled in settings
    object NoNumber          // Call enabled but no number configured
    object MissingPermission // CALL_PHONE permission not granted
    data class Failure(val reason: String) // Exception message
}
```

---

### SMS Layer

#### `SmsDispatcher.kt`
Sends the emergency SMS to all configured contacts.

```kotlin
SmsDispatcher(context).sendEmergencySms(
    phoneNumbers = listOf("+639123456789"),
    message = "REACT SOS: I need help on campus.\nLocation: 14.65, 121.06"
)
```

Steps:
1. Checks `SEND_SMS` permission — returns `SmsSendResult.MissingPermission` if missing
2. Filters blank/empty numbers from the list
3. Gets `SmsManager` — uses `context.getSystemService(SmsManager::class.java)` on API 31+, `SmsManager.getDefault()` below (for compatibility)
4. For each number: calls `divideMessage()` to split into 160-char parts, then `sendMultipartTextMessage()`
5. Returns `SmsSendResult.Success(count)` or `SmsSendResult.Failure(reason)` on exception

> **Note on dual-SIM devices:** `SmsManager.getDefault()` uses the default SMS SIM slot. On MIUI/HyperOS devices, ensure the SMS SIM is set correctly in system settings, and that the app is not restricted by battery optimization or MIUI permissions.

---

#### `SmsSendResult.kt`

```kotlin
sealed class SmsSendResult {
    data class Success(val requestedCount: Int) : SmsSendResult()
    object MissingPermission : SmsSendResult()
    object NoContacts        : SmsSendResult()
    data class Failure(val reason: String) : SmsSendResult()
}
```

---

#### `SmsStatusReceiver.kt`
A `BroadcastReceiver` shell for tracking SMS delivery/sent status. Not yet implemented — currently a placeholder for future delivery confirmation.

---

### Location Layer

#### `LocationProvider.kt`
Fetches the device's current GPS position using the **Google Play Services Fused Location API**.

```kotlin
LocationProvider(context).getEmergencyLocation(timeoutMillis = 4000L) { result ->
    when (result) {
        is LocationResult.Available -> // use result.latitude, result.longitude
        LocationResult.Timeout      -> // location took too long
        LocationResult.Unavailable  -> // GPS/network returned null
        LocationResult.PermissionDenied -> // permission not granted
    }
}
```

**Strategy (in order):**
1. Try `fusedLocationClient.lastLocation` (cached — immediate, free)
2. If cached location is null → request `getCurrentLocation()` with `PRIORITY_HIGH_ACCURACY`
3. A timeout `Runnable` fires after `timeoutMillis` (default 4000ms) and delivers `LocationResult.Timeout`
4. `AtomicBoolean` ensures the callback fires exactly once regardless of which path resolves first

All callbacks are delivered on the **main thread** via a `mainExecutor` wrapper around `Handler(Looper.getMainLooper())`.

---

#### `LocationResult.kt`

```kotlin
sealed class LocationResult {
    data class Available(
        val latitude: Double,
        val longitude: Double,
        val mapsLink: String      // Google Maps deep link (built but not currently sent in SMS)
    ) : LocationResult()

    object Unavailable      // No fix available
    object PermissionDenied // ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION not granted
    object Timeout          // No fix within the timeout window
}
```

---

#### `LocationFormatter.kt`
Utility that converts an `android.location.Location` into a Google Maps URL string. Used internally by `LocationProvider` when constructing `LocationResult.Available`.

---

### Widget Layer

#### `SosWidgetProvider.kt`
An `AppWidgetProvider` that places a one-tap SOS button on the Android home screen.

On each `onUpdate()` call (triggered by the system when the widget is added or refreshed):
- Builds a `PendingIntent` that broadcasts `ACTION_SOS_WIDGET_CLICK` to `SosWidgetReceiver`
- Applies it to the `widget_sos_button` view inside `RemoteViews`

Widget metadata (size, preview, update period) is defined in `res/xml/sos_widget_info.xml`.

---

#### `SosWidgetReceiver.kt`
A `BroadcastReceiver` that intercepts `ACTION_SOS_WIDGET_CLICK` and calls `EmergencyDispatchService.start(context)`. This is the bridge between the home-screen widget tap and the service.

---

### Permissions Layer

#### `PermissionChecker.kt`
Stateless helper that checks whether each runtime permission still needs to be requested.

```kotlin
val checker = PermissionChecker(context)
checker.needsSmsPermission()      // SEND_SMS
checker.needsLocationPermission() // ACCESS_FINE_LOCATION
checker.needsCallPermission()     // CALL_PHONE
```

On API < 23 (Android < 6), all methods return `false` (permissions are granted at install time).

---

### Data Layer

The data layer contains model classes and repository stubs for future persistence.

#### `EmergencyContact.kt`
```kotlin
data class EmergencyContact(val id: Long, val name: String, val phoneNumber: String)
```

#### `DispatchLog.kt`
```kotlin
data class DispatchLog(val id: Long, val timestamp: Long, val status: String)
```

#### Placeholder repositories
`EmergencyContactRepository`, `EmergencyPreferencesRepository`, `DispatchLogRepository`, and `EmergencyNotificationManager` are empty classes scaffolded for future Room database or file-based persistence. They have no active role in the current build.

---

## Configuration (SharedPreferences)

All user settings are stored in a single `SharedPreferences` file named `"emergency_config"`. This file is read at trigger time by `EmergencyDispatchService`.

| Key | Type | Set by | Read by |
|---|---|---|---|
| `sms_contacts` | `Set<String>` | `SettingsActivity` | `EmergencyDispatchService` |
| `sms_message` | `String` | `SettingsActivity` | `EmergencyDispatchService` |
| `include_location` | `Boolean` | `SettingsActivity` | `EmergencyDispatchService` |
| `call_enabled` | `Boolean` | `SettingsActivity` | `EmergencyDispatchService` |
| `call_number` | `String` | `SettingsActivity` | `EmergencyDispatchService` |

If `sms_message` is blank or absent, the service falls back to `R.string.default_emergency_message` (`"Emergency SOS: I need help on campus."`).

---

## Permissions

| Permission | Why |
|---|---|
| `SEND_SMS` | Send emergency text messages |
| `CALL_PHONE` | Place emergency phone call (optional feature) |
| `ACCESS_FINE_LOCATION` | Get precise GPS coordinates for SMS |
| `ACCESS_COARSE_LOCATION` | Fallback location when fine is unavailable |
| `FOREGROUND_SERVICE` | Run `EmergencyDispatchService` in the foreground |
| `FOREGROUND_SERVICE_LOCATION` | Required on Android 14+ for foreground services accessing location |
| `POST_NOTIFICATIONS` | Show countdown and status notifications (required on Android 13+) |
| `RECEIVE_BOOT_COMPLETED` | Reserved for future boot persistence (`BootReceiver` placeholder) |

SMS and call permissions are **runtime permissions** (Android 6+) requested through `PermissionExplanationActivity`. Location permission is also runtime. The app functions for SMS-only dispatch even if location or call permissions are denied.

---

## Build Configuration

```
compileSdk   35   (Android 15)
targetSdk    35
minSdk       23   (Android 6.0 — covers ~99% of active devices)
versionCode  1
versionName  1.0
```

**Dependencies:**
```
com.google.android.gms:play-services-location:21.3.0
```

No other external libraries. No Jetpack Compose, no Room, no Retrofit.

**Build toolchain:**
```
Android Gradle Plugin  8.7.3
Kotlin                 2.0.21
Java compatibility     VERSION_17
```

---

## Emergency Flow

A complete walkthrough of what happens when a user taps **SOS**:

```
1. User taps SOS button in MainActivity
   └─ PermissionChecker checks SEND_SMS
      ├─ Missing → PermissionExplanationActivity (SMS) → request → return
      └─ Granted → EmergencyDispatchService.start(context)

2. EmergencyDispatchService starts as foreground service
   └─ startForeground() posts notification: "Emergency alert starting — Sending in 3 seconds"
      ├─ Notification has [Cancel] action button
      └─ Countdown ticks: 3 → 2 → 1 → 0

3a. User taps Cancel
    └─ ACTION_CANCEL intent → cancelEmergencyFlow()
       └─ Notification: "Emergency alert canceled" → service stops

3b. Countdown reaches 0 → executeEmergencyTrigger()
    └─ Background thread starts

4. sendEmergencySms()
   ├─ Reads contacts, message, include_location from SharedPreferences
   ├─ awaitEmergencyLocation() — blocks up to 4.5s
   │   └─ LocationProvider tries lastLocation, then getCurrentLocation
   │       ├─ Got fix → LocationResult.Available(lat, lon, mapsLink)
   │       ├─ Timeout  → LocationResult.Timeout
   │       └─ No GPS   → LocationResult.Unavailable
   ├─ EmergencyMessageBuilder builds final string
   │   ├─ Location available → "REACT SOS: I need help on campus.\nLocation: 14.6543, 121.0614"
   │   └─ No location        → "REACT SOS: I need help on campus."
   └─ SmsDispatcher.sendEmergencySms(contacts, message)
       └─ sendMultipartTextMessage() to each contact

5. placeOptionalCall()
   ├─ call_enabled = false → Skipped
   └─ call_enabled = true  → ACTION_CALL intent with configured number

6. Completion notification posted, service stops
```

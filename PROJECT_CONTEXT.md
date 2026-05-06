PROJECT: Campus One-Tap Emergency App (Android)

GOAL:
A home-screen widget that allows students to trigger an emergency alert to campus security with optional GPS location via SMS and/or call.

CORE PRINCIPLES:
- 1 tap / 1 action emergency trigger
- Must work offline (SMS fallback)
- Location optional but preferred
- Minimal UI, optimized for panic usage
- No login system

TARGET USERS:
University students and staff on campus

PRIMARY FLOW:
Widget → Confirmation → Send SMS + optional call → Status screen

TECH STACK:
- Kotlin
- Android SDK (min API 23+)
- FusedLocationProviderClient
- SmsManager
- AppWidgetProvider
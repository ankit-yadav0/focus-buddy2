# Focuss Buddy

**Focuss Buddy** is a distraction-blocking and study-planning Android app, built end-to-end in Kotlin and Jetpack Compose. It combines an app/website blocker, a strict "digital lockdown" mode, and daily time quotas into a single app aimed at focused exam prep.

## ✨ Features

**Distraction blocking**
- Accessibility-service-based app and website blocker (Chrome/Brave/Firefox/Edge/Opera/Samsung Browser aware, full-TLD domain matching)
- Exact YouTube Shorts / Instagram Reels / Spotify Spotlight detection and blocking
- Daily time-quota system per app (hours/minutes/seconds pickers, live progress ring, midnight reset)

**Strict Mode**
- A 4-step setup wizard for a hard-lockdown session (custom duration, up to 365 days)
- Device Admin + Accessibility based anti-tamper: attempts to uninstall the app, disable its accessibility service, or deactivate its device-admin access are silently backed out of
- Reset options / Factory reset / Device admin apps list / Special app access are blocked from within Settings for the duration of a Strict session
- App uninstallation is blocked system-wide while a Strict session is active

**Auto-update**
- In-app updater checks GitHub Releases for a newer signed build and offers a one-tap update, preserving all local data (same signing key = Android treats it as an in-place update, not a reinstall)

## 🛠 Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose
- **Persistence:** Room
- **Enforcement:** AccessibilityService + Device Admin (DevicePolicyManager)
- **Target device:** tested primarily on a Realme C11 2021 (2GB RAM, Android 11 Go Edition)

## 🚀 Run Locally

**Prerequisites:** [Android Studio](https://developer.android.com/studio)

1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project
4. Create a file named `.env` in the project directory and set `GEMINI_API_KEY` in it to your Gemini API key (see `.env.example`)
5. Remove this line from the app's `build.gradle.kts` file: `signingConfig = signingConfigs.getByName("debugConfig")`
6. Run the app on an emulator or physical device

## 📦 CI / Releases

Pushes to `main` build both debug and release APKs via GitHub Actions (`.github/workflows/main2.yml`). The release build is signed with the repo's `KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` secrets and published as a GitHub Release tagged `v<versionCode>`, which the app's in-app updater checks for automatically.

## ⚠️ Required Permissions

Focuss Buddy needs Accessibility Service access (to detect and block apps/sites) and Device Admin access (to resist uninstall attempts during a Strict session). These are powerful permissions — only install builds you built or trust.

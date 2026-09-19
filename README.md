# Focuss Buddy

**Focuss Buddy** is a distraction-blocking and study-planning Android app, built end-to-end in Kotlin and Jetpack Compose. It combines an app/website blocker, a strict "digital lockdown" mode, daily time quotas, and progress tracking (streaks, reflections, plant-growth visuals) into a single app aimed at focused exam prep.

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

**Progress tracking**
- Session-end reflection prompts, streak tracking with freeze tokens, subject-wise progress, and plant-growth visuals tied to syllabus completion

**Auto-update**
- In-app updater checks GitHub Releases for a newer signed build and offers a one-tap update, preserving all local data (same signing key = Android treats it as an in-place update, not a reinstall)

## 🛠 Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose
- **Persistence:** Room
- **Enforcement:** AccessibilityService + Device Admin (DevicePolicyManager)
- **Target device:** tested primarily on a Realme C11 2021 (2GB RAM, Android 11 Go Edition)

## ⚠️ Required Permissions

Focuss Buddy needs Accessibility Service access (to detect and block apps/sites) and Device Admin access (to resist uninstall attempts during a Strict session). These are powerful permissions — only install builds you built or trust.


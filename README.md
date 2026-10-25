<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Run and deploy your AI Studio app

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/2d03a1d8-3dbe-4602-abb3-2bc666433114

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)


1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Create a file named `.env` in the project directory and set `GEMINI_API_KEY` in that file to your Gemini API key (see `.env.example` for an example)
5. Remove this line from the app's `build.gradle.kts` file: `signingConfig = signingConfigs.getByName("debugConfig")`
6. Run the app on an emulator or physical device

## Banking Pause (Navi / GPay / PhonePe ...)

Banking apps refuse to run while a user-installed accessibility service is on. With Banking Pause,
Focuss Buddy switches its own accessibility service off when a chosen app opens and back on when
it is left (hard cap: 10 minutes). Setup, once:

1. App > Banking Pause > grant **Usage access**.
2. Grant the permission that lets the app switch itself back on (adb, Android 11 wireless debugging):
   `adb shell pm grant com.aistudio.focussbuddy.kxmpzq android.permission.WRITE_SECURE_SETTINGS`
   Turn Wireless debugging off again afterwards; the grant survives reboots and in-place updates.
3. Switch Banking Pause on and tick the banking apps. The list is frozen while Strict Mode runs.

Safety nets: persisted marker + recovery alarm (fires even if the watcher dies), boot-time restore,
"tap to turn back on" notification if re-enabling fails, "Resume now" action on the pause notification.

# WifiLocker

Android Studio project. Package: `com.wifilocker`.

## Build the APK locally
1. Open this folder in Android Studio (latest stable).
2. Let Gradle sync (it will download the Gradle wrapper / dependencies).
3. Build > Build Bundle(s) / APK(s) > Build APK(s).
   Or from terminal: `./gradlew assembleDebug`
4. APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Notes
- Requires Android 6.0+ (minSdk 23).
- On Android 10+, `ACCESS_FINE_LOCATION` must be granted at runtime to read the connected Wi-Fi SSID — the app requests it on launch.
- Accounts are persisted via SharedPreferences (JSON via Gson).
- Password formula: `SHA-256(currentSSID + ":" + savedPassword + ":" + "YYYY-M-D-H")` → first 12 hex chars.

# Phone Backup — Android

Android companion app for **Phone Backup by PaliaAPK HUB**.

## Permission flow
The app intentionally uses Android's Storage Access Framework (`ACTION_OPEN_DOCUMENT_TREE`) instead of asking for broad `MANAGE_EXTERNAL_STORAGE` access. On first use, tap **Add folder**; Android's own system folder picker appears and the user grants access to each folder. The grant is retained when the document provider supports persistable permissions.

`INTERNET` is a normal permission and does not display a runtime dialog. No unnecessary contacts, SMS, call-log, camera, microphone, or all-files permissions are requested.

## Features
- Add multiple folders using Android's native folder picker.
- Display the folders for which access was granted.
- Start/stop a local HTTP sharing server on port `8765`.
- Generate a six-digit pairing code each time sharing starts.
- Provide folder/file listing and individual file download endpoints for the Windows companion.

## Android limitations
Only folders explicitly selected by the user are available. `Android/data`, `Android/obb`, private app data, and protected system data may be blocked by Android. This is a file backup/transfer tool, not a full-device image or app-data clone.

## Build APK
Use JDK 17 and Android SDK Platform 35. Open this `android` folder in Android Studio and select **Build > Build Bundle(s) / APK(s) > Build APK(s)**. Debug APK output: `app/build/outputs/apk/debug/app-debug.apk`.

Keep the app open during transfers in this first version. Use the pairing code only on a trusted Wi-Fi/hotspot network and tap **Stop sharing** when done.

## API
- `GET /api/status?code=123456`
- `GET /api/files?code=123456`
- `GET /api/download?id=<base64url-document-uri>&code=123456`

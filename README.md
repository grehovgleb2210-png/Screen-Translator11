# ScreenTranslator

Android app that captures the screen after explicit user approval, recognizes Latin-script text with ML Kit Text Recognition, translates English to Russian with ML Kit Translate, and displays the result in a floating overlay.

## Requirements
- Android 8.0 (API 26) or newer.
- Internet connection for the first download of the English-to-Russian translation model. ML Kit can use the downloaded model on-device afterward.
- User approval for screen capture and the Android "display over other apps" permission.

## Build locally
Open this folder in Android Studio (recent version), allow Gradle sync, then run the `app` configuration or use:

```bash
gradle :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Build on GitHub
1. Create a GitHub repository, for example `ScreenTranslator`.
2. Extract this ZIP on your device/computer. Upload the **contents** of the extracted `ScreenTranslator` folder to the repository root. Do not upload the ZIP as one file.
3. Confirm that `.github/workflows/android.yml` exists in the repository. On GitHub, enable Actions if prompted.
4. Open the repository's **Actions** tab.
5. Select **Build Android APK** and press **Run workflow**. A push to the `main` branch also starts a build automatically.
6. Wait for the run to finish with a green check. Open that run, scroll to **Artifacts**, and download `ScreenTranslator-debug-apk`.
7. Extract the downloaded artifact ZIP. It contains `app-debug.apk`. Transfer it to the Android phone and install it. Android may ask you to allow installation from that browser/file manager.

## Notes
- This is a debug APK, suitable for testing. It is not a Play Store release and is not signed with a personal release key.
- Android always displays its own screen-capture consent prompt. The app does not bypass that permission.
- Screen contents are processed on-device by ML Kit. The app does not send screenshots to a server. The first translation model download requires internet.
- Screen capture may be blocked by Android for protected content, DRM video, or apps that use secure windows.
- This is a first working project baseline; test on the target phone because manufacturer-specific Android restrictions can affect overlays and background services.

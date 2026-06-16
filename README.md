# Safe Call Reminder

A safe Android assignment project that helps you call a number and manually retry if the call was busy, cut, or not answered.

## Safety note
This app intentionally does **not** do continuous hidden auto-calling or auto-redial loops. It opens the Android dialer with the number, then the user manually presses call. After the call, return to the app and choose:

- **Answered → Finish**
- **Busy / Cut / No Answer → Retry**
- **Stop Task**

## Features

- Phone number input
- Retry delay input
- Maximum retry limit
- Beautiful simple UI
- No dangerous call permission required
- GitHub Actions workflow to build debug APK

## Build on GitHub

1. Upload all files to a GitHub repository.
2. Go to **Actions** tab.
3. Open **Android Debug APK Build** workflow.
4. Click **Run workflow** or push code to `main`.
5. Download APK from workflow **Artifacts**.

## Local build

```bash
gradle :app:assembleDebug
```

Output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Default Phone App / Built-in Dialer Patch

This build adds Android default phone app support:

- Handles `Intent.ACTION_DIAL` so Android can treat this as a dialer candidate.
- Adds a **Set as Default Phone App** button.
- Uses `RoleManager.ROLE_DIALER` on Android 10+ and `TelecomManager.ACTION_CHANGE_DEFAULT_DIALER` on older supported Android versions.
- Adds `SafeInCallService` with `android.telecom.IN_CALL_SERVICE_UI=true` and `android.telecom.IN_CALL_SERVICE_RINGING=true`.
- Adds `InCallActivity` with Answer, End/Reject, Hold/Unhold, and Speaker buttons.

Important: User must manually approve this app as the default phone app in the Android role dialog. Android will always use the preloaded dialer for emergency calls.

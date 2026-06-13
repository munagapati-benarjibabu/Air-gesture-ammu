# Ammu ProjectAir Requirements

## Project Location

Open this folder in Android Studio:

`/Users/munagapatibenarji/Documents/ammu projectair`

## Development Software

- Android Studio with Android SDK
- JDK 17 or newer
- Android SDK Platform 36
- Android SDK Build Tools
- Android Platform Tools (`adb`)
- Git, optional
- USB cable and an Android phone with USB debugging enabled

## Android Requirements

- Minimum Android version: Android 7.0 (API 24)
- Target Android SDK: API 36
- Front camera for air gestures
- Microphone for voice commands
- Location services for lost-phone tracking
- Accessibility Service enabled for scroll, back, home, and recent apps

## Main Libraries

- Kotlin and Jetpack Compose
- AndroidX Activity and Lifecycle
- CameraX 1.5.3
- MediaPipe Tasks Vision 0.10.20
- Google Play Services Location 21.3.0
- Kotlin Coroutines 1.9.0
- Android Mail 1.6.7

## Runtime Permissions

- Microphone
- Camera
- Fine or coarse location
- Notifications
- Read phone state
- Answer phone calls
- Foreground camera and location services

## Local Configuration

The project reads private configuration from `local.properties`.
Use `local.properties.example` as the field reference.

Required or optional values:

- `sdk.dir`
- `OPENAI_API_KEY`
- `LOST_PHONE_EMAIL_TO`
- `LOST_PHONE_EMAIL_FROM`
- `LOST_PHONE_SMTP_HOST`
- `LOST_PHONE_SMTP_PORT`
- `LOST_PHONE_SMTP_USERNAME`
- `LOST_PHONE_SMTP_PASSWORD`
- `LOST_MODE_SECRET_CODE`

Do not upload `local.properties` to GitHub. Revoke and replace any API key
that has previously been shared in messages or committed to source control.

For Gmail SMTP, use an App Password instead of the normal Gmail password.

## Build

From Terminal:

```bash
cd "/Users/munagapatibenarji/Documents/ammu projectair"
./gradlew :app:assembleDebug
```

The debug APK is generated at:

`app/build/outputs/apk/debug/app-debug.apk`

## Install On USB Device

1. Enable Developer Options on the phone.
2. Enable USB debugging.
3. Connect the phone and approve the debugging prompt.
4. Check the connection:

```bash
adb devices
```

5. Install and run from Android Studio, or use:

```bash
./gradlew :app:installDebug
```

## Required Phone Setup

1. Allow the requested app permissions.
2. Open Android Accessibility settings.
3. Enable `Ammu No Touch Control`.
4. For voice commands, tap `Tap To Talk`, then speak a command.
5. Start Air Gestures before using hand gestures in other apps.

## Current Features

- Tap-to-talk voice commands
- Open YouTube, WhatsApp, and Android Settings
- Accessibility scroll, back, home, and recent-app actions
- Media play/pause control
- CameraX and MediaPipe hand-gesture recognition
- Foreground air-gesture camera service
- Sensor and proximity gesture controls
- Lost-phone location email service

## Important Limitations

- Android's normal `SpeechRecognizer` is not a true always-on wake-word
  engine. Automatic repeated listening can cause system microphone sounds,
  so this version uses `Tap To Talk`.
- Accessibility must remain enabled for controlling other applications.
- Some phone and call actions depend on Android version and manufacturer
  restrictions.
- Location email requires valid SMTP configuration and network access.


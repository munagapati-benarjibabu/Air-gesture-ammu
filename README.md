# Air Gesture Ammu

Air Gesture Ammu is an Android assistant project that combines tap-to-talk voice commands, CameraX + MediaPipe hand gesture recognition, Android Accessibility Service actions, call controls, sensor controls, and lost-phone location email support.

## Main Features

- Tap-to-talk voice command interface
- Open apps such as YouTube, WhatsApp, and Android Settings
- Accessibility actions for scroll, back, home, and recent apps
- CameraX foreground service for air gestures
- MediaPipe hand recognition for swipe, open palm, and fist gestures
- Media play/pause from hand gesture
- Sensor and proximity gesture controls
- Lost-phone location email service using SMTP

## Requirements

- Android Studio
- JDK 17 or newer
- Android SDK Platform 36
- Android phone with USB debugging enabled
- Microphone, camera, location, phone, notification, and accessibility permissions

## Build

```bash
./gradlew :app:assembleDebug
```

## Install

```bash
./gradlew :app:installDebug
```

## Run

Open the app on the phone. For voice commands, tap `Tap To Talk` and say a command such as:

```text
open YouTube
scroll down
go home
recent apps
start air gesture
```

## Configuration

Copy `local.properties.example` to `local.properties` and fill the required private values. Do not commit `local.properties` because it can contain API keys and email passwords.

## Documentation

See `REQUIREMENTS.md` for project setup, permissions, configuration, and testing details.

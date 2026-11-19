# LocalAlert

LocalAlert is an Android app for sending quick, full-screen alerts between phones connected to the same Wi-Fi network.

## Features

- Send a custom text alert to a paired phone on the local Wi-Fi network.
- Discover compatible LocalAlert devices on the network.
- Pair devices using a shared PIN.
- Choose an alert colour (red, orange, blue, or green).
- Save reusable message shortcuts.
- Configure receiving, ringtone, vibration, and automatic dismissal.
- Show incoming alerts using a high-priority notification and full-screen alert activity.

## Requirements

- Android Studio
- JDK 17
- Android SDK Platform 34
- Android 9 (API 28) or newer

The app's Gradle configuration currently uses `minSdk 28`, `compileSdk 34`, and `targetSdk 33`.

## Build

1. Clone or download this repository.
2. Open the repository root (the folder containing `settings.gradle.kts`) in Android Studio.
3. Allow Gradle to sync.
4. Select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. Install the generated APK on both phones.

## Setup

1. Connect both phones to the same Wi-Fi network.
2. Open LocalAlert on each phone and complete the initial setup.
3. Give both phones the same pairing PIN and choose a name for each device.
4. On the phone that should receive alerts, enable receiving and grant the requested Android permissions.
5. On the sending phone, open Settings and use **Find phones on my Wi-Fi**, or add a phone manually.
6. Select the destination, write a message, choose a colour, and tap **SEND ALERT**.

For reliable background operation, review the app's notification, full-screen alert, overlay, and battery settings. Android manufacturers may apply additional background restrictions.

## Network and security notes

LocalAlert communicates over the local network using TCP port `8765`. The receiver checks that incoming connections are from the same Wi-Fi subnet and that the supplied pairing PIN matches.

The current implementation does not encrypt network traffic. Use it only on a trusted private Wi-Fi network, and do not reuse a sensitive password as the pairing PIN.

## Project structure

- `app/src/main/java/com/localalert/` — Kotlin application source
- `app/src/main/AndroidManifest.xml` — app components and permissions
- `app/src/main/res/` — application resources
- `gradle/` — Gradle wrapper configuration

## Current status

This repository contains the Android Studio project source. It is an independent project and is not affiliated with Android or Google.

## License

No license has been selected yet. Unless a license is added, the source is publicly viewable but standard copyright restrictions still apply.

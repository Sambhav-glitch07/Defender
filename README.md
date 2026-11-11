# Suzuki Defender Edition – Infotainment OS

Native Android (Kotlin) infotainment launcher with a Defender Pivi Pro–inspired layout:
graphite surfaces, warm-white type, champagne accent, left navigation rail, right volume rail,
tall rectangular panels. No 3D, no shaders, no background services (the only declared service is the
standard notification-listener stub, used only if you grant "now playing" access).

## Build
- **GitHub Actions:** push to `main`; APKs are uploaded as the `SuzukiDefenderOS-apk` artifact (Java 17, Gradle 8.5, AGP 8.2.2).
- **Android Studio (Hedgehog or newer):** open the folder, let it sync, Run. JDK 17.
- **CLI:** `gradle assembleDebug` (Gradle 8.5+; or run `gradle wrapper` once to generate `./gradlew`).

## Screens
Home · Navigation · Media · Phone · Vehicle · Settings (rail on the left).

## Permissions (all optional; the app degrades gracefully)
Location (current place, GPS speed), Bluetooth connect (connected phone name), contacts / call log /
call phone (Phone screen), and "notification access" (now-playing metadata – opened from Media screen).

## Honest limits
- No vehicle bus is read. Fuel, range and trip computer show "Not connected". GPS speed/distance are real, from the head unit's location.
- The map is a static backdrop; routing is handed to the installed navigation app (`google.navigation:` / `geo:` intents).
- The "S" is an original stand-in mark, not the official Suzuki emblem. Replace
  `res/drawable/ic_suzuki_s.xml` and `ic_launcher_foreground.xml` with a licensed asset before any distribution.

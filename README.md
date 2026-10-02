# Location Reminders for Android

A buildable Android app using OpenStreetMap by default and Android/Google Play services geofencing for arrival reminders. No API key, billing account, payment, or Google sign-in is required for the default map, manual selection, or geofencing.

The original repository contained Java/XML snippets rather than a Gradle project. Those files are preserved unchanged in their original directory; the original description is preserved in [docs/ORIGINAL_README.md](docs/ORIGINAL_README.md).

## Use the app

- Tap the map, enter `latitude, longitude`, search for a place, or use the Tokyo/Dhaka shortcuts. Coordinates support the entire world, including negative values. Search uses Android's Geocoder without a country restriction; its availability depends on the device/network.
- Tap **Save reminder**, enter a name, optionally add a comment, and keep the default **500 m** radius or choose 100–10,000 m.
- Reminders are eligible immediately by default. The date/time picker can restrict alerts to arrivals **after** a future time. This is an arrival condition, not an exact scheduled alarm.
- Allow precise location and notifications. For background arrival alerts, open **Options → Enable location / notification permissions**, then set app location permission to **Allow all the time** in Android Settings.
- In Android **Settings → Location → Location services → Location Accuracy**, enable **Improve Location Accuracy**. Geofence registration can return error 1000 when it is disabled.
- **Reminders** shows stored names, coordinates, radius, status, eligibility time, and comments. Choose a reminder to show it on the map, retry activation, or delete it. Data remains on the device across app restarts. Delivered reminders stay in the list as **Reminded** and alert once.

## Mac setup (Apple Silicon)

Install the free Android Studio distribution from [Android Developers](https://developer.android.com/studio). In SDK Manager install Android SDK Platform 35, Build Tools 35.0.0 (or compatible 35.x), Android SDK Platform-Tools, and Android Emulator. In Device Manager create a phone using an **ARM64 / arm64-v8a Google Play** system image. The tested Mac already had Android 36 Google Play ARM64 installed; it was reused. Do not create a second emulator if one is running.

From the project root:

```sh
export ANDROID_SDK_ROOT="$HOME/Library/Android/sdk"
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/emulator:$PATH"
printf 'sdk.dir=%s\n' "$ANDROID_SDK_ROOT" > local.properties
./gradlew assembleDebug lintDebug
```

The wrapper uses Gradle 8.12, Android Gradle Plugin 8.7.3, Java 17 language compatibility, compile/target SDK 35, and minimum SDK 26. Dependencies download from Google Maven and Maven Central on the first build. `local.properties` is ignored and must stay local.

## Run on an existing emulator

```sh
adb devices
emulator -list-avds
# Only when no emulator is already running:
emulator -avd Medium_Phone_API_36.0
```

Choose the actual emulator serial from `adb devices`:

```sh
export ANDROID_SERIAL=emulator-5554
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 2; done
adb shell getprop ro.product.cpu.abi
adb shell pm list packages com.google.android.gms
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -W -n com.radwan.locationalert/.MainActivity
```

Use `install -r` to preserve existing app data. Avoid uninstalling, clearing app data, wiping the AVD, or running tests that automatically uninstall the app when it contains reminders you want to keep.

## Optional providers and keys

**OpenStreetMap** is the default embedded map. It needs no key. The app identifies itself to the tile server and displays attribution. It requests visible tiles, with ordinary on-device caching, not bulk offline downloads. See the [OSM tile policy](https://operations.osmfoundation.org/policies/tiles/).

**Google Maps** is optional: **Options → Google Maps** opens the selected coordinates in an external Google Maps app/browser through a public Maps URL. It is not an embedded Google Maps SDK and does not require a Maps API key or billing setup. See [Maps URLs](https://developers.google.com/maps/documentation/urls/get-started). Google Play services' geofencing API is independent of this map choice.

**Geoapify** is an optional embedded tile provider. **Options → Geoapify tiles** accepts an existing API key at runtime. The app does not create an account, enable billing, obtain a key, or make any Geoapify requests unless you choose it. The key is kept only in memory and is not committed or persisted. Selecting OpenStreetMap switches back; restarting always defaults to OpenStreetMap. No Geoapify key was used during verification.

## Test and diagnose

Three instrumented tests cover persistent worldwide coordinates/Unicode names, status updates and removal, and malformed storage recovery. They use a separate test preference namespace.

For an emulator without valuable app data, `./gradlew connectedDebugAndroidTest` is convenient, but Gradle can uninstall the app afterward. To preserve an existing installation:

```sh
./gradlew assembleDebug assembleDebugAndroidTest lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.radwan.locationalert.test/androidx.test.runner.AndroidJUnitRunner
adb shell am start -W -n com.radwan.locationalert/.MainActivity
```

For an actual geofence test, enable permissions and Location Accuracy. Set an outside position, tap **My location** to acquire a fresh GPS fix, select Tokyo and save a 500 m reminder, then move inside. `geo fix` takes **longitude first, latitude second**:

```sh
adb emu geo fix 139.70 35.60
# Tap My location, wait for the outside fix, then save the Tokyo reminder.
adb emu geo fix 139.767125 35.681236
# Tap My location if GPS is idle, and repeat the inside fix.
adb emu geo fix 139.767125 35.681236
adb logcat -d -s LocationReminders
adb shell dumpsys notification --noredact
adb shell cmd statusbar expand-notifications
adb logcat -d -b crash
```

Inspect the real Android notification, not just registration success. Do not send a synthetic intent to the receiver as a substitute for movement. For persistence, press Home, kill the background app with `adb shell am kill com.radwan.locationalert`, reopen, and inspect **Reminders**. Delete only the test reminder, then reopen the list and check it is gone.

Actual results and remaining limitations: [docs/TEST_RESULTS.md](docs/TEST_RESULTS.md).

## Limitations

- Geofence delivery depends on Google Play services, precise/background location permission, enabled device Location Accuracy, and location fixes. Android can delay background events by minutes; this is not a safety-critical or exact-time alarm. See [Android geofencing guidance](https://developer.android.com/develop/sensors-and-location/location/geofencing).
- At most 100 stored reminders are allowed, matching the per-app Android geofence limit. Reminders are one-shot. Re-create a delivered reminder to arm it again.
- A future eligibility time does not trigger a notification by itself. If already inside when it becomes eligible, leave and re-enter, or receive a subsequent dwell event.
- Stored reminder details work offline. Uncached map tiles and place searches need network access; no offline map download is included.
- The interface is English; Unicode place names/comments and worldwide coordinates are supported. Search coverage depends on Android Geocoder.
- Boot restoration is implemented but was not verified across an emulator reboot. After force-stop, reopen the app to restore geofences. Doze/OEM battery restrictions and physical-device behavior were not tested.
- Optional Google Maps handoff and Geoapify tiles are not prerequisites; Geoapify requires a user-supplied key and was not tested with one.
- No release APK, signing secret, SDK, emulator image, or generated build output belongs in Git. This project is a debug-build implementation; no release is published.

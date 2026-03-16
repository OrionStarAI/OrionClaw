# OrionClaw — Robot Control Android APK

A minimal Android APK that connects a service robot to the **OpenClaw** control framework.

## What It Does

After launch, the APK connects to `robot-ws-ingress` via WebSocket, registers its device identity, and relays commands from OpenClaw to the robot's underlying OS (via the RobotOS SDK).

Supported commands:
- Navigation: `nav.start` / `nav.stop` / `nav.status` / `robot.getPlaceList`
- Camera: `camera.takePhoto`
- TTS: `tts.play`
- Head control: `head.move` / `head.reset`
- Base rotation: `base.turn`
- Screen: `screen.show` / `screen.update` / `screen.hide`
- Audio: `audio.play` / `audio.stop`
- Charging: `charge.start` / `charge.leave`
- Status: `robot.status`

---

## Build Instructions

### Step 1: Get the Vendor SDK

Contact your robot vendor to obtain the SDK jar matching your robot OS version (typically named `robotservice_<version>.jar`). Rename it to `robotservice.jar` and place it in:

```
app/libs/robotservice.jar
```

Also update the Maven repository URL in `settings.gradle.kts` to the vendor's actual address (currently a placeholder):

```kotlin
maven {
    url = uri("https://your-vendor-maven-repo/")  // ← Replace with vendor's actual Maven URL
}
```

### Step 2: Package Name

The source uses `com.orionstar.openclaw` — **no changes needed**, build as-is.

To use your own package name, update `namespace` and `applicationId` in `app/build.gradle.kts` and rename the source directory accordingly.

### Step 3: Build

```bash
./gradlew assembleDebug
```

> Requires Android SDK (API level 26+). Android Studio is recommended.

Output: `app/build/outputs/apk/debug/app-debug.apk`

---

## Install on the Robot

```bash
adb connect <ADB_ADDRESS>
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
```

---

## Launch with Parameters

Use ADB to pass configuration parameters at launch (avoids manual input in the UI):

```bash
adb shell am start -n com.orionstar.openclaw/.MainActivity \
  --es gatewayHost "<YOUR_GATEWAY_IP>" \
  --es token "<YOUR_TOKEN>" \
  --es deviceId "<YOUR_DEVICE_ID>"
```

| Parameter | Description |
|-----------|-------------|
| `gatewayHost` | IP of the server running `robot-ws-ingress` |
| `token` | Shared token matching the `robot-ws-ingress` config |
| `deviceId` | Unique robot device ID, must match OpenClaw config |

---

## Verify Connection

After the APK connects successfully:

```bash
curl "http://<YOUR_GATEWAY_IP>:18795/robot/online?token=<YOUR_TOKEN>"
# Example response: {"ok":true,"devices":["<YOUR_DEVICE_ID>"]}
```

---

## Notes

- The RobotOS API requires the APK to run in the **foreground** to maintain chassis control.
- If SDK classes are not found at runtime, the bridge logs errors gracefully without crashing.
- The APK uses reflection (`RobotOsBridge`) so the project compiles without bundling the vendor SDK.

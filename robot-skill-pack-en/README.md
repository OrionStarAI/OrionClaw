# Robot Skill Pack for OpenClaw (English Edition)

A ready-to-deploy OpenClaw control solution for service robots.

---

## Prerequisites

Make sure the following tools are installed before you begin:

| Tool | Purpose | How to install |
|------|---------|----------------|
| [OpenClaw](https://docs.openclaw.ai) | AI control framework (core) | See official docs |
| Node.js 22.16.0+ | Run robot-ws-ingress | https://nodejs.org |
| Python 3.8+ | Run control scripts | https://python.org |
| Android SDK + adb | Build and install the APK | Bundled with Android Studio, or install platform-tools separately |

> **⚠️ OpenClaw Version Compatibility**
>
> | OpenClaw version | Impact | Action required |
> |-----------------|--------|-----------------|
> | **v2026.3.13+** (macOS) | Minimum Node.js raised to **22.16.0**; lower versions are rejected on macOS | Ensure Node.js ≥ 22.16.0 |
> | **v2026.3.2+** | **Breaking**: new installs default to `tools.profile = messaging`, which excludes the `exec` tool. Robot skills invoke Python scripts via exec — without it, scripts won't run | Set in `openclaw.json` (two options, see below) |
>
> **Fix for tools.profile issue (v2026.3.2+ fresh installs)**
>
> Add one of the following to the top level of `~/.openclaw/openclaw.json`:
>
> **Option A: Switch globally to the coding profile (recommended)**
> ```json
> {
>   "tools": { "profile": "coding" }
> }
> ```
>
> **Option B: Keep messaging profile, allow exec explicitly**
> ```json
> {
>   "tools": {
>     "profile": "messaging",
>     "allow": ["exec", "process"]
>   }
> }
> ```
>
> Restart OpenClaw after making changes.
>
> **Plugin API note**: `robot-ws-ingress` runs its own HTTP server and does not call `api.registerHttpHandler`. It is unaffected by the v2026.3.2 plugin API breaking change.

---

## Directory Structure

```
robot-skill-pack-en/
├── README.md                               # This file (deployment guide)
├── OrionClaw/                              # 📱 Robot control APK source code
│   ├── README.md                           # Detailed build & deploy instructions
│   ├── app/
│   │   ├── build.gradle.kts               # Build config (set your package name here)
│   │   ├── libs/                           # Place vendor SDK jar here
│   │   └── src/main/java/…                 # Kotlin source files
│   ├── settings.gradle.kts                # Maven repo config (set vendor URL here)
│   └── gradlew / gradlew.bat              # Gradle build scripts
├── robot-ws-ingress/                       # 🔌 Gateway service
│   ├── index.js                            # Run this file directly (pre-compiled)
│   ├── index.ts                            # TypeScript source (readable)
│   ├── package.json                        # Dependencies (ws library only)
│   └── openclaw.plugin.json                # OpenClaw plugin manifest
├── skills/
│   ├── robot-control/                      # 🤖 Robot control skill
│   │   ├── SKILL.md                        # AI usage guide (44+ commands)
│   │   └── scripts/
│   │       ├── robot_cmd.py                # Generic command CLI tool
│   │       ├── get_places.py               # List navigation waypoints
│   │       ├── take_photo_to_file.py       # Take photo and save to file
│   │       ├── charging.py                 # Charging control
│   │       ├── dance_player.py             # Action sequence player
│   │       ├── music_gen.py                # WAV music synthesizer
│   │       └── dances/                     # Example choreography files
│   └── robot-march/                        # 🚶 Patrol route skill
│       ├── SKILL.md
│       └── scripts/march.py               # Patrol script
└── workspace-template/                     # 📋 OpenClaw workspace template
    ├── robot-agents-addon.md       # Append to AGENTS.md: robot control rules
    └── robot-tools-addon.md        # Append to TOOLS.md: robot connection config
```

---

## Quick Deployment (5 Steps)

### Step 1: Deploy robot-ws-ingress

`robot-ws-ingress` is the gateway service that relays commands between OpenClaw and the robot.

```bash
cd robot-ws-ingress
npm install
```

Add the plugin to your OpenClaw `config.yaml` (replace `/path/to/` with the actual path):

```yaml
plugins:
  - path: /path/to/robot-ws-ingress/index.js
    config:
      enabled: true
      port: 18795              # Port the gateway listens on
      path: /robot/ws          # WebSocket path
      token: "your-secret-token-here"   # Pick a secure token (you'll use this everywhere)
      allowDeviceIds: []       # Leave empty to allow all devices, or set ["my-robot"] to restrict
      gatewayPort: 18789       # OpenClaw Gateway port (default 18789, usually no need to change)
```

Start OpenClaw — the plugin loads automatically with the Gateway.

**Verify the gateway is running:**
```bash
curl http://localhost:18795/robot/health
# Expected: {"ok":true,"online":0}
```

---

### Step 2: Build and Install OrionClaw APK

OrionClaw is the Android app installed on the robot. It receives commands and calls the robot OS APIs.

> 💡 See `OrionClaw/README.md` for full details. Here is a summary.

#### 2.1 Get the vendor SDK

Contact your robot vendor to obtain the SDK jar for your robot OS version. Rename it to `robotservice.jar` and place it in:

```
OrionClaw/app/libs/robotservice.jar
```

Also update `OrionClaw/settings.gradle.kts` with the vendor's actual Maven repository URL:

```kotlin
// Find this line and replace with the vendor's actual Maven URL:
url = uri("https://your-vendor-maven-repo/")
// Update credentials (username/password) if required by the vendor
```

#### 2.2 Package name

The source already uses `com.orionstar.openclaw` — **no changes needed**, build as-is.

To use your own package name, update `namespace` and `applicationId` in `OrionClaw/app/build.gradle.kts` and rename `app/src/main/java/com/orionstar/openclaw/` to match your package path.

#### 2.3 Build the APK

```bash
cd OrionClaw
./gradlew assembleDebug
```

The APK will be at:
```
app/build/outputs/apk/debug/app-debug.apk
```

> Requires Java 11+ and Android SDK (API 26+). Opening in Android Studio is recommended — it downloads all dependencies automatically.

#### 2.4 Install on the robot

```bash
# Connect to the robot over Wi-Fi ADB
adb connect <robot-ip-address>:5555

# Install the APK
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
```

#### 2.5 Launch the app with parameters

Use ADB to pass configuration at launch (avoids manual input in the UI):

```bash
adb shell am start -n com.orionstar.openclaw/.MainActivity \
  --es gatewayHost "<gateway-server-ip>" \
  --es token "your-secret-token-here" \
  --es deviceId "my-robot"
```

> `gatewayHost` = IP of the server running robot-ws-ingress; `token` = same token from Step 1; `deviceId` = any name you choose for this robot.

**Verify the robot is online:**
```bash
curl "http://localhost:18795/robot/online?token=your-secret-token-here"
# Expected: {"ok":true,"devices":["my-robot"]}
```

---

### Step 3: Set Up OpenClaw Workspace

**Install skill files** (safe copy — does not overwrite anything):

```bash
cp -r skills/robot-control  ~/.openclaw/workspace/skills/
cp -r skills/robot-march    ~/.openclaw/workspace/skills/
```

**Append robot config snippets** to your existing workspace files:

```bash
# Append robot control rules to AGENTS.md (photo handling, progress reporting, map fetching, etc.)
cat workspace-template/robot-agents-addon.md >> ~/.openclaw/workspace/AGENTS.md

# Append robot connection config to TOOLS.md
cat workspace-template/robot-tools-addon.md >> ~/.openclaw/workspace/TOOLS.md
```

> **Why append instead of overwrite?**
> Your `AGENTS.md` and `TOOLS.md` likely already contain your own settings (other devices, preferences, rules).
> Appending adds only the robot-specific content at the end without touching anything else.
>
> After appending, open each file and replace `<GATEWAY_IP>`, `<YOUR_TOKEN>`, and `<YOUR_DEVICE_ID>` with your actual values.

---

### Step 4: Fill In Your Settings

Open `~/.openclaw/workspace/TOOLS.md`, find the robot section just appended, and replace the placeholders:

| Placeholder | Replace with |
|-------------|--------------|
| `<GATEWAY_IP>` | IP of the server running robot-ws-ingress |
| `<YOUR_TOKEN>` | The token you set in Step 1 |
| `<YOUR_DEVICE_ID>` | The deviceId set when launching the APK |
| `<ROBOT_IP>` | The robot's IP address (for ADB) |

> TOOLS.md is read by the AI — it uses these values to connect to and control the robot.

You can also set environment variables to run scripts directly:
```bash
export ROBOT_GATEWAY_URL="http://<GATEWAY_IP>:18795"
export ROBOT_TOKEN="<YOUR_TOKEN>"
export ROBOT_DEVICE_ID="<YOUR_DEVICE_ID>"
```

---

### Step 5: Start Using It

Restart OpenClaw to load the new skills, then send messages to your AI assistant:

- "Navigate the robot to the lobby"
- "Make the robot say hello"
- "Take a photo and show me"
- "Play the Ode to Joy dance"
- "Send the robot to charge"

> 💡 Place names must exactly match the waypoint names on the robot's map. Ask "list all robot navigation places" to see available names first.

---

## Running Scripts Directly (Without AI)

Once environment variables are set, you can control the robot from the command line:

```bash
export ROBOT_GATEWAY_URL="http://<gateway-ip>:18795"
export ROBOT_TOKEN="<TOKEN>"
export ROBOT_DEVICE_ID="<DEVICE_ID>"

# Check robot status
python3 skills/robot-control/scripts/robot_cmd.py robot.status

# List all navigation places
python3 skills/robot-control/scripts/get_places.py

# Make the robot speak
python3 skills/robot-control/scripts/robot_cmd.py tts.play --args '{"text":"Hello, I am a service robot."}'

# Navigate to a place
python3 skills/robot-control/scripts/robot_cmd.py nav.start --args '{"destName":"Lobby"}'

# Take a photo and save locally
python3 skills/robot-control/scripts/take_photo_to_file.py --out-dir ./photos

# Play a dance sequence
python3 skills/robot-control/scripts/dance_player.py \
  skills/robot-control/scripts/dances/dance_hello.json

# Patrol route with announcements
python3 skills/robot-march/scripts/march.py \
  --waypoints "Place A" "Place B" "Place C" \
  --shout-text "Welcome to our event!"
```

---

## Architecture

```
User/AI ──HTTP──> robot-ws-ingress ──WebSocket──> OrionClaw APK
                  (port 18795)                     (Android robot)
                       │
                       └── Calls robot OS SDK APIs (navigation, camera, TTS, etc.)
```

---

## Troubleshooting

**Q: Robot not coming online (devices list is empty)?**
A: Check that the APK is running, the `gatewayHost` and `token` exactly match the ingress config, and that the network allows the robot to reach the gateway server.

**Q: Navigation error -108?**
A: The destination name doesn't exist on the robot's current map. Call `robot.getPlaceList` first to get the exact names, then use one of those names.

**Q: `nav.start` returns `ok:true` but the robot doesn't move?**
A: `nav.start` is asynchronous — `ok:true` means the command was accepted, not that the robot has arrived. Poll `robot.status` and check the `isInNavigation` field to detect arrival.

**Q: Build error — SDK classes not found?**
A: Make sure `app/libs/robotservice.jar` is in place with the correct filename, and that `settings.gradle.kts` has been updated with the vendor's actual Maven URL and credentials.

---

## License

MIT License

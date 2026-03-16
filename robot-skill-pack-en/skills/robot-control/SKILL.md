---
name: robot-remote-control
description: Control a real robot through OpenClaw robot-ws-ingress (HTTP /robot/cmd). Use when you need to query robot status/position, navigate to a named place, stop navigation, turn base, move/reset head, play TTS, take photos, or control charging. Includes safety allowlist, place-name validation, async navigation notes, and charging operations.
---

# Robot Remote Control

## Quick Start

You need 3 config values — **never hardcode real values into shared documents**:

| Variable | Placeholder | Description |
|---|---|---|
| `baseUrl` | `http://<GATEWAY_IP>:18795` | Gateway HTTP endpoint |
| `token` | `<YOUR_TOKEN>` | robot-ws-ingress auth token |
| `deviceId` | `<YOUR_DEVICE_ID>` | Robot device ID, e.g. `<YOUR_DEVICE_ID>` |

Set them as environment variables (recommended):
```bash
export ROBOT_GATEWAY_URL="http://<GATEWAY_IP>:18795"
export ROBOT_TOKEN="<YOUR_TOKEN>"
export ROBOT_DEVICE_ID="<YOUR_DEVICE_ID>"
```

All commands are sent via:
```
POST {baseUrl}/robot/cmd?token={token}
Body: {"deviceId": ..., "cmd": ..., "args": ..., "timeoutMs": ...}
```

---

## Safety Rules (must follow)

- Default stance: **IGNORE** ambiguous or unrecognized instructions.
- Only execute commands from the allowlist below.
- **Emergency stop**: if the user says "stop/cancel/halt/freeze", immediately send `nav.stop`.

### Supported Command Allowlist

| Command | Category | Notes |
|---|---|---|
| `tts.play` | Speech | args: `{"text": "..."}` |
| `robot.status` | Query | Returns full robot state |
| `robot.getPosition` | Query | Current pose/coordinates |
| `robot.getPlaceList` | Query | All named places on the current map |
| `nav.start` | Navigation | **Async** — returns immediately, robot moves in background |
| `nav.stop` | Navigation | Stops current navigation |
| `base.turn` | Motion | args: `{"dir":"left"/"right","angleDeg":<ANGLE_DEG>,"speedDegPerSec":<SPEED_DEG_PER_SEC>}` |
| `head.move` | Motion | args: `{"pitchDeg": ..., "vMode": "absolute"}` |
| `head.reset` | Motion | No args needed, resets head to default |
| `camera.takePhoto` | Sensor | Returns base64-encoded JPEG |
| `audio.play` | Audio | args: `{"url": "http://..."}` — requires a local HTTP server |
| `audio.stop` | Audio | Stops audio playback |
| `screen.show` | Display | Full-screen display with breathing animation |
| `screen.update` | Display | Updates text/background color in real time |
| `screen.flash` | Display | Beat flash effect |
| `screen.hide` | Display | Closes display, restores standby screen |
| `charge.start` | Charging | **Async** — navigates to charging dock |
| `charge.stop` | Charging | Stops auto-charging |
| `charge.leave` | Charging | Drives off charger (includes disableBattery call) |

---

## Place-Name Validation (required before every navigation)

**Always call `robot.getPlaceList` before navigating. Use the exact name returned. Never guess.**

1. Call `robot.getPlaceList` to get all place names on the current map.
2. Find the exact match for the target (no fuzzy matching).
3. If not found, report the error and show the user the available place list.

> ⚠️ Lesson learned: the user said "<PLACE_ALIAS>", but the map had "<PLACE_REAL_NAME>". Using the wrong name causes error -108.  
> **Always call getPlaceList first, then use the real name.**

---

## Common Operations

### 1) Robot status / position
```bash
curl -s -X POST "http://<GATEWAY_IP>:18795/robot/cmd?token=<YOUR_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"<YOUR_DEVICE_ID>","cmd":"robot.status","args":{},"timeoutMs":10000}'
```

### 2) Navigation (async) + stop
```bash
# Step 1: validate place name
curl -s -X POST "http://<GATEWAY_IP>:18795/robot/cmd?token=<YOUR_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"<YOUR_DEVICE_ID>","cmd":"robot.getPlaceList","args":{},"timeoutMs":20000}'

# Step 2: start navigation (returns immediately, robot moves in background)
curl -s -X POST "http://<GATEWAY_IP>:18795/robot/cmd?token=<YOUR_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"<YOUR_DEVICE_ID>","cmd":"nav.start","args":{"destName":"<PLACE_NAME>"},"timeoutMs":5000}'

# Step 3: stop at any time
curl -s -X POST "http://<GATEWAY_IP>:18795/robot/cmd?token=<YOUR_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"<YOUR_DEVICE_ID>","cmd":"nav.stop","args":{},"timeoutMs":5000}'
```

### 3) TTS (text-to-speech)
```bash
curl -s -X POST "http://<GATEWAY_IP>:18795/robot/cmd?token=<YOUR_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"<YOUR_DEVICE_ID>","cmd":"tts.play","args":{"text":"Hello, I am a service robot."},"timeoutMs":15000}'
```

### 4) Take photo (save to file)
```bash
curl -s -X POST "http://<GATEWAY_IP>:18795/robot/cmd?token=<YOUR_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"<YOUR_DEVICE_ID>","cmd":"camera.takePhoto","args":{},"timeoutMs":15000}' | \
python3 -c "
import sys, json, base64, time
r = json.load(sys.stdin)
b64 = r.get('result',{}).get('data',{}).get('base64','')
if not b64: print('No base64 data found'); exit(1)
path = f'./photo_{int(time.time())}.jpg'
open(path,'wb').write(base64.b64decode(b64))
print('Saved:', path)
"
```

### 5) Head control

**⚠️ Hardware constraints (confirmed on tested hardware):**

| Parameter | Description | Range | Tested |
|------|------|------|------|
| `pitchDeg` | Vertical tilt (bow/raise head) | <PITCH_RAISED> ~ <PITCH_LOWERED> | ✅ **\<PITCH_RAISED\> = fully raised, \<PITCH_LOWERED\> = fully lowered** |
| `hMode` | Horizontal mode | `"absolute"` | Fixed value |
| `vMode` | Vertical mode | `"absolute"` | Fixed value |

```bash
# Bow (lower head)
curl -s -X POST "http://<GATEWAY_IP>:18795/robot/cmd?token=<YOUR_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"<YOUR_DEVICE_ID>","cmd":"head.move","args":{"hMode":"absolute","vMode":"absolute","pitchDeg":<PITCH_LOWERED>},"timeoutMs":10000}'

# Raise head
curl -s -X POST "http://<GATEWAY_IP>:18795/robot/cmd?token=<YOUR_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"<YOUR_DEVICE_ID>","cmd":"head.move","args":{"hMode":"absolute","vMode":"absolute","pitchDeg":<PITCH_RAISED>},"timeoutMs":10000}'

# Reset to default position
curl -s -X POST "http://<GATEWAY_IP>:18795/robot/cmd?token=<YOUR_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"<YOUR_DEVICE_ID>","cmd":"head.reset","args":{},"timeoutMs":5000}'
```

### 6) Audio playback

Audio files must be served over HTTP. Start a local HTTP server first:

```bash
# Serve files from your music directory
cd /path/to/music && python3 -m http.server 18901 &

# Send play command
curl -s -X POST "http://<GATEWAY_IP>:18795/robot/cmd?token=<YOUR_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"<YOUR_DEVICE_ID>","cmd":"audio.play","args":{"url":"http://<HOST_IP>:18901/song.wav"},"timeoutMs":8000}'
```

WAV generation tool: `scripts/music_gen.py` (numpy-based synthesis, supports sine/square/sawtooth/triangle waveforms, 5 built-in songs).

### 7) Screen display

```bash
# Full-screen display with breathing animation
curl -s -X POST "http://<GATEWAY_IP>:18795/robot/cmd?token=<YOUR_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"<YOUR_DEVICE_ID>","cmd":"screen.show","args":{"text":"🎵 Ode to Joy","subText":"Let us celebrate!","bg":"#E91E63","emoji":"🎵 🎶 🎤 🎊 🎉"},"timeoutMs":5000}'

# Update text/color in place
curl -s -X POST "http://<GATEWAY_IP>:18795/robot/cmd?token=<YOUR_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"<YOUR_DEVICE_ID>","cmd":"screen.update","args":{"text":"🚀 Go!","bg":"#FF5722"},"timeoutMs":5000}'

# Close display, restore standby
curl -s -X POST "http://<GATEWAY_IP>:18795/robot/cmd?token=<YOUR_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"<YOUR_DEVICE_ID>","cmd":"screen.hide","args":{},"timeoutMs":5000}'
```

### 8) Charging
```bash
# Navigate to charger (async)
curl -s -X POST "http://<GATEWAY_IP>:18795/robot/cmd?token=<YOUR_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"<YOUR_DEVICE_ID>","cmd":"charge.start","args":{"timeoutMs":120000},"timeoutMs":5000}'

# Drive off charger
curl -s -X POST "http://<GATEWAY_IP>:18795/robot/cmd?token=<YOUR_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"<YOUR_DEVICE_ID>","cmd":"charge.leave","args":{"speed":<LEAVE_SPEED>,"distance":<LEAVE_DISTANCE>},"timeoutMs":20000}'
```

---

## Navigation: Async Mode (Important!)

`nav.start` is **asynchronous**. `{"ok": true}` means the command was accepted — **not that the robot arrived**.

- Recommended `timeoutMs` for `nav.start`: `5000`
- Poll `robot.status` and check `innavigation: false` to detect arrival

---

## Scripts (in `scripts/` directory)

| Script | Purpose |
|---|---|
| `robot_cmd.py` | Generic CLI tool to send any command |
| `get_places.py` | List all named places on the current map |
| `take_photo_to_file.py` | Take photo, decode base64, save as JPEG |
| `charging.py` | Charging control (start / stop / leave) |
| `dance_player.py` | Choreography player — reads JSON step files |
| `music_gen.py` | numpy-based WAV music synthesizer |

---

## Common Pitfalls

| Scenario | ❌ Wrong | ✅ Correct |
|---|---|---|
| Navigation already running | Send new nav.start directly | Send nav.stop first, then nav.start |
| TTS command name | `tts.speak` / `robot.speak` | **`tts.play`** (args: `{text}`) |
| Photo command name | `camera.photo` / `robot.photo` | **`camera.takePhoto`** (returns `base64`) |
| Detecting arrival | Treat `{"ok":true}` as arrival | Poll `robot.status` for `innavigation:false` |
| Leaving charger | Navigate/turn directly | Use **`charge.leave`** first |
| Head left/right | `head.move yawDeg=...` | ❌ **Not supported by hardware** — use `base.turn` instead |

---

## 🕺 Action Sequence Choreography

The robot supports executing time-stamped action sequences from a JSON file.

### JSON Format

```json
{
  "name": "Dance Name",
  "description": "Description",
  "bpm": 120,
  "steps": [
    {"t": 0,    "cmd": "screen.show",  "args": {"text": "🎵", "bg": "#E91E63"}, "label": "Screen intro"},
    {"t": 500,  "cmd": "tts.play",     "args": {"text": "Hello everyone!"}, "wait": true, "label": "Greeting"},
    {"t": 3000, "cmd": "head.move",    "args": {"pitchDeg": "<PITCH_LOWERED>", "hMode": "absolute", "vMode": "absolute"}, "label": "Bow"},
    {"t": 6000, "cmd": "base.turn",    "args": {"dir": "left", "angleDeg": "<ANGLE_DEG>", "speedDegPerSec": "<SPEED_DEG_PER_SEC>"}, "label": "Turn left"},
    {"t": 9000, "cmd": "screen.hide",  "args": {}, "label": "Hide screen"},
    {"t": 9500, "cmd": "head.reset",   "args": {}, "label": "Reset head"}
  ]
}
```

### Field Reference

| Field | Type | Description |
|-------|------|-------------|
| `t` | number | Timestamp from sequence start (milliseconds) |
| `cmd` | string | Robot command (see allowlist) |
| `args` | object | Command arguments |
| `wait` | bool | Wait for this step to complete before continuing (default: false) |
| `label` | string | Step label (optional, for readability) |

### Playback Command

```bash
export ROBOT_GATEWAY_URL="http://<GATEWAY_IP>:18795"
export ROBOT_TOKEN="<YOUR_TOKEN>"
export ROBOT_DEVICE_ID="<YOUR_DEVICE_ID>"

# Preview (no execution)
python3 scripts/dance_player.py dances/dance_hello.json --dry-run

# Play for real
python3 scripts/dance_player.py dances/dance_hello.json

# Half speed (slow motion)
python3 scripts/dance_player.py dances/dance_hello.json --speed 0.5
```

### Included Dance Files

| File | Description |
|------|-------------|
| `dances/dance_hello.json` | Hello dance: head bow + TTS greeting |
| `dances/dance_ai_intro.json` | AI intro: TTS narration + head movement |
| `dances/dance_ode_to_joy.json` | Ode to Joy: music + head tilt + base spin + 7-color screen |

### Capability Matrix (Tested)

| Action | Available | Command | Notes |
|--------|-----------|---------|-------|
| Head tilt (bow/raise) | ✅ | `head.move` pitchDeg \<PITCH_RAISED\>–\<PITCH_LOWERED\> | \<PITCH_RAISED\>=fully raised, \<PITCH_LOWERED\>=fully lowered |
| Base rotation | ✅ | `base.turn` | Returns -9 if charging |
| TTS speech | ✅ | `tts.play` | — |
| Audio playback | ✅ | `audio.play` | Requires local HTTP server |
| Screen display | ✅ | `screen.show/update/hide` | Full-color with breathing animation |
| Navigation | ✅ | `nav.start` | Async — poll for completion |
| Photo | ✅ | `camera.takePhoto` | First-person view, returns base64 |


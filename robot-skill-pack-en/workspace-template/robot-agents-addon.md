
---
<!-- ↓ Added by robot-skill-pack — append this to the end of your AGENTS.md -->

## 🤖 Robot Configuration (read in every session)

Any session can control this robot!

- **How to control**: Read `skills/robot-control/SKILL.md` for the full 44+ command reference

### 🗺️ About "Maps"

The robot's "map" is a **list of named navigation waypoints**, not an image. Always fetch it live:

```bash
curl -s -X POST "http://<GATEWAY_IP>:18795/robot/cmd?token=<YOUR_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"<YOUR_DEVICE_ID>","cmd":"robot.getPlaceList","args":{},"timeoutMs":20000}'
```

- Returns all named waypoints on the current map
- **Call this before every navigation** — use exact names returned, never guess
- ⚠️ Maps can change at any time — do not hardcode place names; always fetch live

### 📸 Taking Photos

1. **Lower the head first and take a shot** to verify the actual field of view
2. Adjust the angle if needed and retake
3. After shooting, call `head.reset` to restore the default position

### ⚠️ Progress Reporting (mandatory, no exceptions)

When executing any robot task, **send a progress update after every sub-step**, immediately after each command returns, regardless of success or failure.

- Format: `[emoji] + brief description (≤30 characters)`
- Examples:
  - `🤖 Task received! Heading to destination…`
  - `📍 Arrived at destination`
  - `✅ Photo taken`
  - `❌ Navigation failed, retrying`
- **Do not** wait until all steps are done to send a single summary — report every step

### ⚠️ Image Handling Rules (mandatory, no exceptions)

**Never pass image data as base64 strings!**

- Images must be passed as local file paths or URLs
- After taking a photo, decode the base64 and save as a local file, then send with the `message` tool:

```
message(action=send, media="/path/to/photo.jpg")
```

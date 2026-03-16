---
name: robot-march
description: Navigate the robot along a defined route, detect people with vision along the way, announce to them via TTS, and stop at the final destination. Use cases include event patrol, hall greeting runs, and custom broadcast routes. Trigger phrases: patrol / march / cruise / announce while moving.
---

# Robot March (Patrol Route)

The robot navigates through a list of waypoints in order. Every ~4 seconds it takes a photo and checks whether a real person is visible. If a person is detected, the robot speaks the configured announcement. The interval is configurable (default: 10 seconds). Shouting stops at the final waypoint.

## Quick Start

```bash
export ROBOT_GATEWAY_URL="http://<GATEWAY_IP>:18795"
export ROBOT_TOKEN="<YOUR_TOKEN>"
export ROBOT_DEVICE_ID="<YOUR_DEVICE_ID>"

python3 skills/robot-march/scripts/march.py \
  --waypoints "<PLACE_1>" "<PLACE_2>" "<PLACE_3>" "<PLACE_END>" \
  --shout-text "Hello everyone, thank you for being here!" \
  --shout-interval 10
```

## Parameters

| Parameter | Required | Default | Description |
|-----------|----------|---------|-------------|
| `--waypoints` | ✅ | — | List of waypoints. **Last one is the final destination** (no shouting there). Must be exact names from `robot.getPlaceList` |
| `--shout-text` | No | (Chinese default) | Text to speak when a person is detected |
| `--shout-interval` | No | 10 | Minimum interval between announcements (seconds) |
| `--gateway` | No | `ROBOT_GATEWAY_URL` env | Gateway base URL |
| `--token` | No | `ROBOT_TOKEN` env | Auth token |
| `--device` | No | `ROBOT_DEVICE_ID` env | Device ID |
| `--litellm-url` | No | `http://localhost:4000/v1/chat/completions` | Vision inference API URL |
| `--litellm-key` | No | `local-litellm-key` | API key |
| `--model` | No | `claude-sonnet-4-5` | Multimodal model name (must support image input) |
| `--photo-dir` | No | `./photos` | Directory to save patrol photos |

## Confirm Waypoints Before Starting

**Always confirm place names before navigating — never guess:**

```bash
curl -s -X POST "http://<GATEWAY_IP>:18795/robot/cmd?token=<YOUR_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"<YOUR_DEVICE_ID>","cmd":"robot.getPlaceList","args":{},"timeoutMs":20000}'
```

## Technical Notes

- **Vision detection**: Uses LiteLLM or any OpenAI-compatible multimodal API to determine if a real person is in the photo.
- **Navigation race condition fix**: After `nav.start`, the script waits 3 seconds before starting detection to avoid false "arrived" readings.
- **Double-confirmation**: Arrival is only confirmed after two consecutive `isInNavigation=false` readings, preventing false positives.
- **Photo storage**: Each photo is saved to `--photo-dir` with a timestamp-based filename.

## Troubleshooting

- **nav times out on first attempt**: Normal — the script automatically retries once.
- **Will the robot keep shouting after the final waypoint?**: No. The last waypoint skips photo detection entirely.
- **Wrong place name**: Use `robot.getPlaceList` to get the exact names. Do not guess.
- **Which model do I need?**: Any multimodal model that accepts image input, such as Claude Sonnet, GPT-4o, etc.

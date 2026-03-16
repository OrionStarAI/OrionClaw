
---
<!-- ↓ Added by robot-skill-pack — append this to the end of your TOOLS.md -->

## 🤖 Robot (OrionClaw)

- **Gateway URL**: `http://<GATEWAY_IP>:18795` (server running robot-ws-ingress)
- **Token**: `<YOUR_TOKEN>` (must match robot-ws-ingress config)
- **Device ID**: `<YOUR_DEVICE_ID>` (name registered by the APK at startup)
- **ADB address**: `<ROBOT_IP>:5555`

### Quick Verification

```bash
# Check if the robot is online
curl "http://<GATEWAY_IP>:18795/robot/online?token=<YOUR_TOKEN>"

# Get robot status
curl -s -X POST "http://<GATEWAY_IP>:18795/robot/cmd?token=<YOUR_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"<YOUR_DEVICE_ID>","cmd":"robot.status","args":{},"timeoutMs":10000}'
```

### Script Environment Variables

```bash
export ROBOT_GATEWAY_URL="http://<GATEWAY_IP>:18795"
export ROBOT_TOKEN="<YOUR_TOKEN>"
export ROBOT_DEVICE_ID="<YOUR_DEVICE_ID>"
```

### Skill Locations

- `skills/robot-control/SKILL.md` — Full robot control command reference (44+ commands)
- `skills/robot-march/SKILL.md` — Patrol route / march feature

### APK Launch Command

```bash
adb -s <ROBOT_IP>:5555 shell am start -n com.orionstar.openclaw/.MainActivity \
  --es gatewayHost "<GATEWAY_IP>" \
  --es token "<YOUR_TOKEN>" \
  --es deviceId "<YOUR_DEVICE_ID>"
```

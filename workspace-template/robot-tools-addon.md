
---
<!-- ↓ 以下内容由 robot-skill-pack 添加，追加到你的 TOOLS.md 末尾 -->

## 🤖 机器人（OrionClaw）

- **Gateway URL**：`http://<GATEWAY_IP>:18795`（运行 robot-ws-ingress 的服务器）
- **Token**：`<YOUR_TOKEN>`（与 robot-ws-ingress 配置一致）
- **Device ID**：`<YOUR_DEVICE_ID>`（APK 启动时注册的设备名）
- **ADB 地址**：`<ROBOT_IP>:5555`

### 快速验证

```bash
# 检查机器人是否在线
curl "http://<GATEWAY_IP>:18795/robot/online?token=<YOUR_TOKEN>"

# 查询机器人状态
curl -s -X POST "http://<GATEWAY_IP>:18795/robot/cmd?token=<YOUR_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"<YOUR_DEVICE_ID>","cmd":"robot.status","args":{},"timeoutMs":10000}'
```

### 脚本环境变量

```bash
export ROBOT_GATEWAY_URL="http://<GATEWAY_IP>:18795"
export ROBOT_TOKEN="<YOUR_TOKEN>"
export ROBOT_DEVICE_ID="<YOUR_DEVICE_ID>"
```

### Skill 位置

- `skills/robot-control/SKILL.md` — 完整机器人控制指令参考（44+ 条）
- `skills/robot-march/SKILL.md` — 巡场/穿越路线功能

### APK 启动方式

```bash
adb -s <ROBOT_IP>:5555 shell am start -n com.orionstar.openclaw/.MainActivity \
  --es gatewayHost "<GATEWAY_IP>" \
  --es token "<YOUR_TOKEN>" \
  --es deviceId "<YOUR_DEVICE_ID>"
```

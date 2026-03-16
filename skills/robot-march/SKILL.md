---
name: robot-march
description: 让机器人沿指定路线穿越，边走边用视觉检测人，看到人就喊话，到终点停止。适用于：活动现场巡场喊话、穿越大厅打招呼、定制巡逻路线播报。触发词：穿越/巡场/巡逻/走过去/路过喊话/边走边说。
---

# 机器人巡场（robot-march）

机器人沿指定点位顺序导航，途中每隔约 4 秒拍照检测是否有真实的人，检测到人则通过 TTS 喊话，间隔可配置（默认 10 秒），到达终点后停止喊话。

## 快速使用

```bash
export ROBOT_GATEWAY_URL="http://<GATEWAY_IP>:18795"
export ROBOT_TOKEN="<YOUR_TOKEN>"
export ROBOT_DEVICE_ID="<YOUR_DEVICE_ID>"

python3 skills/robot-march/scripts/march.py \
  --waypoints <地点1> <地点2> <地点3> <终点> \
  --shout-text "<YOUR_SHOUT_TEXT>" \
  --shout-interval 10
```

## 参数说明

| 参数 | 必填 | 默认值 | 说明 |
|------|------|--------|------|
| `--waypoints` | ✅ | — | 途经点位列表，**最后一个为终点**（终点不喊话）。必须是 `robot.getPlaceList` 返回的真实名称 |
| `--shout-text` | 否 | <YOUR_SHOUT_TEXT> | 检测到人时说的话 |
| `--shout-interval` | 否 | 10 | 连续喊话最小间隔（秒） |
| `--gateway` | 否 | 读取 `ROBOT_GATEWAY_URL` | Gateway 地址 |
| `--token` | 否 | 读取 `ROBOT_TOKEN` | 鉴权 token |
| `--device` | 否 | 读取 `ROBOT_DEVICE_ID` | 设备 ID |
| `--litellm-url` | 否 | `http://localhost:4000/v1/chat/completions` | 视觉推理 API 地址 |
| `--litellm-key` | 否 | `local-litellm-key` | API 密钥 |
| `--model` | 否 | `claude-sonnet-4-5` | 多模态模型名（需支持图片输入） |
| `--photo-dir` | 否 | `./photos` | 拍照保存目录 |

## 使用前确认点位

**导航前必须先确认点位名称**，不能猜：

```bash
curl -s -X POST "http://<GATEWAY_IP>:18795/robot/cmd?token=<YOUR_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"<YOUR_DEVICE_ID>","cmd":"robot.getPlaceList","args":{},"timeoutMs":20000}'
```

## 技术说明

- **视觉检测**：通过 LiteLLM 或兼容 OpenAI API 的多模态大模型，对照片判断是否有真实的人
- **导航竞争条件修复**：`nav.start` 后强制等待 3 秒再开始检测，避免机器人还没动就误判"已到达"
- **连续确认**：连续 2 次检测到 `isInNavigation=false` 才认为真正到达，避免误判
- **照片保存**：每次拍照保存到 `--photo-dir` 指定目录，文件名含时间戳

## 常见问题

- **nav 第一次超时**：正常现象，脚本会自动重试一次
- **到达终点后是否还在喊话**：不会，终点（`waypoints` 最后一项）跳过拍照检测
- **点位名称不对**：用 `robot.getPlaceList` 获取真实名称，不要猜测
- **视觉检测需要什么模型**：任何支持图片输入的多模态大模型，如 Claude Sonnet、GPT-4o 等

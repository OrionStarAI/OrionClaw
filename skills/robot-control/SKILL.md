---
name: robot-remote-control
description: 通过 OpenClaw 的 robot-ws-ingress 远程控制机器人（HTTP /robot/cmd）。适用于：查询机器人状态/位置、导航到指定地点、停止导航、底盘转向、头部控制、TTS 播报、拍照、充电控制。包含安全指令白名单、点位验证规则、异步导航说明和充电操作。
---

# 机器人远程控制

## 快速开始

你需要 3 个配置值——**不要把真实值硬编码到共享文档中**：

| 变量 | 占位符 | 说明 |
|---|---|---|
| `baseUrl` | `http://<GATEWAY_IP>:18795` | Gateway HTTP 地址 |
| `token` | `<YOUR_TOKEN>` | robot-ws-ingress 鉴权 token |
| `deviceId` | `<YOUR_DEVICE_ID>` | 机器人设备 ID，如 `<YOUR_DEVICE_ID>` |

通过环境变量配置（推荐）：
```bash
export ROBOT_GATEWAY_URL="http://<GATEWAY_IP>:18795"
export ROBOT_TOKEN="<YOUR_TOKEN>"
export ROBOT_DEVICE_ID="<YOUR_DEVICE_ID>"
```

所有指令通过以下方式执行：
```
POST {baseUrl}/robot/cmd?token={token}
请求体: {"deviceId": ..., "cmd": ..., "args": ..., "timeoutMs": ...}
```

---

## 安全规则（必须遵守）

- 默认立场：对模糊或无法识别的指令**忽略不执行**。
- 仅执行以下白名单中的指令。
- **紧急停止**：用户说"停/停止/取消/别动/别走"时，立即发送 `nav.stop`。

### 支持的指令白名单

| 指令 | 类别 | 说明 |
|---|---|---|
| `tts.play` | 语音 | 参数: `{"text": "..."}` |
| `robot.status` | 查询 | 返回机器人完整状态 |
| `robot.getPosition` | 查询 | 当前位置坐标 |
| `robot.getPlaceList` | 查询 | 当前地图所有命名点位 |
| `nav.start` | 导航 | **异步** — 立即返回，机器人在后台移动 |
| `nav.stop` | 导航 | 停止当前导航 |
| `base.turn` | 运动 | 参数: `{"dir":"left"/"right","angleDeg":90,"speedDegPerSec":30}` |
| `head.move` | 运动 | 参数: `{"pitchDeg": ..., "vMode": "absolute"}` |
| `head.reset` | 运动 | 无需参数，头部复位 |
| `camera.takePhoto` | 传感器 | 返回 base64 JPEG 图片 |
| `audio.play` | 音频 | 参数: `{"url": "http://..."}` — 需本地 HTTP 服务 |
| `audio.stop` | 音频 | 停止音频播放 |
| `screen.show` | 屏幕 | 全屏展示，带呼吸动画 |
| `screen.update` | 屏幕 | 实时更新文字/背景色 |
| `screen.flash` | 屏幕 | 节拍闪烁效果 |
| `screen.hide` | 屏幕 | 关闭展示，恢复待机 |
| `charge.start` | 充电 | **异步** — 导航前往充电桩 |
| `charge.stop` | 充电 | 停止自动充电 |
| `charge.leave` | 充电 | 离开充电桩（内含 disableBattery） |

---

## 点位验证（导航前强制执行）

**每次导航前必须先调 `robot.getPlaceList`，使用返回的真实名字，禁止猜测或假设。**

1. 调 `robot.getPlaceList` 获取当前地图所有地点名
2. 从列表中精确匹配目标点位（**不能模糊匹配**）
3. 找不到则报错，并将可用点位列表告知用户

> ⚠️ 教训：用户说"<PLACE_ALIAS>"，地图里实际是"<PLACE_REAL_NAME>"，直接使用会报错 -108。  
> **不管用户怎么说，都要先 getPlaceList，再用真实名字导航。**

---

## 常用操作

### 1) 查询状态 / 位置
```bash
curl -s -X POST "http://<GATEWAY_IP>:18795/robot/cmd?token=<YOUR_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"<YOUR_DEVICE_ID>","cmd":"robot.status","args":{},"timeoutMs":10000}'
```

### 2) 导航（异步）+ 停止
```bash
# 第一步：验证点位
curl -s -X POST "http://<GATEWAY_IP>:18795/robot/cmd?token=<YOUR_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"<YOUR_DEVICE_ID>","cmd":"robot.getPlaceList","args":{},"timeoutMs":20000}'

# 第二步：开始导航（立即返回，机器人在后台移动）
curl -s -X POST "http://<GATEWAY_IP>:18795/robot/cmd?token=<YOUR_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"<YOUR_DEVICE_ID>","cmd":"nav.start","args":{"destName":"<PLACE_NAME>"},"timeoutMs":5000}'

# 第三步：随时停止
curl -s -X POST "http://<GATEWAY_IP>:18795/robot/cmd?token=<YOUR_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"<YOUR_DEVICE_ID>","cmd":"nav.stop","args":{},"timeoutMs":5000}'
```

### 3) TTS 播报
```bash
curl -s -X POST "http://<GATEWAY_IP>:18795/robot/cmd?token=<YOUR_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"<YOUR_DEVICE_ID>","cmd":"tts.play","args":{"text":"你好，我是服务机器人"},"timeoutMs":15000}'
```

### 4) 拍照（保存为文件）
```bash
curl -s -X POST "http://<GATEWAY_IP>:18795/robot/cmd?token=<YOUR_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"<YOUR_DEVICE_ID>","cmd":"camera.takePhoto","args":{},"timeoutMs":15000}' | \
python3 -c "
import sys, json, base64, time
r = json.load(sys.stdin)
b64 = r.get('result',{}).get('data',{}).get('base64','')
if not b64: print('无 base64 数据'); exit(1)
path = f'./photo_{int(time.time())}.jpg'
open(path,'wb').write(base64.b64decode(b64))
print('已保存:', path)
"
```

### 5) 头部控制

**⚠️ 硬件限制（服务机器人实测确认）：**

| 参数 | 说明 | 范围 | 实测 |
|------|------|------|------|
| `pitchDeg` | 垂直俯仰（低头/抬头） | 0 ~ 80 | ✅ **0=最仰头，80=最低头** |
| `hMode` | 水平模式 | `"absolute"` | 固定值 |
| `vMode` | 垂直模式 | `"absolute"` | 固定值 |

```bash
# 低头（鞠躬）
curl -s -X POST "http://<GATEWAY_IP>:18795/robot/cmd?token=<YOUR_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"<YOUR_DEVICE_ID>","cmd":"head.move","args":{"hMode":"absolute","vMode":"absolute","pitchDeg":80},"timeoutMs":10000}'

# 仰头
curl -s -X POST "http://<GATEWAY_IP>:18795/robot/cmd?token=<YOUR_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"<YOUR_DEVICE_ID>","cmd":"head.move","args":{"hMode":"absolute","vMode":"absolute","pitchDeg":0},"timeoutMs":10000}'

# 复位
curl -s -X POST "http://<GATEWAY_IP>:18795/robot/cmd?token=<YOUR_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"<YOUR_DEVICE_ID>","cmd":"head.reset","args":{},"timeoutMs":5000}'
```

### 6) 音频播放

音乐文件须通过 HTTP URL 传递，需先在本机起 HTTP 服务：

```bash
# 在音乐文件目录起服务
cd /path/to/music && python3 -m http.server 18901 &

# 发送播放指令
curl -s -X POST "http://<GATEWAY_IP>:18795/robot/cmd?token=<YOUR_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"<YOUR_DEVICE_ID>","cmd":"audio.play","args":{"url":"http://<HOST_IP>:18901/song.wav"},"timeoutMs":8000}'
```

WAV 文件生成工具：`scripts/music_gen.py`（numpy 合成，支持 sine/square/sawtooth/triangle 波形，含 5 首内置曲目）

### 7) 屏幕显示

```bash
# 全屏展示（呼吸动画）
curl -s -X POST "http://<GATEWAY_IP>:18795/robot/cmd?token=<YOUR_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"<YOUR_DEVICE_ID>","cmd":"screen.show","args":{"text":"🎵 欢乐颂","subText":"大家一起欢乐！","bg":"#E91E63","emoji":"🎵 🎶 🎤 🎊 🎉"},"timeoutMs":5000}'

# 更新文字/颜色
curl -s -X POST "http://<GATEWAY_IP>:18795/robot/cmd?token=<YOUR_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"<YOUR_DEVICE_ID>","cmd":"screen.update","args":{"text":"🚀 出发！","bg":"#FF5722"},"timeoutMs":5000}'

# 关闭屏幕，恢复待机
curl -s -X POST "http://<GATEWAY_IP>:18795/robot/cmd?token=<YOUR_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"<YOUR_DEVICE_ID>","cmd":"screen.hide","args":{},"timeoutMs":5000}'
```

### 8) 充电控制
```bash
# 前往充电桩（异步）
curl -s -X POST "http://<GATEWAY_IP>:18795/robot/cmd?token=<YOUR_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"<YOUR_DEVICE_ID>","cmd":"charge.start","args":{"timeoutMs":120000},"timeoutMs":5000}'

# 离开充电桩
curl -s -X POST "http://<GATEWAY_IP>:18795/robot/cmd?token=<YOUR_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"<YOUR_DEVICE_ID>","cmd":"charge.leave","args":{"speed":0.7,"distance":0.3},"timeoutMs":20000}'
```

---

## 导航异步说明（重要）

`nav.start` 是**异步指令**。返回 `{"ok": true}` 表示指令被接受，**不代表机器人已到达**。

- `nav.start` 建议 `timeoutMs` 设为 `5000`
- 通过轮询 `robot.status` 中的 `innavigation:false` 来判断是否到达目的地

---

## 脚本工具（`scripts/` 目录）

| 脚本 | 功能 |
|---|---|
| `robot_cmd.py` | 通用指令发送工具（命令行） |
| `get_places.py` | 列出当前地图所有点位名称 |
| `take_photo_to_file.py` | 拍照并解码 base64 保存为 JPEG |
| `charging.py` | 充电控制（开始/停止/离桩） |
| `dance_player.py` | 动作序列编排播放器（读取 JSON 文件） |
| `music_gen.py` | numpy WAV 音乐合成器 |

---

## 常见错误

| 场景 | ❌ 错误做法 | ✅ 正确做法 |
|---|---|---|
| 导航中再次导航 | 直接发 nav.start | 先 nav.stop，再 nav.start |
| TTS 指令名 | `tts.speak` / `robot.speak` | **`tts.play`**（参数：`{text}`） |
| 拍照指令名 | `camera.photo` / `robot.photo` | **`camera.takePhoto`**（返回 `base64`） |
| 判断是否到达 | 以 `{"ok":true}` 为到达标志 | 轮询 `robot.status` 中 `innavigation:false` |
| 离开充电桩 | 直接导航或转向 | 先 **`charge.leave`** 再做其他动作 |
| 头部左右转 | `head.move yawDeg=...` | ❌ **硬件不支持**，改用 `base.turn` |

---

## 🕺 动作序列编排

机器人支持通过 JSON 文件定义动作序列，实现自动表演。

### JSON 格式

```json
{
  "name": "舞蹈名称",
  "description": "描述",
  "bpm": 120,
  "steps": [
    {"t": 0,    "cmd": "screen.show",  "args": {"text": "🎵", "bg": "#E91E63"}, "label": "屏幕开场"},
    {"t": 500,  "cmd": "tts.play",     "args": {"text": "大家好"}, "wait": true, "label": "开场白"},
    {"t": 3000, "cmd": "head.move",    "args": {"pitchDeg": 80, "hMode": "absolute", "vMode": "absolute"}, "label": "低头"},
    {"t": 6000, "cmd": "base.turn",    "args": {"dir": "left", "angleDeg": 90, "speedDegPerSec": 30}, "label": "左转"},
    {"t": 9000, "cmd": "screen.hide",  "args": {}, "label": "屏幕复位"},
    {"t": 9500, "cmd": "head.reset",   "args": {}, "label": "头部复位"}
  ]
}
```

### 字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `t` | number | 从序列开始的时间戳（毫秒） |
| `cmd` | string | 机器人指令（见白名单） |
| `args` | object | 指令参数 |
| `wait` | bool | 是否等待该步完成后再继续（默认 false） |
| `label` | string | 步骤标签（可选，便于阅读） |

### 播放命令

```bash
export ROBOT_GATEWAY_URL="http://<GATEWAY_IP>:18795"
export ROBOT_TOKEN="<YOUR_TOKEN>"
export ROBOT_DEVICE_ID="<YOUR_DEVICE_ID>"

# 预览（不执行）
python3 scripts/dance_player.py dances/dance_hello.json --dry-run

# 正式播放
python3 scripts/dance_player.py dances/dance_hello.json

# 慢速播放（0.5倍速）
python3 scripts/dance_player.py dances/dance_hello.json --speed 0.5
```

### 内置舞蹈文件

| 文件 | 说明 |
|------|------|
| `dances/dance_hello.json` | 你好舞：点头 + TTS 招呼 |
| `dances/dance_ai_intro.json` | AI 介绍舞：TTS 旁白 + 头部动作 |
| `dances/dance_ode_to_joy.json` | 欢乐颂：音乐 + 仰低头 + 底盘转身 + 7 色屏幕 |

### 能力矩阵（实测）

| 动作类型 | 可用 | 指令 | 备注 |
|----------|------|------|------|
| 低头/仰头 | ✅ | `head.move` pitchDeg 0~80 | 0=最仰，80=最低 |
| 底盘转身 | ✅ | `base.turn` | 充电状态下返回 -9 |
| 语音播报 | ✅ | `tts.play` | — |
| 音乐播放 | ✅ | `audio.play` | 需本地 HTTP 服务 |
| 屏幕显示 | ✅ | `screen.show/update/hide` | 全屏彩色+呼吸动画 |
| 导航移位 | ✅ | `nav.start` | 异步，需轮询状态 |
| 拍照 | ✅ | `camera.takePhoto` | 第一视角，base64 |


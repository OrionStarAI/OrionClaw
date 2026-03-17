# 机器人 Skill Pack（OpenClaw 中文版）

基于服务机器人的 OpenClaw 控制方案，开箱即用。

---

## 前置依赖

在开始之前，请确认以下工具已安装：

| 工具 | 用途 | 安装方式 |
|------|------|----------|
| [OpenClaw](https://docs.openclaw.ai) | AI 控制框架（核心） | 参考官方文档 |
| Node.js 22.16.0+ | 运行 robot-ws-ingress | https://nodejs.org |
| Python 3.8+ | 运行控制脚本 | https://python.org |
| Android SDK + adb | 编译并安装 APK | Android Studio 自带，或单独安装 platform-tools |

> **⚠️ OpenClaw 版本兼容性说明**
>
> | OpenClaw 版本 | 影响 | 处理方式 |
> |---------------|------|----------|
> | **v2026.3.13+**（macOS） | 最低 Node.js 版本提升至 **22.16.0**，低于此版本的 macOS 安装会被拒绝 | 确保 Node.js ≥ 22.16.0 |
> | **v2026.3.2+** | **Breaking**：新安装默认 `tools.profile = messaging`，不含 `exec` 工具。AI 调用 Python 脚本依赖 exec，若未配置则脚本无法执行 | 在 `openclaw.json` 中设置（二选一，见下方说明） |
>
> **修复 tools.profile 问题（v2026.3.2+ 新安装用户）**
>
> 在 `~/.openclaw/openclaw.json` 的顶层加入以下配置之一：
>
> **方案 A：全局改为 coding profile（推荐）**
> ```json
> {
>   "tools": { "profile": "coding" }
> }
> ```
>
> **方案 B：保持 messaging profile，单独开放 exec**
> ```json
> {
>   "tools": {
>     "profile": "messaging",
>     "allow": ["exec", "process"]
>   }
> }
> ```
>
> 修改后重启 OpenClaw 生效。
>
> **其他版本注意**：robot-ws-ingress 插件使用自建 HTTP Server（不调用 `api.registerHttpHandler`），不受 v2026.3.2 Breaking Plugin API 变更影响。

---

## 目录结构

```
robot-skill-pack-zh/
├── README.md                               # 本文件（部署指南）
├── OrionClaw/                              # 📱 机器人控制 APK 源码
│   ├── README.md                           # APK 编译与部署详细说明
│   ├── app/
│   │   ├── build.gradle.kts               # 构建配置（修改包名在此）
│   │   ├── libs/                           # 放入厂商 SDK jar
│   │   └── src/main/java/…                 # Kotlin 源码
│   ├── settings.gradle.kts                # Maven 仓库配置（已配置好）
│   └── gradlew / gradlew.bat              # Gradle 构建脚本
├── robot-ws-ingress/                       # 🔌 Gateway 网关服务
│   ├── index.js                            # 直接运行此文件（已编译）
│   ├── index.ts                            # TypeScript 源码（可读）
│   ├── package.json                        # 依赖声明（仅 ws 库）
│   └── openclaw.plugin.json                # OpenClaw 插件配置声明
├── skills/
│   ├── robot-control/                      # 🤖 机器人控制 Skill
│   │   ├── SKILL.md                        # AI 使用说明（44+ 条指令）
│   │   └── scripts/
│   │       ├── robot_cmd.py                # 通用指令发送工具
│   │       ├── get_places.py               # 查询导航点位
│   │       ├── take_photo_to_file.py       # 拍照并保存为文件
│   │       ├── charging.py                 # 充电控制
│   │       ├── dance_player.py             # 动作序列播放器
│   │       ├── music_gen.py                # WAV 音乐合成器
│   │       └── dances/                     # 示例舞蹈编排文件
│   └── robot-march/                        # 🚶 巡场/穿越路线 Skill
│       ├── SKILL.md
│       └── scripts/march.py               # 巡场脚本
└── workspace-template/                     # 📋 OpenClaw Workspace 模板
    ├── robot-agents-addon.md       # 追加到 AGENTS.md 的机器人控制规则片段
    └── robot-tools-addon.md        # 追加到 TOOLS.md 的机器人连接配置片段
```

---

## 快速部署（5 步）

### 第 1 步：部署 robot-ws-ingress

robot-ws-ingress 是机器人与 OpenClaw 之间的网关服务，负责转发控制指令。

```bash
cd robot-ws-ingress
npm install
```

在 `~/.openclaw/openclaw.json` 中添加插件配置（`/path/to/robot-ws-ingress` 替换为实际目录路径）：

```json
{
  "plugins": {
    "load": {
      "paths": ["/path/to/robot-ws-ingress"]
    },
    "entries": {
      "robot-ws-ingress": {
        "enabled": true,
        "config": {
          "port": 18795,
          "path": "/robot/ws",
          "token": "your-secret-token-here",
          "allowDeviceIds": [],
          "gatewayPort": 18789
        }
      }
    }
  }
}
```

启动 OpenClaw，插件会随 Gateway 自动加载。

**验证网关是否正常运行：**
```bash
curl http://localhost:18795/robot/health
# 期望返回：{"ok":true,"online":0}
```

---

### 第 2 步：安装 OrionClaw APK

OrionClaw 是安装在机器人（Android 设备）上的控制 APP，负责接收指令并调用机器人 OS API。

#### 2.1 下载预编译 APK

从 [GitHub Releases](https://github.com/OrionStarAI/OrionClaw/releases/latest) 下载最新 APK，**无需 Android Studio 或 Java 环境**。

```bash
# macOS / Linux
curl -L -o orionclaw.apk \
  https://github.com/OrionStarAI/OrionClaw/releases/download/v1.0.0/orionclaw-v1.0.0.apk
```

Windows 用户直接在浏览器打开上方链接下载即可。

#### 2.2 安装 adb

adb（Android Debug Bridge）是连接机器人的命令行工具，**只需约 10MB，无需完整 Android Studio**。

| 系统 | 安装命令 |
|------|----------|
| macOS | `brew install android-platform-tools` |
| Windows | `winget install Google.PlatformTools` |
| Linux | `sudo apt install adb` |

#### 2.3 在机器人上开启 Wi-Fi ADB

在机器人的 Android 系统设置中，找到「开发者选项」→「无线调试」（或「ADB over Wi-Fi」），打开开关。**需要在机器人本机上操作**，开启后会显示机器人的 IP 地址（端口默认 5555）。

```bash
# 连接机器人
adb connect <机器人IP地址>:5555
```

#### 2.4 安装 APK

```bash
adb -s <机器人IP地址>:5555 install -r -t orionclaw.apk
```

> ⚠️ 安装时机器人屏幕上可能弹出「是否允许安装」的授权弹框，**需要在机器人上手动点击「允许」**，否则安装会一直等待或失败。

#### 2.5 启动 APP（命令行传参）

通过 ADB 命令启动 APP 并传入配置，无需在机器人界面手动输入：

```bash
adb -s <机器人IP地址>:5555 shell am start -n com.orionstar.openclaw/.MainActivity \
  --es gatewayHost "<网关服务器IP>" \
  --es token "your-secret-token-here" \
  --es deviceId "my-robot"
```

> - `gatewayHost`：运行 robot-ws-ingress 的服务器 IP
> - `token`：与第 1 步配置的 token 一致
> - `deviceId`：随便起一个名字，后面会用到

**验证机器人是否上线：**
```bash
curl "http://localhost:18795/robot/online?token=your-secret-token-here"
# 期望返回：{"ok":true,"devices":["my-robot"]}
```

---

### 第 3 步：配置 OpenClaw Workspace

**安装 Skill 文件**（直接复制，不影响现有文件）：

```bash
cp -r skills/robot-control  ~/.openclaw/workspace/skills/
cp -r skills/robot-march    ~/.openclaw/workspace/skills/
```

**追加机器人配置片段**到你现有的 workspace 文件：

```bash
# 向 AGENTS.md 追加机器人控制规则（拍照、进度播报、地图获取等）
cat workspace-template/robot-agents-addon.md >> ~/.openclaw/workspace/AGENTS.md

# 向 TOOLS.md 追加机器人连接配置
cat workspace-template/robot-tools-addon.md >> ~/.openclaw/workspace/TOOLS.md
```

> **为什么用追加而不是覆盖？**
> `AGENTS.md` 和 `TOOLS.md` 通常已有你自己的配置（如其他设备、偏好、规则）。
> 追加方式只在文件末尾加入机器人相关内容，完全不影响已有内容。
>
> 追加后，打开文件找到 `<GATEWAY_IP>`、`<YOUR_TOKEN>`、`<YOUR_DEVICE_ID>` 并替换为实际值。

---

### 第 4 步：填写机器人配置

打开 `~/.openclaw/workspace/TOOLS.md`，找到刚追加的机器人配置部分，将占位符替换为实际值：

| 占位符 | 替换为 |
|--------|--------|
| `<GATEWAY_IP>` | 运行 robot-ws-ingress 的服务器 IP |
| `<YOUR_TOKEN>` | 第 1 步配置的 token |
| `<YOUR_DEVICE_ID>` | APK 启动时设置的 deviceId |
| `<ROBOT_IP>` | 机器人的 IP 地址（ADB 用） |

> TOOLS.md 是给 AI 读的说明文件，AI 会从这里获取连接参数来控制机器人。

运行脚本时也可以用环境变量：
```bash
export ROBOT_GATEWAY_URL="http://<GATEWAY_IP>:18795"
export ROBOT_TOKEN="<YOUR_TOKEN>"
export ROBOT_DEVICE_ID="<YOUR_DEVICE_ID>"
```

---

### 第 5 步：开始使用

重启 OpenClaw 加载新 Skill，然后向 AI 发送消息：

- "机器人去前台"（导航到某个地点）
- "让机器人说你好"
- "拍一张照片给我看看"
- "让机器人跳欢乐颂"
- "机器人去充电"

> 💡 地点名称必须与机器人地图中的实际点位名称完全一致。可以先说"查询一下机器人现在有哪些导航点位"来获取列表。

---

## 直接运行脚本（不通过 AI）

设置好环境变量后，可以直接用命令行控制机器人：

```bash
export ROBOT_GATEWAY_URL="http://<网关IP>:18795"
export ROBOT_TOKEN="<TOKEN>"
export ROBOT_DEVICE_ID="<设备ID>"

# 查询机器人状态
python3 skills/robot-control/scripts/robot_cmd.py robot.status

# 查询所有导航点位
python3 skills/robot-control/scripts/get_places.py

# 让机器人说话
python3 skills/robot-control/scripts/robot_cmd.py tts.play --args '{"text":"你好，我是服务机器人"}'

# 导航到某个地点
python3 skills/robot-control/scripts/robot_cmd.py nav.start --args '{"destName":"前台"}'

# 拍照保存到本地
python3 skills/robot-control/scripts/take_photo_to_file.py --out-dir ./photos

# 播放舞蹈动作序列
python3 skills/robot-control/scripts/dance_player.py \
  skills/robot-control/scripts/dances/dance_hello.json

# 巡场路线喊话
python3 skills/robot-march/scripts/march.py \
  --waypoints "地点A" "地点B" "地点C" \
  --shout-text "欢迎参观！"
```

---

## 系统架构

```
用户/AI ──HTTP──> robot-ws-ingress ──WebSocket──> OrionClaw APK
                  (端口 18795)                     (Android 机器人)
                       │
                       └── 调用机器人 OS SDK API（导航/拍照/语音等）
```

---

## 常见问题

**Q: 机器人不上线（devices 列表为空）？**
A: 检查 APK 是否正在运行、`gatewayHost` 和 `token` 是否与 ingress 配置完全一致、网络是否互通。

**Q: 导航报错 -108？**
A: 目的地名称不存在于当前地图，先调用 `robot.getPlaceList` 获取真实点位名称，用精确名称导航。

**Q: `nav.start` 返回 ok:true 但机器人没动？**
A: `nav.start` 是异步指令，`ok:true` 只表示命令已被接受。需要轮询 `robot.status` 并检查 `isInNavigation` 字段来判断是否真正到达目的地。

**Q: 编译 APK 时报错找不到 SDK 类？**
A: 确认 `app/libs/robotservice.jar` 存在（文件名必须完全一致），并且 Android Studio 已同步 Gradle。如果仍有问题，执行 File → Sync Project with Gradle Files 或 `./gradlew --refresh-dependencies`。

---

## 许可证

MIT License

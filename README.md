# 机器人 Skill Pack（OpenClaw 中文版）

基于服务机器人的 OpenClaw 控制方案，开箱即用。

---

## 前置依赖

在开始之前，运行以下命令确认环境：

```bash
node --version    # 需要 22.16.0+
python3 --version # 需要 3.8+
adb version       # 需要已安装
```

如未安装，按如下方式安装：

| 工具 | 用途 | macOS | Windows | Linux |
|------|------|-------|---------|-------|
| [OpenClaw](https://docs.openclaw.ai) | AI 控制框架（核心） | 参考官方文档 | 参考官方文档 | 参考官方文档 |
| Node.js 22.16.0+ | 运行 robot-ws-ingress | `brew install node` | [官网下载](https://nodejs.org) | `nvm install 22` |
| Python 3.8+ | 运行控制脚本 | 系统自带 | [官网下载](https://python.org) | `apt install python3` |
| adb | 连接机器人 | `brew install android-platform-tools` | `winget install Google.PlatformTools` | `apt install adb` |

> **adb 无需安装完整 Android Studio**，单独安装 platform-tools（约 10MB）即可。

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

### 第 0 步：备份现有配置

```bash
cp ~/.openclaw/openclaw.json ~/.openclaw/openclaw.json.bak
```

> 任何时候出问题，执行以下命令回滚，然后重启 OpenClaw：
> ```bash
> cp ~/.openclaw/openclaw.json.bak ~/.openclaw/openclaw.json
> ```

---

### 第 1 步：部署 robot-ws-ingress

robot-ws-ingress 是机器人与 OpenClaw 之间的网关服务，负责转发控制指令。

```bash
cd robot-ws-ingress
npm install
```

在 `~/.openclaw/openclaw.json` 中添加插件配置（`/path/to/robot-ws-ingress` 替换为实际绝对路径）：

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

> 如果 `openclaw.json` 里已有 `plugins` 字段，只需在 `entries` 里追加 `robot-ws-ingress` 这一块，在 `load.paths` 数组里追加路径，不要覆盖已有内容。

**重启 OpenClaw：**

macOS（launchd 托管方式）：
```bash
launchctl kickstart -k gui/$UID/ai.openclaw.gateway
```

macOS（直接进程方式，如果上面报错 "service not found"）：
```bash
# macOS / Linux
kill $(ps aux | grep openclaw-gateway | grep -v grep | awk '{print $2}')
sleep 2
openclaw gateway
```

Windows（PowerShell）：
```powershell
Stop-Process -Name "openclaw-gateway" -ErrorAction SilentlyContinue
Start-Sleep 2
openclaw gateway
```

Linux / 服务器：
```bash
openclaw gateway restart
```

**验证网关和插件是否加载成功（启动日志里应有 `[robot-ws-ingress] listening`）：**
```bash
curl http://localhost:18795/robot/health
# 期望返回：{"ok":true,"online":0}
```

---

### 第 2 步：安装 OrionClaw APK

OrionClaw 是安装在机器人（Android 设备）上的控制 APP，负责接收指令并调用机器人 OS API。

#### 2.1 下载预编译 APK（无需 Android Studio）

从 [GitHub Releases](https://github.com/OrionStarAI/OrionClaw/releases/latest) 下载最新 APK：

```bash
# macOS / Linux
curl -L -o orionclaw.apk \
  https://github.com/OrionStarAI/OrionClaw/releases/download/v1.0.0/orionclaw-v1.0.0.apk
```

Windows 用户直接在浏览器打开上方链接下载即可。

> 如需自行编译，参考 `OrionClaw/README.md`（需要 Java 11+ 和 Android SDK）。

#### 2.2 在机器人上开启 Wi-Fi ADB

通过猎户星空接待后台获取动态密码，并在机器人上打开Wi-Fi ADB调试。开启后记录机器人的 IP 地址（端口默认 5555）。

```bash
adb connect <机器人IP地址>:5555
```

#### 2.3 安装 APK

```bash
adb -s <机器人IP地址>:5555 install -r -t orionclaw.apk
```

> ⚠️ 安装时机器人屏幕上可能弹出「是否允许xxx」（例如相机、麦克风等）的授权弹框，**需要在机器人上手动点击「允许」**，否则安装会一直等待或失败。

#### 2.4 启动 APP（带参数）

通过 ADB 命令启动 APP 并传入配置，避免手动在界面输入。

> ⚠️ 必须先 `force-stop` 再启动，否则如果 APP 已在运行，参数注入不会生效。

```bash
# 先强制停止
adb -s <机器人IP地址>:5555 shell am force-stop com.orionstar.openclaw
# 等待 2 秒
sleep 2
# 启动并注入配置
adb -s <机器人IP地址>:5555 shell am start -n com.orionstar.openclaw/.MainActivity \
  --es gatewayHost "<网关服务器IP>" \
  --es token "your-secret-token-here" \
  --es deviceId "my-robot"
```

> - `gatewayHost`：运行 robot-ws-ingress 的机器 IP（不是机器人 IP）
> - `token`：与第 1 步配置的 token 一致
> - `deviceId`：随便起一个名字，后面会用到

完成后，通过应用中心返回机器人主界面，再次点击应用图标完成重启，然后验证机器人是否上线：
```bash
curl "http://localhost:18795/robot/online?token=your-secret-token-here"
# 期望返回：{"ok":true,"devices":["my-robot"]}
```

---

### 第 3 步：配置 OpenClaw Workspace

**先备份，再追加：**

```bash
# 备份
cp ~/.openclaw/workspace/AGENTS.md ~/.openclaw/workspace/AGENTS.md.bak
cp ~/.openclaw/workspace/TOOLS.md  ~/.openclaw/workspace/TOOLS.md.bak

# 复制 Skills
cp -r skills/robot-control ~/.openclaw/workspace/skills/
cp -r skills/robot-march   ~/.openclaw/workspace/skills/

# 追加机器人配置
cat workspace-template/robot-agents-addon.md >> ~/.openclaw/workspace/AGENTS.md
cat workspace-template/robot-tools-addon.md  >> ~/.openclaw/workspace/TOOLS.md
```

> 追加方式只在文件末尾加入机器人相关内容，完全不影响已有内容。如需回滚：
> ```bash
> cp ~/.openclaw/workspace/AGENTS.md.bak ~/.openclaw/workspace/AGENTS.md
> cp ~/.openclaw/workspace/TOOLS.md.bak  ~/.openclaw/workspace/TOOLS.md
> ```

---

### 第 4 步：填写机器人配置

追加完成后，**AGENTS.md 和 TOOLS.md 里都有占位符需要替换**：

macOS / Linux：
```bash
# 替换 TOOLS.md
sed -i '' \
  -e 's/<GATEWAY_IP>/你的网关IP/g' \
  -e 's/<YOUR_TOKEN>/your-secret-token-here/g' \
  -e 's/<YOUR_DEVICE_ID>/my-robot/g' \
  -e 's/<ROBOT_IP>/机器人IP/g' \
  ~/.openclaw/workspace/TOOLS.md

# 替换 AGENTS.md
sed -i '' \
  -e 's/<GATEWAY_IP>/你的网关IP/g' \
  -e 's/<YOUR_TOKEN>/your-secret-token-here/g' \
  -e 's/<YOUR_DEVICE_ID>/my-robot/g' \
  ~/.openclaw/workspace/AGENTS.md
```

Windows（PowerShell）：
```powershell
(Get-Content ~/.openclaw/workspace/TOOLS.md) `
  -replace '<GATEWAY_IP>','你的网关IP' `
  -replace '<YOUR_TOKEN>','your-secret-token-here' `
  -replace '<YOUR_DEVICE_ID>','my-robot' `
  -replace '<ROBOT_IP>','机器人IP' |
  Set-Content ~/.openclaw/workspace/TOOLS.md

(Get-Content ~/.openclaw/workspace/AGENTS.md) `
  -replace '<GATEWAY_IP>','你的网关IP' `
  -replace '<YOUR_TOKEN>','your-secret-token-here' `
  -replace '<YOUR_DEVICE_ID>','my-robot' |
  Set-Content ~/.openclaw/workspace/AGENTS.md
```

| 占位符 | 替换为 |
|--------|--------|
| `<GATEWAY_IP>` | 运行 robot-ws-ingress 的服务器 IP |
| `<YOUR_TOKEN>` | 第 1 步配置的 token |
| `<YOUR_DEVICE_ID>` | APK 启动时设置的 deviceId |
| `<ROBOT_IP>` | 机器人的 IP 地址（ADB 用） |

**在 AGENTS.md 的启动清单里加一条读 TOOLS.md 的指令：**

找到 `AGENTS.md` 里的「Every Session」部分，确认第 4 条是否为以下内容，如果没有请手动添加：

```
4. Read `TOOLS.md` — connection config (robots, devices, etc.)
```

> 这一步很重要：AI 每次启动时会按顺序读这个清单，没有这条指令它就不知道去 TOOLS.md 找机器人连接参数，会反过来问你。

---

### 第 5 步：开始使用

重启 OpenClaw 加载新 Skill（重启方式同第 1 步），然后向 AI 发送消息：

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

**Q: `curl localhost:18795` 连不上？**
A: robot-ws-ingress 插件没有加载。检查 `openclaw.json` 里是否同时有 `load.paths` 和 `entries.robot-ws-ingress`，重启后查看启动日志，确认有 `[robot-ws-ingress] listening` 字样。

**Q: 机器人不上线（devices 列表为空）？**
A: 先 `force-stop` APP，再重新 `am start`。确认 `gatewayHost` 填的是运行 robot-ws-ingress 的机器 IP（不是 localhost），`token` 与 `openclaw.json` 里的一致，机器人和网关在同一网络下。

**Q: AI 还是问我要 Gateway 地址/Token？**
A: 两个可能：①TOOLS.md 里的占位符没替换完；②AGENTS.md 的启动清单里没有「Read `TOOLS.md`」这一条。

**Q: 导航报错 -108？**
A: 目的地名称不存在于当前地图，先调用 `robot.getPlaceList` 获取真实点位名称，用精确名称导航。

**Q: `nav.start` 返回 ok:true 但机器人没动？**
A: `nav.start` 是异步指令，`ok:true` 只表示命令已被接受。需要轮询 `robot.status` 并检查 `isInNavigation` 字段来判断是否真正到达目的地。

**Q: 编译 APK 时报错找不到 SDK 类？**
A: 确认 `app/libs/robotservice.jar` 存在（文件名必须完全一致）。如果仍有问题，执行 `./gradlew --refresh-dependencies`。

---

## 许可证

MIT License

# OrionClaw — 机器人控制 Android APK

这是一个极简的 Android APK，用于将服务机器人接入 **OpenClaw** 控制体系。

## 功能说明

APK 启动后会主动通过 WebSocket 连接到 `robot-ws-ingress` 服务，注册设备身份，并监听来自 OpenClaw 的控制指令，转发给机器人底层 OS（通过 RobotOS SDK）。

主要能力：
- 导航：`nav.start` / `nav.stop` / `nav.status` / `robot.getPlaceList`
- 拍照：`camera.takePhoto`
- 语音播报：`tts.play`
- 头部控制：`head.move` / `head.reset`
- 底盘转向：`base.turn`
- 屏幕显示：`screen.show` / `screen.update` / `screen.hide`
- 音频播放：`audio.play` / `audio.stop`
- 充电控制：`charge.start` / `charge.leave`
- 状态查询：`robot.status`

---

## 编译前准备

### 第 1 步：确认 SDK

SDK jar（`robotservice.jar`）已在 `app/libs/` 目录中，Maven 仓库地址和 credentials 已在 `settings.gradle.kts` 中配置好，**无需任何修改。**

### 第 2 步：确认包名

源码已使用包名 `com.orionstar.openclaw`，**无需修改**，直接编译即可。

如需改为自己的包名，修改 `app/build.gradle.kts` 中的 `namespace` 和 `applicationId`，并重命名对应的源码目录。

### 第 3 步：编译

```bash
./gradlew assembleDebug
```

> 需要 Android SDK（API 级别 26+）。推荐使用 Android Studio 打开项目。

产物路径：`app/build/outputs/apk/debug/app-debug.apk`

---

## 获取 APK

**推荐：直接下载预编译包（无需 Android Studio）**

从 [GitHub Releases](https://github.com/OrionStarAI/OrionClaw/releases/latest) 下载最新 APK：

```bash
curl -L -o orionclaw.apk \
  https://github.com/OrionStarAI/OrionClaw/releases/download/v1.0.0/orionclaw-v1.0.0.apk
```

如需自行编译，参考上方「编译前准备」步骤（需要 Android SDK）。

---

## 安装到机器人

**第一步：通过 backoffice 进入机器人后台，打开 Wi-Fi ADB 调试。**

```bash
# 连接机器人
adb connect <机器人IP>:5555

# 安装 APK
adb -s <机器人IP>:5555 install -r -t orionclaw.apk
```

> ⚠️ 安装时机器人屏幕可能弹出授权框，**需要在机器人上手动点击「允许」**。

---

## 启动方式（带参数）

建议通过 ADB 携带配置参数启动，避免手动在界面输入。

> ⚠️ 必须先 `force-stop` 再启动，否则如果 APP 已在运行，参数注入不会生效。

```bash
# 先强制停止
adb -s <机器人IP>:5555 shell am force-stop com.orionstar.openclaw
sleep 2
# 启动并注入配置
adb -s <机器人IP>:5555 shell am start -n com.orionstar.openclaw/.MainActivity \
  --es gatewayHost "<YOUR_GATEWAY_IP>" \
  --es token "<YOUR_TOKEN>" \
  --es deviceId "<YOUR_DEVICE_ID>"
```

| 参数 | 说明 |
|------|------|
| `gatewayHost` | `robot-ws-ingress` 所在服务器 IP |
| `token` | 与 `robot-ws-ingress` 配置的共享 token 一致 |
| `deviceId` | 机器人设备唯一标识，与 OpenClaw 配置保持一致 |

---

## 验证连接

APK 启动并连接成功后，可通过以下接口确认设备在线：

```bash
curl "http://<YOUR_GATEWAY_IP>:18795/robot/online?token=<YOUR_TOKEN>"
# 返回示例：{"ok":true,"devices":["<YOUR_DEVICE_ID>"]}
```

---

## 注意事项

- RobotOS API 要求 APK 保持**前台运行**以持有底盘控制权。
- 如果 SDK 类在运行时未找到，Bridge 会打印错误日志，不会崩溃。
- APK 使用反射调用（`RobotOsBridge`），源码可在不打包厂商 SDK 的情况下正常编译。

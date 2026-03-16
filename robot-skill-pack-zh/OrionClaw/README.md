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

### 第 1 步：获取厂商 SDK

联系你的机器人厂商，获取适配你的机器人 OS 版本的 SDK jar 文件（通常命名为 `robotservice_<版本>.jar`），重命名为 `robotservice.jar`，放入：

```
app/libs/robotservice.jar
```

同时确认 `settings.gradle.kts` 中 Maven 仓库地址已更新为厂商提供的实际地址（当前为占位符）：

```kotlin
maven {
    url = uri("https://your-vendor-maven-repo/")  // ← 替换为厂商实际 Maven 地址
}
```

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

## 安装到机器人

```bash
adb connect <ADB_ADDRESS>
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
```

---

## 启动方式（带参数）

建议通过 ADB 携带配置参数启动，避免手动在界面输入：

```bash
adb shell am start -n com.orionstar.openclaw/.MainActivity \
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

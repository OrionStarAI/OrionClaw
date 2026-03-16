#!/usr/bin/env python3
"""
robot-march: 机器人穿越路线巡检，边走边喊话

用法：
  python3 march.py --waypoints <PLACE_1> <PLACE_2> <PLACE_3> <PLACE_END> \
                   [--shout-text "<YOUR_SHOUT_TEXT>"] \
                   [--shout-interval 10]

配置（优先级顺序）：
  1. CLI flags: --gateway, --token, --device
  2. 环境变量: ROBOT_GATEWAY_URL, ROBOT_TOKEN, ROBOT_DEVICE_ID

视觉检测需要 LiteLLM 或兼容的 OpenAI API：
  --litellm-url  (默认: http://localhost:4000/v1/chat/completions)
  --litellm-key  (默认: local-litellm-key)
  --model        (默认: claude-sonnet-4-5, 需支持多模态)
"""
import subprocess, json, base64, time, os, sys, argparse
import urllib.request

def get_env(key, default=""):
    return os.environ.get(key, default)

def log(msg):
    print(f"[{time.strftime('%H:%M:%S')}] {msg}", flush=True)

def robot_cmd(gateway, token, device, cmd, args={}, timeout=15000):
    body = json.dumps({"deviceId": device, "cmd": cmd, "args": args, "timeoutMs": timeout})
    try:
        r = subprocess.run(
            ["curl", "-s", "-X", "POST", f"{gateway}/robot/cmd?token={token}",
             "-H", "Content-Type: application/json", "-d", body],
            capture_output=True, text=True, timeout=25
        )
        return json.loads(r.stdout)
    except Exception as e:
        log(f"  robot_cmd error: {e}")
        return {}

def take_photo(gateway, token, device, photo_dir):
    r = robot_cmd(gateway, token, device, "camera.takePhoto", {}, 15000)
    b64 = r.get("result", {}).get("data", {}).get("base64", "")
    if not b64:
        return None, None
    path = f"{photo_dir}/march_{int(time.time())}.jpg"
    open(path, "wb").write(base64.b64decode(b64))
    return path, b64

def has_person(b64_data, litellm_url, litellm_key, model):
    payload = {
        "model": model,
        "max_tokens": 10,
        "messages": [{
            "role": "user",
            "content": [
                {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{b64_data}"}},
                {"type": "text", "text": "画面中有没有真实的人？只回答YES或NO。"}
            ]
        }]
    }
    req = urllib.request.Request(
        litellm_url,
        data=json.dumps(payload).encode(),
        headers={"Content-Type": "application/json", "Authorization": f"Bearer {litellm_key}"}
    )
    res = json.loads(urllib.request.urlopen(req, timeout=20).read())
    answer = res["choices"][0]["message"]["content"].strip().upper()
    return "YES" in answer

def tts(gateway, token, device, text):
    robot_cmd(gateway, token, device, "tts.play", {"text": text}, 15000)

def is_navigating(gateway, token, device):
    r = robot_cmd(gateway, token, device, "robot.status", {}, 10000)
    nav_str = r.get("result", {}).get("data", {}).get("status", {}).get("isInNavigation", "{}")
    try:
        return json.loads(nav_str).get("innavigation", False)
    except:
        return False

def nav_to(gateway, token, device, dest):
    robot_cmd(gateway, token, device, "nav.stop", {}, 5000)
    time.sleep(1)
    r = robot_cmd(gateway, token, device, "nav.start", {"destName": dest}, 8000)
    ok = r.get("ok", False) and r.get("result", {}).get("ok", False)
    if not ok:
        log(f"  ⚠️ 第一次nav失败，3秒后重试")
        time.sleep(3)
        r = robot_cmd(gateway, token, device, "nav.start", {"destName": dest}, 8000)
        ok = r.get("ok", False) and r.get("result", {}).get("ok", False)
    log(f"  nav.start({dest}) ok={ok}")
    return ok

def main():
    parser = argparse.ArgumentParser(description="robot-march: 机器人穿越路线巡检")
    parser.add_argument("--waypoints", nargs="+", required=True,
                        help="途经点位列表，最后一个为终点（终点不喊话）")
    parser.add_argument("--shout-text", default="<YOUR_SHOUT_TEXT>",
                        help="检测到人时喊的话")
    parser.add_argument("--shout-interval", type=int, default=10,
                        help="连续喊话最小间隔（秒），默认10")
    parser.add_argument("--gateway", default=get_env("ROBOT_GATEWAY_URL"),
                        help="Gateway URL (env: ROBOT_GATEWAY_URL)")
    parser.add_argument("--token", default=get_env("ROBOT_TOKEN"),
                        help="Auth token (env: ROBOT_TOKEN)")
    parser.add_argument("--device", default=get_env("ROBOT_DEVICE_ID"),
                        help="Device ID (env: ROBOT_DEVICE_ID)")
    parser.add_argument("--litellm-url",
                        default=get_env("LITELLM_URL", "http://localhost:4000/v1/chat/completions"),
                        help="LiteLLM API URL for vision inference")
    parser.add_argument("--litellm-key",
                        default=get_env("LITELLM_KEY", "local-litellm-key"),
                        help="LiteLLM API key")
    parser.add_argument("--model", default="claude-sonnet-4-5",
                        help="Multimodal model to use for person detection")
    parser.add_argument("--photo-dir", default="./photos",
                        help="Directory to save patrol photos")
    args = parser.parse_args()

    # Validate required config
    missing = []
    if not args.gateway: missing.append("--gateway / ROBOT_GATEWAY_URL")
    if not args.token:   missing.append("--token / ROBOT_TOKEN")
    if not args.device:  missing.append("--device / ROBOT_DEVICE_ID")
    if missing:
        print(f"Error: missing required config: {', '.join(missing)}", file=sys.stderr)
        sys.exit(1)

    os.makedirs(args.photo_dir, exist_ok=True)

    waypoints = args.waypoints
    shout_text = args.shout_text
    shout_interval = args.shout_interval

    log(f"🚀 出发！路线：{' → '.join(waypoints)}")
    log(f"   喊话内容：{shout_text}  间隔：{shout_interval}s")

    for dest in waypoints:
        is_last = (dest == waypoints[-1])
        log(f"\n▶ 前往：{dest}")

        nav_to(args.gateway, args.token, args.device, dest)

        log(f"  等3秒让机器人出发...")
        time.sleep(3)

        last_shout_time = 0
        no_nav_count = 0

        while True:
            navigating = is_navigating(args.gateway, args.token, args.device)
            if not navigating:
                no_nav_count += 1
                log(f"  isNavigating=False ({no_nav_count}/2)")
                if no_nav_count >= 2:
                    log(f"  ✅ 确认到达 {dest}")
                    break
            else:
                no_nav_count = 0

            if not is_last:
                try:
                    path, b64 = take_photo(args.gateway, args.token, args.device, args.photo_dir)
                    if path and b64:
                        person = has_person(b64, args.litellm_url, args.litellm_key, args.model)
                        now = time.time()
                        if person:
                            if now - last_shout_time >= shout_interval:
                                log(f"  👥 检测到人！喊话！")
                                tts(args.gateway, args.token, args.device, shout_text)
                                last_shout_time = now
                            else:
                                log(f"  👥 有人，距上次喊话{int(now-last_shout_time)}s，等待中")
                        else:
                            log(f"  🤫 无人")
                    else:
                        log(f"  ❌ 拍照失败")
                except Exception as e:
                    log(f"  ⚠️ 检测异常: {e}")

            time.sleep(4)

    log(f"\n🏁 已到达终点 {waypoints[-1]}，任务完成，停止喊话。")

if __name__ == "__main__":
    main()

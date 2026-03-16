#!/usr/bin/env python3
"""Robot Dance / Action Sequence Player

Reads a choreography JSON file and executes each step at the right time.

Choreography JSON format:
{
  "name": "舞蹈名称",
  "description": "描述",
  "bpm": 120,        // optional, for reference
  "steps": [
    {"t": 0,    "cmd": "tts.play",  "args": {"text": "大家好！"}, "wait": true},
    {"t": 2000, "cmd": "head.move", "args": {"pitchDeg": "<PITCH_DEG>", "hMode": "absolute", "vMode": "absolute"}},
    {"t": 3500, "cmd": "base.turn", "args": {"dir": "left", "angleDeg": "<ANGLE_DEG>", "speedDegPerSec": "<SPEED_DEG_PER_SEC>"}},
    {"t": 5000, "cmd": "head.reset","args": {}},
    ...
  ]
}

Step fields:
  t      : timestamp in milliseconds from start
  cmd    : robot command (see allowlist in SKILL.md)
  args   : command arguments (dict)
  wait   : if true, wait for this step to complete before continuing (default: false)
  label  : optional human-readable label for this step

Config (in priority order):
  1. CLI flags: --base-url, --token, --device-id
  2. Environment variables: ROBOT_GATEWAY_URL, ROBOT_TOKEN, ROBOT_DEVICE_ID

Examples:
  export ROBOT_GATEWAY_URL="http://<GATEWAY_IP>:18795"
  export ROBOT_TOKEN="<YOUR_TOKEN>"
  export ROBOT_DEVICE_ID="<YOUR_DEVICE_ID>"

  python3 dance_player.py dance_hello.json
  python3 dance_player.py dance_hello.json --dry-run
  python3 dance_player.py dance_hello.json --speed 0.5   # half speed (slow motion)
"""

import argparse
import json
import os
import sys
import time
import urllib.request
import urllib.parse
import threading


# ── HTTP helper ──────────────────────────────────────────────────────────────

def http_post_json(url: str, payload: dict, timeout_s: float) -> dict:
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(
        url, data=data,
        headers={"content-type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=timeout_s) as resp:
        return json.loads(resp.read().decode("utf-8"))


def send_cmd(base_url: str, token: str, device_id: str,
             cmd: str, args: dict, timeout_ms: int = 15000) -> dict:
    url = f"{base_url.rstrip('/')}/robot/cmd?token={urllib.parse.quote(token)}"
    body = {"deviceId": device_id, "cmd": cmd, "timeoutMs": timeout_ms, "args": args}
    http_timeout_s = max(5.0, timeout_ms / 1000.0 + 5.0)
    return http_post_json(url, body, http_timeout_s)


# ── Choreography player ───────────────────────────────────────────────────────

def play(choreo: dict, base_url: str, token: str, device_id: str,
         speed: float = 1.0, dry_run: bool = False):
    """Execute a choreography sequence."""
    name = choreo.get("name", "未命名")
    steps = sorted(choreo.get("steps", []), key=lambda s: s["t"])

    print(f"\n🎭 开始播放：{name}")
    if choreo.get("description"):
        print(f"   {choreo['description']}")
    print(f"   共 {len(steps)} 步，speed={speed}x, dry_run={dry_run}\n")

    start_time = time.time()

    for i, step in enumerate(steps):
        target_t = step["t"] / 1000.0 / speed   # seconds from start
        cmd      = step.get("cmd", "")
        args     = step.get("args", {})
        label    = step.get("label", "")
        wait     = step.get("wait", False)

        # Sleep until this step's time
        elapsed = time.time() - start_time
        sleep_s = target_t - elapsed
        if sleep_s > 0:
            time.sleep(sleep_s)

        t_str  = f"{step['t']}ms"
        lbl    = f" [{label}]" if label else ""
        print(f"  ▶ [{i+1}/{len(steps)}] t={t_str}{lbl}  {cmd}  args={json.dumps(args, ensure_ascii=False)}")

        if dry_run:
            # Simulate execution time
            if wait:
                time.sleep(0.5)
            continue

        try:
            resp = send_cmd(base_url, token, device_id, cmd, args)
            ok = resp.get("ok", False)
            status = "✅" if ok else "❌"
            print(f"     {status} {json.dumps(resp.get('result', {}).get('data', {}), ensure_ascii=False)[:80]}")
        except Exception as e:
            print(f"     ❌ Error: {e}")

    # Always reset head at the end
    elapsed = time.time() - start_time
    print(f"\n🏁 序列结束（用时 {elapsed:.1f}s），复位头部...")
    if not dry_run:
        try:
            send_cmd(base_url, token, device_id, "head.reset", {})
        except Exception as e:
            print(f"  head.reset failed: {e}")
    print("✅ 完成！\n")


# ── Main ─────────────────────────────────────────────────────────────────────

def main():
    ap = argparse.ArgumentParser(
        description="Robot dance / action sequence player",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    ap.add_argument("choreo_file", help="Path to choreography JSON file")
    ap.add_argument("--base-url",  default=os.environ.get("ROBOT_GATEWAY_URL", ""))
    ap.add_argument("--token",     default=os.environ.get("ROBOT_TOKEN", ""))
    ap.add_argument("--device-id", default=os.environ.get("ROBOT_DEVICE_ID", ""))
    ap.add_argument("--speed",     type=float, default=1.0,
                    help="Playback speed multiplier (0.5=slow, 2.0=fast, default 1.0)")
    ap.add_argument("--dry-run",   action="store_true",
                    help="Print steps without executing them")
    args = ap.parse_args()

    if not args.dry_run:
        missing = []
        if not args.base_url:  missing.append("--base-url / ROBOT_GATEWAY_URL")
        if not args.token:     missing.append("--token / ROBOT_TOKEN")
        if not args.device_id: missing.append("--device-id / ROBOT_DEVICE_ID")
        if missing:
            print(f"Error: missing config: {', '.join(missing)}", file=sys.stderr)
            sys.exit(1)

    with open(args.choreo_file, encoding="utf-8") as f:
        choreo = json.load(f)

    play(choreo, args.base_url, args.token, args.device_id,
         speed=args.speed, dry_run=args.dry_run)


if __name__ == "__main__":
    main()

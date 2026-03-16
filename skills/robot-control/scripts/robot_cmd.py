#!/usr/bin/env python3
"""Generic robot-ws-ingress /robot/cmd caller.

No external deps (stdlib only).

Config (in priority order):
  1. CLI flags: --base-url, --token, --device-id
  2. Environment variables: ROBOT_GATEWAY_URL, ROBOT_TOKEN, ROBOT_DEVICE_ID

Examples:
  # Using environment variables
  export ROBOT_GATEWAY_URL="http://<GATEWAY_IP>:18795"
  export ROBOT_TOKEN="<YOUR_TOKEN>"
  export ROBOT_DEVICE_ID="<YOUR_DEVICE_ID>"

  python3 robot_cmd.py robot.status
  python3 robot_cmd.py nav.start --args '{"destName":"<PLACE_NAME>"}' --timeout-ms 5000
  python3 robot_cmd.py tts.play --args '{"text":"你好"}' --timeout-ms 15000

  # Or pass flags directly
  python3 robot_cmd.py --base-url http://<GATEWAY_IP>:18795 --token <YOUR_TOKEN> \\
    --device-id <YOUR_DEVICE_ID> robot.status
"""

import argparse
import json
import os
import sys
import urllib.request
import urllib.parse


def http_post_json(url: str, payload: dict, timeout_s: float):
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=data,
        headers={"content-type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=timeout_s) as resp:
        return resp.read().decode("utf-8")


def main():
    ap = argparse.ArgumentParser(
        description="Generic robot-ws-ingress /robot/cmd caller",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    ap.add_argument(
        "--base-url",
        default=os.environ.get("ROBOT_GATEWAY_URL", ""),
        help="Gateway base URL (env: ROBOT_GATEWAY_URL)",
    )
    ap.add_argument(
        "--token",
        default=os.environ.get("ROBOT_TOKEN", ""),
        help="Auth token (env: ROBOT_TOKEN)",
    )
    ap.add_argument(
        "--device-id",
        default=os.environ.get("ROBOT_DEVICE_ID", ""),
        help="Device ID (env: ROBOT_DEVICE_ID)",
    )
    ap.add_argument("--timeout-ms", type=int, default=30000, help="Command timeout in ms")
    ap.add_argument("cmd", help="Robot command (e.g. robot.status, nav.start)")
    ap.add_argument("--args", default=None, help="JSON string for command args")
    args = ap.parse_args()

    # Validate required config
    missing = []
    if not args.base_url:
        missing.append("--base-url / ROBOT_GATEWAY_URL")
    if not args.token:
        missing.append("--token / ROBOT_TOKEN")
    if not args.device_id:
        missing.append("--device-id / ROBOT_DEVICE_ID")
    if missing:
        print(f"Error: missing required config: {', '.join(missing)}", file=sys.stderr)
        print("Set environment variables or pass CLI flags.", file=sys.stderr)
        sys.exit(1)

    url = f"{args.base_url.rstrip('/')}/robot/cmd?token={urllib.parse.quote(args.token)}"
    body = {
        "deviceId": args.device_id,
        "cmd": args.cmd,
        "timeoutMs": args.timeout_ms,
    }
    if args.args:
        body["args"] = json.loads(args.args)

    # Add a small extra HTTP timeout cushion
    http_timeout_s = max(5.0, args.timeout_ms / 1000.0 + 10.0)

    try:
        raw = http_post_json(url, body, http_timeout_s)
        print(raw)
    except Exception as e:
        print(json.dumps({"ok": False, "error": str(e)}), file=sys.stderr)
        sys.exit(2)


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Print place list names (one per line).

Uses robot.getPlaceList via /robot/cmd.
No external deps (stdlib only).

Config (in priority order):
  1. CLI flags: --base-url, --token, --device-id
  2. Environment variables: ROBOT_GATEWAY_URL, ROBOT_TOKEN, ROBOT_DEVICE_ID

Examples:
  export ROBOT_GATEWAY_URL="http://<GATEWAY_IP>:18795"
  export ROBOT_TOKEN="<YOUR_TOKEN>"
  export ROBOT_DEVICE_ID="<YOUR_DEVICE_ID>"

  python3 get_places.py
  python3 get_places.py | grep "<KEYWORD>"
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
        description="List all named places on the robot's current map",
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
        sys.exit(1)

    url = f"{args.base_url.rstrip('/')}/robot/cmd?token={urllib.parse.quote(args.token)}"
    body = {
        "deviceId": args.device_id,
        "cmd": "robot.getPlaceList",
        "timeoutMs": 20000,
    }

    raw = http_post_json(url, body, 40)
    j = json.loads(raw)
    msg = j.get("result", {}).get("data", {}).get("message")
    if not msg:
        print("No place list returned.", file=sys.stderr)
        return

    arr = json.loads(msg)
    for it in arr:
        name = it.get("name")
        if name:
            print(name)


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(str(e), file=sys.stderr)
        sys.exit(2)

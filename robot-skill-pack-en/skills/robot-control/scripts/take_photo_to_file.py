#!/usr/bin/env python3
"""camera.takePhoto -> decode base64 -> save jpg -> print path.

Avoids passing base64 image data through chat/logs.
No external deps (stdlib only).

Config (in priority order):
  1. CLI flags: --base-url, --token, --device-id
  2. Environment variables: ROBOT_GATEWAY_URL, ROBOT_TOKEN, ROBOT_DEVICE_ID

Examples:
  export ROBOT_GATEWAY_URL="http://<GATEWAY_IP>:18795"
  export ROBOT_TOKEN="<YOUR_TOKEN>"
  export ROBOT_DEVICE_ID="<YOUR_DEVICE_ID>"

  python3 take_photo_to_file.py --out-dir ./photos
  python3 take_photo_to_file.py --out-dir ./photos --max-width 1280 --quality 90
"""

import argparse
import base64
import datetime
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
        description="Take photo via robot camera, decode base64, save as JPEG",
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
    ap.add_argument("--out-dir", required=True, help="Output directory for saved photos")
    ap.add_argument("--max-width", type=int, default=640, help="Max image width (px)")
    ap.add_argument("--quality", type=int, default=80, help="JPEG quality (1-100)")
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

    os.makedirs(args.out_dir, exist_ok=True)

    url = f"{args.base_url.rstrip('/')}/robot/cmd?token={urllib.parse.quote(args.token)}"
    body = {
        "deviceId": args.device_id,
        "cmd": "camera.takePhoto",
        "timeoutMs": 60000,
        "args": {"maxWidth": args.max_width, "quality": args.quality},
    }

    raw = http_post_json(url, body, 80)
    j = json.loads(raw)
    b64 = j.get("result", {}).get("data", {}).get("base64")
    if not b64:
        raise RuntimeError(
            "camera.takePhoto returned no base64 data. "
            f"Response keys: {list(j.get('result', {}).get('data', {}).keys())}"
        )

    jpg = base64.b64decode(b64)
    ts = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    out = os.path.join(args.out_dir, f"{ts}-photo.jpg")
    with open(out, "wb") as f:
        f.write(jpg)

    print(out)


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(str(e), file=sys.stderr)
        sys.exit(2)

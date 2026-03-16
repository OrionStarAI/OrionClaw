#!/usr/bin/env python3
"""Robot charging control script.

Supports three sub-commands:
  start   -- Navigate to charger and begin charging (async)
  stop    -- Stop auto-charging
  leave   -- Drive off the charging pile

No external deps (stdlib only).

Config (in priority order):
  1. CLI flags: --base-url, --token, --device-id
  2. Environment variables: ROBOT_GATEWAY_URL, ROBOT_TOKEN, ROBOT_DEVICE_ID

Examples:
  export ROBOT_GATEWAY_URL="http://<GATEWAY_IP>:18795"
  export ROBOT_TOKEN="<YOUR_TOKEN>"
  export ROBOT_DEVICE_ID="<YOUR_DEVICE_ID>"

  python3 charging.py start                           # start charging (default timeout 120s)
  python3 charging.py start --timeout 180             # start charging with 180s timeout
  python3 charging.py stop                            # stop charging
  python3 charging.py leave                           # leave charger (default speed/distance)
  python3 charging.py leave --speed 0.5 --distance 0.3  # custom speed and distance

  # Check charging status:
  python3 robot_cmd.py robot.status | python3 -c "
  import sys, json
  s = json.load(sys.stdin)
  data = s.get('result', {}).get('data', {})
  print('isCharging:', data.get('isCharging'))
  "
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


def send_cmd(base_url: str, token: str, device_id: str, cmd: str, args: dict, timeout_ms: int):
    """Send a robot command and return the parsed response."""
    url = f"{base_url.rstrip('/')}/robot/cmd?token={urllib.parse.quote(token)}"
    body = {
        "deviceId": device_id,
        "cmd": cmd,
        "timeoutMs": timeout_ms,
        "args": args,
    }
    http_timeout_s = max(5.0, timeout_ms / 1000.0 + 10.0)
    raw = http_post_json(url, body, http_timeout_s)
    return json.loads(raw)


def add_common_args(parser: argparse.ArgumentParser):
    """Add shared config args to a subparser."""
    parser.add_argument(
        "--base-url",
        default=os.environ.get("ROBOT_GATEWAY_URL", ""),
        help="Gateway base URL (env: ROBOT_GATEWAY_URL)",
    )
    parser.add_argument(
        "--token",
        default=os.environ.get("ROBOT_TOKEN", ""),
        help="Auth token (env: ROBOT_TOKEN)",
    )
    parser.add_argument(
        "--device-id",
        default=os.environ.get("ROBOT_DEVICE_ID", ""),
        help="Device ID (env: ROBOT_DEVICE_ID)",
    )


def validate_config(args):
    """Validate that required config is present."""
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


def cmd_start(args):
    """Navigate to charger and begin charging (async)."""
    validate_config(args)

    timeout_ms = args.timeout * 1000  # convert seconds → ms
    print(f"Starting auto-charge navigation (timeout: {args.timeout}s)...")
    print("This is async — command returns immediately, robot moves in background.")
    print("Use 'python3 robot_cmd.py robot.status' to check isCharging status.")

    resp = send_cmd(
        args.base_url,
        args.token,
        args.device_id,
        cmd="charge.start",
        args={"timeoutMs": timeout_ms},
        timeout_ms=8000,  # just enough to accept the command
    )
    print(json.dumps(resp, ensure_ascii=False, indent=2))


def cmd_stop(args):
    """Stop auto-charging."""
    validate_config(args)

    print("Stopping auto-charge...")
    resp = send_cmd(
        args.base_url,
        args.token,
        args.device_id,
        cmd="charge.stop",
        args={},
        timeout_ms=8000,
    )
    print(json.dumps(resp, ensure_ascii=False, indent=2))


def cmd_leave(args):
    """Drive off the charging pile."""
    validate_config(args)

    leave_args = {}
    if args.speed is not None:
        leave_args["speed"] = args.speed
    if args.distance is not None:
        leave_args["distance"] = args.distance

    speed_str = f"{args.speed}" if args.speed is not None else "default"
    dist_str = f"{args.distance}m" if args.distance is not None else "default"
    print(f"Leaving charging pile (speed: {speed_str}, distance: {dist_str})...")

    resp = send_cmd(
        args.base_url,
        args.token,
        args.device_id,
        cmd="charge.leave",
        args=leave_args,
        timeout_ms=15000,
    )
    print(json.dumps(resp, ensure_ascii=False, indent=2))


def main():
    ap = argparse.ArgumentParser(
        description="Robot charging control",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    subparsers = ap.add_subparsers(dest="action", metavar="ACTION")
    subparsers.required = True

    # ---- start ----
    p_start = subparsers.add_parser(
        "start",
        help="Navigate to charger and begin charging (async)",
    )
    add_common_args(p_start)
    p_start.add_argument(
        "--timeout",
        type=int,
        default=120,
        metavar="SECONDS",
        help="Charging navigation timeout in seconds (default: 120)",
    )
    p_start.set_defaults(func=cmd_start)

    # ---- stop ----
    p_stop = subparsers.add_parser(
        "stop",
        help="Stop auto-charging",
    )
    add_common_args(p_stop)
    p_stop.set_defaults(func=cmd_stop)

    # ---- leave ----
    p_leave = subparsers.add_parser(
        "leave",
        help="Drive off the charging pile",
    )
    add_common_args(p_leave)
    p_leave.add_argument(
        "--speed",
        type=float,
        default=None,
        metavar="SPEED",
        help="Movement speed 0.0–1.0 (default: 0.7)",
    )
    p_leave.add_argument(
        "--distance",
        type=float,
        default=None,
        metavar="METERS",
        help="Distance to back away in meters (default: 0.2)",
    )
    p_leave.set_defaults(func=cmd_leave)

    args = ap.parse_args()
    try:
        args.func(args)
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(2)


if __name__ == "__main__":
    main()

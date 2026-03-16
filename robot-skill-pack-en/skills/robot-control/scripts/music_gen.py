#!/usr/bin/env python3
"""🎵 Robot Music Generator — pure Python + numpy, no external deps beyond numpy.

Generates WAV files from a simple music description JSON, then plays them on the robot
via audio.play (URL served locally).

Music JSON format:
{
  "name": "欢乐颂",
  "bpm": 120,
  "instrument": "sine",   // sine | square | sawtooth | triangle
  "volume": 0.5,           // 0.0 - 1.0
  "tracks": [
    {
      "notes": ["E4","E4","F4","G4","G4","F4","E4","D4","C4","C4","D4","E4","E4","D4","D4"],
      "durations": [1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1.5, 0.5, 2],
      "octave_shift": 0
    }
  ]
}

Note names: C D E F G A B (with optional # for sharp, b for flat), followed by octave 2-7
Special: "R" or "rest" = silence

Instrument waveforms:
  sine      - smooth, clean (default)
  square    - buzzy, retro game
  sawtooth  - bright, synth-like
  triangle  - mellow, flute-like

Config (in priority order):
  1. CLI flags: --gateway, --token, --device
  2. Environment variables: ROBOT_GATEWAY_URL, ROBOT_TOKEN, ROBOT_DEVICE_ID

Usage:
  python3 music_gen.py music_happy.json            # generate WAV only
  python3 music_gen.py music_happy.json --serve    # serve via HTTP + play on robot
  python3 music_gen.py --builtin ode_to_joy        # use built-in song

Built-in songs: ode_to_joy | twinkle | happy_birthday | fur_elise | dance_beat
"""

import argparse
import json
import math
import os
import struct
import sys
import time
import urllib.request
import urllib.parse
import wave
import threading
import http.server
import socketserver
from pathlib import Path

try:
    import numpy as np
    HAS_NUMPY = True
except ImportError:
    HAS_NUMPY = False

# ── Note → Frequency ─────────────────────────────────────────────────────────

NOTE_SEMITONES = {
    'C': 0, 'C#': 1, 'Db': 1, 'D': 2, 'D#': 3, 'Eb': 3,
    'E': 4, 'F': 5, 'F#': 6, 'Gb': 6, 'G': 7, 'G#': 8,
    'Ab': 8, 'A': 9, 'A#': 10, 'Bb': 10, 'B': 11,
}

def note_to_freq(note: str) -> float:
    """Convert note name (e.g. 'A4', 'C#5') to frequency in Hz."""
    if note.lower() in ('r', 'rest', '-'):
        return 0.0
    if len(note) >= 3 and note[1] in ('#', 'b'):
        name, octave = note[:3], int(note[3:])
    else:
        name, octave = note[:-1], int(note[-1])
    semitone = NOTE_SEMITONES.get(name.upper(), 0)
    midi = (octave + 1) * 12 + semitone
    return 440.0 * (2 ** ((midi - 69) / 12))


# ── Waveform generators ───────────────────────────────────────────────────────

def gen_wave(freq: float, duration_s: float, sample_rate: int,
             instrument: str = 'sine', volume: float = 0.5) -> 'np.ndarray':
    n = int(sample_rate * duration_s)
    t = np.linspace(0, duration_s, n, endpoint=False)

    if freq == 0:  # rest
        return np.zeros(n, dtype=np.float32)

    phase = 2 * np.pi * freq * t

    if instrument == 'sine':
        wave_data = np.sin(phase)
    elif instrument == 'square':
        wave_data = np.sign(np.sin(phase))
    elif instrument == 'sawtooth':
        wave_data = 2 * (t * freq - np.floor(0.5 + t * freq))
    elif instrument == 'triangle':
        wave_data = 2 * np.abs(2 * (t * freq - np.floor(t * freq + 0.5))) - 1
    else:
        wave_data = np.sin(phase)

    # Envelope: short attack + decay to avoid clicks
    attack = min(int(sample_rate * 0.01), n // 4)
    release = min(int(sample_rate * 0.02), n // 4)
    env = np.ones(n, dtype=np.float32)
    if attack > 0:
        env[:attack] = np.linspace(0, 1, attack)
    if release > 0:
        env[-release:] = np.linspace(1, 0, release)

    return (wave_data * env * volume).astype(np.float32)


# ── WAV writer ────────────────────────────────────────────────────────────────

def write_wav(samples: 'np.ndarray', path: str, sample_rate: int = 44100):
    pcm = (samples * 32767).clip(-32768, 32767).astype(np.int16)
    with wave.open(path, 'w') as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(sample_rate)
        wf.writeframes(pcm.tobytes())


# ── Music renderer ────────────────────────────────────────────────────────────

def render_music(spec: dict, out_path: str, sample_rate: int = 44100) -> float:
    """Render music spec to WAV file. Returns duration in seconds."""
    bpm        = spec.get('bpm', 120)
    instrument = spec.get('instrument', 'sine')
    volume     = spec.get('volume', 0.5)
    tracks     = spec.get('tracks', [])
    beat_s     = 60.0 / bpm

    all_samples = []

    for track in tracks:
        notes     = track.get('notes', [])
        durations = track.get('durations', [1] * len(notes))
        oct_shift = track.get('octave_shift', 0)
        track_vol = track.get('volume', volume)

        parts = []
        for note, dur in zip(notes, durations):
            freq = note_to_freq(note)
            if oct_shift and freq > 0:
                freq *= (2 ** oct_shift)
            seg = gen_wave(freq, dur * beat_s, sample_rate, instrument, track_vol)
            parts.append(seg)

        if parts:
            all_samples.append(np.concatenate(parts))

    if not all_samples:
        raise ValueError("No tracks / notes to render")

    max_len = max(len(s) for s in all_samples)
    mixed = np.zeros(max_len, dtype=np.float32)
    for s in all_samples:
        mixed[:len(s)] += s

    peak = np.abs(mixed).max()
    if peak > 1.0:
        mixed /= peak

    write_wav(mixed, out_path, sample_rate)
    return max_len / sample_rate


# ── Built-in songs ────────────────────────────────────────────────────────────

BUILTIN_SONGS = {
    "ode_to_joy": {
        "name": "欢乐颂",
        "bpm": 100,
        "instrument": "sine",
        "volume": 0.6,
        "tracks": [{
            "notes":     ["E4","E4","F4","G4","G4","F4","E4","D4","C4","C4","D4","E4","E4","D4","D4",
                          "E4","E4","F4","G4","G4","F4","E4","D4","C4","C4","D4","E4","D4","C4","C4"],
            "durations": [1,   1,   1,   1,   1,   1,   1,   1,   1,   1,   1,   1,   1.5, 0.5, 2,
                          1,   1,   1,   1,   1,   1,   1,   1,   1,   1,   1,   1,   1.5, 0.5, 2]
        }]
    },
    "twinkle": {
        "name": "小星星",
        "bpm": 100,
        "instrument": "triangle",
        "volume": 0.6,
        "tracks": [{
            "notes":     ["C4","C4","G4","G4","A4","A4","G4","R",
                          "F4","F4","E4","E4","D4","D4","C4","R",
                          "G4","G4","F4","F4","E4","E4","D4","R",
                          "G4","G4","F4","F4","E4","E4","D4","R",
                          "C4","C4","G4","G4","A4","A4","G4","R",
                          "F4","F4","E4","E4","D4","D4","C4"],
            "durations": [1,   1,   1,   1,   1,   1,   2,   0.5,
                          1,   1,   1,   1,   1,   1,   2,   0.5,
                          1,   1,   1,   1,   1,   1,   2,   0.5,
                          1,   1,   1,   1,   1,   1,   2,   0.5,
                          1,   1,   1,   1,   1,   1,   2,   0.5,
                          1,   1,   1,   1,   1,   1,   2]
        }]
    },
    "happy_birthday": {
        "name": "生日快乐",
        "bpm": 100,
        "instrument": "sine",
        "volume": 0.65,
        "tracks": [{
            "notes":     ["G4","G4","A4","G4","C5","B4","R",
                          "G4","G4","A4","G4","D5","C5","R",
                          "G4","G4","G5","E5","C5","B4","A4","R",
                          "F5","F5","E5","C5","D5","C5"],
            "durations": [0.75,0.25,1,   1,   1,   2,   0.5,
                          0.75,0.25,1,   1,   1,   2,   0.5,
                          0.75,0.25,1,   1,   1,   1,   2,   0.5,
                          0.75,0.25,1,   1,   1,   2]
        }]
    },
    "fur_elise": {
        "name": "致爱丽丝",
        "bpm": 130,
        "instrument": "sine",
        "volume": 0.55,
        "tracks": [{
            "notes":     ["E5","Eb5","E5","Eb5","E5","B4","D5","C5","A4","R",
                          "C4","E4","A4","B4","R","E4","Ab4","B4","C5","R",
                          "E4","E5","Eb5","E5","Eb5","E5","B4","D5","C5","A4","R",
                          "C4","E4","A4","B4","R","E4","C5","B4","A4"],
            "durations": [0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 1,   0.5,
                          0.5, 0.5, 0.5, 1,   0.5, 0.5, 0.5, 0.5, 1,   0.5,
                          0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 1,   0.5,
                          0.5, 0.5, 0.5, 1,   0.5, 0.5, 0.5, 0.5, 2]
        }]
    },
    "dance_beat": {
        "name": "舞蹈节拍",
        "bpm": 140,
        "instrument": "square",
        "volume": 0.5,
        "tracks": [
            {
                "notes":     ["C5","R","C5","R","G5","R","G5","R","A5","R","A5","R","G5","R","R","R",
                              "F5","R","F5","R","E5","R","E5","R","D5","R","D5","R","C5","R","R","R"],
                "durations": [0.5, 0.5,0.5, 0.5,0.5, 0.5,0.5, 0.5,0.5, 0.5,0.5, 0.5,1,   0.5,0.5, 0.5,
                              0.5, 0.5,0.5, 0.5,0.5, 0.5,0.5, 0.5,0.5, 0.5,0.5, 0.5,1,   0.5,0.5, 0.5]
            },
            {
                "notes":     ["C3","R","G3","R","C3","R","G3","R","C3","R","G3","R","C3","R","R","R",
                              "F3","R","C4","R","F3","R","C4","R","G3","R","D4","R","C3","R","R","R"],
                "durations": [0.5, 0.5,0.5, 0.5,0.5, 0.5,0.5, 0.5,0.5, 0.5,0.5, 0.5,1,   0.5,0.5, 0.5,
                              0.5, 0.5,0.5, 0.5,0.5, 0.5,0.5, 0.5,0.5, 0.5,0.5, 0.5,1,   0.5,0.5, 0.5],
                "volume": 0.35
            }
        ]
    }
}


# ── Robot audio.play via HTTP serve ──────────────────────────────────────────

def serve_and_play(wav_path: str, gateway_url: str, token: str, device_id: str,
                   host_ip: str = "", port: int = 18900):
    """Serve WAV over HTTP and tell robot to play it."""
    directory = str(Path(wav_path).parent)
    filename  = Path(wav_path).name

    class Handler(http.server.SimpleHTTPRequestHandler):
        def __init__(self, *args, **kwargs):
            super().__init__(*args, directory=directory, **kwargs)
        def log_message(self, fmt, *args): pass

    if not host_ip:
        import socket
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        try:
            s.connect(("8.8.8.8", 80))
            host_ip = s.getsockname()[0]
        finally:
            s.close()

    with socketserver.TCPServer(("", port), Handler) as httpd:
        url = f"http://{host_ip}:{port}/{filename}"
        print(f"  📡 Serving: {url}")

        t = threading.Thread(target=httpd.serve_forever, daemon=True)
        t.start()

        body = {"deviceId": device_id, "cmd": "audio.play",
                "args": {"url": url}, "timeoutMs": 15000}
        api_url = f"{gateway_url.rstrip('/')}/robot/cmd?token={urllib.parse.quote(token)}"
        data = json.dumps(body, ensure_ascii=False).encode()
        req = urllib.request.Request(api_url, data=data,
                                     headers={"content-type": "application/json"}, method="POST")
        resp = json.loads(urllib.request.urlopen(req, timeout=20).read())
        print(f"  🤖 audio.play response: {resp}")

        dur = resp.get('result', {}).get('data', {}).get('durationMs', 30000) / 1000.0
        print(f"  ⏳ Playing for ~{dur:.1f}s ...")
        time.sleep(dur + 2)
        httpd.shutdown()


# ── CLI ───────────────────────────────────────────────────────────────────────

def main():
    ap = argparse.ArgumentParser(
        description="🎵 Robot music generator",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__
    )
    grp = ap.add_mutually_exclusive_group(required=True)
    grp.add_argument("music_file", nargs="?", help="Music spec JSON file")
    grp.add_argument("--builtin", choices=list(BUILTIN_SONGS.keys()),
                     help="Use a built-in song")
    ap.add_argument("--out-dir", default="./music",
                    help="Output directory for WAV files (default: ./music)")
    ap.add_argument("--serve",   action="store_true", help="Serve via HTTP and play on robot")
    ap.add_argument("--gateway", default=os.environ.get("ROBOT_GATEWAY_URL", ""),
                    help="Gateway URL (env: ROBOT_GATEWAY_URL)")
    ap.add_argument("--token",   default=os.environ.get("ROBOT_TOKEN", ""),
                    help="Auth token (env: ROBOT_TOKEN)")
    ap.add_argument("--device",  default=os.environ.get("ROBOT_DEVICE_ID", ""),
                    help="Device ID (env: ROBOT_DEVICE_ID)")
    ap.add_argument("--host-ip", default="", help="Host IP for HTTP serve (auto-detect if blank)")
    ap.add_argument("--port",    type=int, default=18900)
    args = ap.parse_args()

    if not HAS_NUMPY:
        print("Error: numpy is required. Install with: pip install numpy", file=sys.stderr)
        sys.exit(1)

    if args.serve:
        missing = []
        if not args.gateway: missing.append("--gateway / ROBOT_GATEWAY_URL")
        if not args.token:   missing.append("--token / ROBOT_TOKEN")
        if not args.device:  missing.append("--device / ROBOT_DEVICE_ID")
        if missing:
            print(f"Error: missing config for --serve: {', '.join(missing)}", file=sys.stderr)
            sys.exit(1)

    if args.builtin:
        spec = BUILTIN_SONGS[args.builtin]
    else:
        with open(args.music_file, encoding="utf-8") as f:
            spec = json.load(f)

    os.makedirs(args.out_dir, exist_ok=True)
    name    = spec.get("name", "music").replace(" ", "_")
    ts      = int(time.time())
    out     = os.path.join(args.out_dir, f"{name}_{ts}.wav")

    print(f"\n🎵 Generating: {spec.get('name', name)} ...")
    duration = render_music(spec, out)
    print(f"   ✅ WAV saved: {out}  ({duration:.1f}s)")

    if args.serve:
        serve_and_play(out, args.gateway, args.token, args.device,
                       host_ip=args.host_ip, port=args.port)
    else:
        print(f"\n  To play on robot:")
        print(f"    python3 music_gen.py {args.music_file or '--builtin ' + (args.builtin or '')} --serve")


if __name__ == "__main__":
    main()

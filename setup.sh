#!/usr/bin/env bash
# setup.sh — OrionClaw 一键部署脚本（幂等，安全跳过已有配置）
set -euo pipefail

REPO_DIR="$(cd "$(dirname "$0")" && pwd)"
OPENCLAW_JSON="${HOME}/.openclaw/openclaw.json"
WORKSPACE="${HOME}/.openclaw/workspace"
SKILLS_DIR="${WORKSPACE}/skills"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
ok()   { echo -e "${GREEN}✅ $*${NC}"; }
skip() { echo -e "${YELLOW}⏭️  跳过：$*${NC}"; }
warn() { echo -e "${RED}⚠️  $*${NC}"; }
info() { echo -e "   $*"; }

echo ""
echo "=============================="
echo " OrionClaw Setup"
echo "=============================="
echo ""

# ── 依赖检查 ──────────────────────────────────────────────────────────────────
echo "【0】检查依赖"

for cmd in node npm python3 adb; do
  if command -v "$cmd" &>/dev/null; then
    ok "$cmd 已安装 ($(command -v $cmd))"
  else
    warn "$cmd 未找到 — 部分功能可能受限"
  fi
done
echo ""

# ── Step 1: robot-ws-ingress ──────────────────────────────────────────────────
echo "【1】robot-ws-ingress"

INGRESS_SRC="${REPO_DIR}/robot-ws-ingress"

# npm install
if [ -d "${INGRESS_SRC}/node_modules" ]; then
  skip "node_modules 已存在，跳过 npm install"
else
  info "运行 npm install..."
  (cd "$INGRESS_SRC" && npm install --silent)
  ok "npm install 完成"
fi

# openclaw.json — 检查 plugin entry 是否已存在
if [ ! -f "$OPENCLAW_JSON" ]; then
  warn "未找到 ${OPENCLAW_JSON}，跳过插件配置（请手动配置）"
else
  EXISTING_PATH=$(python3 -c "
import json, sys
try:
  d = json.load(open('${OPENCLAW_JSON}'))
  paths = d.get('plugins',{}).get('load',{}).get('paths',[])
  entry = d.get('plugins',{}).get('entries',{}).get('robot-ws-ingress',{})
  print(paths[0] if paths else '')
except: pass
" 2>/dev/null || true)

  if echo "$EXISTING_PATH" | grep -q "robot-ws-ingress"; then
    skip "robot-ws-ingress 插件已在 openclaw.json 中配置"
    info "当前路径：$EXISTING_PATH"
    info "如需切换到本仓库路径，请手动编辑 ${OPENCLAW_JSON}"
  else
    # 追加插件配置
    python3 - <<PYEOF
import json, sys

path = '${OPENCLAW_JSON}'
ingress_path = '${INGRESS_SRC}'

with open(path) as f:
    d = json.load(f)

plugins = d.setdefault('plugins', {})
load = plugins.setdefault('load', {})
paths = load.setdefault('paths', [])
if ingress_path not in paths:
    paths.append(ingress_path)

entries = plugins.setdefault('entries', {})
if 'robot-ws-ingress' not in entries:
    import secrets
    token = secrets.token_hex(16)
    entries['robot-ws-ingress'] = {
        'enabled': True,
        'config': {
            'port': 18795,
            'path': '/robot/ws',
            'token': token,
            'allowDeviceIds': [],
            'gatewayPort': 18789
        }
    }
    print(f'TOKEN:{token}')

with open(path, 'w') as f:
    json.dump(d, f, indent=2, ensure_ascii=False)
PYEOF
    ok "robot-ws-ingress 插件配置已写入 ${OPENCLAW_JSON}"
    warn "请重启 OpenClaw 使插件生效"
  fi
fi
echo ""

# ── Step 2: Skills ────────────────────────────────────────────────────────────
echo "【2】Skills"

for skill in robot-control robot-march; do
  SRC="${REPO_DIR}/skills/${skill}"
  DST="${SKILLS_DIR}/${skill}"
  if [ -d "$DST" ]; then
    skip "${skill} 已存在于 ${DST}"
    info "如需更新请手动 cp -r ${SRC} ${SKILLS_DIR}/"
  else
    mkdir -p "$SKILLS_DIR"
    cp -r "$SRC" "$DST"
    ok "${skill} 已复制到 ${DST}"
  fi
done
echo ""

# ── Step 3: Workspace 追加 ────────────────────────────────────────────────────
echo "【3】Workspace 配置"

AGENTS_MD="${WORKSPACE}/AGENTS.md"
TOOLS_MD="${WORKSPACE}/TOOLS.md"

# AGENTS.md
if grep -q "robot-ws-ingress\|robot.getPlaceList\|机器人任务进度" "$AGENTS_MD" 2>/dev/null; then
  skip "AGENTS.md 已包含机器人配置，跳过追加"
else
  cat "${REPO_DIR}/workspace-template/robot-agents-addon.md" >> "$AGENTS_MD"
  ok "robot-agents-addon.md 已追加到 AGENTS.md"
fi

# TOOLS.md
if grep -q "ROBOT_GATEWAY_URL\|robot-ws-ingress\|GATEWAY_IP" "$TOOLS_MD" 2>/dev/null; then
  skip "TOOLS.md 已包含机器人配置，跳过追加"
else
  cat "${REPO_DIR}/workspace-template/robot-tools-addon.md" >> "$TOOLS_MD"
  ok "robot-tools-addon.md 已追加到 TOOLS.md"
  warn "请打开 ${TOOLS_MD}，将 <GATEWAY_IP>/<YOUR_TOKEN>/<YOUR_DEVICE_ID> 替换为实际值"
fi
echo ""

# ── 验证 ──────────────────────────────────────────────────────────────────────
echo "【4】验证网关"
if curl -s --max-time 3 "http://localhost:18795/robot/health" | grep -q '"ok":true'; then
  ok "robot-ws-ingress 网关运行正常"
else
  warn "网关未响应（可能需要先重启 OpenClaw）"
  info "验证命令：curl http://localhost:18795/robot/health"
fi
echo ""

echo "=============================="
echo " 部署完成！"
echo "=============================="
echo ""
echo "下一步："
echo "  1. 重启 OpenClaw"
echo "  2. 确认网关：curl http://localhost:18795/robot/health"
echo "  3. 连接机器人 ADB，安装 OrionClaw APK"
echo "  4. 向 AI 发送「查询一下机器人有哪些导航点位」测试"
echo ""

#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TOOL_DIR="$SCRIPT_DIR/../tools/route-builder"

CONFIG="${1:-pilot.config.json}"
REPO_ROOT="${2:-.}"

if [ ! -f "$CONFIG" ]; then
    echo "Error: Config file not found: $CONFIG"
    echo "Create a pilot.config.json in your project root."
    echo "See $TOOL_DIR/pilot.config.example.json for a template."
    exit 1
fi

CONFIG_ABS="$(cd "$(dirname "$CONFIG")" && pwd)/$(basename "$CONFIG")"
REPO_ROOT_ABS="$(cd "$REPO_ROOT" && pwd)"

echo "Starting Pilot Route Builder..."
echo "  Config: $CONFIG_ABS"
echo "  Repo:   $REPO_ROOT_ABS"

python3 "$TOOL_DIR/server.py" \
    --port 8080 \
    --config "$CONFIG_ABS" \
    --repo-root "$REPO_ROOT_ABS" &

SERVER_PID=$!
sleep 1

# Open browser
if command -v open &>/dev/null; then
    open "http://localhost:8080"
elif command -v xdg-open &>/dev/null; then
    xdg-open "http://localhost:8080"
fi

echo "Server running at http://localhost:8080 (PID: $SERVER_PID)"
echo "Press Ctrl+C to stop."

wait $SERVER_PID

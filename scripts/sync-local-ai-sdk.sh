#!/usr/bin/env bash
# Copies rmant7/AI's local-ai-sdk sources into this repository's :local-ai-sdk
# module and records the commit they came from. Usage:
#   scripts/sync-local-ai-sdk.sh /path/to/rmant7-AI-checkout
set -euo pipefail
src="${1:?path to a rmant7/AI checkout}"
here="$(cd "$(dirname "$0")/.." && pwd)"
rm -rf "$here/local-ai-sdk/src"
cp -r "$src/local-ai-sdk/src" "$here/local-ai-sdk/src"
cp "$src/local-ai-sdk/README.md" "$here/local-ai-sdk/README.md"
echo "rmant7/AI local-ai-sdk/src at $(git -C "$src" rev-parse --short HEAD) ($(git -C "$src" rev-parse --abbrev-ref HEAD))" > "$here/local-ai-sdk/SOURCE"
echo "synced: $(cat "$here/local-ai-sdk/SOURCE")"

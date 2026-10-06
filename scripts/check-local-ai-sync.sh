#!/usr/bin/env bash
# Proves the vendored local-AI sources are exactly rmant7/AI at the commit
# local-ai-sdk/SOURCE names -- no hand edits, no half-done sync. CI runs it;
# rmant7/AI is public, so it needs no credentials.
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
commit="$(head -1 "$here/local-ai-sdk/SOURCE")"
[[ "$commit" =~ ^[0-9a-f]{40}$ ]] || { echo "local-ai-sdk/SOURCE: first line is not a full commit: $commit"; exit 1; }
work="$(mktemp -d)"
git -C "$work" init -q
git -C "$work" fetch -q --depth 1 https://github.com/rmant7/AI.git "$commit"
git -C "$work" checkout -q FETCH_HEAD
status=0
for pair in "local-ai-sdk/src:local-ai-sdk/src" "core/src/main:local-ai-core/src/main" "llama-runtime/src/main:llama-runtime/src/main"; do
  upstream="${pair%%:*}"; vendored="${pair##*:}"
  if diff -r "$work/$upstream" "$here/$vendored" > /dev/null; then
    echo "ok: $vendored == rmant7/AI@${commit:0:12}:$upstream"
  else
    echo "::error::$vendored differs from rmant7/AI@${commit:0:12}:$upstream -- re-run scripts/sync-local-ai-sdk.sh, never edit it by hand"
    diff -r "$work/$upstream" "$here/$vendored" | head -40 || true
    status=1
  fi
done
exit $status

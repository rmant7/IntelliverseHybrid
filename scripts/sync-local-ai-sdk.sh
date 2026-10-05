#!/usr/bin/env bash
# Copies rmant7/AI's on-device AI modules into this repository and records the
# commit they came from (local-ai-sdk/SOURCE):
#   local-ai-sdk/   <- AI local-ai-sdk/src   (the LocalAi contract)
#   local-ai-core/  <- AI core/src/main      (RuntimeManager, admission, weights load policy, ...)
#   llama-runtime/  <- AI llama-runtime/src/main (llama.cpp JNI + native build, LlamaCppRuntime,
#                                              RAM measuring, LocalModelEngine)
# Only sources are copied; each module's build.gradle.kts here is this
# repository's own (its AGP/Kotlin/library versions, Java 8 bytecode).
# Usage: scripts/sync-local-ai-sdk.sh /path/to/rmant7-AI-checkout
set -euo pipefail
src="${1:?path to a rmant7/AI checkout}"
here="$(cd "$(dirname "$0")/.." && pwd)"

rm -rf "$here/local-ai-sdk/src"
cp -r "$src/local-ai-sdk/src" "$here/local-ai-sdk/src"
cp "$src/local-ai-sdk/README.md" "$here/local-ai-sdk/README.md"

rm -rf "$here/local-ai-core/src"
mkdir -p "$here/local-ai-core/src"
cp -r "$src/core/src/main" "$here/local-ai-core/src/main"

rm -rf "$here/llama-runtime/src"
mkdir -p "$here/llama-runtime/src"
cp -r "$src/llama-runtime/src/main" "$here/llama-runtime/src/main"

echo "rmant7/AI at $(git -C "$src" rev-parse --short HEAD) ($(git -C "$src" rev-parse --abbrev-ref HEAD)): local-ai-sdk/src, core/src/main -> local-ai-core, llama-runtime/src/main" > "$here/local-ai-sdk/SOURCE"
echo "synced: $(cat "$here/local-ai-sdk/SOURCE")"

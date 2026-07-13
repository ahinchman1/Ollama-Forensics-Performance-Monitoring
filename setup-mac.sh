#!/usr/bin/env bash
set -euo pipefail

if ! command -v brew >/dev/null 2>&1; then
  echo "Homebrew is not installed. Install it from https://brew.sh and re-run this script."
  exit 1
fi

echo "Installing ollama, tmux, and btop via Homebrew..."
brew install ollama tmux btop

echo "Pulling default Ollama model (llama3.2:1b)..."
ollama pull llama3.2:1b

echo "Verifying Ollama API..."
if curl -s --max-time 10 http://localhost:11434/api/generate \
  -d '{"model":"llama3.2:1b","prompt":"ping","stream":false}' >/dev/null; then
  echo "Ollama is responding."
else
  echo "Ollama did not respond. Make sure 'ollama serve' is running before using the app."
fi

echo "macOS setup complete."

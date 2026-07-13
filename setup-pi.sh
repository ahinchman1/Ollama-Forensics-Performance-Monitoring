#!/usr/bin/env bash
set -euo pipefail

if [[ "$(id -u)" -ne 0 ]]; then
  echo "Please run this script with sudo: sudo bash setup-pi.sh"
  exit 1
fi

echo "Updating package index..."
apt-get update

echo "Installing tmux and btop..."
apt-get install -y tmux btop

echo "Installing Ollama..."
if ! command -v ollama >/dev/null 2>&1; then
  curl -fsSL https://ollama.com/install.sh | sh
else
  echo "Ollama is already installed."
fi

echo "Pulling default Ollama model (llama3.2:1b)..."
ollama pull llama3.2:1b

echo "Verifying Ollama API..."
if curl -s --max-time 10 http://localhost:11434/api/generate \
  -d '{"model":"llama3.2:1b","prompt":"ping","stream":false}' >/dev/null; then
  echo "Ollama is responding."
else
  echo "Ollama did not respond. Start it with 'ollama serve' before using the app."
fi

echo "Raspberry Pi setup complete."

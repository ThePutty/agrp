#!/bin/sh
# Start Ollama and make sure the embedding model is present.
set -e
ollama serve &
PID=$!
until ollama list >/dev/null 2>&1; do sleep 1; done
if ! ollama list | grep -q "bge-m3"; then
  echo "Pulling bge-m3 embedding model (one-time, ~1.2 GB)..."
  ollama pull bge-m3
fi
echo "Ollama ready with bge-m3"
wait $PID

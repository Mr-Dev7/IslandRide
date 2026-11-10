#!/usr/bin/env bash
# ==============================================================================
# IslandRide — macOS/Linux Local Setup Script
# ==============================================================================

set -euo pipefail

echo ">>> Initializing IslandRide Local Development Environment..."

# 1. Check for .env file
if [ ! -f ".env" ]; then
    echo "Creating .env from .env.example..."
    cp .env.example .env
    echo ".env created. Please inspect and update your local secrets."
else
    echo ".env already exists."
fi

# 2. Check Docker
if command -v docker >/dev/null 2>&1; then
    echo "Starting MySQL 8.4 and Redis 7.4 via Docker Compose..."
    docker compose up -d
    echo "Docker services started."
else
    echo "Docker is not in PATH. Ensure Docker daemon is running."
fi

echo ">>> Local environment setup complete!"

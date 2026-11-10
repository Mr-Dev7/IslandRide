# ==============================================================================
# IslandRide — Windows PowerShell Local Setup Script
# ==============================================================================

Write-Host ">>> Initializing IslandRide Local Development Environment..." -ForegroundColor Cyan

# 1. Check for .env file
if (-not (Test-Path ".env")) {
    Write-Host "Creating .env from .env.example..." -ForegroundColor Yellow
    Copy-Item ".env.example" ".env"
    Write-Host ".env created. Please inspect and update your local secrets." -ForegroundColor Green
} else {
    Write-Host ".env already exists." -ForegroundColor Gray
}

# 2. Check Docker
if (Get-Command docker -ErrorAction SilentlyContinue) {
    Write-Host "Starting MySQL 8.4 and Redis 7.4 via Docker Compose..." -ForegroundColor Yellow
    docker compose up -d
    Write-Host "Docker services started." -ForegroundColor Green
} else {
    Write-Host "Docker is not in PATH. Ensure Docker Desktop is running before starting the backend." -ForegroundColor Yellow
}

Write-Host ">>> Local environment setup complete!" -ForegroundColor Cyan


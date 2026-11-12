<#
.SYNOPSIS
    IslandRide — Automated Local One-Command Startup Script
.DESCRIPTION
    Verifies development prerequisites (Docker, Java 25, Node.js, npm), starts Docker Compose
    infrastructure (MySQL 8.4 and Redis 7.4), performs health checks, detects existing processes
    to prevent duplicate instances, launches the Spring Boot backend and Angular frontend in
    dedicated interactive terminal windows, and displays a comprehensive service status summary.
.EXAMPLE
    .\start-islandride.ps1
#>

[CmdletBinding()]
param()

$RepoRoot = $PSScriptRoot
$BackendDir = Join-Path $RepoRoot "backend"
$FrontendDir = Join-Path $RepoRoot "frontend"

Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host "                IslandRide — Local Development Environment Startup              " -ForegroundColor Cyan
Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host ""

# -----------------------------------------------------------------------------
# 1. PREREQUISITE CHECKS
# -----------------------------------------------------------------------------
Write-Host "[1/5] Verifying Development Prerequisites..." -ForegroundColor Yellow

# Docker CLI resolution (including Docker Desktop installation directory fallback)
$dockerCmd = Get-Command docker -ErrorAction SilentlyContinue
if (-not $dockerCmd) {
    $fallbackPaths = @(
        "$env:LOCALAPPDATA\Programs\DockerDesktop\resources\bin",
        "C:\Program Files\Docker\Docker\resources\bin"
    )
    foreach ($p in $fallbackPaths) {
        if (Test-Path (Join-Path $p "docker.exe")) {
            $env:Path = "$p;" + $env:Path
            break
        }
    }
}

$dockerCmd = Get-Command docker -ErrorAction SilentlyContinue
if (-not $dockerCmd) {
    Write-Host "  [ERROR] Docker CLI (docker) not found in PATH or standard installation directories. Please install or start Docker Desktop." -ForegroundColor Red
    exit 1
}

try {
    $dockerInfo = & docker info 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host "  [ERROR] Docker daemon is not running. Please start Docker Desktop and try again." -ForegroundColor Red
        exit 1
    }
    Write-Host "  [OK] Docker Daemon: Running" -ForegroundColor Green
} catch {
    Write-Host "  [ERROR] Failed to connect to Docker daemon: $_" -ForegroundColor Red
    exit 1
}

# Java 25 Check
$javaCmd = Get-Command java -ErrorAction SilentlyContinue
if (-not $javaCmd) {
    Write-Host "  [ERROR] Java runtime (java) not found in PATH. Java 25 LTS is required." -ForegroundColor Red
    exit 1
}
$javaVerOutput = (& java -version 2>&1) | Out-String
$javaMatch = [regex]::Match($javaVerOutput, 'version\s+"(\d+)')
if ($javaMatch.Success) {
    $majorVer = [int]$javaMatch.Groups[1].Value
    if ($majorVer -lt 22) {
        Write-Warning "Detected Java version $majorVer. IslandRide requires Java 22+ (Java 25 LTS recommended)."
    } else {
        Write-Host "  [OK] Java Runtime: Version $majorVer ($($javaCmd.Source))" -ForegroundColor Green
    }
} else {
    Write-Host "  [OK] Java Runtime: Detected" -ForegroundColor Green
}

# Node.js Check
$nodeCmd = Get-Command node -ErrorAction SilentlyContinue
if (-not $nodeCmd) {
    Write-Host "  [ERROR] Node.js (node) not found in PATH. Node.js 24 LTS (or 20+) is required." -ForegroundColor Red
    exit 1
}
$nodeVer = (& node -v).Trim()
Write-Host "  [OK] Node.js Runtime: $nodeVer" -ForegroundColor Green

# npm Check
$npmCmd = Get-Command npm -ErrorAction SilentlyContinue
if (-not $npmCmd) {
    Write-Host "  [ERROR] npm package manager (npm) not found in PATH." -ForegroundColor Red
    exit 1
}
$npmVer = (& npm -v).Trim()
Write-Host "  [OK] npm Package Manager: v$npmVer" -ForegroundColor Green
Write-Host ""

# -----------------------------------------------------------------------------
# 2. INFRASTRUCTURE STARTUP (Docker Compose: MySQL 8.4 & Redis 7.4)
# -----------------------------------------------------------------------------
Write-Host "[2/5] Starting Infrastructure Containers (MySQL 8.4 on :3307 & Redis 7.4 on :6379)..." -ForegroundColor Yellow

# Detect compose command syntax (docker compose vs docker-compose)
$useLegacy = $false
try {
    $testCompose = & docker compose version 2>&1
    if ($LASTEXITCODE -ne 0) {
        $useLegacy = $true
    }
} catch {
    $useLegacy = $true
}

if ($useLegacy) {
    & docker-compose up -d mysql redis
} else {
    & docker compose up -d mysql redis
}

if ($LASTEXITCODE -ne 0) {
    Write-Host "  [ERROR] Failed to start infrastructure containers with Docker Compose." -ForegroundColor Red
    exit 1
}

# Helper: Test port listening
function Test-PortListening {
    param([int]$Port)
    $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    return ($null -ne $conn)
}

# Wait for MySQL (Port 3307)
Write-Host "  Waiting for MySQL on port 3307 to accept connections..." -NoNewline
$mysqlTimeoutSeconds = 60
$mysqlElapsed = 0
$mysqlReady = $false

while ($mysqlElapsed -lt $mysqlTimeoutSeconds) {
    if (Test-PortListening 3307) {
        try {
            $health = (& docker inspect --format="{{.State.Health.Status}}" islandride-mysql 2>&1).Trim()
            if ($health -eq "healthy" -or $health -eq "") {
                $mysqlReady = $true
                break
            }
        } catch {
            $mysqlReady = $true
            break
        }
    }
    Start-Sleep -Seconds 2
    $mysqlElapsed += 2
    Write-Host "." -NoNewline
}

if (-not $mysqlReady) {
    Write-Host ""
    Write-Host "  [ERROR] MySQL did not become ready on port 3307 within $mysqlTimeoutSeconds seconds." -ForegroundColor Red
    exit 1
}
Write-Host " READY" -ForegroundColor Green

# Wait for Redis (Port 6379)
Write-Host "  Waiting for Redis on port 6379 to accept connections..." -NoNewline
$redisTimeoutSeconds = 30
$redisElapsed = 0
$redisReady = $false

while ($redisElapsed -lt $redisTimeoutSeconds) {
    if (Test-PortListening 6379) {
        $redisReady = $true
        break
    }
    Start-Sleep -Seconds 1
    $redisElapsed += 1
    Write-Host "." -NoNewline
}

if (-not $redisReady) {
    Write-Host ""
    Write-Host "  [ERROR] Redis did not become ready on port 6379 within $redisTimeoutSeconds seconds." -ForegroundColor Red
    exit 1
}
Write-Host " READY" -ForegroundColor Green
Write-Host ""

# -----------------------------------------------------------------------------
# 3. HELPER FUNCTIONS FOR HTTP PROBING
# -----------------------------------------------------------------------------
function Test-HttpEndpoint {
    param([string]$Url)
    try {
        $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3 -ErrorAction Stop
        return ($response.StatusCode -ge 200 -and $response.StatusCode -lt 400)
    } catch {
        return $false
    }
}

# -----------------------------------------------------------------------------
# 4. BACKEND PROCESS DETECTION & LAUNCH
# -----------------------------------------------------------------------------
Write-Host "[3/5] Checking Backend Service (Port 8080)..." -ForegroundColor Yellow

$backendAlreadyRunning = $false
$backendHealthUrl = "http://localhost:8080/actuator/health"
$backendApiHealthUrl = "http://localhost:8080/api/v1/health"

if (Test-PortListening 8080) {
    if ((Test-HttpEndpoint $backendHealthUrl) -or (Test-HttpEndpoint $backendApiHealthUrl)) {
        Write-Host "  [ALREADY ACTIVE] Backend is already running and healthy on port 8080." -ForegroundColor Cyan
        $backendAlreadyRunning = $true
    } else {
        Write-Host "  [BUSY] Port 8080 is listening, checking responsiveness..." -ForegroundColor Yellow
        $backendAlreadyRunning = $true
    }
}

if (-not $backendAlreadyRunning) {
    Write-Host "  Launching Spring Boot backend in a new PowerShell window..." -ForegroundColor White
    $mvnwPath = Join-Path $BackendDir "mvnw.cmd"
    $backendCmd = "Set-Location '$BackendDir'; & '$mvnwPath' spring-boot:run"
    Start-Process powershell.exe -ArgumentList "-NoExit", "-Command", $backendCmd -WorkingDirectory $BackendDir

    Write-Host "  Waiting for backend health check ($backendHealthUrl)..." -NoNewline
    $backendTimeoutSeconds = 90
    $backendElapsed = 0
    $backendHealthy = $false

    while ($backendElapsed -lt $backendTimeoutSeconds) {
        if ((Test-HttpEndpoint $backendHealthUrl) -or (Test-HttpEndpoint $backendApiHealthUrl)) {
            $backendHealthy = $true
            break
        }
        Start-Sleep -Seconds 3
        $backendElapsed += 3
        Write-Host "." -NoNewline
    }

    if ($backendHealthy) {
        Write-Host " HEALTHY" -ForegroundColor Green
    } else {
        Write-Host ""
        Write-Warning "Backend process launched but health check did not report healthy within $backendTimeoutSeconds seconds. Check the backend terminal window for logs."
    }
}
Write-Host ""

# -----------------------------------------------------------------------------
# 5. FRONTEND PROCESS DETECTION & LAUNCH
# -----------------------------------------------------------------------------
Write-Host "[4/5] Checking Frontend Service (Port 4200)..." -ForegroundColor Yellow

$frontendAlreadyRunning = $false
$frontendUrl = "http://localhost:4200"

if (Test-PortListening 4200) {
    if (Test-HttpEndpoint $frontendUrl) {
        Write-Host "  [ALREADY ACTIVE] Frontend is already running and accessible on port 4200." -ForegroundColor Cyan
        $frontendAlreadyRunning = $true
    } else {
        Write-Host "  [BUSY] Port 4200 is listening, skipping duplicate launch." -ForegroundColor Yellow
        $frontendAlreadyRunning = $true
    }
}

if (-not $frontendAlreadyRunning) {
    Write-Host "  Launching Angular frontend in a new PowerShell window..." -ForegroundColor White
    $frontendCmd = "Set-Location '$FrontendDir'; npm start"
    Start-Process powershell.exe -ArgumentList "-NoExit", "-Command", $frontendCmd -WorkingDirectory $FrontendDir

    Write-Host "  Waiting for frontend to respond at $frontendUrl..." -NoNewline
    $frontendTimeoutSeconds = 60
    $frontendElapsed = 0
    $frontendHealthy = $false

    while ($frontendElapsed -lt $frontendTimeoutSeconds) {
        if (Test-HttpEndpoint $frontendUrl) {
            $frontendHealthy = $true
            break
        }
        Start-Sleep -Seconds 3
        $frontendElapsed += 3
        Write-Host "." -NoNewline
    }

    if ($frontendHealthy) {
        Write-Host " READY" -ForegroundColor Green
    } else {
        Write-Host ""
        Write-Warning "Frontend dev server launched but did not respond within $frontendTimeoutSeconds seconds. Check the frontend terminal window for compilation output."
    }
}
Write-Host ""

# -----------------------------------------------------------------------------
# 6. STATUS SUMMARY BANNER
# -----------------------------------------------------------------------------
Write-Host "[5/5] Startup Complete! IslandRide Service Status Summary:" -ForegroundColor Yellow
Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host ("{0,-22} | {1,-14} | {2,-6} | {3}" -f "Component", "Status", "Port", "URL / Details") -ForegroundColor White
Write-Host "--------------------------------------------------------------------------------" -ForegroundColor DarkGray

# MySQL
$mysqlStatus = if (Test-PortListening 3307) { "Healthy" } else { "Offline" }
Write-Host ("{0,-22} | {1,-14} | {2,-6} | {3}" -f "MySQL 8.4 LTS", $mysqlStatus, "3307", "jdbc:mysql://localhost:3307/islandride_db") -ForegroundColor Green

# Redis
$redisStatus = if (Test-PortListening 6379) { "Healthy" } else { "Offline" }
Write-Host ("{0,-22} | {1,-14} | {2,-6} | {3}" -f "Redis 7.4", $redisStatus, "6379", "redis://localhost:6379") -ForegroundColor Green

# Backend Spring Boot
$backendStatus = if (Test-PortListening 8080) { "Running" } else { "Offline" }
Write-Host ("{0,-22} | {1,-14} | {2,-6} | {3}" -f "Spring Boot Backend", $backendStatus, "8080", "http://localhost:8080/api/v1") -ForegroundColor Green
Write-Host ("{0,-22} | {1,-14} | {2,-6} | {3}" -f "Swagger UI Docs", $backendStatus, "8080", "http://localhost:8080/swagger-ui.html") -ForegroundColor Cyan
Write-Host ("{0,-22} | {1,-14} | {2,-6} | {3}" -f "Actuator Health", $backendStatus, "8080", "http://localhost:8080/actuator/health") -ForegroundColor Cyan

# Frontend Angular
$frontendStatus = if (Test-PortListening 4200) { "Running" } else { "Offline" }
Write-Host ("{0,-22} | {1,-14} | {2,-6} | {3}" -f "Angular 22 Frontend", $frontendStatus, "4200", "http://localhost:4200") -ForegroundColor Green
Write-Host ("{0,-22} | {1,-14} | {2,-6} | {3}" -f "Tourist Portal", $frontendStatus, "4200", "http://localhost:4200/tourist") -ForegroundColor White
Write-Host ("{0,-22} | {1,-14} | {2,-6} | {3}" -f "Partner Console", $frontendStatus, "4200", "http://localhost:4200/partner") -ForegroundColor White
Write-Host ("{0,-22} | {1,-14} | {2,-6} | {3}" -f "Admin Console", $frontendStatus, "4200", "http://localhost:4200/admin") -ForegroundColor White

Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host "Tip: To stop the local environment, close the terminal windows and run:" -ForegroundColor Gray
Write-Host "     docker compose down" -ForegroundColor Gray
Write-Host ""

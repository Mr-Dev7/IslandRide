$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.5.8-hotspot"
$javaBin = "C:\Program Files\Eclipse Adoptium\jdk-25.0.5.8-hotspot\bin\java.exe"

Write-Output "[INFO] Starting Spring Boot application on local profile..."
$proc = Start-Process -FilePath $javaBin -ArgumentList "-jar", "target\islandride-backend-1.0.0-SNAPSHOT.jar", "--spring.profiles.active=local" -RedirectStandardOutput "backend.log" -RedirectStandardError "backend-err.log" -PassThru

Write-Output "[INFO] Process PID: $($proc.Id). Waiting for HTTP endpoint to become healthy..."

$maxRetries = 35
$healthy = $false
for ($i = 1; $i -le $maxRetries; $i++) {
    Start-Sleep -Seconds 1
    try {
        $response = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/health" -TimeoutSec 2 -ErrorAction Stop
        if ($response.status -eq "UP") {
            $healthy = $true
            Write-Output "[INFO] Health check succeeded at attempt $i!"
            break
        }
    } catch {
        Write-Output "[DEBUG] Attempt $i - waiting for server..."
    }
}

if (-not $healthy) {
    Write-Output "[ERROR] Spring Boot did not respond healthy within $maxRetries seconds."
    if (Test-Path "backend.log") { Get-Content "backend.log" -Tail 60 }
    if (Test-Path "backend-err.log") { Get-Content "backend-err.log" -Tail 60 }
    Stop-Process -Id $proc.Id -Force
    exit 1
}

Write-Output ""
Write-Output "=== 1. HEALTH ENDPOINT RESPONSE ==="
$healthJson = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/health"
$healthJson | ConvertTo-Json

Write-Output ""
Write-Output "=== 2. OPENAPI V3 API-DOCS ==="
$apiDocs = Invoke-RestMethod -Uri "http://localhost:8080/v3/api-docs"
Write-Output "OpenAPI Version: $($apiDocs.openapi)"
Write-Output "API Title: $($apiDocs.info.title)"
Write-Output "API Version: $($apiDocs.info.version)"
Write-Output "Paths: $($apiDocs.paths.PSObject.Properties.Name -join ', ')"

Write-Output ""
Write-Output "=== 3. SWAGGER UI ENDPOINT ==="
$swagger = Invoke-WebRequest -Uri "http://localhost:8080/swagger-ui/index.html" -UseBasicParsing
Write-Output "Swagger UI Status: $($swagger.StatusCode) $($swagger.StatusDescription)"

Write-Output ""
Write-Output "=== 4. APPLICATION STARTUP LOGS (EXCERPT) ==="
Get-Content "backend.log" | Select-String "HikariPool", "Flyway", "Started IslandRideApplication", "Tomcat"

Write-Output ""
Write-Output "[INFO] Verification complete. Application remains running with PID: $($proc.Id)"


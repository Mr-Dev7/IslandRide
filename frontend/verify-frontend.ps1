Write-Output "[INFO] Starting Angular development server on port 4200..."
$proc = Start-Process -FilePath "npx.cmd" -ArgumentList "ng", "serve", "--port", "4200" -WorkingDirectory (Get-Location).Path -RedirectStandardOutput "frontend.log" -RedirectStandardError "frontend-err.log" -PassThru

Write-Output "[INFO] Process PID: $($proc.Id). Waiting for Angular dev server to serve http://localhost:4200..."

$maxRetries = 40
$healthy = $false
for ($i = 1; $i -le $maxRetries; $i++) {
    Start-Sleep -Seconds 1
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:4200" -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
        if ($response.StatusCode -eq 200) {
            $healthy = $true
            Write-Output "[INFO] Angular dev server responded HTTP 200 at attempt $i!"
            break
        }
    } catch {
        Write-Output "[DEBUG] Attempt $i - waiting for dev server..."
    }
}

if (-not $healthy) {
    Write-Output "[ERROR] Angular dev server did not respond within $maxRetries seconds."
    if (Test-Path "frontend.log") { Get-Content "frontend.log" -Tail 60 }
    if (Test-Path "frontend-err.log") { Get-Content "frontend-err.log" -Tail 60 }
    Stop-Process -Id $proc.Id -Force
    exit 1
}

Write-Output ""
Write-Output "=== ANGULAR FRONTEND PROBE ==="
$web = Invoke-WebRequest -Uri "http://localhost:4200" -UseBasicParsing
Write-Output "Status Code: $($web.StatusCode) $($web.StatusDescription)"
Write-Output "Content Length: $($web.RawContentLength) bytes"
$hasTitle = $web.Content -match "<title>(.*?)</title>"
if ($hasTitle) {
    Write-Output "Page Title: $($Matches[1])"
}
$hasAppRoot = $web.Content -match "<app-root>"
Write-Output "Contains <app-root>: $hasAppRoot"

Write-Output ""
Write-Output "[INFO] Frontend verification complete. Server PID: $($proc.Id)"


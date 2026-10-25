# WineGuard - sobe a aplicacao no notebook (PostgreSQL + backend + frontend) e abre o dashboard.
# Uso: de dois cliques em iniciar.bat (na pasta wineguard) ou rode: powershell -ExecutionPolicy Bypass -File scripts\iniciar.ps1
$ErrorActionPreference = 'Continue'
Set-Location (Join-Path $PSScriptRoot '..')

function Test-Docker { docker info *> $null; return ($LASTEXITCODE -eq 0) }

# 1) Docker Desktop
if (-not (Test-Docker)) {
    Write-Host 'Iniciando o Docker Desktop...'
    $candidates = @("$env:LOCALAPPDATA\Programs\DockerDesktop\Docker Desktop.exe",
                    "$env:ProgramFiles\Docker\Docker\Docker Desktop.exe")
    $exe = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
    if (-not $exe) { Write-Host 'Docker Desktop nao encontrado. Abra-o manualmente.'; exit 1 }
    Start-Process $exe
    for ($i = 0; $i -lt 60 -and -not (Test-Docker); $i++) { Start-Sleep -Seconds 3 }
    if (-not (Test-Docker)) { Write-Host 'O Docker nao respondeu em 3 minutos.'; exit 1 }
}
Write-Host 'Docker pronto.'

# 2) Containers da aplicacao (o FIWARE roda na EC2)
docker compose up -d
if ($LASTEXITCODE -ne 0) { Write-Host 'Falha ao subir os containers (veja a mensagem acima).'; exit 1 }

# 3) Espera o backend
Write-Host 'Aguardando o backend...'
$ok = $false
for ($i = 0; $i -lt 60; $i++) {
    try { if ((Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 'http://localhost:8080/actuator/health').StatusCode -eq 200) { $ok = $true; break } } catch {}
    Start-Sleep -Seconds 2
}
if ($ok) { Write-Host 'Backend OK.' } else { Write-Host 'Backend ainda nao respondeu: veja "docker compose logs backend".' }

# 4) FIWARE (endereco configurado no .env)
$orion = (Get-Content .env | Where-Object { $_ -match '^FIWARE_ORION_URL=' }) -replace '^FIWARE_ORION_URL=', ''
if ($orion) {
    try {
        Invoke-WebRequest -UseBasicParsing -TimeoutSec 8 "$orion/version" | Out-Null
        Write-Host "FIWARE OK em $orion"
    } catch {
        Write-Host "FIWARE NAO respondeu em $orion. A EC2 esta ligada? O IP mudou? Use atualizar-ip.bat <novo IP>."
    }
}

# 5) Dashboard
Start-Process 'http://localhost:5173'
Write-Host 'Dashboard: http://localhost:5173   (API: http://localhost:8080/swagger-ui.html)'

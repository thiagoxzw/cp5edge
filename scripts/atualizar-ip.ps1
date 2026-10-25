# WineGuard - troca o IP da EC2 (FIWARE) em todos os lugares de uma vez.
# Uso: atualizar-ip.bat 13.222.9.65          (rede comum)
#      atualizar-ip.bat 13.222.9.65 dns      (rede que filtra IP, ex.: FIAP-IOT: o backend usa o nome DNS da EC2)
# Atualiza: .env (backend), firmware/WineGuard/config.h (ESP32), wokwi/config.wokwi.h + wokwi/sketch.ino (Wokwi).
param(
    [Parameter(Mandatory = $true)][string]$Ip,
    [string]$Modo = ''
)
$ErrorActionPreference = 'Continue'
Set-Location (Join-Path $PSScriptRoot '..')

if ($Ip -notmatch '^\d{1,3}(\.\d{1,3}){3}$') { Write-Host "IP invalido: $Ip"; exit 1 }
$utf8 = New-Object System.Text.UTF8Encoding($false)
$hostHttp = if ($Modo -eq 'dns') { 'ec2-' + ($Ip -replace '\.', '-') + '.compute-1.amazonaws.com' } else { $Ip }

function Update-File([string]$path, [scriptblock]$change) {
    if (-not (Test-Path $path)) { Write-Host "  (nao encontrado: $path)"; return }
    $full = (Resolve-Path $path).Path
    $text = [IO.File]::ReadAllText($full, $utf8)
    [IO.File]::WriteAllText($full, (& $change $text), $utf8)
    Write-Host "  atualizado: $path"
}

Write-Host "Novo endereco da EC2: $Ip (backend usa $hostHttp)"
Update-File '.env' {
    param($t)
    $t = $t -replace '(?m)^FIWARE_ORION_URL=.*$', "FIWARE_ORION_URL=http://${hostHttp}:1026"
    $t = $t -replace '(?m)^FIWARE_IOTA_URL=.*$', "FIWARE_IOTA_URL=http://${hostHttp}:4041"
    $t = $t -replace '(?m)^FIWARE_STH_URL=.*$', "FIWARE_STH_URL=http://${hostHttp}:8666"
    $t -replace '(?m)^MQTT_BROKER_URL=.*$', "MQTT_BROKER_URL=tcp://${Ip}:1883"
}
$mqttLine = '#define MQTT_BROKER     "' + $Ip + '"'
Update-File 'firmware/WineGuard/config.h' { param($t) $t -replace '(?m)^#define MQTT_BROKER .*$', $mqttLine }
Update-File 'wokwi/config.wokwi.h' { param($t) $t -replace '(?m)^#define MQTT_BROKER .*$', $mqttLine }

node firmware/tools/build-wokwi.mjs
docker compose up -d backend
Write-Host ''
Write-Host 'Pronto. Falta fazer a mao:'
Write-Host '  - Wokwi: cole o novo wokwi/sketch.ino no projeto, Save e Start.'
Write-Host '  - ESP32 fisico: grave o firmware de novo (o broker esta no config.h).'

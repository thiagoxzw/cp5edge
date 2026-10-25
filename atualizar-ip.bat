@echo off
REM Uso: atualizar-ip.bat ^<IP da EC2^> [dns]
if "%~1"=="" (echo Uso: atualizar-ip.bat ^<IP da EC2^> [dns] & pause & exit /b 1)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\atualizar-ip.ps1" -Ip %1 -Modo "%~2"
pause

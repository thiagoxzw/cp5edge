@echo off
REM Sobe o WineGuard (PostgreSQL + backend + frontend) e abre o dashboard.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\iniciar.ps1"
pause

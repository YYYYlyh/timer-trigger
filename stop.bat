@echo off
set SCRIPT_DIR=%~dp0
for /f "usebackq delims=" %%p in (`powershell -NoProfile -Command "Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'java.exe' -and $_.CommandLine -like '*timer-trigger-1.0.0.jar*' } | Select-Object -ExpandProperty ProcessId"`) do (
  echo Stopping timer-trigger (PID %%p)
  taskkill /F /PID %%p >nul 2>&1
)

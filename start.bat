@echo off
set SCRIPT_DIR=%~dp0
java -jar "%SCRIPT_DIR%\target\timer-trigger-1.0.0.jar" --config "%SCRIPT_DIR%\config.yaml"

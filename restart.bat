@echo off
set SCRIPT_DIR=%~dp0
call "%SCRIPT_DIR%\stop.bat"
call "%SCRIPT_DIR%\start.bat"

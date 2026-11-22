@echo off
setlocal
cd /d "%~dp0.."
java -cp target\campusbite.jar campusbite.tools.LastSandwichRace %*
if errorlevel 1 pause

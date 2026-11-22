@echo off
setlocal
cd /d "%~dp0.."
java -jar target\campusbite.jar server --demo --reset-demo %*
if errorlevel 1 pause

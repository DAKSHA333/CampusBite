@echo off
setlocal
cd /d "%~dp0.."
java -jar target\campusbite.jar server %*
if errorlevel 1 pause

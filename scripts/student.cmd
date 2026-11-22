@echo off
setlocal
cd /d "%~dp0.."
java -jar target\campusbite.jar student %*
if errorlevel 1 pause

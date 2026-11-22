@echo off
setlocal
cd /d "%~dp0.."
call mvnw.cmd clean verify
if errorlevel 1 pause

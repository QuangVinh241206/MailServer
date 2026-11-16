@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

set "CP=bin;lib\sqlite-jdbc.jar;lib\slf4j-api.jar;lib\slf4j-simple.jar"

if not exist "bin\tools\ViewDatabase.class" (
    echo [THONG BAO] Dang bien dich cong cu ViewDatabase...
    call compile.bat nopause
)

java -cp "%CP%" tools.ViewDatabase

echo.
pause

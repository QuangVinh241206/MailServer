@echo off
setlocal
cd /d "%~dp0"

echo ===================================================
echo   CHAY KIEM TRA TU DONG SMTP + POP3 + DATABASE
echo ===================================================

set "CP=bin;lib\sqlite-jdbc.jar;lib\slf4j-api.jar;lib\slf4j-simple.jar"

if not exist "bin\test\IntegrationTest.class" (
    echo [THONG BAO] Chua bien dich, dang goi compile.bat...
    call compile.bat nopause
)

java -cp "%CP%" test.IntegrationTest

if "%1" neq "nopause" pause

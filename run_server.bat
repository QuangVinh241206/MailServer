@echo off
setlocal
cd /d "%~dp0"

echo ===================================================
echo   KHOI CHAY MAIL SERVER (SMTP: 2525 ^| POP3: 1110)
echo ===================================================

set "FX_LIB=lib\javafx-sdk-21.0.1\lib"
set "CP=bin;lib\sqlite-jdbc.jar;lib\slf4j-api.jar;lib\slf4j-simple.jar"

if not exist "bin\server\ui\ServerUI.class" (
    echo [THONG BAO] Chua bien dich, dang goi compile.bat...
    call compile.bat nopause
)

echo Dang mo Bang dieu khien Mail Server...
start "Mail Server Dashboard" javaw --module-path "%FX_LIB%" --add-modules javafx.controls,javafx.fxml -cp "%CP%" server.ui.ServerUI

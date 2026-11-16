@echo off
setlocal
cd /d "%~dp0"

echo ===================================================
echo   KHOI CHAY GMAIL CLIENT (Co the mo nhieu cua so)
echo ===================================================

set "FX_LIB=lib\javafx-sdk-21.0.1\lib"
set "CP=bin;lib\sqlite-jdbc.jar;lib\slf4j-api.jar;lib\slf4j-simple.jar"

if not exist "bin\ui\MailClientUI.class" (
    echo [THONG BAO] Chua bien dich, dang goi compile.bat...
    call compile.bat nopause
)

echo Dang mo mot cua so Gmail Client...
start "Gmail Client" javaw --module-path "%FX_LIB%" --add-modules javafx.controls,javafx.fxml -cp "%CP%" ui.MailClientUI %*

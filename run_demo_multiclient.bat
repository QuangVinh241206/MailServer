@echo off
setlocal
cd /d "%~dp0"

echo ========================================================
echo   DEMO HE THONG: 1 MAIL SERVER + 2 MAIL CLIENT DONG THOI
echo ========================================================

set "FX_LIB=lib\javafx-sdk-21.0.1\lib"
set "CP=bin;lib\sqlite-jdbc.jar;lib\slf4j-api.jar;lib\slf4j-simple.jar"

if not exist "bin\server\ui\ServerUI.class" (
    echo [THONG BAO] Chua bien dich, dang goi compile.bat...
    call compile.bat nopause
)

echo [1/3] Dang khoi dong Mail Server (ports 2525, 1110)...
start "Mail Server Dashboard" javaw --module-path "%FX_LIB%" --add-modules javafx.controls,javafx.fxml -cp "%CP%" server.ui.ServerUI

timeout /t 2 /nobreak >nul

echo [2/3] Dang mo Client 1 (vi du dang nhap: user1@localhost)...
start "Gmail Client 1" javaw --module-path "%FX_LIB%" --add-modules javafx.controls,javafx.fxml -cp "%CP%" ui.MailClientUI

timeout /t 1 /nobreak >nul

echo [3/3] Dang mo Client 2 (vi du dang nhap: user2@localhost)...
start "Gmail Client 2" javaw --module-path "%FX_LIB%" --add-modules javafx.controls,javafx.fxml -cp "%CP%" ui.MailClientUI

echo.
echo ========================================================
echo  DA KHOI DONG THANH CONG!
echo  - Cua so Server dang lang nghe tai port 2525 va 1110
echo  - Cua so Client 1: Dang nhap user1@localhost (pass 123456)
echo  - Cua so Client 2: Dang nhap user2@localhost (pass 123456)
echo  Thu soan thu tu Client 1 gui sang Client 2 va nguoc lai!
echo ========================================================
timeout /t 5

@echo off
setlocal
cd /d "%~dp0"

echo ========================================================
echo        HE THONG MAIL SERVER VA MAIL CLIENT (JAVAFX)
echo ========================================================
echo.
echo   [1] Khoi chay Mail Server (Bang dieu khien Server)
echo   [2] Khoi chay Mail Client (Mo 1 cua so Client)
echo   [3] Chay Demo Da Client (1 Server + 2 Client dong thoi)
echo   [4] Xem co so du lieu Database (mailserver.db)
echo   [5] Chay kiem tra tu dong (Test suite)
echo   [6] Bien dich lai ma nguon (Compile)
echo   [7] Ket noi toi May chu tu xa (Demo tren may thu 2 qua LAN/Wi-Fi)
echo   [0] Thoat
echo.
echo ========================================================
set /p opt="Nhap lua chon cua ban [1-7]: "

if "%opt%"=="1" goto opt1
if "%opt%"=="2" goto opt2
if "%opt%"=="3" goto opt3
if "%opt%"=="4" goto opt4
if "%opt%"=="5" goto opt5
if "%opt%"=="6" goto opt6
if "%opt%"=="7" goto opt7
if "%opt%"=="0" exit /b
goto end

:opt1
call run_server.bat
goto end

:opt2
call run_client.bat
goto end

:opt3
call run_demo_multiclient.bat
goto end

:opt4
call view_db.bat
goto end

:opt5
call test.bat
goto end

:opt6
call compile.bat
goto end

:opt7
echo.
echo ========================================================
echo   DEMO MAY THU 2: KET NOI TOI MAY CHU MAIL SERVER
echo ========================================================
set /p srvip="Nhap dia chi IP cua May chu (vi du: 192.168.1.88): "
call run_client.bat %srvip%
goto end

:end

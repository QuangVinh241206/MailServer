@echo off
setlocal
cd /d "%~dp0"

echo ===================================================
echo   BIEN DICH DU AN MAIL SERVER - JAVAFX + SQLITE
echo ===================================================

set "FX_LIB=lib\javafx-sdk-21.0.1\lib"
set "CP=lib\sqlite-jdbc.jar;lib\slf4j-api.jar;lib\slf4j-simple.jar"

if not exist "bin" mkdir "bin"

echo [1/2] Dang thu thap cac tap tin nguon...
dir /s /b src\*.java > sources.txt

echo [2/2] Dang bien dich bang javac...
javac --module-path "%FX_LIB%" --add-modules javafx.controls,javafx.fxml -cp "%CP%" -d bin @sources.txt

if %ERRORLEVEL% EQU 0 (
    if exist sources.txt del sources.txt
    echo.
    echo ===================================================
    echo  BIEN DICH THANH CONG!
    echo  Khoi chay ung dung: run.bat
    echo  Chay kiem tra tu dong: test.bat
    echo ===================================================
) else (
    echo.
    echo [LOI] Bien dich that bai!
)

if "%1" neq "nopause" pause

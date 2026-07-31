@echo off
REM ============================================================
REM CarAssistant crash log grabber
REM Usage: connect car-machine via ADB, then double-click this bat
REM ============================================================

setlocal

REM Locate adb.exe
set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not exist "%ADB%" (
    set "ADB=%USERPROFILE%\AppData\Local\Android\Sdk\platform-tools\adb.exe"
)
if not exist "%ADB%" (
    for /f "delims=" %%i in ('where adb 2^>nul') do set "ADB=%%i"
)
if not exist "%ADB%" (
    echo [ERROR] adb.exe not found.
    echo Please install Android Platform Tools or set ADB path manually.
    pause
    exit /b 1
)

echo [1/4] Checking device connection...
"%ADB%" devices
echo.

echo [2/4] Clearing old logs...
"%ADB%" logcat -c
echo.

echo ============================================================
echo  [3/4] Now reproduce the crash on the car machine.
echo  Logs will be saved to crash_log.txt automatically.
echo  Press any key in this window to stop capturing.
echo ============================================================
echo.

REM Start logcat in background, redirect output to file
start /b "" "%ADB%" logcat -v time *:E AndroidRuntime:E AndroidRuntime:V DEBUG:V System.err:V > crash_log.txt

REM Wait for user to stop
echo Capturing... press any key to stop.
pause >nul

REM Kill background logcat
taskkill /f /im adb.exe >nul 2>&1

echo.
echo [4/4] Log saved to crash_log.txt
echo Please send this file to the developer.
echo.
pause

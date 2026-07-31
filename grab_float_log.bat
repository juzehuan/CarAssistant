@echo off
REM ============================================================
REM FloatService debug log grabber
REM ============================================================

setlocal

set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not exist "%ADB%" (
    set "ADB=%USERPROFILE%\AppData\Local\Android\Sdk\platform-tools\adb.exe"
)
if not exist "%ADB%" (
    for /f "delims=" %%i in ('where adb 2^>nul') do set "ADB=%%i"
)
if not exist "%ADB%" (
    echo [ERROR] adb.exe not found.
    pause
    exit /b 1
)

echo [1/3] Clearing old logs...
"%ADB%" logcat -c
echo.

echo ============================================================
echo  [2/3] Now click the floating button several times.
echo  Press any key to stop.
echo ============================================================

start /b "" "%ADB%" logcat -v time FloatService:V AndroidRuntime:E > float_debug.txt
pause >nul
taskkill /f /im adb.exe >nul 2>&1

echo.
echo [3/3] Log saved to float_debug.txt
pause

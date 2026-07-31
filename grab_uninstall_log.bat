@echo off
REM ============================================================
REM App uninstall debug log grabber
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
echo  [2/3] Now click UNINSTALL button on an app in the app list.
echo  Then click the CONFIRM button in the dialog.
echo  Press any key to stop.
echo ============================================================

start /b "" "%ADB%" logcat -v time AppFragment:V ConfirmDialog:V AndroidRuntime:E > uninstall_debug.txt
pause >nul
taskkill /f /im adb.exe >nul 2>&1

echo.
echo [3/3] Log saved to uninstall_debug.txt
pause

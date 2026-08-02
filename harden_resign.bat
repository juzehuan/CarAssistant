@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion
REM =============================================================================
REM CarAssistant 加固后再签名脚本
REM
REM 用途：
REM   对「加固后的 APK」进行 zipalign 对齐 + 用 release keystore 重新签名。
REM   加固（乐固 / 360 加固保 / 梆梆等）需先由对应工具产出加固 APK，
REM   本脚本负责加固之后的「对齐 + 重签名」环节。
REM
REM 用法：
REM   harden_resign.bat <加固后的APK> [输出APK]
REM   例：harden_resign.bat app-release_legu.apk app-release-final.apk
REM
REM 说明：
REM   - 签名信息从项目根目录 keystore.properties 读取
REM   - Android SDK / build-tools 自动探测（也可用 ANDROID_HOME 覆盖）
REM   - JAVA_HOME 缺失时回退到 Android Studio 自带 JRE
REM =============================================================================

if "%~1"=="" (
    echo [错误] 缺少参数：加固后的 APK 路径
    echo 用法: harden_resign.bat ^<加固后的APK^> [输出APK]
    exit /b 1
)

set "INPUT_APK=%~1"
if not exist "%INPUT_APK%" (
    echo [错误] 找不到输入 APK: %INPUT_APK%
    exit /b 1
)

set "PROJECT_DIR=%~dp0"
if "%~2"=="" (
    set "OUTPUT_APK=%PROJECT_DIR%app-release-final.apk"
) else (
    set "OUTPUT_APK=%~2"
)

REM ---------- 1. 准备 JAVA_HOME ----------
if not defined JAVA_HOME (
    if exist "%ProgramFiles%\Android\Android Studio\jbr" (
        set "JAVA_HOME=%ProgramFiles%\Android\Android Studio\jbr"
    ) else if exist "%LOCALAPPDATA%\.." (
        REM 兜底：用户目录下的 Android Studio
        for /d %%d in ("%LOCALAPPDATA%\..\*\AppData\Local\Android\Android Studio\jbr") do set "JAVA_HOME=%%d"
    )
)
if defined JAVA_HOME (
    set "PATH=%JAVA_HOME%\bin;%PATH%"
)
where java >nul 2>nul
if errorlevel 1 (
    echo [错误] 未找到 java，请设置 JAVA_HOME 或安装 JDK
    exit /b 1
)

REM ---------- 2. 定位 build-tools ----------
set "SDK_DIR="
if defined ANDROID_HOME (
    set "SDK_DIR=%ANDROID_HOME%"
) else (
    set "SDK_DIR=%LOCALAPPDATA%\Android\Sdk"
)
set "BUILD_TOOLS="
for /d %%d in ("%SDK_DIR%\build-tools\*") do set "BUILD_TOOLS=%%d"
if not defined BUILD_TOOLS (
    echo [错误] 未找到 build-tools，请检查 Android SDK 路径
    exit /b 1
)
set "ZIPALIGN=%BUILD_TOOLS%\zipalign.exe"
set "APKSIGNER=%BUILD_TOOLS%\apksigner.bat"
if not exist "%ZIPALIGN%" (
    echo [错误] 找不到 zipalign: %ZIPALIGN%
    exit /b 1
)

REM ---------- 3. 读取 keystore.properties ----------
set "KEYSTORE_PROPS=%PROJECT_DIR%keystore.properties"
if not exist "%KEYSTORE_PROPS%" (
    echo [错误] 找不到 keystore.properties
    exit /b 1
)
set "STORE_FILE="
set "STORE_PW="
set "KEY_ALIAS="
set "KEY_PW="
for /f "usebackq tokens=1,* delims==" %%a in ("%KEYSTORE_PROPS%") do (
    set "K=%%a"
    set "V=%%b"
    if "!K!"=="storeFile"      set "STORE_FILE=!V!"
    if "!K!"=="storePassword"  set "STORE_PW=!V!"
    if "!K!"=="keyAlias"       set "KEY_ALIAS=!V!"
    if "!K!"=="keyPassword"    set "KEY_PW=!V!"
)
set "STORE_FILE=%PROJECT_DIR%!STORE_FILE!"
if not exist "!STORE_FILE!" (
    echo [错误] 找不到 keystore: !STORE_FILE!
    exit /b 1
)

REM ---------- 4. zipalign 对齐 ----------
set "ALIGNED_APK=%PROJECT_DIR%_aligned_tmp.apk"
if exist "%ALIGNED_APK%" del /f /q "%ALIGNED_APK%"
echo [1/2] zipalign 对齐...
"%ZIPALIGN%" -p 4 "%INPUT_APK%" "%ALIGNED_APK%"
if errorlevel 1 (
    echo [错误] zipalign 失败
    exit /b 1
)

REM ---------- 5. apksigner 重签名 ----------
echo [2/2] apksigner 用 release keystore 签名...
"%APKSIGNER%" sign ^
    --ks "!STORE_FILE!" ^
    --ks-key-alias "!KEY_ALIAS!" ^
    --ks-pass pass:"!STORE_PW!" ^
    --key-pass pass:"!KEY_PW!" ^
    --out "%OUTPUT_APK%" ^
    "%ALIGNED_APK%"
if errorlevel 1 (
    echo [错误] apksigner 签名失败
    exit /b 1
)

REM ---------- 6. 校验 ----------
echo [校验] 验证签名...
"%APKSIGNER%" verify --print-certs "%OUTPUT_APK%"
if errorlevel 1 (
    echo [错误] 签名校验失败
    exit /b 1
)

del /f /q "%ALIGNED_APK%"
echo.
echo [完成] 已生成并重签名: %OUTPUT_APK%
endlocal

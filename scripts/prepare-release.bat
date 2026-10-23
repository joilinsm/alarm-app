@echo off
setlocal EnableExtensions
cd /d "%~dp0\.."
if errorlevel 1 (
  echo Failed to change to the project root.
  exit /b 1
)

call "%~dp0check-environment.bat"
if errorlevel 1 exit /b 1

if exist "C:\Program Files\Android\Android Studio\jbr" (
  set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
)
if exist "%LOCALAPPDATA%\Android\Sdk" (
  set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
  set "ANDROID_SDK_ROOT=%LOCALAPPDATA%\Android\Sdk"
)

echo Running unit tests...
call gradlew.bat test --no-daemon
if errorlevel 1 exit /b 1

echo Running lintDebug...
call gradlew.bat lintDebug --no-daemon
if errorlevel 1 exit /b 1

echo Building debug APK...
call gradlew.bat assembleDebug --no-daemon
if errorlevel 1 exit /b 1

set "APK=app\build\outputs\apk\debug\app-debug.apk"
if not exist "%APK%" (
  echo ERROR: APK missing at %APK%
  exit /b 1
)

set "OUT=release-staging"
if not exist "%OUT%" mkdir "%OUT%"

for /f "usebackq delims=" %%H in (`powershell -NoProfile -Command "(Get-FileHash -Algorithm SHA256 '%CD%\%APK%').Hash.ToLower()"`) do set "SHA=%%H"
for /f "usebackq delims=" %%S in (`powershell -NoProfile -Command "(Get-Item '%CD%\%APK%').Length"`) do set "SIZE=%%S"

copy /Y "%APK%" "%OUT%\AlarmX-debug.apk" >nul
> "%OUT%\SHA256SUMS" echo %SHA%  AlarmX-debug.apk
> "%OUT%\version.json" (
  echo {
  echo   "versionCode": 2,
  echo   "versionName": "1.1.0-debug",
  echo   "apkUrl": "REPLACE_WITH_ASSET_OR_PROXY_URL",
  echo   "sha256": "%SHA%",
  echo   "releaseNotes": "AlarmX 1.1.0: video wallpapers, monthly recurrence, private update checks.",
  echo   "minSdk": 26,
  echo   "packageName": "com.alarmx.smartalarm"
  echo }
)

echo.
echo Release staging ready in %CD%\%OUT%
echo   APK size: %SIZE% bytes
echo   SHA-256: %SHA%
echo Edit version.json apkUrl before publishing if using a manifest proxy.
echo This script does NOT publish. Run publish-private-release.bat explicitly.
exit /b 0

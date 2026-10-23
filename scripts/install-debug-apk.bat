@echo off
setlocal EnableExtensions
cd /d "%~dp0\.."
if errorlevel 1 (
  echo Failed to change to the project root.
  exit /b 1
)

set "APK=app\build\outputs\apk\debug\app-debug.apk"
if not exist "%APK%" (
  echo Debug APK not found. Building it first.
  call "%~dp0build-debug-apk.bat"
  if errorlevel 1 exit /b 1
)

set "ADB="
if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not defined ADB if exist "%ANDROID_HOME%\platform-tools\adb.exe" set "ADB=%ANDROID_HOME%\platform-tools\adb.exe"
where adb >nul 2>&1
if not errorlevel 1 if not defined ADB set "ADB=adb"

if not defined ADB (
  echo ERROR: adb was not found. Install platform-tools or copy the APK to the phone manually:
  echo   %CD%\%APK%
  exit /b 1
)

echo Using "%ADB%"
"%ADB%" devices
"%ADB%" install -r "%APK%"
set "INSTALL_EXIT=%ERRORLEVEL%"
if not "%INSTALL_EXIT%"=="0" (
  echo ERROR: adb install failed with exit code %INSTALL_EXIT%.
  echo Authorize USB debugging on the phone if prompted, or copy the APK manually.
  exit /b %INSTALL_EXIT%
)

echo Installed %APK%
exit /b 0

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

echo Building debug APK with Gradle wrapper...
call gradlew.bat assembleDebug --stacktrace
set "BUILD_EXIT=%ERRORLEVEL%"
if not "%BUILD_EXIT%"=="0" (
  echo ERROR: Debug build failed with exit code %BUILD_EXIT%.
  exit /b %BUILD_EXIT%
)

set "APK=app\build\outputs\apk\debug\app-debug.apk"
if not exist "%APK%" (
  echo ERROR: Build reported success but %APK% was not found.
  exit /b 1
)

echo Debug APK ready:
echo   %CD%\%APK%
for %%I in ("%APK%") do echo   Size: %%~zI bytes
exit /b 0

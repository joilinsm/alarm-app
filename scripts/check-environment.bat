@echo off
setlocal EnableExtensions
cd /d "%~dp0\.."
if errorlevel 1 (
  echo Failed to change to the project root.
  exit /b 1
)

echo AlarmX environment check
echo Project root: %CD%

if not defined JAVA_HOME (
  if exist "C:\Program Files\Android\Android Studio\jbr" (
    set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
  )
)
if not defined ANDROID_HOME (
  if exist "%LOCALAPPDATA%\Android\Sdk" (
    set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
  )
)
if not defined ANDROID_SDK_ROOT if defined ANDROID_HOME set "ANDROID_SDK_ROOT=%ANDROID_HOME%"

echo JAVA_HOME=%JAVA_HOME%
echo ANDROID_HOME=%ANDROID_HOME%

if not exist "%JAVA_HOME%\bin\java.exe" (
  echo ERROR: Java was not found. Install JDK 17 or 21, or Android Studio, then set JAVA_HOME.
  exit /b 1
)
"%JAVA_HOME%\bin\java.exe" -version
if errorlevel 1 exit /b 1

if not exist "%ANDROID_HOME%\platform-tools" (
  echo ERROR: Android SDK was not found. Install Android Studio and the Android SDK.
  exit /b 1
)

if not exist "gradlew.bat" (
  echo ERROR: gradlew.bat is missing from the project root.
  exit /b 1
)

echo Environment looks usable.
exit /b 0

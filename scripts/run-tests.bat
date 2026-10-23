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

echo Running JVM unit tests...
call gradlew.bat testDebugUnitTest --stacktrace
set "TEST_EXIT=%ERRORLEVEL%"
if not "%TEST_EXIT%"=="0" (
  echo ERROR: Unit tests failed with exit code %TEST_EXIT%.
  exit /b %TEST_EXIT%
)

echo Unit tests finished.
echo Instrumented tests require an emulator or a phone:
echo   gradlew.bat connectedDebugAndroidTest
exit /b 0

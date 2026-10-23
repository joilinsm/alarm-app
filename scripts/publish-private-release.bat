@echo off
setlocal EnableExtensions
cd /d "%~dp0\.."
if errorlevel 1 (
  echo Failed to change to the project root.
  exit /b 1
)

where gh >nul 2>&1
if errorlevel 1 (
  echo ERROR: GitHub CLI gh is required.
  exit /b 1
)

gh auth status >nul 2>&1
if errorlevel 1 (
  echo ERROR: Not authenticated. Run: gh auth login
  exit /b 1
)

if "%~1"=="" (
  echo Usage: publish-private-release.bat v1.1.0
  echo This publishes a GitHub Release for the private alarm-app repository.
  exit /b 1
)

set "TAG=%~1"
set "STAGING=release-staging"
set "APK=%STAGING%\AlarmX-debug.apk"
set "META=%STAGING%\version.json"
set "SUMS=%STAGING%\SHA256SUMS"

if not exist "%APK%" (
  echo ERROR: Missing %APK%. Run prepare-release.bat first.
  exit /b 1
)
if not exist "%META%" (
  echo ERROR: Missing %META%. Run prepare-release.bat first.
  exit /b 1
)
if not exist "%SUMS%" (
  echo ERROR: Missing %SUMS%. Run prepare-release.bat first.
  exit /b 1
)

for /f "usebackq delims=" %%H in (`powershell -NoProfile -Command "(Get-FileHash -Algorithm SHA256 '%CD%\%APK%').Hash.ToLower()"`) do set "ACTUAL=%%H"
for /f "usebackq tokens=1" %%H in ("%SUMS%") do set "EXPECTED=%%H"
if /I not "%ACTUAL%"=="%EXPECTED%" (
  echo ERROR: SHA-256 mismatch between APK and SHA256SUMS. Re-run prepare-release.bat.
  exit /b 1
)

echo Publishing private release %TAG% to joilinsm/alarm-app ...
gh release create "%TAG%" "%APK%" "%META%" "%SUMS%" --repo joilinsm/alarm-app --title "AlarmX %TAG%" --notes-file "%META%"
if errorlevel 1 exit /b 1

echo Release published.
gh release view "%TAG%" --repo joilinsm/alarm-app
exit /b 0

@echo off
echo =======================================================
echo   Synced N - Quick Emulator & ADB Clean Recovery Tool
echo =======================================================
echo.
echo [1/3] Terminating hung emulator and adb processes...
taskkill /F /IM qemu-system-x86_64.exe /T 2>nul
taskkill /F /IM emulator.exe /T 2>nul
taskkill /F /IM adb.exe /T 2>nul

echo [2/3] Removing orphaned AVD lock files...
del /F /Q /S "%USERPROFILE%\.android\avd\*.lock" 2>nul
for /d /r "%USERPROFILE%\.android\avd" %%d in (*.lock) do rd /s /q "%%d" 2>nul

echo [3/3] Restarting ADB server...
if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
    "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" start-server
) else (
    adb start-server 2>nul
)

echo.
echo =======================================================
echo   Recovery Complete! 
echo   You can now launch the Emulator and Android Studio cleanly.
echo =======================================================
pause

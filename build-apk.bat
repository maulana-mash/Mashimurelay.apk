@echo off
title Build Simurelay Android Native APK
cd /d "%~dp0"
echo ===================================================================
echo     SIMURELAY ANDROID - 100%% KOTLIN NATIVE BUILD SYSTEM
echo ===================================================================
echo Memeriksa JDK Android Studio...

set "JAVA_HOME=D:\Game Mash_\Android Studio\jbr"
if not exist "%JAVA_HOME%\bin\javac.exe" (
    echo [INFO] Menyesuaikan JAVA_HOME default...
)

echo Memulai kompilasi Gradle (assembleDebug)...
call gradlew.bat assembleDebug

if %ERRORLEVEL% EQU 0 (
    if not exist "release-apk" mkdir "release-apk"
    copy /Y "app\build\outputs\apk\debug\app-debug.apk" "release-apk\Simurelay.apk"
    echo.
    echo ===================================================================
    echo   [SUKSES] Kompilasi APK Native Selesai!
    echo   File APK tersimpan di: release-apk\Simurelay.apk
    echo ===================================================================
) else (
    echo.
    echo [ERROR] Gagal mengompilasi APK. Silakan periksa pesan error di atas.
)

pause

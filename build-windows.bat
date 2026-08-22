@echo off
setlocal enabledelayedexpansion

echo ========================================
echo   VPN Manager Build for Windows
echo ========================================

set JAVA_HOME=C:\Program Files\BellSoft\LibericaJDK-21-FULL
set PATH=%JAVA_HOME%\bin;%PATH%

echo Using JAVA_HOME: %JAVA_HOME%
java -version
echo.

echo Building project...
call mvn clean package
if errorlevel 1 goto error

for %%f in (target\*.jar) do (
    echo %%f | findstr "original" >nul
    if errorlevel 1 (
        set JAR_NAME=%%~nxf
        goto found_jar
    )
)
:found_jar
echo Found JAR: !JAR_NAME!

set VERSION=1.1.0
echo Version: %VERSION%

echo Creating JRE with JavaFX modules...
set RUNTIME_IMAGE=target\runtime
rmdir /s /q %RUNTIME_IMAGE% 2>nul

set JAVAFX_MODULES=
if exist "%JAVA_HOME%\jmods" (
    for %%m in ("%JAVA_HOME%\jmods\javafx.*.jmod") do (
        set JAVAFX_MODULES=!JAVAFX_MODULES!,%%~nm
    )
    set JAVAFX_MODULES=!JAVAFX_MODULES:~1!
    echo Found JavaFX modules: !JAVAFX_MODULES!
)

set MODULES=java.base,java.desktop,java.logging,java.management,java.naming,java.net.http,java.sql,java.xml,jdk.crypto.ec,jdk.unsupported,java.compiler,jdk.compiler,jdk.unsupported.desktop,!JAVAFX_MODULES!

"%JAVA_HOME%\bin\jlink" --add-modules %MODULES% --output %RUNTIME_IMAGE% --compress=2 --no-header-files --no-man-pages --strip-debug --vm=server
if errorlevel 1 goto error

if not exist "%RUNTIME_IMAGE%\bin\java.exe" (
    echo JRE creation failed!
    goto error
)

echo [OK] JRE created successfully
"%RUNTIME_IMAGE%\bin\java.exe" -version
echo.

REM --- ИСПОЛЬЗУЕМ ГОТОВЫЙ ICO ФАЙЛ ---
echo Using icon from resources...
set ICON_FILE=src\main\resources\icons\vpnmanager.ico

if exist "%ICON_FILE%" (
    echo [OK] Icon found: %ICON_FILE%
) else (
    echo [WARNING] Icon not found at %ICON_FILE%
    echo Using default Java icon...
    set ICON_FILE=
)
echo.

echo Creating EXE installer...
jpackage ^
    --type exe ^
    --name "VPNManager" ^
    --app-version %VERSION% ^
    --vendor "GreatStep" ^
    --description "VPN Manager for OpenWrt" ^
    --copyright "GreatStep 2024" ^
    --main-class ru.greatstep.vpnmanager.MainApp ^
    --main-jar !JAR_NAME! ^
    --input target ^
    --dest target/dist ^
    --win-shortcut ^
    --win-menu ^
    --win-dir-chooser ^
    --win-per-user-install ^
    --runtime-image %RUNTIME_IMAGE% ^
    --icon "%ICON_FILE%"

if errorlevel 1 goto error

echo.
echo ========================================
echo [OK] EXE installer created successfully!
echo Location: target\dist\VPNManager-%VERSION%.exe
if exist "%ICON_FILE%" (
    echo Icon included: %ICON_FILE%
)
echo ========================================
pause
exit /b 0

:error
echo Build failed!
pause
exit /b 1
@echo off
setlocal enabledelayedexpansion

echo ========================================
echo   VPN Manager Build for Windows
echo ========================================

REM Проверка наличия Java 21+
echo Checking Java version...
java -version 2>&1 | findstr "version \"21" >nul
if errorlevel 1 (
    echo Java 21+ is required!
    echo Please install Java 21+ from https://adoptium.net/
    pause
    exit /b 1
)

REM Проверка наличия jpackage
echo Checking jpackage...
where jpackage >nul 2>&1
if errorlevel 1 (
    echo jpackage not found!
    echo Please install JDK 21+ with jpackage support
    pause
    exit /b 1
)

REM Сборка проекта
echo Building project...
call mvn clean package
if errorlevel 1 (
    echo Maven build failed!
    pause
    exit /b 1
)

REM Находим JAR файл
set JAR_NAME=
for %%f in (target\*.jar) do (
    echo %%f | findstr "original" >nul
    if errorlevel 1 (
        set JAR_NAME=%%~nxf
        echo Found JAR: !JAR_NAME!
        goto :found_jar
    )
)
:found_jar

if "!JAR_NAME!"=="" (
    echo No JAR file found!
    pause
    exit /b 1
)

REM Получение версии
for /f "tokens=2" %%i in ('mvn help:evaluate -Dexpression=project.version -q -DforceStdout') do set VERSION=%%i
echo Version: %VERSION%

REM Создание JRE
echo Creating JRE runtime image...
set RUNTIME_IMAGE=target\runtime
rmdir /s /q %RUNTIME_IMAGE% 2>nul

REM Определяем список модулей
set MODULES=java.base,java.desktop,java.logging,java.management,java.naming,java.net.http,java.sql,java.xml,jdk.crypto.ec,jdk.unsupported,java.compiler,jdk.compiler,jdk.unsupported.desktop

REM Добавляем JavaFX модули если есть
if exist "%JAVA_HOME%\jmods" (
    for %%m in ("%JAVA_HOME%\jmods\javafx.*.jmod") do (
        set JAVAFX_MODULES=!JAVAFX_MODULES!,%%~nm
    )
    if not "!JAVAFX_MODULES!"=="" (
        set MODULES=!MODULES!!JAVAFX_MODULES!
        echo Found JavaFX modules
    )
)

REM Создаем JRE через jlink
echo Creating runtime with jlink...
"%JAVA_HOME%\bin\jlink" ^
    --add-modules %MODULES% ^
    --output %RUNTIME_IMAGE% ^
    --compress=2 ^
    --no-header-files ^
    --no-man-pages ^
    --strip-debug ^
    --vm=server

if errorlevel 1 (
    echo Failed to create runtime with jlink!
    pause
    exit /b 1
)

if not exist "%RUNTIME_IMAGE%\bin\java.exe" (
    echo JRE creation failed!
    pause
    exit /b 1
)

echo [OK] JRE created successfully

REM Создание EXE установщика
echo Creating EXE installer...

REM Очищаем старые сборки
rmdir /s /q target\deb 2>nul
mkdir target\deb\opt\vpnmanager
mkdir target\deb\usr\bin
mkdir target\deb\usr\share\applications

REM Создаем директории для иконок
for %%s in (16 32 64 128 256 512) do (
    mkdir target\deb\usr\share\icons\hicolor\%%sx%%s\apps 2>nul
)

REM Копируем JAR
copy target\!JAR_NAME! target\deb\opt\vpnmanager\ >nul

REM Копируем JRE
xcopy /E /I %RUNTIME_IMAGE% target\deb\opt\vpnmanager\jre\ >nul

REM Копируем иконки
echo Copying icons...
set ICONS_COPIED=0
for %%s in (16 32 64 128 256 512) do (
    if exist "src\main\resources\icons\vpnmanager_%%s.png" (
        copy "src\main\resources\icons\vpnmanager_%%s.png" "target\deb\usr\share\icons\hicolor\%%sx%%s\apps\vpnmanager.png" >nul
        if errorlevel 1 (
            echo [ERROR] Failed to copy %%sx%%s icon
        ) else (
            echo [OK] Copied %%sx%%s icon
            set /a ICONS_COPIED+=1
        )
    ) else (
        echo [WARNING] Icon %%sx%%s not found
    )
)

if %ICONS_COPIED%==0 (
    echo [ERROR] No icons copied!
    pause
    exit /b 1
)

REM Создаем скрипт запуска
(
echo @echo off
echo set DIR=%%~dp0
echo if exist "%%DIR%%jre\bin\java.exe" (
echo     "%%DIR%%jre\bin\java.exe" -jar "%%DIR%%"*.jar %%*
echo ) else (
echo     echo ERROR: Built-in JRE not found
echo     pause
echo )
) > target\deb\opt\vpnmanager\start.bat

REM Создаем .desktop файл
(
echo [Desktop Entry]
echo Name=VPN Manager
echo Comment=Manage VPN domains and IPs on OpenWrt
echo Exec=/opt/vpnmanager/start.bat
echo Icon=vpnmanager
echo Terminal=false
echo Type=Application
echo Categories=Network;
echo StartupNotify=true
) > target\deb\usr\share\applications\vpnmanager.desktop

REM Создаем EXE через jpackage
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
    --runtime-image %RUNTIME_IMAGE%

if errorlevel 1 (
    echo Failed to create EXE installer!
    pause
    exit /b 1
) else (
    echo [OK] EXE installer created successfully!
    echo Location: target\dist\VPNManager-%VERSION%.exe
)

echo ========================================
echo Build complete!
pause
#!/bin/bash

# Цвета для вывода
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  VPN Manager Build for macOS${NC}"
echo -e "${GREEN}========================================${NC}"

# Проверяем наличие Liberica JDK
LIBERICA_HOME="/Users/$USER/.jdks/liberica-full-21.0.10"

if [ -d "$LIBERICA_HOME" ]; then
    export JAVA_HOME="$LIBERICA_HOME"
    export PATH="$JAVA_HOME/bin:$PATH"
    echo -e "${GREEN}Using Liberica Full JDK from: $JAVA_HOME${NC}"
else
    # Пробуем найти Java 21 через /usr/libexec/java_home
    JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null)
    if [ -n "$JAVA_HOME" ]; then
        export JAVA_HOME="$JAVA_HOME"
        export PATH="$JAVA_HOME/bin:$PATH"
        echo -e "${GREEN}Using Java 21 from: $JAVA_HOME${NC}"
    else
        echo -e "${RED}Java 21 not found!${NC}"
        echo "Please install Liberica Full JDK 21 from:"
        echo "  https://bell-sw.com/pages/downloads/#jdk-21-lts"
        echo ""
        echo "Or install via brew:"
        echo "  brew install openjdk@21"
        exit 1
    fi
fi

# Проверка версии Java
echo -e "${YELLOW}Java version:${NC}"
"$JAVA_HOME/bin/java" -version 2>&1 | head -1

# Проверка наличия jpackage
echo -e "${YELLOW}Checking jpackage...${NC}"
if ! command -v jpackage &> /dev/null; then
    echo -e "${RED}jpackage not found!${NC}"
    echo "Please install JDK 21+ with jpackage support"
    exit 1
fi

# Сборка проекта
echo -e "${YELLOW}Building project...${NC}"
mvn clean package
if [ $? -ne 0 ]; then
    echo -e "${RED}Maven build failed!${NC}"
    exit 1
fi

# Находим JAR файл
JAR_NAME=$(ls target/*.jar 2>/dev/null | grep -v "original" | head -n1 | xargs basename)
if [ -z "$JAR_NAME" ]; then
    echo -e "${RED}No JAR file found!${NC}"
    exit 1
fi
echo -e "${YELLOW}Found JAR: $JAR_NAME${NC}"

# Получение версии
VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
echo -e "${YELLOW}Version: $VERSION${NC}"

# Создание JRE
echo -e "${YELLOW}Creating JRE runtime image...${NC}"
RUNTIME_IMAGE="target/runtime"
rm -rf "$RUNTIME_IMAGE"

# Список модулей
MODULES="java.base,java.desktop,java.logging,java.management,java.naming,java.net.http,java.sql,java.xml,jdk.crypto.ec,jdk.unsupported,java.compiler,jdk.compiler,jdk.unsupported.desktop"

# Добавляем JavaFX модули из Liberica
if [ -d "$JAVA_HOME/jmods" ]; then
    JAVAFX_MODULES=$(ls "$JAVA_HOME/jmods" 2>/dev/null | grep -E "javafx\." | sed 's/\.jmod//g' | tr '\n' ',' | sed 's/,$//')
    if [ -n "$JAVAFX_MODULES" ]; then
        MODULES="$MODULES,$JAVAFX_MODULES"
        echo -e "${GREEN}Found JavaFX modules: $JAVAFX_MODULES${NC}"
    fi
fi

# Создаем JRE с помощью jlink
echo -e "${YELLOW}Creating runtime with jlink...${NC}"
"$JAVA_HOME/bin/jlink" \
    --add-modules $MODULES \
    --output "$RUNTIME_IMAGE" \
    --compress=2 \
    --no-header-files \
    --no-man-pages \
    --strip-debug \
    --vm=server 2>&1

if [ $? -ne 0 ] || [ ! -f "$RUNTIME_IMAGE/bin/java" ]; then
    echo -e "${RED}Failed to create runtime with jlink!${NC}"
    exit 1
fi

# Проверяем создание JRE
if [ -f "$RUNTIME_IMAGE/bin/java" ]; then
    echo -e "${GREEN}✅ JRE created successfully${NC}"
    "$RUNTIME_IMAGE/bin/java" -version 2>&1 | head -1
else
    echo -e "${RED}JRE creation failed!${NC}"
    exit 1
fi

# Создание DMG образа
echo -e "${YELLOW}Creating DMG package...${NC}"

# Очищаем старые сборки
rm -rf target/deb
mkdir -p target/deb/opt/vpnmanager
mkdir -p target/deb/usr/bin
mkdir -p target/deb/usr/share/applications

# Создаем директории для иконок
for size in 16 32 64 128 256 512; do
    mkdir -p target/deb/usr/share/icons/hicolor/${size}x${size}/apps
done

# Копируем JAR
cp target/"$JAR_NAME" target/deb/opt/vpnmanager/

# Копируем JRE
cp -r "$RUNTIME_IMAGE" target/deb/opt/vpnmanager/jre

# Проверяем, что JRE скопировался
if [ ! -f "target/deb/opt/vpnmanager/jre/bin/java" ]; then
    echo -e "${RED}JRE not copied correctly!${NC}"
    exit 1
fi

# Копируем иконки
echo -e "${YELLOW}Copying icons...${NC}"
ICONS_COPIED=0
for size in 16 32 64 128 256 512; do
    ICON_SRC="src/main/resources/icons/vpnmanager_${size}.png"
    ICON_DEST="target/deb/usr/share/icons/hicolor/${size}x${size}/apps/vpnmanager.png"

    if [ -f "$ICON_SRC" ]; then
        cp "$ICON_SRC" "$ICON_DEST"
        if [ $? -eq 0 ]; then
            echo -e "${GREEN}✅ Copied ${size}x${size} icon${NC}"
            ICONS_COPIED=$((ICONS_COPIED + 1))
        else
            echo -e "${RED}❌ Failed to copy ${size}x${size} icon${NC}"
        fi
    else
        echo -e "${YELLOW}⚠️ Icon ${size}x${size} not found${NC}"
    fi
done

if [ $ICONS_COPIED -eq 0 ]; then
    echo -e "${RED}❌ No icons copied!${NC}"
    exit 1
fi

# Создаем скрипт запуска
cat > target/deb/opt/vpnmanager/start.sh << 'EOF'
#!/bin/bash
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ -f "$DIR/jre/bin/java" ]; then
    exec "$DIR/jre/bin/java" -jar "$DIR/"*.jar "$@"
else
    echo "ERROR: Built-in JRE not found at $DIR/jre"
    echo "Please reinstall the application"
    exit 1
fi
EOF
chmod +x target/deb/opt/vpnmanager/start.sh

# Создаем .desktop файл
cat > target/deb/usr/share/applications/vpnmanager.desktop << EOF
[Desktop Entry]
Name=VPN Manager
Comment=Manage VPN domains and IPs on OpenWrt
Exec=/opt/vpnmanager/start.sh
Icon=vpnmanager
Terminal=false
Type=Application
Categories=Network;
StartupNotify=true
EOF

# Создаем DMG через jpackage
jpackage \
    --type dmg \
    --name "VPNManager" \
    --app-version "$VERSION" \
    --vendor "GreatStep" \
    --description "VPN Manager for OpenWrt - Manage VPN domains and IPs" \
    --copyright "GreatStep 2024" \
    --main-class ru.greatstep.vpnmanager.MainApp \
    --main-jar "$JAR_NAME" \
    --input target \
    --dest target/dist \
    --mac-package-name "VPN Manager" \
    --mac-package-identifier ru.greatstep.vpnmanager \
    --mac-sign false \
    --runtime-image "$RUNTIME_IMAGE"

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ DMG package created successfully!${NC}"
    echo -e "${GREEN}📦 Location: target/dist/VPNManager-${VERSION}.dmg${NC}"
else
    echo -e "${RED}❌ Failed to create DMG package!${NC}"
    exit 1
fi

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Build complete!${NC}"

echo -e "${YELLOW}To install:${NC}"
echo "  Open VPNManager-${VERSION}.dmg and drag to Applications"
echo ""
echo -e "${YELLOW}To verify icons:${NC}"
echo "  ls -la /Applications/VPNManager.app/Contents/Resources/"
echo ""
echo -e "${YELLOW}To run:${NC}"
echo "  Open from Launchpad or /Applications/VPNManager.app"
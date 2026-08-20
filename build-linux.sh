#!/bin/bash

# Цвета для вывода
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  VPN Manager Build for Linux${NC}"
echo -e "${GREEN}========================================${NC}"

# Используем ваш Liberica Full JDK 21
LIBERICA_HOME="/home/greatstep/.jdks/liberica-full-21.0.10"

# Проверяем наличие Liberica JDK
if [ -d "$LIBERICA_HOME" ]; then
    export JAVA_HOME="$LIBERICA_HOME"
    export PATH="$JAVA_HOME/bin:$PATH"
    echo -e "${GREEN}Using Liberica Full JDK from: $JAVA_HOME${NC}"
else
    echo -e "${RED}Liberica JDK not found at $LIBERICA_HOME${NC}"
    echo "Please check the path"
    exit 1
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

# Создание DEB пакета
echo -e "${YELLOW}Creating DEB package...${NC}"

# Очищаем старые сборки
rm -rf target/deb
mkdir -p target/deb/opt/vpnmanager
mkdir -p target/deb/usr/bin
mkdir -p target/deb/usr/share/applications

# Создаем директории для иконок всех размеров (включая 512)
for size in 16 32 64 128 256 512; do
    mkdir -p target/deb/usr/share/icons/hicolor/${size}x${size}/apps
done

# Копируем JAR
cp target/"$JAR_NAME" target/deb/opt/vpnmanager/

# Копируем JRE целиком
cp -r "$RUNTIME_IMAGE" target/deb/opt/vpnmanager/jre

# Проверяем, что JRE скопировался
if [ ! -f "target/deb/opt/vpnmanager/jre/bin/java" ]; then
    echo -e "${RED}JRE not copied correctly!${NC}"
    exit 1
fi

# Копируем иконки из ресурсов
echo -e "${YELLOW}Copying icons from src/main/resources/icons/...${NC}"

# Проверяем наличие иконок
ICONS_DIR="src/main/resources/icons"
if [ ! -d "$ICONS_DIR" ]; then
    echo -e "${RED}Icons directory not found: $ICONS_DIR${NC}"
    exit 1
fi

# Копируем все размеры иконок
ICONS_COPIED=0
for size in 16 32 64 128 256 512; do
    ICON_SRC="$ICONS_DIR/vpnmanager_${size}.png"
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
        echo -e "${YELLOW}⚠️ Icon ${size}x${size} not found at $ICON_SRC${NC}"
    fi
done

# Проверяем что хотя бы одна иконка скопировалась
if [ $ICONS_COPIED -eq 0 ]; then
    echo -e "${RED}❌ No icons copied! Please check your icons directory.${NC}"
    echo -e "${YELLOW}Expected files: vpnmanager_16.png, vpnmanager_32.png, vpnmanager_64.png, vpnmanager_128.png, vpnmanager_256.png, vpnmanager_512.png${NC}"
    exit 1
fi

# Проверяем главную иконку (128x128 используется в .desktop)
if [ -f "target/deb/usr/share/icons/hicolor/128x128/apps/vpnmanager.png" ]; then
    echo -e "${GREEN}✅ Main icon (128x128) copied successfully${NC}"
    file target/deb/usr/share/icons/hicolor/128x128/apps/vpnmanager.png
else
    echo -e "${YELLOW}⚠️ Main icon (128x128) not found, using fallback${NC}"
    # Используем любой доступный размер как fallback
    for size in 512 256 64 32 16; do
        if [ -f "target/deb/usr/share/icons/hicolor/${size}x${size}/apps/vpnmanager.png" ]; then
            cp "target/deb/usr/share/icons/hicolor/${size}x${size}/apps/vpnmanager.png" \
               "target/deb/usr/share/icons/hicolor/128x128/apps/vpnmanager.png"
            echo -e "${GREEN}✅ Used ${size}x${size} as fallback for 128x128${NC}"
            break
        fi
    done
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

# Создаем символическую ссылку в /usr/bin
ln -sf /opt/vpnmanager/start.sh target/deb/usr/bin/vpnmanager

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

# Создаем control файл
mkdir -p target/deb/DEBIAN
cat > target/deb/DEBIAN/control << EOF
Package: vpnmanager
Version: $VERSION
Section: net
Priority: optional
Architecture: amd64
Maintainer: support@greatstep.ru
Description: VPN Manager for OpenWrt
 Manage VPN domains and IPs on OpenWrt routers
EOF

# Создаем postinst скрипт
cat > target/deb/DEBIAN/postinst << 'EOF'
#!/bin/bash
chmod +x /opt/vpnmanager/start.sh
chmod +x /opt/vpnmanager/jre/bin/java
update-desktop-database /usr/share/applications/
update-icon-caches /usr/share/icons/hicolor/
exit 0
EOF
chmod +x target/deb/DEBIAN/postinst

# Создаем prerm скрипт
cat > target/deb/DEBIAN/prerm << 'EOF'
#!/bin/bash
rm -f /usr/bin/vpnmanager
exit 0
EOF
chmod +x target/deb/DEBIAN/prerm

# Создаем целевую директорию
mkdir -p target/dist

# Собираем DEB пакет
dpkg-deb --root-owner-group --build target/deb target/dist/vpnmanager_${VERSION}_amd64.deb

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ DEB package created successfully!${NC}"
    echo -e "${GREEN}📦 Location: target/dist/vpnmanager_${VERSION}_amd64.deb${NC}"
else
    echo -e "${RED}❌ Failed to create DEB package!${NC}"
    exit 1
fi

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Build complete!${NC}"

echo -e "${YELLOW}To install:${NC}"
echo "  sudo dpkg -i target/dist/vpnmanager_${VERSION}_amd64.deb"
echo ""
echo -e "${YELLOW}To verify icons:${NC}"
echo "  ls -la /usr/share/icons/hicolor/*/apps/vpnmanager.png"
echo ""
echo -e "${YELLOW}To run:${NC}"
echo "  vpnmanager"
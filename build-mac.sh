#!/bin/bash

# Цвета для вывода
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  VPN Manager Build for macOS${NC}"
echo -e "${GREEN}========================================${NC}"

# ---- УСТАНАВЛИВАЕМ JAVA_HOME ДЛЯ СБОРКИ ----
echo -e "${YELLOW}Setting JAVA_HOME for build...${NC}"

# Пытаемся найти Liberica Full JDK
if [ -d "/Library/Java/JavaVirtualMachines/liberica-jdk-21-full.jdk/Contents/Home" ]; then
    export JAVA_HOME="/Library/Java/JavaVirtualMachines/liberica-jdk-21-full.jdk/Contents/Home"
elif [ -d "$HOME/.jdks/liberica-full-21.0.10" ]; then
    export JAVA_HOME="$HOME/.jdks/liberica-full-21.0.10"
elif [ -d "/opt/homebrew/Cellar/liberica-jdk21-full" ]; then
    export JAVA_HOME="/opt/homebrew/Cellar/liberica-jdk21-full/libexec/openjdk.jdk/Contents/Home"
else
    # Пробуем найти через java_home
    TEMP_JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null)
    if [ -n "$TEMP_JAVA_HOME" ]; then
        export JAVA_HOME="$TEMP_JAVA_HOME"
        echo -e "${YELLOW}Found Java 21 via java_home: $JAVA_HOME${NC}"
    else
        echo -e "${RED}❌ Liberica Full JDK 21 not found!${NC}"
        echo "Please install from: https://bell-sw.com/pages/downloads/#jdk-21-lts"
        exit 1
    fi
fi

export PATH="$JAVA_HOME/bin:$PATH"

echo -e "${GREEN}Using JAVA_HOME: $JAVA_HOME${NC}"
echo -e "${YELLOW}Java version:${NC}"
"$JAVA_HOME/bin/java" -version 2>&1 | head -1
echo ""

# ---- ПРОВЕРЯЕМ JAVAFX МОДУЛИ ----
echo -e "${YELLOW}Checking JavaFX modules...${NC}"
if [ -d "$JAVA_HOME/jmods" ]; then
    JAVAFX_COUNT=$(ls "$JAVA_HOME/jmods" 2>/dev/null | grep -c "javafx\.")
    if [ "$JAVAFX_COUNT" -gt 0 ]; then
        echo -e "${GREEN}✅ Found $JAVAFX_COUNT JavaFX modules${NC}"
    else
        echo -e "${RED}❌ No JavaFX modules found in $JAVA_HOME/jmods${NC}"
        exit 1
    fi
else
    echo -e "${RED}❌ jmods directory not found!${NC}"
    exit 1
fi

# ---- СБОРКА ----
echo -e "${YELLOW}Building project...${NC}"
mvn clean package
if [ $? -ne 0 ]; then
    echo -e "${RED}Maven build failed!${NC}"
    exit 1
fi

JAR_NAME=$(ls target/*.jar 2>/dev/null | grep -v "original" | head -n1 | xargs basename)
if [ -z "$JAR_NAME" ]; then
    echo -e "${RED}No JAR file found!${NC}"
    exit 1
fi
echo -e "${GREEN}Found JAR: $JAR_NAME${NC}"

VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout 2>/dev/null || echo "1.0.0")
if [ -z "$VERSION" ] || [ "$VERSION" = "Help" ]; then
    VERSION="1.0.0"
fi
echo -e "${GREEN}Version: $VERSION${NC}"

# ---- СОЗДАНИЕ JRE ----
echo -e "${YELLOW}Creating JRE runtime image with JavaFX...${NC}"
RUNTIME_IMAGE="target/runtime"
rm -rf "$RUNTIME_IMAGE"

JAVAFX_MODULES=$(ls "$JAVA_HOME/jmods" 2>/dev/null | grep "javafx\." | sed 's/\.jmod//g' | tr '\n' ',' | sed 's/,$//')
echo -e "${GREEN}JavaFX modules: $JAVAFX_MODULES${NC}"

MODULES="java.base,java.desktop,java.logging,java.management,java.naming,java.net.http,java.sql,java.xml,jdk.crypto.ec,jdk.unsupported,java.compiler,jdk.compiler,jdk.unsupported.desktop,$JAVAFX_MODULES"

echo -e "${YELLOW}Creating runtime...${NC}"
"$JAVA_HOME/bin/jlink" \
    --add-modules $MODULES \
    --output "$RUNTIME_IMAGE" \
    --compress=2 \
    --no-header-files \
    --no-man-pages \
    --strip-debug \
    --vm=server

if [ $? -ne 0 ] || [ ! -f "$RUNTIME_IMAGE/bin/java" ]; then
    echo -e "${RED}Failed to create runtime image!${NC}"
    exit 1
fi

echo -e "${GREEN}✅ JRE created successfully${NC}"
"$RUNTIME_IMAGE/bin/java" -version 2>&1 | head -1
echo ""

# Проверяем JavaFX в JRE
echo -e "${YELLOW}Verifying JavaFX in created JRE...${NC}"
"$RUNTIME_IMAGE/bin/java" --list-modules 2>/dev/null | grep javafx | head -3 || echo -e "${RED}❌ JavaFX not found in JRE!${NC}"

# ---- СОЗДАНИЕ DMG ----
echo -e "${YELLOW}Creating DMG package...${NC}"

ICON_FILE="src/main/resources/icons/vpnmanager.icns"
if [ ! -f "$ICON_FILE" ]; then
    echo -e "${YELLOW}ICNS icon not found...${NC}"
    if command -v magick &> /dev/null || command -v convert &> /dev/null; then
        mkdir -p target/icons.iconset
        for size in 16 32 64 128 256 512; do
            if [ -f "src/main/resources/icons/vpnmanager_${size}.png" ]; then
                convert "src/main/resources/icons/vpnmanager_${size}.png" -resize ${size}x${size} "target/icons.iconset/icon_${size}x${size}.png" 2>/dev/null
            fi
        done
        iconutil -c icns target/icons.iconset -o target/vpnmanager.icns 2>/dev/null
        ICON_FILE="target/vpnmanager.icns"
        echo -e "${GREEN}✅ ICNS created${NC}"
    fi
else
    echo -e "${GREEN}✅ ICNS icon found: $ICON_FILE${NC}"
fi

echo -e "${YELLOW}Running jpackage...${NC}"
jpackage \
    --type dmg \
    --name "VPNManager" \
    --app-version "$VERSION" \
    --vendor "GreatStep" \
    --description "VPN Manager for OpenWrt" \
    --copyright "GreatStep 2024" \
    --main-class ru.greatstep.vpnmanager.MainApp \
    --main-jar "$JAR_NAME" \
    --input target \
    --dest target/dist \
    --mac-package-name "VPN Manager" \
    --mac-package-identifier ru.greatstep.vpnmanager \
    --runtime-image "$RUNTIME_IMAGE" \
    ${ICON_FILE:+--icon "$ICON_FILE"} \
    --verbose

if [ $? -ne 0 ]; then
    echo -e "${RED}❌ Failed to create DMG package!${NC}"
    exit 1
fi

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}✅ DMG package created successfully!${NC}"
echo -e "${GREEN}📦 Location: target/dist/VPNManager-${VERSION}.dmg${NC}"
echo -e "${GREEN}========================================${NC}"

echo -e "${YELLOW}To test from terminal:${NC}"
echo "  /Applications/VPNManager.app/Contents/MacOS/VPNManager"
echo ""
echo -e "${YELLOW}If app doesn't start:${NC}"
echo "  xattr -d com.apple.quarantine /Applications/VPNManager.app"
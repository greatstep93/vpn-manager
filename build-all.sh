#!/bin/bash

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  VPN Manager Universal Build${NC}"
echo -e "${GREEN}========================================${NC}"

# Определяем ОС
OS=$(uname -s)
echo -e "${YELLOW}Detected OS: $OS${NC}"

# Проверяем наличие Java
if ! command -v java &> /dev/null; then
    echo -e "${RED}Java not found!${NC}"
    exit 1
fi

# Сборка проекта
echo -e "${YELLOW}Building project...${NC}"
mvn clean package
if [ $? -ne 0 ]; then
    echo -e "${RED}Maven build failed!${NC}"
    exit 1
fi

# Находим JAR файл (исключая original)
JAR_NAME=$(ls target/*.jar 2>/dev/null | grep -v "original" | head -n1 | xargs basename)
if [ -z "$JAR_NAME" ]; then
    echo -e "${RED}No JAR file found!${NC}"
    exit 1
fi
echo -e "${YELLOW}Found JAR: $JAR_NAME${NC}"

VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
echo -e "${YELLOW}Version: $VERSION${NC}"

# Создание JRE
echo -e "${YELLOW}Creating JRE runtime image...${NC}"
RUNTIME_IMAGE="target/runtime"
jlink \
    --add-modules java.base,java.desktop,java.logging,java.management,java.naming,java.net.http,java.sql,java.xml,jdk.crypto.ec,jdk.unsupported \
    --output "$RUNTIME_IMAGE" 2>/dev/null

if [ $? -ne 0 ]; then
    echo -e "${YELLOW}Warning: Could not create runtime image, trying without jlink...${NC}"
    RUNTIME_IMAGE=""
fi

# Сборка для конкретной ОС
case $OS in
    Linux*)
        echo -e "${YELLOW}Building for Linux...${NC}"
        jpackage \
            --type deb \
            --name "vpnmanager" \
            --app-version "$VERSION" \
            --vendor "GreatStep" \
            --description "VPN Manager for OpenWrt" \
            --copyright "GreatStep 2024" \
            --main-class ru.greatstep.vpnmanager.svg.MainApp \
            --main-jar "$JAR_NAME" \
            --input target \
            --dest target/dist \
            --linux-package-name vpnmanager.svg \
            --linux-deb-maintainer "support@greatstep.ru" \
            --linux-shortcut \
            --linux-menu-group "Network" \
            ${RUNTIME_IMAGE:+--runtime-image "$RUNTIME_IMAGE"}
        ;;
    Darwin*)
        echo -e "${YELLOW}Building for macOS...${NC}"
        jpackage \
            --type dmg \
            --name "VPNManager" \
            --app-version "$VERSION" \
            --vendor "GreatStep" \
            --description "VPN Manager for OpenWrt" \
            --copyright "GreatStep 2024" \
            --main-class ru.greatstep.vpnmanager.svg.MainApp \
            --main-jar "$JAR_NAME" \
            --input target \
            --dest target/dist \
            --mac-package-name "VPN Manager" \
            --mac-package-identifier ru.greatstep.vpnmanager.svg \
            ${RUNTIME_IMAGE:+--runtime-image "$RUNTIME_IMAGE"}
        ;;
    MINGW*|CYGWIN*|MSYS*)
        echo -e "${YELLOW}Building for Windows...${NC}"
        jpackage \
            --type exe \
            --name "VPNManager" \
            --app-version "$VERSION" \
            --vendor "GreatStep" \
            --description "VPN Manager for OpenWrt" \
            --copyright "GreatStep 2024" \
            --main-class ru.greatstep.vpnmanager.svg.MainApp \
            --main-jar "$JAR_NAME" \
            --input target \
            --dest target/dist \
            --win-shortcut \
            --win-menu \
            --win-dir-chooser \
            ${RUNTIME_IMAGE:+--runtime-image "$RUNTIME_IMAGE"}
        ;;
    *)
        echo -e "${RED}Unsupported OS: $OS${NC}"
        exit 1
        ;;
esac

if [ $? -eq 0 ]; then
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}✅ Build complete!${NC}"
    echo -e "${GREEN}📦 Check target/dist/ directory${NC}"
    ls -la target/dist/
else
    echo -e "${RED}❌ Build failed!${NC}"
    exit 1
fi
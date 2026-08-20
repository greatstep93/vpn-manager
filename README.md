## Для Windows:

### Установите WiX Toolset (для создания MSI/EXE):

Скачайте с https://wixtoolset.org/

### Или через chocolatey:

```choco install wixtoolset```


Запустите сборку:

```build-windows.bat```

### Результат: 

```target/dist/VPNManager-1.0.0.exe```


## Для MacOS:

### Установите Liberica Full JDK 21 или:

```brew install openjdk@21```


Запустите сборку:

```
chmod +x build-mac.sh
./build-mac.sh
```

### Результат:

```target/dist/VPNManager-1.0.0.dmg```


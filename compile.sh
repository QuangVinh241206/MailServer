#!/bin/bash
set -e

# Chuyen den thu muc chua script
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
cd "$DIR"

echo "==================================================="
echo "  BIEN DICH DU AN MAIL SERVER - JAVAFX + SQLITE"
echo "==================================================="

# Tim JavaFX SDK
if [ -n "$PATH_TO_FX" ]; then
    FX_LIB="$PATH_TO_FX"
elif [ -d "/usr/share/openjfx/lib" ]; then
    FX_LIB="/usr/share/openjfx/lib"
elif [ -d "lib/javafx-sdk-linux/lib" ]; then
    FX_LIB="lib/javafx-sdk-linux/lib"
else
    FX_LIB="lib/javafx-sdk-21.0.1/lib"
fi

CP="lib/sqlite-jdbc.jar:lib/slf4j-api.jar:lib/slf4j-simple.jar"

mkdir -p bin

echo "[1/2] Dang thu thap cac tap tin nguon..."
find src -name "*.java" > sources.txt

echo "[2/2] Dang bien dich bang javac..."
javac --module-path "$FX_LIB" --add-modules javafx.controls,javafx.fxml -cp "$CP" -d bin @sources.txt

rm -f sources.txt

echo ""
echo "==================================================="
echo " BIEN DICH THANH CONG!"
echo " Khoi chay ung dung: ./run.sh"
echo " Chay kiem tra tu dong: ./test.sh"
echo "==================================================="

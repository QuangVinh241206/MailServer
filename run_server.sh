#!/bin/bash
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
cd "$DIR"

echo "==================================================="
echo "   KHOI CHAY MAIL SERVER (SMTP: 2525 | POP3: 1110)"
echo "==================================================="

if [ -n "$PATH_TO_FX" ]; then
    FX_LIB="$PATH_TO_FX"
elif [ -d "/usr/share/openjfx/lib" ]; then
    FX_LIB="/usr/share/openjfx/lib"
elif [ -d "lib/javafx-sdk-linux/lib" ]; then
    FX_LIB="lib/javafx-sdk-linux/lib"
else
    FX_LIB="lib/javafx-sdk-21.0.1/lib"
fi

CP="bin:lib/sqlite-jdbc.jar:lib/slf4j-api.jar:lib/slf4j-simple.jar"

if [ ! -f "bin/server/ui/ServerUI.class" ]; then
    echo "[THONG BAO] Chua bien dich, dang goi compile.sh..."
    ./compile.sh
fi

echo "Dang mo Bang dieu khien Mail Server..."
java --module-path "$FX_LIB" --add-modules javafx.controls,javafx.fxml -cp "$CP" server.ui.ServerUI "$@" &

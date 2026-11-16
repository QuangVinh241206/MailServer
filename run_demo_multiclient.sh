#!/bin/bash
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
cd "$DIR"

echo "========================================================"
echo "  DEMO HE THONG: 1 MAIL SERVER + 2 MAIL CLIENT DONG THOI"
echo "========================================================"

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

echo "[1/3] Dang khoi dong Mail Server (ports 2525, 1110)..."
java --module-path "$FX_LIB" --add-modules javafx.controls,javafx.fxml -cp "$CP" server.ui.ServerUI &

sleep 2

echo "[2/3] Dang mo Client 1 (vi du dang nhap: user1@localhost)..."
java --module-path "$FX_LIB" --add-modules javafx.controls,javafx.fxml -cp "$CP" ui.MailClientUI &

sleep 1

echo "[3/3] Dang mo Client 2 (vi du dang nhap: user2@localhost)..."
java --module-path "$FX_LIB" --add-modules javafx.controls,javafx.fxml -cp "$CP" ui.MailClientUI &

echo ""
echo "========================================================"
echo "  DA KHOI DONG THANH CONG!"
echo "  - Cua so Server dang lang nghe tai port 2525 va 1110"
echo "  - Cua so Client 1: Dang nhap user1@localhost (pass 123456)"
echo "  - Cua so Client 2: Dang nhap user2@localhost (pass 123456)"
echo "  Thu soan thu tu Client 1 gui sang Client 2 va nguoc lai!"
echo "========================================================"

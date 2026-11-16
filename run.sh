#!/bin/bash
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
cd "$DIR"

clear 2>/dev/null || true
echo "========================================================"
echo "       HE THONG MAIL SERVER VA MAIL CLIENT (JAVAFX)"
echo "========================================================"
echo ""
echo "  [1] Khoi chay Mail Server (Bang dieu khien Server)"
echo "  [2] Khoi chay Mail Client (Mo 1 cua so Client)"
echo "  [3] Chay Demo Da Client (1 Server + 2 Client dong thoi)"
echo "  [4] Xem co so du lieu Database (mailserver.db)"
echo "  [5] Chay kiem tra tu dong (Test suite)"
echo "  [6] Bien dich lai ma nguon (Compile)"
echo "  [7] Ket noi toi May chu tu xa (Demo tren may thu 2 qua LAN/Wi-Fi)"
echo "  [0] Thoat"
echo ""
echo "========================================================"
read -p "Nhap lua chon cua ban [0-7]: " opt

case "$opt" in
    1) ./run_server.sh ;;
    2) ./run_client.sh ;;
    3) ./run_demo_multiclient.sh ;;
    4) ./view_db.sh ;;
    5) ./test.sh ;;
    6) ./compile.sh ;;
    7) 
        echo ""
        echo "========================================================"
        echo "  DEMO MAY THU 2: KET NOI TOI MAY CHU MAIL SERVER"
        echo "========================================================"
        read -p "Nhap dia chi IP cua May chu (vi du: 192.168.1.88): " srvip
        ./run_client.sh "$srvip"
        ;;
    0) exit 0 ;;
    *) echo "Lua chon khong hop le!" ;;
esac

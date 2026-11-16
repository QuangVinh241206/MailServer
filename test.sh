#!/bin/bash
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
cd "$DIR"

echo "==================================================="
echo "  CHAY KIEM TRA TU DONG SMTP + POP3 + DATABASE"
echo "==================================================="

CP="bin:lib/sqlite-jdbc.jar:lib/slf4j-api.jar:lib/slf4j-simple.jar"

if [ ! -f "bin/test/IntegrationTest.class" ]; then
    echo "[THONG BAO] Chua bien dich, dang goi compile.sh..."
    ./compile.sh
fi

java -cp "$CP" test.IntegrationTest

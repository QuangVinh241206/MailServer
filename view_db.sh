#!/bin/bash
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
cd "$DIR"

CP="bin:lib/sqlite-jdbc.jar:lib/slf4j-api.jar:lib/slf4j-simple.jar"

if [ ! -f "bin/tools/ViewDatabase.class" ]; then
    echo "[THONG BAO] Dang bien dich cong cu ViewDatabase..."
    ./compile.sh
fi

java -cp "$CP" tools.ViewDatabase

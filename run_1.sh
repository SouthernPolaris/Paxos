#!/bin/bash
# scenario1-mvn.sh - Start all 9 council members for Scenario 1 (ideal network) using Maven

# Kill all Maven exec:java launches for your project
pkill -f 'org.codehaus.plexus.classworlds.launcher.ExecJavaMojo'
sleep 3

for port in {9001..9009}; do
    pid=$(lsof -t -i:$port)
    if [ -n "$pid" ]; then
        kill -9 $pid
    fi
done

LOG_DIR="./logs/scenario1"
mkdir -p "$LOG_DIR"

MEMBERS=(M1 M2 M3 M4 M5 M6 M7 M8 M9)

for MEMBER in "${MEMBERS[@]}"; do
    if [ "$MEMBER" == "M5" ]; then
        # M5 auto-proposes "M5"
        mvn exec:java -q -Dexec.mainClass="member.CouncilMember" -Dexec.args="$MEMBER --propose M5" \
            > "$LOG_DIR/$MEMBER.log" 2>&1 &
    else
        mvn exec:java -q -Dexec.mainClass="member.CouncilMember" -Dexec.args="$MEMBER" \
            > "$LOG_DIR/$MEMBER.log" 2>&1 &
    fi
done

echo "All members started for Scenario 1. Logs in $LOG_DIR"

# Wait for consensus (adjust time as needed)
sleep 20

echo "Scenario 1 execution complete."

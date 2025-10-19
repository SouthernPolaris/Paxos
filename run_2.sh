#!/bin/bash
# scenario2-mvn-fixed.sh - Start all 9 council members for Scenario 2 (concurrent proposals) using Maven

echo "Killing all running CouncilMember processes..."
# Kill any java processes running your CouncilMember
pkill -f 'member.CouncilMember' 2>/dev/null || true

# Give OS a moment to release ports
sleep 2

# Force kill any process still holding ports 9001-9009
for port in {9001..9009}; do
    pid=$(lsof -ti tcp:$port)
    if [ -n "$pid" ]; then
        echo "Killing process $pid holding port $port"
        kill -9 $pid
    fi
done

LOG_DIR="./logs/scenario2"
mkdir -p "$LOG_DIR"

MEMBERS=(M1 M2 M3 M4 M5 M6 M7 M8 M9)

# We'll auto-propose from M1 and M8 at startup rather than launching separate JVMs later
PROPOSERS=(M1 M8)

# 1. Start all members (auto-propose for M1 and M8)
for MEMBER in "${MEMBERS[@]}"; do
    args="$MEMBER"
    for P in "${PROPOSERS[@]}"; do
        if [ "$P" = "$MEMBER" ]; then
            # member will auto-propose its own id
            args="$args --propose ${MEMBER}"
            break
        fi
    done

    mvn -q exec:java -Dexec.mainClass="member.CouncilMember" \
        -Dexec.args="$args" \
        > "$LOG_DIR/$MEMBER.log" 2>&1 &
done

echo "All members started (M1 and M8 will auto-propose). Waiting 3s for them to initialize..."
sleep 3

# Give the proposers a bit of time; they will run concurrently because both were started above
echo "Proposals should be triggered by M1 and M8 automatically. Waiting up to 30s for consensus..."
sleep 30

echo "Scenario 2 execution complete. Logs in $LOG_DIR"

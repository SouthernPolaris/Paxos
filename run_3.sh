#!/bin/bash
set -u -o pipefail
# Robust run_3.sh for Scenario 3 (fault-tolerance)
# - Starts all members using Maven exec:java
# - Subtests:
#    3a: M4 (standard) proposes M5
#    3b: M2 (latent) proposes M6
#    3c: M3 (failing) proposes then crashes, M4 drives M7

LOG_DIR="./logs/scenario3"
mkdir -p "$LOG_DIR"

MEMBERS=(M1 M2 M3 M4 M5 M6 M7 M8 M9)
PORTS=(9001 9002 9003 9004 9005 9006 9007 9008 9009)

# ---------- Cleanup ----------
cleanup() {
    echo "[cleanup] Killing all running CouncilMember processes..."
    pkill -f 'member.CouncilMember' 2>/dev/null || true
    sleep 2
    for port in {9001..9009}; do
        pid=$(lsof -ti tcp:$port || true)
        if [ -n "$pid" ]; then
            echo "[cleanup] Killing process $pid holding port $port"
            kill -9 $pid || true
        fi
    done
}
trap cleanup EXIT

# ---------- Start member ----------
start_member() {
    local MEMBER=$1
    local PROFILE=$2
    local EXTRA_ARGS=${3:-}
    echo "[start] $MEMBER profile=$PROFILE extra='$EXTRA_ARGS'"
    mvn -q exec:java -Dexec.mainClass="member.CouncilMember" \
        -Dexec.args="$MEMBER --profile $PROFILE $EXTRA_ARGS" \
        > "$LOG_DIR/$MEMBER.log" 2>&1 &
}

# ---------- Wait for consensus ----------
wait_for_consensus() {
    local VALUE=$1
    local TIMEOUT=${2:-40}
    local START=$(date +%s)
    echo "[wait] Waiting for consensus on $VALUE..."
    while true; do
        if grep -R "has learned the value: $VALUE" "$LOG_DIR" >/dev/null 2>&1 || true; then
            if grep -R "has learned the value: $VALUE" "$LOG_DIR" >/dev/null 2>&1; then
                echo "[wait] Consensus on $VALUE observed!"
                return 0
            fi
        fi
        sleep 1
        NOW=$(date +%s)
        if (( NOW - START > TIMEOUT )); then
            echo "[wait] Timeout waiting for consensus on $VALUE"
            return 1
        fi
    done
}

# ---------- Subtest 3a ----------
cleanup
echo "[3a] Starting all members; M4 will auto-propose M5"
for i in "${!MEMBERS[@]}"; do
    M=${MEMBERS[$i]}
    PROFILE=STANDARD
    [[ "$M" == "M1" ]] && PROFILE=RELIABLE
    [[ "$M" == "M2" ]] && PROFILE=LATENT
    [[ "$M" == "M3" ]] && PROFILE=FAILURE
    EXTRA=""
    [[ "$M" == "M4" ]] && EXTRA="--propose M5"
    start_member "$M" "$PROFILE" "$EXTRA"
done

# Give all members a moment to bind ports
sleep 5
wait_for_consensus M5 40 || echo "[3a] FAIL"
cleanup

# ---------- Subtest 3b ----------
cleanup
echo "[3b] Starting all members; M2 (latent) will auto-propose M6"
for i in "${!MEMBERS[@]}"; do
    M=${MEMBERS[$i]}
    PROFILE=STANDARD
    [[ "$M" == "M1" ]] && PROFILE=RELIABLE
    [[ "$M" == "M2" ]] && PROFILE=LATENT
    [[ "$M" == "M3" ]] && PROFILE=FAILURE
    EXTRA=""
    [[ "$M" == "M2" ]] && EXTRA="--propose M6"
    start_member "$M" "$PROFILE" "$EXTRA"
done

# Latent member may be slow
sleep 7
wait_for_consensus M6 60 || echo "[3b] FAIL"
cleanup


# ---------- Subtest 3c ----------
cleanup
echo "[3c] Running staged start: baseline members, then M3 (crash), then M4 drives M7"

# Start baseline members except M3 and M4
for i in "${!MEMBERS[@]}"; do
    M=${MEMBERS[$i]}
    if [[ "$M" == "M3" || "$M" == "M4" ]]; then
        continue
    fi
    PROFILE=STANDARD
    [[ "$M" == "M1" ]] && PROFILE=RELIABLE
    [[ "$M" == "M2" ]] && PROFILE=LATENT
    start_member "$M" "$PROFILE"
done

# Start M3 (failing) with auto-propose+crash
start_member "M3" FAILURE "--propose M3 --crashAfterSend"

# Wait until M3 actually exits (max 20s)
M3_PID=$(pgrep -f "member.CouncilMember.*M3")
echo "[3c] Waiting for M3 to exit after crash..."
START=$(date +%s)
while kill -0 "$M3_PID" >/dev/null 2>&1; do
    sleep 0.2
    NOW=$(date +%s)
    if (( NOW - START > 20 )); then
        echo "[3c] warning: M3 still running after 20s; proceeding to start M4 anyway"
        break
    fi
done

# Now start M4 to drive consensus
start_member "M4" STANDARD "--propose M7"


# Give system time and wait for consensus
sleep 1
wait_for_consensus M7 60 || echo "[3c] FAIL"
cleanup

echo "[done] Scenario 3 completed. Logs in $LOG_DIR"

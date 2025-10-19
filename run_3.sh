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
echo "[3c] Starting all members; M3 failing will crash, M4 drives M7"
for i in "${!MEMBERS[@]}"; do
    M=${MEMBERS[$i]}
    PROFILE=STANDARD
    [[ "$M" == "M1" ]] && PROFILE=RELIABLE
    [[ "$M" == "M2" ]] && PROFILE=LATENT
    [[ "$M" == "M3" ]] && PROFILE=FAILURE
    EXTRA=""
    [[ "$M" == "M3" ]] && EXTRA="--propose M3 --crashAfterSend"
    [[ "$M" == "M4" ]] && EXTRA="--propose M7"
    start_member "$M" "$PROFILE" "$EXTRA"
done

# Give system time to handle M3 crash and M4 proposal
sleep 8
wait_for_consensus M7 60 || echo "[3c] FAIL"
cleanup

echo "[done] Scenario 3 completed. Logs in $LOG_DIR"

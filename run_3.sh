#!/bin/bash
set -euo pipefail

LOG_DIR="./logs/scenario3"
CONFIG_DIR="./conf"
CONFIG_FILE="$CONFIG_DIR/network.config"

mkdir -p "$LOG_DIR"
mkdir -p "$CONFIG_DIR"

MEMBERS=(M1 M2 M3 M4 M5 M6 M7 M8 M9)

# associative array to track FD numbers for each member's opened FIFO
declare -A FIFO_FDS

cleanup() {
    echo "[cleanup] Killing all CouncilMember processes..."
    pkill -f 'member.CouncilMember' 2>/dev/null || true
    sleep 1

    for port in {9001..9009}; do
        pid=$(lsof -ti tcp:$port || true)
        if [ -n "$pid" ]; then
            echo "[cleanup] Killing process $pid holding port $port"
            kill -9 "$pid" || true
        fi
    done

    rm -f "$LOG_DIR"/*.pid

    # Close and remove FIFOs and close stored FDs
    for MEMBER in "${MEMBERS[@]}"; do
        fifo="$LOG_DIR/$MEMBER.in"
        # close FD if we opened it
        if [ -n "${FIFO_FDS[$MEMBER]:-}" ]; then
            FD=${FIFO_FDS[$MEMBER]}
            # Close FD in this shell if still open
            if eval "true 1>&$FD" 2>/dev/null; then
                eval "exec ${FD}>&- || true"
            fi
            unset FIFO_FDS[$MEMBER]
        fi
        if [ -p "$fifo" ]; then
            rm -f "$fifo"
        fi
    done
}
trap cleanup EXIT

write_config() {
    cat > "$CONFIG_FILE" <<EOF
M1,localhost,9001,RELIABLE
M2,localhost,9002,LATENT
M3,localhost,9003,FAILURE
M4,localhost,9004,STANDARD
M5,localhost,9005,STANDARD
M6,localhost,9006,STANDARD
M7,localhost,9007,STANDARD
M8,localhost,9008,STANDARD
M9,localhost,9009,STANDARD
EOF
}

# create per-member input FIFOs if missing
create_fifos() {
    for MEMBER in "${MEMBERS[@]}"; do
        fifo="$LOG_DIR/$MEMBER.in"
        if [ ! -p "$fifo" ]; then
            rm -f "$fifo"
            mkfifo "$fifo"
        fi
    done
}

# Start a member but avoid blocking by opening the fifo RDWR first and duplicating it as stdin
start_member() {
    local MEMBER=$1
    local PROFILE=$2
    local EXTRA_ARGS=${3:-}
    echo "[start] $MEMBER profile=$PROFILE extra='$EXTRA_ARGS'"

    fifo="$LOG_DIR/$MEMBER.in"
    if [ ! -p "$fifo" ]; then
        mkfifo "$fifo"
    fi

    # Ensure the log file exists before starting (prevents grep 'No such file' errors)
    logfile="$LOG_DIR/$MEMBER.log"
    : > "$logfile"

    # Open the FIFO in read-write mode and store the FD number in FIFO_FDS
    # Using bash's "exec {var}<> file" allocates a free FD and opens RDWR (doesn't block).
    exec {FD}<> "$fifo"
    FIFO_FDS[$MEMBER]=$FD

    # Start the Java process with the fifo FD as its stdin so it will not block on start.
    # We redirect stdin from the FD we've opened and let the process inherit that FD.
    # Note: use /proc/self/fd/$FD so redirection works correctly in subshells/background.
    mvn -q exec:java -Dexec.mainClass="member.CouncilMember" \
        -Dexec.args="$MEMBER $EXTRA_ARGS" \
        < /proc/$$/fd/$FD > "$logfile" 2>&1 &
    echo $! > "$LOG_DIR/$MEMBER.pid"
}

wait_for_listening() {
    for MEMBER in "${MEMBERS[@]}"; do
        logfile="$LOG_DIR/$MEMBER.log"
        local start=$(date +%s)
        while true; do
            if [ -f "$logfile" ] && grep -q "Listening on port" "$logfile" 2>/dev/null; then
                break
            fi
            sleep 0.2
            if (( $(date +%s) - start > 15 )); then
                echo "[warn] $MEMBER did not report listening in 15s"
                break
            fi
        done
    done
    sleep 1
}

# REPLACED wait_for_consensus: robust per-file detection; prints matches and files that matched.
wait_for_consensus() {
    local VALUE=$1
    local TIMEOUT=${2:-40}
    local start=$(date +%s)
    echo "[wait] Waiting for consensus on $VALUE..."
    while true; do
        # For each log file, check if that file contains evidence of learning VALUE.
        # We consider a file "showed consensus" if:
        #  - any line in the file contains both a 'learn' token and VALUE, OR
        #  - the file contains the word VALUE and also contains 'learn' somewhere in the file.
        matched_any=0
        matches_output=""
        for f in "$LOG_DIR"/*.log; do
            [ -f "$f" ] || continue
            # First check for a single-line match (learn + value)
            line_match=$(grep -iE "learn.*$VALUE|$VALUE.*learn" "$f" 2>/dev/null || true)
            if [ -n "$line_match" ]; then
                matched_any=1
                matches_output+="$f: $line_match"$'\n'
                # We can break early, but continue to collect more matches for diagnostics
                continue
            fi
            # Otherwise, check if file has both tokens somewhere
            if grep -qi "$VALUE" "$f" 2>/dev/null && grep -qi "learn" "$f" 2>/dev/null; then
                matched_any=1
                # collect the lines that contain either token for context
                context=$(grep -niE "learn|$VALUE" "$f" 2>/dev/null || true)
                matches_output+="$f: (context lines)\n$context\n"
            fi
        done

        if (( matched_any == 1 )); then
            echo "[wait] Consensus on $VALUE observed! Matching lines/files:"
            # print matches_output safely
            echo "$matches_output"
            return 0
        fi

        sleep 1
        if (( $(date +%s) - start > TIMEOUT )); then
            echo "[wait] Timeout waiting for consensus on $VALUE"
            echo "[debug] Dumping last 200 lines of each log to help diagnose:"
            for f in "$LOG_DIR"/*.log; do
                echo "----- $f -----"
                tail -n 200 "$f" || true
            done
            return 1
        fi
    done
}

wait_for_specific_members() {
    local members=("$@")
    for MEMBER in "${members[@]}"; do
        logfile="$LOG_DIR/$MEMBER.log"
        local start=$(date +%s)
        while true; do
            if [ -f "$logfile" ] && grep -q "Listening on port" "$logfile" 2>/dev/null; then
                break
            fi
            sleep 0.2
            if (( $(date +%s) - start > 15 )); then
                echo "[warn] $MEMBER did not report listening in 15s"
                break
            fi
        done
    done
    sleep 1
}

send_proposal() {
    local MEMBER=$1
    local VALUE=$2
    fifo="$LOG_DIR/$MEMBER.in"
    if [ ! -p "$fifo" ]; then
        echo "[proposal] ERROR: FIFO $fifo not found for $MEMBER"
        return 1
    fi
    echo "[proposal] Sending '$VALUE' to $MEMBER via $fifo..."
    # write to FIFO (this will succeed because we have an RDWR open on the fifo)
    printf "%s\n" "$VALUE" > "$fifo" &
    sleep 0.6
}

# Build project
echo "[build] Compiling project..."
mvn -q -DskipTests=true compile

write_config

# Ensure FIFOs exist
create_fifos

# ------------------ Scenario 3a ------------------
echo -e "\n=== Scenario 3a: Standard member (M4) proposes M5 ==="
cleanup
rm -f "$LOG_DIR"/*
create_fifos

for i in "${!MEMBERS[@]}"; do
    MEMBER=${MEMBERS[$i]}
    PROFILE="STANDARD"
    [[ "$MEMBER" == "M1" ]] && PROFILE="RELIABLE"
    [[ "$MEMBER" == "M2" ]] && PROFILE="LATENT"
    [[ "$MEMBER" == "M3" ]] && PROFILE="FAILURE"
    start_member "$MEMBER" "$PROFILE"
done

wait_for_listening
# propose from M4 using stdin FIFO
send_proposal "M4" "M5"
wait_for_consensus "M5" 40
cleanup

# ------------------ Scenario 3b ------------------
echo -e "\n=== Scenario 3b: Latent member (M2) proposes M6 ==="
cleanup
rm -f "$LOG_DIR"/*
create_fifos

for i in "${!MEMBERS[@]}"; do
    MEMBER=${MEMBERS[$i]}
    PROFILE="STANDARD"
    [[ "$MEMBER" == "M1" ]] && PROFILE="RELIABLE"
    [[ "$MEMBER" == "M2" ]] && PROFILE="LATENT"
    [[ "$MEMBER" == "M3" ]] && PROFILE="FAILURE"
    start_member "$MEMBER" "$PROFILE"
done

wait_for_listening
sleep 5   # latent member may be slow to start
# propose from M2 using stdin FIFO
send_proposal "M2" "M6"
wait_for_consensus "M6" 60
cleanup

# ------------------ Scenario 3c ------------------
echo -e "\n=== Scenario 3c: Failing member (M3) proposes then crashes, M4 drives M7 ==="
cleanup
rm -f "$LOG_DIR"/*
create_fifos

# Start all members (M3 gets crashAfterSend flag)
for i in "${!MEMBERS[@]}"; do
    MEMBER=${MEMBERS[$i]}
    PROFILE="STANDARD"
    [[ "$MEMBER" == "M1" ]] && PROFILE="RELIABLE"
    [[ "$MEMBER" == "M2" ]] && PROFILE="LATENT"
    [[ "$MEMBER" == "M3" ]] && PROFILE="FAILURE"
    EXTRA=""
    if [[ "$MEMBER" == "M3" ]]; then
        EXTRA="--crashAfterSend"
    fi
    start_member "$MEMBER" "$PROFILE" "$EXTRA"
done

wait_for_listening

# Immediately propose from M3 (writes to M3's stdin fifo) and let M3 crash after send
send_proposal "M3" "M3"

# Wait until M3 crashes
echo "[3c] Waiting for M3 to crash..."
timeout=20
start_time=$(date +%s)
while true; do
    if [ ! -f "$LOG_DIR/M3.pid" ]; then
        echo "[3c] No pid file for M3; assuming it's down."
        break
    fi
    pid=$(cat "$LOG_DIR/M3.pid")
    if ! kill -0 "$pid" 2>/dev/null; then
        echo "[3c] M3 process $pid no longer running."
        break
    fi
    if (( $(date +%s) - start_time > timeout )); then
        echo "[3c] M3 did not crash in ${timeout}s, force killing..."
        kill -9 "$pid" 2>/dev/null || true
        break
    fi
    sleep 0.5
done
sleep 1

# Propose from M4 now to drive consensus to M7
send_proposal "M4" "M7"

wait_for_consensus "M7" 60
cleanup

echo "[done] All Scenario 3 tests completed. Logs: $LOG_DIR"
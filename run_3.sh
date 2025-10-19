#!/bin/bash
set -euo pipefail

# TODO: Change from JSON to just the value string as proposal input

LOG_ROOT="./logs/scenario3"
FIFOS_ROOT="./fifos"
CONFIG_DIR="./conf"
CONFIG_FILE="$CONFIG_DIR/network.config"

mkdir -p "$LOG_ROOT"
mkdir -p "$FIFOS_ROOT"
mkdir -p "$CONFIG_DIR"

MEMBERS=(M1 M2 M3 M4 M5 M6 M7 M8 M9)

# track allocated FD for each member per test run
declare -A FIFO_FDS

cleanup_procs() {
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
}

cleanup_fifos() {
    # Close any opened FDs and remove fifos
    for key in "${!FIFO_FDS[@]}"; do
        FD=${FIFO_FDS[$key]}
        # Close FD if still open
        if eval "true 1>&$FD" 2>/dev/null; then
            eval "exec ${FD}>&- || true"
        fi
    done
    FIFO_FDS=()

    # remove the fifos root completely
    if [ -d "$FIFOS_ROOT" ]; then
        rm -rf "$FIFOS_ROOT"
    fi
}

cleanup() {
    cleanup_procs
    cleanup_fifos
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

# create FIFO directory for subtest and per-member fifos (outside log dirs)
create_fifos_for_subtest() {
    local SUB=$1
    local DIR="$FIFOS_ROOT/$SUB"
    mkdir -p "$DIR"
    for MEMBER in "${MEMBERS[@]}"; do
        fifo="$DIR/$MEMBER.in"
        if [ ! -p "$fifo" ]; then
            rm -f "$fifo"
            mkfifo "$fifo"
        fi
    done
}

# Start a member using fifo as stdin (fifos located outside log dirs).
# We open the fifo RDWR in this shell to avoid blocking on open.
start_member() {
    local MEMBER=$1
    local PROFILE=$2
    local EXTRA_ARGS=${3:-}
    local TEST_DIR=$4
    local SUB=$5

    echo "[start] $MEMBER profile=$PROFILE extra='$EXTRA_ARGS' (logs -> $TEST_DIR/$MEMBER.log)"

    mkdir -p "$TEST_DIR"
    logfile="$TEST_DIR/$MEMBER.log"
    : > "$logfile"

    fifo="$FIFOS_ROOT/$SUB/$MEMBER.in"
    if [ ! -p "$fifo" ]; then
        echo "[start] FIFO $fifo missing; creating."
        mkdir -p "$(dirname "$fifo")"
        mkfifo "$fifo"
    fi

    # Open the FIFO in read-write mode and store FD in FIFO_FDS with a unique key
    exec {FD}<> "$fifo"
    FIFO_FDS["$SUB:$MEMBER"]=$FD

    # Start the JVM with stdin from the FD we opened (/proc/$$/fd/$FD ensures background inherits it)
    mvn -q exec:java -Dexec.mainClass="member.CouncilMember" \
        -Dexec.args="$MEMBER --profile $PROFILE $EXTRA_ARGS" \
        < /proc/$$/fd/$FD > "$logfile" 2>&1 &
    # allow a short moment for process to start and open its end
    sleep 0.05
}

wait_for_listening() {
    local TEST_DIR=$1
    for MEMBER in "${MEMBERS[@]}"; do
        logfile="$TEST_DIR/$MEMBER.log"
        local start=$(date +%s)
        while true; do
            if [ -f "$logfile" ] && grep -q "Listening on port" "$logfile" 2>/dev/null; then
                break
            fi
            sleep 0.2
            if (( $(date +%s) - start > 20 )); then
                echo "[warn] $MEMBER did not report listening in 20s (check $logfile)"
                break
            fi
        done
    done
    sleep 0.5
}

# send proposal as JSON into the member's stdin fifo (not to the network)
send_proposal_via_fifo() {
    local SUB=$1
    local MEMBER=$2
    local VALUE=$3
    fifo="$FIFOS_ROOT/$SUB/$MEMBER.in"
    if [ ! -p "$fifo" ]; then
        echo "[proposal] ERROR: FIFO $fifo not found for $MEMBER"
        return 1
    fi

    # JSON proposal (type present as requested). This JSON will be delivered to the member's stdin
    # and CouncilMember's stdin reader will propose the entire JSON string as the value.
    local json
    json="{\"type\":\"PROPOSE\",\"value\":\"$VALUE\",\"from\":\"$MEMBER\"}"

    echo "[proposal] Sending JSON into stdin FIFO for $MEMBER: $json"
    # write the json as a single line into the fifo
    printf '%s\n' "$json" > "$fifo" &
    # give the member a short moment to process
    sleep 0.6
}

# Extract learned value (same extractor as before)
extract_learned_value() {
    local f=$1
    # Get the last line that mentions learning
    local line
    line=$(grep -i "learn" "$f" 2>/dev/null | tail -n1 || true)
    if [ -z "$line" ]; then
        echo ""
        return
    fi

    # 1) Try to extract a JSON object substring {...}
    local json
    json=$(echo "$line" | sed -n 's/.*\({.*}\).*/\1/p' || true)
    if [ -n "$json" ]; then
        # Try using python3 to parse the JSON and extract the "value" field
        if command -v python3 >/dev/null 2>&1; then
            # echo JSON to python and try to read the "value" key
            val=$(printf '%s' "$json" | python3 -c 'import sys, json
s=sys.stdin.read()
try:
    o=json.loads(s)
    v=o.get("value", "")
    if isinstance(v, str):
        print(v)
    else:
        # if value is not a string, print JSON-encoded value
        print(json.dumps(v))
except Exception:
    sys.exit(0)
' 2>/dev/null || true)
            if [ -n "$val" ]; then
                echo "$val"
                return
            fi
        fi

        # If python3 is not available or parsing failed, try a simple sed/awk fallback for "value":"X"
        val=$(echo "$json" | sed -n 's/.*"value"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' || true)
        if [ -n "$val" ]; then
            echo "$val"
            return
        fi

        # If no inner "value" field found, return the entire JSON string as a canonical fallback
        echo "$json"
        return
    fi

    # 2) If not JSON, attempt to find token after "value:" in the log line (legacy)
    val=$(echo "$line" | awk '{
        for(i=1;i<=NF;i++){
            token=tolower($i)
            if(token ~ /^value:?$/){
                if(i+1<=NF){ print $(i+1); exit }
            }
        }
    }')
    if [ -n "$val" ]; then
        echo "$val" | sed 's/[^[:alnum:]_.-].*$//'
        return
    fi

    # 3) Fallback: return last whitespace token
    val=$(echo "$line" | awk '{print $NF}')
    echo "$val" | sed 's/[^[:alnum:]_.-].*$//'
}

wait_for_all_learners_consensus() {
    local TEST_DIR=$1
    local TIMEOUT=${2:-60}
    local start=$(date +%s)
    echo "[wait-all] Waiting for all learners in $TEST_DIR to learn the same value..."

    while true; do
        declare -A seen
        all_have=1
        values_list=""

        for MEMBER in "${MEMBERS[@]}"; do
            logfile="$TEST_DIR/$MEMBER.log"
            if [ ! -f "$logfile" ]; then
                all_have=0
                break
            fi
            if ! grep -qi "learn" "$logfile" 2>/dev/null; then
                all_have=0
                break
            fi
            val=$(extract_learned_value "$logfile")
            if [ -z "$val" ]; then
                all_have=0
                break
            fi
            seen["$val"]=1
            values_list+="$MEMBER:$val "
        done

        if (( all_have == 1 )); then
            unique_count=0
            last_val=""
            for k in "${!seen[@]}"; do
                unique_count=$((unique_count+1))
                last_val="$k"
            done

            if (( unique_count == 1 )); then
                echo "[wait-all] SUCCESS: all members learned value '$last_val'. Details: $values_list"
                return 0
            else
                echo "[wait-all] Conflict: members have different learned values: $values_list"
            fi
        fi

        sleep 1
        if (( $(date +%s) - start > TIMEOUT )); then
            echo "[wait-all] TIMEOUT after ${TIMEOUT}s waiting for all learners to agree in $TEST_DIR"
            echo "[debug] Per-member learning lines (last 100 lines of each log):"
            for f in "$TEST_DIR"/*.log; do
                echo "----- $f -----"
                tail -n 100 "$f" || true
            done
            return 1
        fi
    done
}

wait_for_subset_learners_consensus() {
    local TEST_DIR=$1
    local TIMEOUT=${2:-60}
    shift 2
    local members_to_check=("$@")

    local start=$(date +%s)
    echo "[wait-subset] Waiting for members (${members_to_check[*]}) in $TEST_DIR to learn the same value..."

    while true; do
        declare -A seen
        all_have=1
        values_list=""

        for MEMBER in "${members_to_check[@]}"; do
            logfile="$TEST_DIR/$MEMBER.log"
            if [ ! -f "$logfile" ]; then
                all_have=0
                break
            fi
            if ! grep -qi "learn" "$logfile" 2>/dev/null; then
                all_have=0
                break
            fi
            val=$(extract_learned_value "$logfile")
            if [ -z "$val" ]; then
                all_have=0
                break
            fi
            seen["$val"]=1
            values_list+="$MEMBER:$val "
        done

        if (( all_have == 1 )); then
            unique_count=0
            last_val=""
            for k in "${!seen[@]}"; do
                unique_count=$((unique_count+1))
                last_val="$k"
            done

            if (( unique_count == 1 )); then
                echo "[wait-subset] SUCCESS: subset members learned value '$last_val'. Details: $values_list"
                return 0
            else
                echo "[wait-subset] Conflict among subset: $values_list"
                # keep waiting up to timeout in case of late convergence
            fi
        fi

        sleep 1
        if (( $(date +%s) - start > TIMEOUT )); then
            echo "[wait-subset] TIMEOUT after ${TIMEOUT}s waiting for subset to agree in $TEST_DIR"
            echo "[debug] Per-member learning lines (last 100 lines of each subset log):"
            for MEMBER in "${members_to_check[@]}"; do
                f="$TEST_DIR/$MEMBER.log"
                echo "----- $f -----"
                tail -n 100 "$f" || true
            done
            return 1
        fi
    done
}

# Build project
echo "[build] Compiling project..."
mvn -q -DskipTests=true compile

write_config

# Run single subtest (a, b, or c)
run_subtest() {
    local SUB=$1
    local TEST_DIR="$LOG_ROOT/$SUB"

    echo -e "\n=== Scenario 3$SUB ==="
    cleanup_procs
    # prepare clean test dir (only .log files will be created here)
    mkdir -p "$TEST_DIR"
    rm -f "$TEST_DIR"/*.log

    # prepare fifos for this subtest (placed outside log dir)
    create_fifos_for_subtest "$SUB"

    if [ "$SUB" = "a" ]; then
        echo "[3a] Standard member (M4) proposes M5"
        for i in "${!MEMBERS[@]}"; do
            MEMBER=${MEMBERS[$i]}
            PROFILE="STANDARD"
            [[ "$MEMBER" == "M1" ]] && PROFILE="RELIABLE"
            [[ "$MEMBER" == "M2" ]] && PROFILE="LATENT"
            [[ "$MEMBER" == "M3" ]] && PROFILE="FAILURE"
            EXTRA=""
            start_member "$MEMBER" "$PROFILE" "$EXTRA" "$TEST_DIR" "$SUB"
        done

        wait_for_listening "$TEST_DIR"
        # send JSON proposal into M4's stdin fifo
        send_proposal_via_fifo "$SUB" "M4" "M5"
        if wait_for_all_learners_consensus "$TEST_DIR" 40; then
            echo "[3a] PASS"
        else
            echo "[3a] FAIL"
        fi

    elif [ "$SUB" = "b" ]; then
        echo "[3b] Latent member (M2) proposes M6"
        for i in "${!MEMBERS[@]}"; do
            MEMBER=${MEMBERS[$i]}
            PROFILE="STANDARD"
            [[ "$MEMBER" == "M1" ]] && PROFILE="RELIABLE"
            [[ "$MEMBER" == "M2" ]] && PROFILE="LATENT"
            [[ "$MEMBER" == "M3" ]] && PROFILE="FAILURE"
            EXTRA=""
            start_member "$MEMBER" "$PROFILE" "$EXTRA" "$TEST_DIR" "$SUB"
        done

        wait_for_listening "$TEST_DIR"
        sleep 5
        send_proposal_via_fifo "$SUB" "M2" "M6"
        if wait_for_all_learners_consensus "$TEST_DIR" 60; then
            echo "[3b] PASS"
        else
            echo "[3b] FAIL"
        fi

    else
        echo "[3c] Failing member (M3) proposes then crashes, M4 drives M7 (M3 will be excluded from final check)"
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
            start_member "$MEMBER" "$PROFILE" "$EXTRA" "$TEST_DIR" "$SUB"
        done

        wait_for_listening "$TEST_DIR"

        # Step 1: M3 proposes and is expected to crash
        send_proposal_via_fifo "$SUB" "M3" "M3"

        # Step 2: wait for M3 to stop listening (port closure) or timeout and kill if necessary
        m3_port=$(grep "^M3," "$CONFIG_FILE" | cut -d',' -f3)
        echo "[3c] Waiting up to 20s for M3 (port $m3_port) to stop..."
        timeout=20
        start_time=$(date +%s)

        m3_port=$(grep "^M3," "$CONFIG_FILE" | cut -d',' -f3)
        echo "[3c] Waiting up to ${timeout}s for M3 (port $m3_port) to stop or log shutdown..."
        start_time=$(date +%s)
        stopped=0
        while true; do
            pid=$(lsof -ti tcp:"$m3_port" || true)

            # check M3 log for explicit shutdown message
            shutdown_logged=0
            if [ -f "$TEST_DIR/M3.log" ]; then
                if grep -qi "Shutting down" "$TEST_DIR/M3.log" 2>/dev/null; then
                    shutdown_logged=1
                fi
            fi

            if [ -z "$pid" ] || [ "$shutdown_logged" -eq 1 ]; then
                if [ "$shutdown_logged" -eq 1 ]; then
                    echo "[3c] M3 log indicates shutdown."
                else
                    echo "[3c] M3 port $m3_port is no longer open (no process listening)."
                fi
                stopped=1
                break
            fi

            if (( $(date +%s) - start_time > timeout )); then
                echo "[3c] M3 did not report shutdown within ${timeout}s; proceeding without killing."
                stopped=0
                break
            fi

            sleep 0.5
        done

        if [ "$stopped" -eq 1 ]; then
            echo "[3c] Confirmed M3 has stopped."
        else
            echo "[3c] M3 still appears running (or did not log shutdown) after ${timeout}s."
        fi

        sleep 1

        # Step 3: M4 drives recovery
        send_proposal_via_fifo "$SUB" "M4" "M7"

        # Step 4: Wait for survivors (exclude M3) to reach consensus
        SUBSET_MEMBERS=(M1 M2 M4 M5 M6 M7 M8 M9)
        if wait_for_subset_learners_consensus "$TEST_DIR" 60 "${SUBSET_MEMBERS[@]}"; then
            echo "[3c] PASS (consensus reached among survivors, M3 excluded)"
        else
            echo "[3c] FAIL (survivors did not reach consensus in time)"
        fi
    fi

    # stop processes and remove fifos for this subtest
    cleanup_procs
    cleanup_fifos
}

run_subtest "a"
run_subtest "b"
run_subtest "c"

echo "[done] Scenario 3 subtests completed. Logs in $LOG_ROOT (subdirs a, b, c)"
#!/bin/bash
set -euo pipefail

LOG_ROOT="./logs/scenario3"
FIFOS_ROOT="./fifos"
CONFIG_DIR="./conf"
CONFIG_FILE="$CONFIG_DIR/network.config"

mkdir -p "$LOG_ROOT" "$FIFOS_ROOT" "$CONFIG_DIR"

MEMBERS=(M1 M2 M3 M4 M5 M6 M7 M8 M9)
declare -A FIFO_FDS

cleanup_procs() {
    pkill -f 'member.CouncilMember' 2>/dev/null || true
    sleep 1
    for port in {9001..9009}; do
        pid=$(lsof -ti tcp:$port 2>/dev/null || true)
        [ -n "$pid" ] && kill -9 "$pid" 2>/dev/null || true
    done
}

cleanup_fifos() {
    for key in "${!FIFO_FDS[@]}"; do
        FD=${FIFO_FDS[$key]}
        eval "exec ${FD}>&-" 2>/dev/null || true
    done
    FIFO_FDS=()
    [ -d "$FIFOS_ROOT" ] && rm -rf "$FIFOS_ROOT"
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

create_fifos_for_subtest() {
    local DIR="$FIFOS_ROOT/$1"
    mkdir -p "$DIR"
    for MEMBER in "${MEMBERS[@]}"; do
        fifo="$DIR/$MEMBER.in"
        [ ! -p "$fifo" ] && { rm -f "$fifo"; mkfifo "$fifo"; }
    done
}

start_member() {
    local MEMBER=$1 PROFILE=$2 EXTRA_ARGS=${3:-} TEST_DIR=$4 SUB=$5
    mkdir -p "$TEST_DIR"
    local logfile="$TEST_DIR/$MEMBER.log"
    : > "$logfile"

    local fifo="$FIFOS_ROOT/$SUB/$MEMBER.in"
    [ ! -p "$fifo" ] && { mkdir -p "$(dirname "$fifo")"; mkfifo "$fifo"; }

    exec {FD}<> "$fifo"
    FIFO_FDS["$SUB:$MEMBER"]=$FD

    mvn -q exec:java -Dexec.mainClass="member.CouncilMember" \
        -Dexec.args="$MEMBER --profile $PROFILE $EXTRA_ARGS" \
        < /proc/$$/fd/$FD > "$logfile" 2>&1 &
    disown
    sleep 0.05
}

wait_for_listening() {
    for MEMBER in "${MEMBERS[@]}"; do
        local logfile="$1/$MEMBER.log"
        local start=$(date +%s)
        while ! { [ -f "$logfile" ] && grep -q "Listening on port" "$logfile" 2>/dev/null; }; do
            sleep 0.2
            (( $(date +%s) - start > 20 )) && break
        done
    done
    sleep 0.5
}

send_proposal_via_fifo() {
    local SUB=$1 MEMBER=$2 VALUE=$3
    local fifo="$FIFOS_ROOT/$SUB/$MEMBER.in"
    [ ! -p "$fifo" ] && { echo "Error: FIFO $fifo not found"; return 1; }
    
    printf '{"type":"PROPOSE","value":"%s","from":"%s"}\n' "$VALUE" "$MEMBER" > "$fifo" &
    sleep 0.6
}

send_proposal_via_command_port() {
    local MEMBER=$1 VALUE=$2 PORT=$3
    local COMMAND_PORT=$((PORT + 100))
    echo "$VALUE" | nc -w 1 localhost $COMMAND_PORT 2>/dev/null || true
    sleep 0.5
}

extract_learned_value() {
    local line=$(grep -i "learn" "$1" 2>/dev/null | tail -n1 || true)
    [ -z "$line" ] && return

    local json=$(echo "$line" | sed -n 's/.*\({.*}\).*/\1/p' || true)
    if [ -n "$json" ] && command -v python3 >/dev/null 2>&1; then
        local val=$(printf '%s' "$json" | python3 -c 'import sys,json
try: print(json.loads(sys.stdin.read()).get("value",""))
except: pass' 2>/dev/null || true)
        [ -n "$val" ] && { echo "$val"; return; }
    fi

    local val=$(echo "$line" | awk '{for(i=1;i<=NF;i++){token=tolower($i); if(token~/^value:?$/ && i+1<=NF){print $(i+1); exit}}}')
    [ -n "$val" ] && { echo "$val" | sed 's/[^[:alnum:]_.-].*$//'; return; }
    
    echo "$line" | awk '{print $NF}' | sed 's/[^[:alnum:]_.-].*$//'
}

wait_for_consensus() {
    local TEST_DIR=$1
    local TIMEOUT=${2:-60}
    shift 2
    
    local members=()
    if [ $# -gt 0 ]; then
        members=("$@")
    else
        members=("${MEMBERS[@]}")
    fi
    
    local start=$(date +%s)

    while true; do
        declare -A seen
        local all_have=1
        local values_list=""

        for MEMBER in "${members[@]}"; do
            local logfile="$TEST_DIR/$MEMBER.log"
            if ! { [ -f "$logfile" ] && grep -qi "learn" "$logfile" 2>/dev/null; }; then
                all_have=0
                break
            fi
            local val=$(extract_learned_value "$logfile")
            [ -z "$val" ] && { all_have=0; break; }
            seen["$val"]=1
            values_list+="$MEMBER:$val "
        done

        if (( all_have == 1 )); then
            local unique_count=0
            local last_val=""
            for k in "${!seen[@]}"; do
                ((unique_count++))
                last_val="$k"
            done

            if (( unique_count == 1 )); then
                echo "Consensus reached: all members learned '$last_val'"
                return 0
            fi
        fi

        sleep 1
        if (( $(date +%s) - start > TIMEOUT )); then
            echo "Timeout: consensus not reached within ${TIMEOUT}s"
            return 1
        fi
    done
}

mvn -q -DskipTests=true compile
write_config

run_subtest() {
    local SUB=$1
    local TEST_DIR="$LOG_ROOT/$SUB"
    echo -e "\n=== Scenario 3$SUB ==="
    
    cleanup_procs
    mkdir -p "$TEST_DIR"
    rm -f "$TEST_DIR"/*.log
    create_fifos_for_subtest "$SUB"

    for i in "${!MEMBERS[@]}"; do
        local MEMBER="${MEMBERS[$i]}"
        local PORT=$((9001 + i))
        local PROFILE="STANDARD"
        [[ "$MEMBER" == "M1" ]] && PROFILE="RELIABLE"
        [[ "$MEMBER" == "M2" ]] && PROFILE="LATENT"
        [[ "$MEMBER" == "M3" ]] && PROFILE="FAILURE"
        local EXTRA=""
        [[ "$SUB" == "c" && "$MEMBER" == "M3" ]] && EXTRA="--crashAfterSend"
        start_member "$MEMBER" "$PROFILE" "$EXTRA" "$TEST_DIR" "$SUB"
    done

    wait_for_listening "$TEST_DIR"

    case "$SUB" in
        a)
            echo "Standard member M4 proposing M5"
            send_proposal_via_fifo "$SUB" "M4" "M5"
            wait_for_consensus "$TEST_DIR" 40 && echo "PASS" || echo "FAIL"
            ;;
        b)
            echo "Latent member M2 proposing M6"
            sleep 5
            send_proposal_via_fifo "$SUB" "M2" "M6"
            wait_for_consensus "$TEST_DIR" 60 && echo "PASS" || echo "FAIL"
            ;;
        c)
            echo "Failing member M3 proposing M3, then crashing"
            
            # M3 proposes via command port
            send_proposal_via_command_port "M3" "M3" 9003
            
            # Wait for M3 to send PREPARE and crash
            local m3_port=9003
            local timeout=20 
            local start_time=$(date +%s)
            
            echo "Waiting for M3 to crash..."
            while true; do
                local pid=$(lsof -ti tcp:"$m3_port" 2>/dev/null || true)
                
                # Check if M3 logged shutdown
                if [ -f "$TEST_DIR/M3.log" ] && grep -qi "Shutting down" "$TEST_DIR/M3.log" 2>/dev/null; then
                    echo "M3 crashed successfully"
                    break
                fi
                
                # Check if port is no longer listening
                if [ -z "$pid" ]; then
                    echo "M3 port closed"
                    break
                fi
                
                if (( $(date +%s) - start_time > timeout )); then
                    echo "M3 did not crash within timeout, forcing kill"
                    [ -n "$pid" ] && kill -9 "$pid" 2>/dev/null || true
                    break
                fi
                
                sleep 0.5
            done
            
            sleep 2
            
            # M4 drives recovery
            echo "M4 driving recovery with M7"
            send_proposal_via_command_port "M4" "M7" 9004
            
            # Wait for consensus among survivors (excluding M3)
            local SUBSET=(M1 M2 M4 M5 M6 M7 M8 M9)
            wait_for_consensus "$TEST_DIR" 60 "${SUBSET[@]}" && echo "PASS" || echo "FAIL"
            ;;
    esac

    cleanup_procs
    cleanup_fifos
    
    echo "Logs written to $TEST_DIR/"
}

run_subtest "a"
run_subtest "b"
run_subtest "c"

echo -e "\nAll tests completed. Logs available in $LOG_ROOT"
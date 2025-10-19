#!/bin/bash
set -euo pipefail

LOG_ROOT="./logs"
CONFIG_DIR="./conf"
CONFIG_FILE="$CONFIG_DIR/network.config"
SCENARIO3_CONFIG="$CONFIG_DIR/network2.config"

mkdir -p "$LOG_ROOT" "$CONFIG_DIR"

MEMBERS=(M1 M2 M3 M4 M5 M6 M7 M8 M9)
declare -A FIFO_FDS

GREEN="\033[1;32m"
RED="\033[1;31m"
CYAN="\033[1;36m"
RESET="\033[0m"

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
}

cleanup() {
    cleanup_procs
    cleanup_fifos
}
trap cleanup EXIT

write_config() {
    cat > "$SCENARIO3_CONFIG" <<EOF
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

# Scenario 1 and 2 - Simple start with auto-propose
run_simple_scenario() {
    local SCENARIO="$1"
    shift
    local PROPOSERS=("$@")
    local WAIT_TIME=20
    [ "$SCENARIO" == "Scenario2" ] && WAIT_TIME=30

    local LOG_DIR="$LOG_ROOT/$SCENARIO"
    mkdir -p "$LOG_DIR"
    rm -f "$LOG_DIR"/*.log

    echo -e "\n${CYAN}=== Running $SCENARIO ===${RESET}"

    for MEMBER in "${MEMBERS[@]}"; do
        local args="$MEMBER"
        for P in "${PROPOSERS[@]}"; do
            if [ "$P" == "$MEMBER" ]; then
                args="$args --propose $MEMBER"
                break
            fi
        done
        mvn -q exec:java -Dexec.mainClass="member.CouncilMember" \
            -Dexec.args="$args" >"$LOG_DIR/$MEMBER.log" 2>&1 &
        disown
    done

    sleep "$WAIT_TIME"

    echo -e "${CYAN}$SCENARIO Results:${RESET}"
    local AGREED_VALUES=()
    for MEMBER in "${MEMBERS[@]}"; do
        local LOG_FILE="$LOG_DIR/$MEMBER.log"
        if grep -q "has learned the value" "$LOG_FILE" 2>/dev/null; then
            local VAL_LINE=$(grep "has learned the value" "$LOG_FILE" | tail -1)
            local VALUE=$(echo "$VAL_LINE" | sed -E 's/.*value: ([^ ]+).*/\1/')
            AGREED_VALUES+=("$VALUE")
            echo -e "${GREEN}[$MEMBER] Learned value: $VALUE${RESET}"
        else
            echo -e "${RED}[$MEMBER] No learned value${RESET}"
        fi
    done

    local UNIQUE_VALUES=($(printf "%s\n" "${AGREED_VALUES[@]}" | sort -u))
    if [ "${#AGREED_VALUES[@]}" -ne "${#MEMBERS[@]}" ]; then
        echo -e "${RED}Not all learners reached a decision (${#AGREED_VALUES[@]}/${#MEMBERS[@]})${RESET}"
    elif [ "${#UNIQUE_VALUES[@]}" -eq 1 ]; then
        echo -e "${GREEN}All learners agreed on: ${UNIQUE_VALUES[0]}${RESET}"
    else
        echo -e "${RED}Consensus mismatch detected${RESET}"
    fi

    cleanup_procs
}

# Scenario 3
create_fifos_for_subtest() {
    local SUB=$1
    local TEST_DIR="$LOG_ROOT/Scenario3/$SUB"
    mkdir -p "$TEST_DIR"
    for MEMBER in "${MEMBERS[@]}"; do
        local fifo="$TEST_DIR/$MEMBER.fifo"
        [ ! -p "$fifo" ] && { rm -f "$fifo"; mkfifo "$fifo"; }
    done
}

start_member() {
    local MEMBER=$1 PROFILE=$2 EXTRA_ARGS=${3:-} TEST_DIR=$4 SUB=$5
    mkdir -p "$TEST_DIR"
    local logfile="$TEST_DIR/$MEMBER.log"
    local pidfile="$TEST_DIR/$MEMBER.pid"
    : > "$logfile"

    local fifo="$TEST_DIR/$MEMBER.fifo"
    [ ! -p "$fifo" ] && { mkdir -p "$(dirname "$fifo")"; mkfifo "$fifo"; }

    exec {FD}<> "$fifo"
    FIFO_FDS["$SUB:$MEMBER"]=$FD

    mvn -q exec:java -Dexec.mainClass="member.CouncilMember" \
        -Dexec.args="$MEMBER --profile $PROFILE $EXTRA_ARGS" \
        < /proc/$$/fd/$FD > "$logfile" 2>&1 &
    
    echo $! > "$pidfile"
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
    local TEST_DIR="$LOG_ROOT/Scenario3/$SUB"
    local fifo="$TEST_DIR/$MEMBER.fifo"
    [ ! -p "$fifo" ] && { echo "Error: FIFO $fifo not found"; return 1; }
    
    printf '{"type":"PREPARE","value":"%s","from":"%s"}\n' "$VALUE" "$MEMBER" > "$fifo" &
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

        for MEMBER in "${members[@]}"; do
            local logfile="$TEST_DIR/$MEMBER.log"
            if ! { [ -f "$logfile" ] && grep -qi "learn" "$logfile" 2>/dev/null; }; then
                all_have=0
                break
            fi
            local val=$(extract_learned_value "$logfile")
            [ -z "$val" ] && { all_have=0; break; }
            seen["$val"]=1
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

run_scenario3_subtest() {
    local SUB=$1
    local TEST_DIR="$LOG_ROOT/Scenario3/$SUB"
    echo -e "\n${CYAN}=== Scenario 3$SUB ===${RESET}"
    
    cleanup_procs
    mkdir -p "$TEST_DIR"
    rm -f "$TEST_DIR"/*.log "$TEST_DIR"/*.pid "$TEST_DIR"/*.fifo
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
            wait_for_consensus "$TEST_DIR" 40 && echo -e "${GREEN}PASS${RESET}" || echo -e "${RED}FAIL${RESET}"
            ;;
        b)
            echo "Latent member M2 proposing M6"
            sleep 5
            send_proposal_via_fifo "$SUB" "M2" "M6"
            wait_for_consensus "$TEST_DIR" 60 && echo -e "${GREEN}PASS${RESET}" || echo -e "${RED}FAIL${RESET}"
            ;;
        c)
            echo "Failing member M3 proposing M3, then crashing"
            
            send_proposal_via_command_port "M3" "M3" 9003
            
            local m3_port=9003
            local timeout=20 
            local start_time=$(date +%s)
            
            echo "Waiting for M3 to crash..."
            while true; do
                local pid=$(lsof -ti tcp:"$m3_port" 2>/dev/null || true)
                
                if [ -f "$TEST_DIR/M3.log" ] && grep -qi "Shutting down" "$TEST_DIR/M3.log" 2>/dev/null; then
                    echo "M3 crashed successfully"
                    break
                fi
                
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
            
            echo "M4 driving recovery with M7"
            send_proposal_via_command_port "M4" "M7" 9004
            
            local SUBSET=(M1 M2 M4 M5 M6 M7 M8 M9)
            wait_for_consensus "$TEST_DIR" 60 "${SUBSET[@]}" && echo -e "${GREEN}PASS${RESET}" || echo -e "${RED}FAIL${RESET}"
            ;;
    esac

    cleanup_procs
    cleanup_fifos
    
    echo "Logs written to $TEST_DIR/"
}

# Main execution
mvn -q -DskipTests=true compile
write_config

echo -e "${CYAN}========================================${RESET}"
echo -e "${CYAN}Starting Paxos Consensus Test Suite${RESET}"
echo -e "${CYAN}========================================${RESET}"

run_simple_scenario "Scenario1" M5
run_simple_scenario "Scenario2" M1 M8

run_scenario3_subtest "a"
run_scenario3_subtest "b"
run_scenario3_subtest "c"

echo -e "\n${CYAN}========================================${RESET}"
echo -e "${GREEN}All tests completed${RESET}"
echo -e "${CYAN}Logs available in $LOG_ROOT${RESET}"
echo -e "${CYAN}========================================${RESET}"
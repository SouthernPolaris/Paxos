#!/bin/bash
# run_scenarios.sh - Run Scenario 1 and Scenario 2 sequentially with final consensus summary.

set -euo pipefail

MVN_CMD="mvn exec:java -q -Dexec.mainClass=member.CouncilMember"
MEMBERS=(M1 M2 M3 M4 M5 M6 M7 M8 M9)

GREEN="\033[1;32m"
RED="\033[1;31m"
CYAN="\033[1;36m"
RESET="\033[0m"

cleanup_ports() {
    echo -e "${CYAN}Cleaning up old processes and ports...${RESET}"
    pkill -f 'member.CouncilMember' 2>/dev/null || true
    sleep 2
    for port in {9001..9009}; do
        pid=$(lsof -t -i:$port 2>/dev/null || true)
        [ -n "$pid" ] && kill -9 "$pid" 2>/dev/null || true
    done
}

run_scenario() {
    local SCENARIO="$1"
    local PROPOSERS=("${!2}")
    local WAIT_TIME="$3"

    local LOG_DIR="./logs/$SCENARIO"
    mkdir -p "$LOG_DIR"
    rm -f "$LOG_DIR"/*.log

    echo -e "${CYAN}=== Running $SCENARIO ===${RESET}"
    echo -e "${CYAN}Starting council members...${RESET}"

    for MEMBER in "${MEMBERS[@]}"; do
        args="$MEMBER"
        for P in "${PROPOSERS[@]}"; do
            if [ "$P" == "$MEMBER" ]; then
                args="$args --propose $MEMBER"
                break
            fi
        done
        $MVN_CMD -Dexec.args="$args" >"$LOG_DIR/$MEMBER.log" 2>&1 &
        PORT=$((9000 + ${MEMBER:1}))
        echo -e "$MEMBER started on port $PORT"
    done

    echo -e "\n${CYAN}Waiting ${WAIT_TIME}s for consensus...${RESET}"
    sleep "$WAIT_TIME"

    # --- Gather results ---
    echo -e "${CYAN}\n=== $SCENARIO Results ===${RESET}"
    AGREED_VALUES=()
    for MEMBER in "${MEMBERS[@]}"; do
        LOG_FILE="$LOG_DIR/$MEMBER.log"
        if grep -q "has learned the value" "$LOG_FILE"; then
            VAL_LINE=$(grep "has learned the value" "$LOG_FILE" | tail -1)
            VALUE=$(echo "$VAL_LINE" | sed -E 's/.*value: ([^ ]+).*/\1/')
            AGREED_VALUES+=("$VALUE")
            echo -e "${GREEN}[$MEMBER] Learned value: $VALUE${RESET}"
        else
            echo -e "${RED}[$MEMBER] No learned value${RESET}"
        fi
    done

    UNIQUE_VALUES=($(printf "%s\n" "${AGREED_VALUES[@]}" | sort -u))
    echo
    if [ "${#AGREED_VALUES[@]}" -ne "${#MEMBERS[@]}" ]; then
        echo -e "${RED}Not all learners reached a decision!${RESET}"
        echo -e "${RED}(${#AGREED_VALUES[@]}/${#MEMBERS[@]} learners responded)${RESET}"
    elif [ "${#UNIQUE_VALUES[@]}" -eq 1 ]; then
        echo -e "${GREEN}All learners agreed on: ${UNIQUE_VALUES[0]}${RESET}"
    else
        echo -e "${RED}Consensus mismatch detected.${RESET}"
        echo -e "${CYAN}Distinct learned values:${RESET}"
        for val in "${UNIQUE_VALUES[@]}"; do
            echo "  $val"
        done
    fi

    echo -e "${CYAN}\n=== $SCENARIO Complete ===${RESET}"
}

# -----------------------------
# Scenario 1: Ideal network
# -----------------------------
cleanup_ports
SCENARIO1_PROPOSER=(M5)
run_scenario "Scenario1" SCENARIO1_PROPOSER[@] 20

# -----------------------------
# Scenario 2: Concurrent proposals
# -----------------------------
cleanup_ports
SCENARIO2_PROPOSERS=(M1 M8)
run_scenario "Scenario2" SCENARIO2_PROPOSERS[@] 30

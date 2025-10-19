#!/bin/bash
# scenario3-mvn.sh - Fault-Tolerance Scenario (Scenario 3) with configurable wait times

set -euo pipefail

MVN_CMD="mvn exec:java -q -Dexec.mainClass=member.CouncilMember"
MEMBERS=(M1 M2 M3 M4 M5 M6 M7 M8 M9)

GREEN="\033[1;32m"
RED="\033[1;31m"
CYAN="\033[1;36m"
RESET="\033[0m"

# -----------------------------
# Configurable wait times (seconds)
# -----------------------------
WAIT_3A=20
WAIT_3B=25
WAIT_3C=30

cleanup_ports() {
    echo -e "${CYAN}Cleaning up old processes and ports...${RESET}"
    pkill -f 'member.CouncilMember' 2>/dev/null || true
    sleep 2
    for port in {9001..9009}; do
        pid=$(lsof -t -i:$port 2>/dev/null || true)
        [ -n "$pid" ] && kill -9 "$pid" 2>/dev/null || true
    done
}

run_subscenario() {
    local NAME="$1"
    local PROPOSER="$2"
    local WAIT_TIME="$3"
    local LOG_DIR="./logs/$NAME"
    mkdir -p "$LOG_DIR"
    rm -f "$LOG_DIR"/*.log

    echo -e "${CYAN}=== $NAME ===${RESET}"

    # --- Start members with profiles ---
    for MEMBER in "${MEMBERS[@]}"; do
        PORT=$((9000 + ${MEMBER:1}))
        case "$MEMBER" in
            M1) PROFILE="reliable" ;;
            M2) PROFILE="latent" ;;
            M3) PROFILE="failure" ;;
            *) PROFILE="standard" ;;
        esac

        args="$MEMBER --profile $PROFILE"
        # Only the designated proposer gets --propose
        if [ "$MEMBER" == "$PROPOSER" ]; then
            args="$args --propose $MEMBER"
        fi

        $MVN_CMD -Dexec.args="$args" >"$LOG_DIR/$MEMBER.log" 2>&1 &
        echo -e "$MEMBER started on port $PORT with profile $PROFILE"
    done

    echo -e "\n${CYAN}Waiting $WAIT_TIME seconds for consensus...${RESET}"
    sleep "$WAIT_TIME"

    # --- Gather results ---
    echo -e "${CYAN}\n=== $NAME Results ===${RESET}"
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
}

# -----------------------------
# Main Scenario 3 Execution
# -----------------------------
cleanup_ports
# Test 3a: Standard member M4 proposes
run_subscenario "Scenario3a_StandardProposal" M4 "$WAIT_3A"

cleanup_ports
# Test 3b: Latent member M2 proposes
run_subscenario "Scenario3b_LatentProposal" M2 "$WAIT_3B"

cleanup_ports
# Test 3c: Failing member M3 proposes
run_subscenario "Scenario3c_FailureProposal" M3 "$WAIT_3C"

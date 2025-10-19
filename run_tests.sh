#!/usr/bin/env bash
set -euo pipefail

# run_tests.sh - runs the three Paxos scenarios described in the assignment
# Usage: ./run_tests.sh
# Builds a temporary conf/network.config with 9 members on localhost ports 9001..9009
# Starts each member's JVM pointing to the project's classpath (target/classes)
# Logs go into per-scenario subdirectories

WORKDIR=$(pwd)
LOGDIR="$WORKDIR/logs"
CONFIG_DIR="$WORKDIR/conf"
TMP_CONFIG="$CONFIG_DIR/network.tmp.config"
ORIG_CONFIG="$CONFIG_DIR/network.config"

mkdir -p "$LOGDIR"

# Create a full 9-member network config (localhost:9001..9009)
cat > "$TMP_CONFIG" <<EOF
M1,localhost,9001,RELIABLE
M2,localhost,9002,RELIABLE
M3,localhost,9003,RELIABLE
M4,localhost,9004,RELIABLE
M5,localhost,9005,RELIABLE
M6,localhost,9006,RELIABLE
M7,localhost,9007,RELIABLE
M8,localhost,9008,RELIABLE
M9,localhost,9009,RELIABLE
EOF

function cleanup() {
  echo "Stopping members..."
  pkill -f "member.CouncilMember" || true
}
trap cleanup EXIT

function wait_for_consensus() {
  # args: expected_value timeout_seconds scenario_dir
  local value="$1"
  local timeout=${2:-30}
  local dir="$3"
  local deadline=$((SECONDS + timeout))

  echo "Waiting up to ${timeout}s for consensus on value '${value}'..."
  while [ $SECONDS -lt $deadline ]; do
      if grep -R "has learned the value: ${value}" "$LOGDIR" >/dev/null 2>&1; then
      echo "Consensus on ${value} observed."
      return 0
    fi
    sleep 1
  done
  echo "Timeout waiting for consensus on ${value}" >&2
  return 1
}
  function wait_for_consensus() {
    # args: log_subdir expected_value timeout_seconds
    local log_subdir="$1"
    local value="$2"
    local timeout=${3:-30}
    local deadline=$((SECONDS + timeout))

    echo "Waiting up to ${timeout}s for consensus on value '${value}' (logs: ${log_subdir})..."
    while [ $SECONDS -lt $deadline ]; do
      # Search logs for learner message indicating chosen value
      if grep -R "has learned the value: ${value}" "$log_subdir" >/dev/null 2>&1; then
        echo "Consensus on ${value} observed."
        return 0
      fi
      sleep 1
    done
    echo "Timeout waiting for consensus on ${value}" >&2
    return 1
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

function start_members() {
  # args: associative array of profiles, scenario_dir, optional proposer_id, optional proposed_value
  declare -n profs="$1"
  local scenario_dir="$2"
  local proposer_id="${3:-}"
  local proposed_value="${4:-}"

  # copy tmp config over the real config used by the program
  cp "$TMP_CONFIG" "$ORIG_CONFIG"

  mkdir -p "$scenario_dir"
  rm -f "$scenario_dir"/*.log

  for i in {1..9}; do
    id="M${i}"
    p=${profs[$id]:-RELIABLE}
    out="$scenario_dir/${id}.log"

    echo "Starting ${id} profile=${p} -> log ${out}"
    args="${id} --profile ${p}"
    # Only add --propose to the intended proposer
    if [[ "$id" == "$proposer_id" ]] && [[ -n "$proposed_value" ]]; then
      args+=" --propose ${proposed_value}"
    fi

    mvn -q exec:java -Dexec.mainClass=member.CouncilMember -Dexec.args="$args" >"$out" 2>&1 &
    sleep 0.2
  done

  # Give members time to start listening
  sleep 5
}
  function start_members() {
    # args: log_subdir, name of associative array of profiles
    local log_subdir="$1"
    declare -n profiles="$2"

    mkdir -p "$log_subdir"
    cp "$TMP_CONFIG" "$ORIG_CONFIG"
    rm -f "$log_subdir"/*.log

    for i in {1..9}; do
      id="M${i}"
      p=${profiles[$id]:-RELIABLE}
      out="$log_subdir/${id}.log"

      echo "Starting ${id} profile=${p} -> log ${out}"
      # Provide --profile flag and let CouncilMember use conf/network.config
      mvn -q exec:java -Dexec.mainClass=member.CouncilMember -Dexec.args="${id} --profile ${p}" >"$out" 2>&1 &
      sleep 0.12
    done

    # Give members a moment to start
    sleep 1
  }

function scenario1() {
  echo -e "\n=== Scenario 1: Ideal Network ==="
  local dir="$LOGDIR/scenario1"
  declare -A profiles
  for i in {1..9}; do profiles[M${i}]=RELIABLE; done
  start_members profiles "$dir" "M4" "M5"

  wait_for_consensus M5 20 "$dir" || echo "Scenario 1 failed"
}

function scenario2() {
  echo -e "\n=== Scenario 2: Concurrent Proposals ==="
  local dir="$LOGDIR/scenario2"
  declare -A profiles
  for i in {1..9}; do profiles[M${i}]=RELIABLE; done
  start_members profiles "$dir"  # start all normally

  echo "Triggering concurrent proposals from M1 and M8"
  # Start proposers separately
  mvn -q exec:java -Dexec.mainClass=member.CouncilMember -Dexec.args="M1 --profile RELIABLE --propose M1" >"$dir/M1_propose.log" 2>&1 &
  (sleep 0.05; mvn -q exec:java -Dexec.mainClass=member.CouncilMember -Dexec.args="M8 --profile RELIABLE --propose M8" >"$dir/M8_propose.log" 2>&1 &)

  if wait_for_consensus M1 20 "$dir"; then
    echo "Consensus reached on M1"
  elif wait_for_consensus M8 20 "$dir"; then
    echo "Consensus reached on M8"
  else
    echo "Scenario 2: No consensus within timeout" >&2
  fi
}

function scenario3() {
  echo -e "\n=== Scenario 3: Fault-Tolerance ==="
  local dir="$LOGDIR/scenario3"
  declare -A profiles
  profiles[M1]=RELIABLE
  profiles[M2]=LATENT
  profiles[M3]=FAILURE
  for i in 4 5 6 7 8 9; do profiles[M${i}]=STANDARD; done

  start_members profiles "$dir"

  # Test 3a: M4 proposes (standard)
  echo "Test 3a: M4 proposes M5"
  mvn -q exec:java -Dexec.mainClass=member.CouncilMember -Dexec.args="M4 --profile STANDARD --propose M5" >"$dir/M4_propose.log" 2>&1 &
  wait_for_consensus M5 30 "$dir" || echo "Test 3a failed"

  # Test 3b: M2 (latent) proposes
  echo "Test 3b: M2 (latent) proposes M6"
  mvn -q exec:java -Dexec.mainClass=member.CouncilMember -Dexec.args="M2 --profile LATENT --propose M6" >"$dir/M2_propose.log" 2>&1 &
  wait_for_consensus M6 60 "$dir" || echo "Test 3b failed"

  # Test 3c: M3 (failing) proposes then crashes
  echo "Test 3c: M3 (failing) proposes M3 then crashes"
  mvn -q exec:java -Dexec.mainClass=member.CouncilMember -Dexec.args="M3 --profile FAILURE --propose M3 --crashAfterSend" >"$dir/M3_propose.log" 2>&1 &
  sleep 2

  # Kick another member to drive consensus after crash
  echo "After M3 crash, M4 proposes M7 to ensure progress"
  mvn -q exec:java -Dexec.mainClass=member.CouncilMember -Dexec.args="M4 --profile STANDARD --propose M7" >"$dir/M4_propose2.log" 2>&1 &
  wait_for_consensus M7 40 "$dir" || echo "Test 3c failed"
}

# Build project first
mvn -q -DskipTests=true compile

scenario1
scenario2
scenario3

echo "All scenarios executed. Logs are in $LOGDIR"

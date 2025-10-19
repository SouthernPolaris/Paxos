# Paxos Implementation

This repository implements a simplified Paxos system. It contains Java source for Proposers, Acceptors, Learners, networking transports, and unit tests.

## Prerequisites

- Java 17 (JDK) installed
- Maven 3 or higher installed

Verify Java and Maven are available:

```bash
java -version
mvn -v
```

## Build
To compile the project (Maven will also download dependencies):

```bash
mvn compile
```

To build and run the unit tests:

```bash
mvn test
```

The test suite uses JUnit 5 and Mockito (test-scoped dependencies are declared in `pom.xml`). On success you should see `BUILD SUCCESS` and a summary of the tests run.

## Running the program
```bash
mvn -q exec:java -Dexec.mainClass="member.CouncilMember" -Dexec.args="[Name]"
```
This brings up an instance of Council Member.

**You must bring up the same number of instances of Council Member as there are entries in `network.conf` or wherever else the configuration is set from.**

You can use the `network.conf` file or similar to set the reliability of the file

## Running the demo / scenario scripts

This repository includes a bash script for functionality testing under `src/test/bash/run_tests.sh`:

Usage example:

```bash
# Make scripts executable
chmod +x ./src/test/bash/run_tests.sh

# Run all scenario scripts
./src/test/bash/run_tests.sh
```

Notes:

## Project layout

- `src/main/java` — implementation
  - `member/` — CouncilMember, MemberConfig, Profile
  - `network/` — MemberTransport, SocketTransport (network abstraction)
  - `paxos_logic/` — Acceptor, Proposer, Learner, PaxosNode
  - `paxos_util/` — messages and utility classes (Prepare, Promise, Accepted, ProposalNumber, etc.)
- `src/test/java` — unit tests (JUnit + Mockito)
- `run_tests.sh` — demo / test scripts
- `logs/` — output logs created by scenario scripts
- `conf/` — configuration files used by the scripts (e.g., `network.config`)

## Config structure
The config files are structured as a CSV-style file. Each line represents a node.

Each line contains:
```
Name,hostname,port,RELIABILITY_TYPE
```

Adjust the config files in `conf/` as required by your test scenarios.

## How tests are structured

- Unit tests are in `src/test/java` and are executed by `mvn test`.

### Design decisions

This section documents the key design decisions made while implementing the Paxos messages and related infrastructure

- Serializable message types (one class per message):
  - Each Paxos message type has its own Java class under `paxos_util/` (for example `Prepare`, `Promise`, `AcceptRequest`, `Accepted`, `PaxosMessage`). This keeps the code explicit and type-safe and makes tests and mocks straightforward

- `ProposalNumber` as a value object:
  - Proposal numbers are represented with a dedicated `ProposalNumber` class (`paxos_util/ProposalNumber.java`) that implements `Comparable<ProposalNumber>`
  - It encapsulates a (proposer id, sequence) pair and provides deterministic ordering (first by sequence, then by proposer id as tiebreaker)
  - Using a value object simplifies comparisons, equality checks, and improves readability compared to passing raw strings around

- JSON serialization with Gson:
  - Messages are serialized to/from JSON when sent over the `MemberTransport` abstraction. The project uses Gson (`com.google.code.gson`) for JSON serialization
  - Gson is simple to configure and produces compact JSON which is convenient for test logs and human inspection

- Transport abstraction (`MemberTransport`):
  - Networking is abstracted behind the `MemberTransport` interface. This allows unit tests to inject mocks (`Mockito`) without starting network sockets
  - A `SocketTransport` implementation is provided for real network runs

- Thread-safety and locking:
  - Acceptors use a `ReentrantLock` to protect updates to `promisedNumber`, `acceptedNumber`, and `acceptedValue` to ensure correctness under concurrent incoming messages
  - The `handlePrepare` and `handleAcceptRequest` methods hold the lock while checking and updating the local state and sending replies. This avoids race conditions
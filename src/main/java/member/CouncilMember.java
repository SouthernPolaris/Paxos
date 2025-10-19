package member;

import network.*;
import paxos_logic.*;
import paxos_util.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

public class CouncilMember {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: java CouncilMember <memberId> [--propose <value>] [--crashAfterSend]");
            return;
        }

        String memberId = args[0];
        String proposeValue = null;
        boolean crashAfterSend = false;

        // Parse optional arguments
        for (int i = 1; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("--propose") && i + 1 < args.length) {
                proposeValue = args[i + 1];
                i++;
            } else if (args[i].equalsIgnoreCase("--crashAfterSend")) {
                crashAfterSend = true;
            }
        }

        // Load network.config
        Map<String, MemberConfig> allConfigs = loadNetworkConfig("conf/network.config");

        if (!allConfigs.containsKey(memberId)) {
            System.out.println("Error: Member ID " + memberId + " not found in network.config");
            return;
        }

        MemberConfig myConfig = allConfigs.get(memberId);
        Set<String> acceptorIds = new HashSet<>(allConfigs.keySet());
        Set<String> learnerIds = new HashSet<>(acceptorIds);

        // Create Paxos node (transport is null for now)
        PaxosNode node = new PaxosNode(memberId, acceptorIds, learnerIds, null);

        // Create transport and link to node
        SocketTransport transport = new SocketTransport(
            memberId,
            myConfig.port,
            allConfigs.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().address)),
            node,
            myConfig.profile
        );

        // If either profile indicates failure or command-line flag given, enable crash-after-send
        if (crashAfterSend || myConfig.profile == Profile.FAILURE) {
            transport.setCrashAfterSend(true);
        }

        // Attach transport back to node
        node.setTransport(transport);

        // Start listening immediately
        transport.startListening();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[Member " + memberId + "] Shutting down...");
            transport.shutdown();
        }));

        // Small delay to ensure other members are up (script will also wait_for_listening)
        Thread.sleep(2000);

        // If a --propose was provided on the command line, propose it now (we allow proposals even for FAILURE profile)
        if (proposeValue != null) {
            System.out.println("[Proposer " + memberId + "] Auto-proposing value: " + proposeValue);
            node.getProposer().propose(proposeValue);
        }

        // Start a background thread that reads proposals from stdin (one per line) and proposes them.
        Thread stdinReader = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        System.out.println("[Proposer " + memberId + "] Received stdin proposal: " + line);
                        try {
                            node.getProposer().propose(line);
                        } catch (Exception e) {
                            System.err.println("[Proposer " + memberId + "] Error while proposing: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }
            } catch (IOException ioe) {
                System.err.println("[Proposer " + memberId + "] Stdin reader closed: " + ioe.getMessage());
            }
        });
        stdinReader.setDaemon(true);
        stdinReader.start();

        // Keep the process alive so this member continues to listen and participate
        CountDownLatch latch = new CountDownLatch(1);
        try {
            latch.await();
        } catch (InterruptedException ignored) {
        }
    }

    private static Map<String, MemberConfig> loadNetworkConfig(String path) throws IOException {
        Map<String, MemberConfig> map = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] parts = line.split(",");
                String memberId = parts[0];
                String host = parts[1];
                int port = Integer.parseInt(parts[2]);
                Profile profile = Profile.valueOf(parts[3].toUpperCase());
                map.put(memberId, new MemberConfig(host, port, profile));
            }
        }
        return map;
    }
}
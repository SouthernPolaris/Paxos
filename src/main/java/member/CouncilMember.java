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
            System.out.println("Usage: java CouncilMember <memberId> [--propose <value>]");
            return;
        }

        String memberId = args[0];
        String proposeValue = null;

        // Parse optional --propose argument
        for (int i = 1; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("--propose") && i + 1 < args.length) {
                proposeValue = args[i + 1];
                i++;
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

        if (myConfig.profile == Profile.FAILURE) {
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

        // Small delay to ensure other members are up
        Thread.sleep(2000);

        // Auto-propose if value provided
        if (proposeValue != null && myConfig.profile != Profile.FAILURE) {
            System.out.println("[Proposer " + memberId + "] Auto-proposing value: " + proposeValue);
            node.getProposer().propose(proposeValue);
        }

        // Keep the process alive so this member continues to listen and participate
        // (previous behavior exited after proposing which caused other nodes to see ConnectionRefused)
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

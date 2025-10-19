package member;

import network.*;
import paxos_logic.*;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

/**
 * Main class for a Council Member running Paxos
 */
public class CouncilMember {
    /**
     * Main method for Council Member
     * @param args Command line arguments
     * @throws Exception on error
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: java CouncilMember <memberId> [--propose <value>] [--crashAfterSend]");
            return;
        }

        String memberId = args[0];
        String proposeValue = null;
        boolean crashAfterSend = false;

        // Parse additional args
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

        // Create Paxos node
        PaxosNode node = new PaxosNode(memberId, acceptorIds, learnerIds, null);

        // Link transport and node
        SocketTransport transport = new SocketTransport(
            memberId,
            myConfig.port,
            allConfigs.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().address)),
            node,
            myConfig.profile
        );

        // Enable crash-after-send if specified
        if (crashAfterSend || myConfig.profile == Profile.FAILURE) {
            transport.setCrashAfterSend(true);
        }

        node.setTransport(transport);

        // Start Paxos listening
        transport.startListening();

        // Graceful shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[Member " + memberId + "] Shutting down...");
            transport.shutdown();
        }));

        // Small startup delay to ensure all nodes are ready
        Thread.sleep(2000);

        // Auto-propose if --propose given
        if (proposeValue != null) {
            System.out.println("[Proposer " + memberId + "] Auto-proposing value: " + proposeValue);
            node.getProposer().propose(proposeValue);
        }

        // Background thread for stdin proposals
        Thread stdinReader = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        System.out.println("[Proposer " + memberId + "] Received stdin proposal: " + line);
                        node.getProposer().propose(line);
                    }
                }
            } catch (IOException ioe) {
                System.err.println("[Proposer " + memberId + "] Stdin reader closed: " + ioe.getMessage());
            }
        });
        stdinReader.setDaemon(true);
        stdinReader.start();

        // Command Port Listener for runtime proposals
        int commandPort = myConfig.port + 100;
        Thread commandServer = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(commandPort)) {
                System.out.println("[Member " + memberId + "] Command port listening on " + commandPort);
                while (true) {
                    try (Socket client = serverSocket.accept();
                         BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            line = line.trim();
                            if (!line.isEmpty()) {
                                System.out.println("[Proposer " + memberId + "] Received command proposal: " + line);
                                node.getProposer().propose(line);
                            }
                        }
                    } catch (IOException e) {
                        System.err.println("[Member " + memberId + "] Command connection error: " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                System.err.println("[Member " + memberId + "] Failed to start command server: " + e.getMessage());
            }
        });
        commandServer.setDaemon(true);
        commandServer.start();

        // Keep process alive
        CountDownLatch latch = new CountDownLatch(1);
        latch.await();
    }

    /**
     * Loads network configuration from file
     * @param path Path to network.config file
     * @return Map of member IDs to their configurations
     * @throws IOException on file read error
     */
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

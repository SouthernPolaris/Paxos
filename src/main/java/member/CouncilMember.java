package member;

import network.*;
import paxos_logic.*;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.stream.Collectors;

public class CouncilMember {
    public static void main(String[] args) throws Exception {
        if(args.length < 2) {
            System.out.println("Usage: java CouncilMember <memberId> --profile <profile>");
            return;
        }

        String memberId = args[0];
        Profile profile = Profile.valueOf(args[2].toUpperCase());

        // Load network.config
        Map<String, InetSocketAddress> members = loadConfig("network.config");

        Set<Integer> acceptorIds = members.keySet().stream()
                .map(s -> Integer.parseInt(s.replace("M","")))
                .collect(Collectors.toSet());

        Set<Integer> learnerIds = new HashSet<>(acceptorIds);

        // Create Paxos node
        PaxosNode node = new PaxosNode(memberId, acceptorIds, learnerIds, null);
        SocketTransport transport = new SocketTransport(memberId, members.get(memberId).getPort(), members, node, profile);

        node = new PaxosNode(memberId, acceptorIds, learnerIds, transport);

        // Optional: trigger proposal from console
        if(profile != Profile.FAILURE) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            System.out.println("Enter candidate to propose:");
            String candidate = reader.readLine();
            node.getProposer().propose(candidate);
        }
    }

    private static Map<String, InetSocketAddress> loadConfig(String path) throws IOException {
        Map<String, InetSocketAddress> members = new HashMap<>();
        try(BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                String memberId = parts[0];
                String host = parts[1];
                int port = Integer.parseInt(parts[2]);
                members.put(memberId, new InetSocketAddress(host, port));
            }
        }
        return members;
    }
}

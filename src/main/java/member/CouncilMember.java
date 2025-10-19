package member;

import network.*;
import paxos_logic.*;
import paxos_util.*;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.stream.Collectors;

public class CouncilMember {
    public static void main(String[] args) throws Exception {
        if(args.length < 1) {
            System.out.println("Usage: java CouncilMember <memberId>");
            return;
        }

        String memberId = args[0];

        // Load network.config
        Map<String, MemberConfig> allConfigs = loadNetworkConfig("conf/network.config");

        if (!allConfigs.containsKey(memberId)) {
            System.out.println("Error: Member ID " + memberId + " not found in network.config");
            return;
        }

        MemberConfig myConfig = allConfigs.get(memberId);

        Set<String> acceptorIds = new HashSet<>(allConfigs.keySet());


        Set<String> learnerIds = new HashSet<>(acceptorIds);

        // Create Paxos node first (no transport yet)
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

        // Now attach the transport back into the node
        node.setTransport(transport);

        // Start listening (this was previously done inside PaxosNode constructor)
        transport.startListening();


        // Optional: trigger proposal from console if not failure
        if(myConfig.profile != Profile.FAILURE) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            System.out.println("Enter candidate to propose:");
            String candidate = reader.readLine();
            node.getProposer().propose(candidate);
        }
    }

    private static Map<String, MemberConfig> loadNetworkConfig(String path) throws IOException {
        Map<String, MemberConfig> map = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) {
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

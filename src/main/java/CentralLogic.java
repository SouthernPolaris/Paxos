import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import member.Member;
import network.NetworkTransport;
import paxos_logic.PaxosServerImpl;
import paxos_logic.PaxosServer;

public class CentralLogic {
    public static void main(String[] args) {
        PaxosServerImpl paxosServer = new PaxosServerImpl();
        NetworkTransport networkTransport = new NetworkTransport(paxosServer);
        paxosServer.setNetworkTransportInstance(networkTransport);

        List<Member> members = new ArrayList<>();
        Map<String, Integer> memberPorts = Map.of(
            "1", 5001,
            "2", 5002,
            "3", 5003,
            "4", 5004,
            "5", 5005,
            "6", 5006,
            "7", 5007,
            "8", 5008,
            "9", 5009
        );

        for (int i = 0; i < memberPorts.size(); i++) {
            String memberId = String.valueOf(i + 1);
            
            NetworkTransport memberNetworkTransport = new NetworkTransport(paxosServer);
            Member member = new Member(memberId, memberNetworkTransport, paxosServer);
            members.add(member);
        }

        System.out.println("Paxos Algorithm Started");

        paxosServer.setMembers(members, memberPorts);

        members.get(0).sendPrepareMessage();
    }
}

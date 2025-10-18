import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import member.Member;
import network.NetworkTransport;
import paxos_logic.PaxosServer;
import paxos_logic.PaxosServerImpl;

public class MemberTest {

    private NetworkTransport networkTransport;
    private PaxosServerImpl paxosServer;

    @BeforeEach
    public void setUp() {
        networkTransport = mock(NetworkTransport.class);
        paxosServer = mock(PaxosServerImpl.class);
    }

    @Test
    public void testMemberCreation() {

        Member member = new Member("1", networkTransport, paxosServer);
        assertEquals("1", member.getMemberId());
        assertEquals(networkTransport, member.getNetworkTransport());
        assertEquals(paxosServer, member.getPaxosServer());

        Member member2 = new Member("5", networkTransport, paxosServer);
        assertEquals("5", member2.getMemberId());
        assertEquals(networkTransport, member2.getNetworkTransport());
        assertEquals(paxosServer, member2.getPaxosServer());
    }

    @Test
    public void testMemberDelay() {
        Member member1 = new Member("1", networkTransport, paxosServer);
        Member member2 = new Member("2", networkTransport, paxosServer);
        Member member3 = new Member("3", networkTransport, paxosServer);

        assertEquals(0, member1.getDelay());
        assertEquals(10000, member2.getDelay());
        assertTrue(member3.getDelay() >= 4000 && member3.getDelay() <= 9000);
    }

    @Test
    public void testCreateProposalNumbers() {
        Member member = new Member("1", networkTransport, paxosServer);

        String proposalNumber1 = member.createProposalNumber();
        String proposalNumber2 = member.createProposalNumber();
        String proposalNumber3 = member.createProposalNumber();

        assertEquals("1:1", proposalNumber1);
        assertEquals("2:1", proposalNumber2);
        assertEquals("3:1", proposalNumber3);
    }

    @Test
    public void testMaxProposalNumber() {
        Member member = new Member("1", networkTransport, paxosServer);

        String max1 = member.getMaxProposalNum();
        assertEquals("0:0", max1);
        member.createProposalNumber(); // 1:1
        String max2 = member.getMaxProposalNum();
        assertEquals("1:1", max2);

        member.setMaxProposalNum("5:3");
        String max3 = member.getMaxProposalNum();
        assertEquals("5:3", max3);

        // Less than current max, should not update
        member.setMaxProposalNum("4:2");
        String max4 = member.getMaxProposalNum();
        assertEquals("5:3", max4);
    }

    @Test
    public void testPrepare() {
        Member member = new Member("1", networkTransport, paxosServer);
        member.sendPrepareMessage();
        verify(paxosServer).broadcastMessage(anyString());
        assertEquals("1", member.getProposalValue());
    }

    @Test
    public void testPromise() {
        Member member = new Member("2", networkTransport, paxosServer);
        // (proposalId, proposalValue, acceptedProposalValue)
        member.sendPromiseMessage("1", "1:1", "15");
        verify(paxosServer).broadcastMessage(anyString());
    }

    @Test
    public void testAcceptRequest() {
        Member member = new Member("3", networkTransport, paxosServer);
        member.sendAcceptRequestMessage("2", "20");
        verify(paxosServer).broadcastMessage(anyString());
    }
}

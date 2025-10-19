package paxos_util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

public class PaxosMessageTest {

    private ProposalNumber testProposalNumber;

    @BeforeEach
    void setUp() {
        testProposalNumber = new ProposalNumber("M1:5");
    }

    @Test
    public void testPrepareMessage() {
        Prepare prepare = new Prepare("M1", testProposalNumber);
        
        assertEquals("PREPARE", prepare.type);
        assertEquals("M1", prepare.fromMemberId);
        assertEquals(testProposalNumber, prepare.proposalNum);
        assertNull(prepare.proposalValue);
    }

    @Test
    public void testPromiseMessage() {
        Promise promise = new Promise("M2", testProposalNumber, "M1:3", "previousValue");
        
        assertEquals("PROMISE", promise.type);
        assertEquals("M2", promise.fromMemberId);
        assertEquals(testProposalNumber, promise.proposalNum);
        assertNull(promise.proposalValue);
        assertEquals("M1:3", promise.acceptedProposalNumber);
        assertEquals("previousValue", promise.acceptedProposalValue);
    }

    @Test
    public void testAcceptRequestMessage() {
        AcceptRequest acceptRequest = new AcceptRequest("M1", testProposalNumber, "proposedValue");
        
        assertEquals("ACCEPT_REQUEST", acceptRequest.type);
        assertEquals("M1", acceptRequest.fromMemberId);
        assertEquals(testProposalNumber, acceptRequest.proposalNum);
        assertEquals("proposedValue", acceptRequest.proposalValue);
    }

    @Test
    public void testAcceptedMessage() {
        Accepted accepted = new Accepted("M2", testProposalNumber, "acceptedValue");
        
        assertEquals("ACCEPTED", accepted.type);
        assertEquals("M2", accepted.fromMemberId);
        assertEquals(testProposalNumber, accepted.proposalNum);
        assertEquals("acceptedValue", accepted.proposalValue);
    }

    @Test
    public void testDifferentProposalNumbers() {
        ProposalNumber pn1 = new ProposalNumber("M1:1");
        ProposalNumber pn2 = new ProposalNumber("M2:10");
        ProposalNumber pn3 = new ProposalNumber("M3:100");

        Prepare prepare1 = new Prepare("M1", pn1);
        Prepare prepare2 = new Prepare("M2", pn2);
        Prepare prepare3 = new Prepare("M3", pn3);

        assertEquals(pn1, prepare1.proposalNum);
        assertEquals(pn2, prepare2.proposalNum);
        assertEquals(pn3, prepare3.proposalNum);

        assertNotEquals(prepare1.proposalNum, prepare2.proposalNum);
        assertNotEquals(prepare2.proposalNum, prepare3.proposalNum);
    }

}
package paxos_logic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import network.MemberTransport;
import paxos_util.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Set;
import java.util.HashSet;

public class PaxosNodeTest {

    @Mock
    private MemberTransport mockTransport;

    private PaxosNode paxosNode;
    private Set<String> acceptorIds;
    private Set<String> learnerIds;
    private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        acceptorIds = new HashSet<>();
        acceptorIds.add("M1");
        acceptorIds.add("M2");
        acceptorIds.add("M3");
        
        learnerIds = new HashSet<>();
        learnerIds.add("M1");
        learnerIds.add("M2");
        learnerIds.add("M3");
        
        paxosNode = new PaxosNode("M1", acceptorIds, learnerIds, mockTransport);
        System.setOut(new PrintStream(outputStream));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
    }

    @Test
    public void testPaxosNodeCreation() {
        assertNotNull(paxosNode);
        assertEquals("M1", paxosNode.getMemberId());
        assertNotNull(paxosNode.getProposer());
        assertNotNull(paxosNode.getAcceptor());
        assertNotNull(paxosNode.getLearner());
    }

    @Test
    public void testStartListening() {
        verify(mockTransport).startListening();
    }

    @Test
    public void testRoutePrepareMessage() {
        String prepareJson = "{\"type\":\"PREPARE\",\"fromMemberId\":\"M2\",\"proposalNum\":{\"proposerId\":2,\"sequence\":1}}";
        
        paxosNode.handleMessage("M2", prepareJson);
        
        String output = outputStream.toString();
        assertTrue(output.contains("Received Prepare(M2:1) from Proposer M2"));
    }

    @Test
    public void testRouteAcceptRequestMessage() {
        String acceptRequestJson = "{\"type\":\"ACCEPT_REQUEST\",\"fromMemberId\":\"M2\",\"proposalNum\":{\"proposerId\":2,\"sequence\":1},\"proposalValue\":\"testValue\"}";
        
        paxosNode.handleMessage("M2", acceptRequestJson);
        
        String output = outputStream.toString();
        assertTrue(output.contains("Received AcceptRequest(M2:1, 'testValue') from Proposer M2"));
    }

    @Test
    public void testRouteAcceptedMessage() {
        String acceptedJson = "{\"type\":\"ACCEPTED\",\"fromMemberId\":\"M2\",\"proposalNum\":{\"proposerId\":2,\"sequence\":1},\"proposalValue\":\"testValue\"}";
        
        paxosNode.handleMessage("M2", acceptedJson);
        
        String output = outputStream.toString();
        assertFalse(output.contains("has learned the value"));
    }

    @Test
    public void testUnknownMessageType() {
        String unknownJson = "{\"type\":\"UNKNOWN\",\"fromMemberId\":\"M2\"}";
        
        paxosNode.handleMessage("M2", unknownJson);
        
        String output = outputStream.toString();
        assertTrue(output.contains("Unknown message type: UNKNOWN"));
    }

    @Test
    public void testMalformedJson() {
        String malformedJson = "{invalid json";
        
        paxosNode.handleMessage("M2", malformedJson);
        
        String output = outputStream.toString();
        assertTrue(output.contains("Failed to parse JSON message"));
    }

    @Test
    public void testNullMessage() {
        paxosNode.handleMessage("M2", null);
        
        String output = outputStream.toString();
        assertTrue(output.contains("Failed to parse JSON message") || 
                   output.contains("Unknown message format"));
    }

    @Test
    public void testMessageWithNullType() {
        String nullTypeJson = "{\"fromMemberId\":\"M2\"}";
        
        paxosNode.handleMessage("M2", nullTypeJson);
        
        String output = outputStream.toString();
        assertTrue(output.contains("Unknown message format"));
    }

    @Test
    public void testEmptyMessage() {
        paxosNode.handleMessage("M2", "");
        
        String output = outputStream.toString();
        assertTrue(output.contains("Failed to parse JSON message") || 
                   output.contains("Unknown message format"));
    }

    @Test
    public void testPaxosIntegration() {
        PaxosNode node1 = new PaxosNode("M1", acceptorIds, learnerIds, mock(MemberTransport.class));
        
        ProposalNumber pn = new ProposalNumber("M1:1");
        
        node1.getProposer().propose("testValue");
        
        node1.getProposer().handlePromise(new Promise("M1", pn, null, null));
        node1.getProposer().handlePromise(new Promise("M2", pn, null, null));
        
        String output = outputStream.toString();
        assertTrue(output.contains("Starting proposal M1:1 with value 'testValue'"));
        assertTrue(output.contains("Sent Accept Request"));
    }

    @Test
    public void testDifferentSenders() {
        String prepareJson = "{\"type\":\"PREPARE\",\"fromMemberId\":\"SENDER1\",\"proposalNum\":{\"proposerId\":2,\"sequence\":1}}";
        
        paxosNode.handleMessage("SENDER1", prepareJson);
        paxosNode.handleMessage("SENDER2", prepareJson);
        paxosNode.handleMessage("SENDER3", prepareJson);
        
        String output = outputStream.toString();
        assertTrue(output.contains("from Proposer SENDER1"));
        assertTrue(output.contains("from Proposer SENDER2"));
        assertTrue(output.contains("from Proposer SENDER3"));
    }

    @Test
    public void testConcurrentMessageProcessing() throws InterruptedException {
        String prepareJson = "{\"type\":\"PREPARE\",\"fromMemberId\":\"M2\",\"proposalNum\":{\"proposerId\":2,\"sequence\":1}}";
        
        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            final int senderId = i;
            threads[i] = new Thread(() -> {
                paxosNode.handleMessage("SENDER" + senderId, prepareJson);
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        String output = outputStream.toString();
        for (int i = 0; i < 10; i++) {
            assertTrue(output.contains("from Proposer SENDER" + i));
        }
    }

    @Test
    public void testMemberIdConsistency() {
        assertEquals("M1", paxosNode.getMemberId());
        
        PaxosNode node2 = new PaxosNode("M2", acceptorIds, learnerIds, mockTransport);
        PaxosNode node3 = new PaxosNode("M3", acceptorIds, learnerIds, mockTransport);
        
        assertEquals("M2", node2.getMemberId());
        assertEquals("M3", node3.getMemberId());
    }
}
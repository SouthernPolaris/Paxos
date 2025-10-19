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

public class ProposerTest {

    @Mock
    private MemberTransport mockTransport;

    private Proposer proposer;
    private Set<String> acceptorIds;
    private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        acceptorIds = new HashSet<>();
        acceptorIds.add("M1");
        acceptorIds.add("M2");
        acceptorIds.add("M3");
        acceptorIds.add("M4");
        acceptorIds.add("M5");
        
        proposer = new Proposer("M1", acceptorIds, mockTransport);
        System.setOut(new PrintStream(outputStream));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
    }

    @Test
    public void testSequenceNumberIncrement() {
        proposer.propose("value1");
        reset(mockTransport);
        proposer.propose("value2");
        
        // Check that second proposal has higher sequence number
        verify(mockTransport).sendMessage(eq("M1"), argThat(msg -> {
            if (msg instanceof Prepare prepare) {
                return prepare.proposalNum.toString().equals("M1:2");
            }
            return false;
        }));
        
        String output = outputStream.toString();
        assertTrue(output.contains("Starting proposal M1:1"));
        assertTrue(output.contains("Starting proposal M1:2"));
    }

    @Test
    public void testSendAcceptRequestOnMajorityPromises() {
        String value = "testValue";
        proposer.propose(value);
        
        ProposalNumber pn = new ProposalNumber("M1:1");
        
        proposer.handlePromise(new Promise("M1", pn, null, null));
        proposer.handlePromise(new Promise("M2", pn, null, null));
        
        reset(mockTransport); 
        proposer.handlePromise(new Promise("M3", pn, null, null));
        
        for (String acceptorId : acceptorIds) {
            verify(mockTransport).sendMessage(eq(acceptorId), any(AcceptRequest.class));
        }
        
        String output = outputStream.toString();
        assertTrue(output.contains("Sent Accept Request for M1:1 with value 'testValue'"));
    }

    @Test
    public void testAdoptHigherAcceptedValue() {
        String originalValue = "originalValue";
        String higherValue = "higherValue";
        
        proposer.propose(originalValue);
        
        ProposalNumber pn = new ProposalNumber("M1:1");
        ProposalNumber lowerAccepted = new ProposalNumber("M1:1");
        ProposalNumber higherAccepted = new ProposalNumber("M1:2");
        
        proposer.handlePromise(new Promise("M1", pn, lowerAccepted.toString(), "lowerValue"));
        proposer.handlePromise(new Promise("M2", pn, higherAccepted.toString(), higherValue));
        
        reset(mockTransport);
        proposer.handlePromise(new Promise("M3", pn, null, null)); // Trigger majority
        
        verify(mockTransport, atLeastOnce()).sendMessage(any(), argThat(msg -> {
            if (msg instanceof AcceptRequest acceptRequest) {
                return acceptRequest.proposalValue.equals(higherValue);
            }
            return false;
        }));
        
        String output = outputStream.toString();
        assertTrue(output.contains("Updated proposal value to 'higherValue'"));
    }

    @Test
    public void testIgnoreWrongProposalNumberPromises() {
        proposer.propose("testValue");
        
        ProposalNumber wrongPN = new ProposalNumber("M1:2");
        
        proposer.handlePromise(new Promise("M1", wrongPN, null, null));
        proposer.handlePromise(new Promise("M2", wrongPN, null, null));
        proposer.handlePromise(new Promise("M3", wrongPN, null, null));
        
        reset(mockTransport);
        
        verify(mockTransport, never()).sendMessage(any(), any(AcceptRequest.class));
        
        String output = outputStream.toString();
        assertTrue(output.contains("Ignored Promise for M1:2 (expected M1:1)"));
    }

    @Test
    public void testNotifyLearnersOnMajorityAccepted() {
        String value = "testValue";
        proposer.propose(value);
        
        ProposalNumber pn = new ProposalNumber("M1:1");
        
        proposer.handlePromise(new Promise("M1", pn, null, null));
        proposer.handlePromise(new Promise("M2", pn, null, null));
        proposer.handlePromise(new Promise("M3", pn, null, null));
        
        proposer.handleAccepted(new Accepted("M1", pn, value));
        proposer.handleAccepted(new Accepted("M2", pn, value));
        
        reset(mockTransport);
        proposer.handleAccepted(new Accepted("M3", pn, value));
        
        for (String acceptorId : acceptorIds) {
            verify(mockTransport).sendMessage(eq(acceptorId), any(Accepted.class));
        }
        
        String output = outputStream.toString();
        assertTrue(output.contains("Proposal M1:1 with value 'testValue' chosen by majority"));
        assertTrue(output.contains("Notifying learners about chosen proposal"));
    }

    @Test
    public void testConcurrentProposals() {
        proposer.propose("value1");
        
        proposer.propose("value2");
        
        String output = outputStream.toString();
        assertTrue(output.contains("Starting proposal M1:1 with value 'value1'"));
        assertTrue(output.contains("Starting proposal M1:2 with value 'value2'"));
        
        verify(mockTransport, atLeast(10)).sendMessage(any(), any(Prepare.class)); // 5 acceptors × 2 proposals
    }

    @Test
    public void testThreadSafety() throws InterruptedException {
        Thread[] threads = new Thread[5];
        for (int i = 0; i < 5; i++) {
            final int valueNum = i;
            threads[i] = new Thread(() -> {
                proposer.propose("value" + valueNum);
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        verify(mockTransport, atLeast(25)).sendMessage(any(), any(Prepare.class)); // 5 acceptors × 5 proposals
    }

    @Test
    public void testDuplicatePromises() {
        proposer.propose("testValue");
        
        ProposalNumber pn = new ProposalNumber("M1:1");
        
        proposer.handlePromise(new Promise("M1", pn, null, null));
        proposer.handlePromise(new Promise("M1", pn, null, null)); // Duplicate
        proposer.handlePromise(new Promise("M2", pn, null, null));
        
        reset(mockTransport);
        verify(mockTransport, never()).sendMessage(any(), any(AcceptRequest.class));
        
        proposer.handlePromise(new Promise("M3", pn, null, null)); // Third unique promise
        
        verify(mockTransport, atLeastOnce()).sendMessage(any(), any(AcceptRequest.class));
    }
}
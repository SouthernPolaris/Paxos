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

public class AcceptorTest {

    @Mock
    private MemberTransport mockTransport;

    private Acceptor acceptor;
    private Set<String> learnerIds;
    private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        learnerIds = new HashSet<>();
        learnerIds.add("M1");
        learnerIds.add("M2");
        learnerIds.add("M3");
        
        acceptor = new Acceptor("M1", mockTransport, learnerIds);
        System.setOut(new PrintStream(outputStream));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
    }

    @Test
    public void testFirstPrepareRequest() {
        ProposalNumber pn = new ProposalNumber("M1:1");
        Prepare prepare = new Prepare("M1", pn);

        acceptor.handlePrepare(prepare, "M1");

        verify(mockTransport).sendMessage(eq("M1"), any(Promise.class));
        
        String output = outputStream.toString();
        assertTrue(output.contains("Received Prepare(M1:1) from Proposer M1"));
        assertTrue(output.contains("Sent Promise for M1:1 to Proposer M1"));
    }

    @Test
    public void testHigherNumberedPrepareRequest() {
        ProposalNumber pn1 = new ProposalNumber("M1:1");
        ProposalNumber pn2 = new ProposalNumber("M1:2");
        
        acceptor.handlePrepare(new Prepare("M1", pn1), "M1");
        
        acceptor.handlePrepare(new Prepare("M2", pn2), "M2");

        verify(mockTransport).sendMessage(eq("M1"), any(Promise.class));
        verify(mockTransport).sendMessage(eq("M2"), any(Promise.class));
    }

    @Test
    public void testLowerNumberedPrepareRequest() {
        ProposalNumber pn1 = new ProposalNumber("M1:2");
        ProposalNumber pn2 = new ProposalNumber("M1:1");
        
        acceptor.handlePrepare(new Prepare("M1", pn1), "M1");
        reset(mockTransport);
        
        acceptor.handlePrepare(new Prepare("M2", pn2), "M2");

        verify(mockTransport, never()).sendMessage(eq("M2"), any(Promise.class));
        
        String output = outputStream.toString();
        assertTrue(output.contains("Ignored Prepare(M1:1), promised number is M1:2"));
    }

    @Test
    public void testValidAcceptRequest() {
        ProposalNumber pn = new ProposalNumber("M1:1");
        String value = "testValue";
        
        acceptor.handlePrepare(new Prepare("M1", pn), "M1");
        
        AcceptRequest acceptRequest = new AcceptRequest("M1", pn, value);
        acceptor.handleAcceptRequest(acceptRequest, "M1");

        verify(mockTransport).sendMessage(eq("M1"), any(Accepted.class));
        
        for (String learnerId : learnerIds) {
            verify(mockTransport).sendMessage(eq(learnerId), any(Accepted.class));
        }
        
        String output = outputStream.toString();
        assertTrue(output.contains("Accepted proposal M1:1 with value 'testValue'"));
    }

    @Test
    public void testIgnoreLowerAcceptRequest() {
        ProposalNumber pn1 = new ProposalNumber("M1:2");
        ProposalNumber pn2 = new ProposalNumber("M1:1");
        
        acceptor.handlePrepare(new Prepare("M1", pn1), "M1");
        reset(mockTransport);
        
        AcceptRequest acceptRequest = new AcceptRequest("M2", pn2, "value");
        acceptor.handleAcceptRequest(acceptRequest, "M2");

        verifyNoInteractions(mockTransport);
        
        String output = outputStream.toString();
        assertTrue(output.contains("Ignored AcceptRequest(M1:1), promised number is M1:2"));
    }

    @Test
    public void testPromiseWithPreviouslyAcceptedValue() {
        ProposalNumber pn1 = new ProposalNumber("M1:1");
        ProposalNumber pn2 = new ProposalNumber("M1:2");
        String acceptedValue = "previousValue";
        
        acceptor.handlePrepare(new Prepare("M1", pn1), "M1");
        acceptor.handleAcceptRequest(new AcceptRequest("M1", pn1, acceptedValue), "M1");
        
        acceptor.handlePrepare(new Prepare("M2", pn2), "M2");

        verify(mockTransport, atLeastOnce()).sendMessage(eq("M2"), argThat(msg -> {
            if (msg instanceof Promise promise) {
                return promise.acceptedProposalNumber != null && 
                       promise.acceptedProposalValue != null &&
                       promise.acceptedProposalValue.equals(acceptedValue);
            }
            return false;
        }));
    }

    @Test
    public void testPromiseWithoutPreviousAcceptance() {
        ProposalNumber pn = new ProposalNumber("M1:1");
        
        acceptor.handlePrepare(new Prepare("M1", pn), "M1");

        verify(mockTransport).sendMessage(eq("M1"), argThat(msg -> {
            if (msg instanceof Promise promise) {
                return promise.acceptedProposalNumber == null && 
                       promise.acceptedProposalValue == null;
            }
            return false;
        }));
    }

    @Test
    public void testMultipleAcceptorsScenario() {
        ProposalNumber pn1 = new ProposalNumber("M1:1");
        ProposalNumber pn2 = new ProposalNumber("M2:1");
        
        acceptor.handlePrepare(new Prepare("M1", pn1), "M1");
        acceptor.handlePrepare(new Prepare("M2", pn2), "M2");
        
        verify(mockTransport).sendMessage(eq("M1"), any(Promise.class));
        verify(mockTransport).sendMessage(eq("M2"), any(Promise.class));
    }

    @Test
    public void testThreadSafety() throws InterruptedException {
        ProposalNumber pn = new ProposalNumber("M1:1");
        
        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            final int proposerId = i;
            threads[i] = new Thread(() -> {
                acceptor.handlePrepare(new Prepare("P" + proposerId, pn), "P" + proposerId);
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        for (int i = 0; i < 10; i++) {
            verify(mockTransport).sendMessage(eq("P" + i), any(Promise.class));
        }
    }
}
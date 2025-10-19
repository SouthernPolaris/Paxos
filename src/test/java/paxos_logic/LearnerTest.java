package paxos_logic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;

import paxos_util.Accepted;
import paxos_util.ProposalNumber;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

public class LearnerTest {

    private Learner learner;
    private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;

    @BeforeEach
    void setUp() {
        learner = new Learner("M1", 5);
        System.setOut(new PrintStream(outputStream));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
    }

    @Test
    public void testNotLearnUntilMajority() {
        ProposalNumber pn = new ProposalNumber("M1:1");
        String value = "testValue";

        learner.handleAccepted(new Accepted("M1", pn, value));
        learner.handleAccepted(new Accepted("M2", pn, value));

        String output = outputStream.toString();
        assertFalse(output.contains("has learned the value"));
    }

    @Test
    public void testLearnWhenMajorityReached() {
        ProposalNumber pn = new ProposalNumber("M1:1");
        String value = "testValue";

        learner.handleAccepted(new Accepted("M1", pn, value));
        learner.handleAccepted(new Accepted("M2", pn, value));
        learner.handleAccepted(new Accepted("M3", pn, value));

        String output = outputStream.toString();
        assertTrue(output.contains("Learner M1 has learned the value: testValue"));
        assertTrue(output.contains("for proposal number: M1:1"));
    }

    @Test
    public void testDifferentProposalNumbers() {
        ProposalNumber pn1 = new ProposalNumber("M1:1");
        ProposalNumber pn2 = new ProposalNumber("M1:2");
        String value1 = "value1";
        String value2 = "value2";

        // Send accepted for first proposal (not majority)
        learner.handleAccepted(new Accepted("M1", pn1, value1));
        learner.handleAccepted(new Accepted("M2", pn1, value1));

        // Send accepted for second proposal (not majority)
        learner.handleAccepted(new Accepted("M3", pn2, value2));
        learner.handleAccepted(new Accepted("M4", pn2, value2));

        String output = outputStream.toString();
        assertFalse(output.contains("has learned the value"));

        learner.handleAccepted(new Accepted("M5", pn1, value1));

        output = outputStream.toString();
        assertTrue(output.contains("has learned the value: value1"));
        assertTrue(output.contains("for proposal number: M1:1"));
    }

    @Test
    public void testMajorityComputation() {
        
        Learner learner3 = new Learner("M1", 3);
        ProposalNumber pn = new ProposalNumber("M1:1");
        String value = "test";

        learner3.handleAccepted(new Accepted("M1", pn, value));
        outputStream.reset();
        learner3.handleAccepted(new Accepted("M2", pn, value));
        
        String output = outputStream.toString();
        assertTrue(output.contains("has learned the value"));

        outputStream.reset();
        Learner learner4 = new Learner("M1", 4);
        learner4.handleAccepted(new Accepted("M1", pn, value));
        learner4.handleAccepted(new Accepted("M2", pn, value));
        
        output = outputStream.toString();
        assertFalse(output.contains("has learned the value"));
        
        learner4.handleAccepted(new Accepted("M3", pn, value));
        output = outputStream.toString();
        assertTrue(output.contains("has learned the value"));
    }

    @Test
    public void testSingleAcceptor() {
        Learner singleLearner = new Learner("M1", 1);
        ProposalNumber pn = new ProposalNumber("M1:1");
        String value = "singleValue";

        singleLearner.handleAccepted(new Accepted("M1", pn, value));

        String output = outputStream.toString();
        assertTrue(output.contains("has learned the value: singleValue"));
    }

    @Test
    public void testThreadSafety() throws InterruptedException {
        ProposalNumber pn = new ProposalNumber("M1:1");
        String value = "concurrentValue";

        Thread[] threads = new Thread[5];
        for (int i = 0; i < 5; i++) {
            final int acceptorId = i + 1;
            threads[i] = new Thread(() -> {
                learner.handleAccepted(new Accepted("M" + acceptorId, pn, value));
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        String output = outputStream.toString();
        assertTrue(output.contains("has learned the value: concurrentValue"));
        
        int learnCount = output.split("has learned the value").length - 1;
        assertEquals(1, learnCount);
    }

}
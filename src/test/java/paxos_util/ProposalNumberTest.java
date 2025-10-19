package paxos_util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ProposalNumberTest {

    @Test
    public void testCompareToBySequence() {
        ProposalNumber pn1 = new ProposalNumber("M1:5");
        ProposalNumber pn2 = new ProposalNumber("M2:3");
        ProposalNumber pn3 = new ProposalNumber("M3:7");

        assertTrue(pn1.compareTo(pn2) > 0);
        assertTrue(pn2.compareTo(pn1) < 0);
        assertTrue(pn3.compareTo(pn1) > 0);
        assertTrue(pn1.compareTo(pn3) < 0);
    }

    @Test
    public void testCompareToTiebreaker() {
        ProposalNumber pn1 = new ProposalNumber("M1:5");
        ProposalNumber pn2 = new ProposalNumber("M3:5");
        ProposalNumber pn3 = new ProposalNumber("M2:5");

        assertTrue(pn1.compareTo(pn2) < 0);
        assertTrue(pn2.compareTo(pn1) > 0);
        assertTrue(pn1.compareTo(pn3) < 0);
        assertTrue(pn3.compareTo(pn2) < 0);
    }

    @Test
    public void testCompareToEqual() {
        ProposalNumber pn1 = new ProposalNumber("M3:5");
        ProposalNumber pn2 = new ProposalNumber("M3:5");

        assertEquals(0, pn1.compareTo(pn2));
        assertEquals(0, pn2.compareTo(pn1));
    }

    @Test
    public void testEquals() {
        ProposalNumber pn1 = new ProposalNumber("M3:5");
        ProposalNumber pn2 = new ProposalNumber("M3:5");
        ProposalNumber pn3 = new ProposalNumber("M3:6");
        ProposalNumber pn4 = new ProposalNumber("M4:5");

        assertEquals(pn1, pn1);

        assertEquals(pn1, pn2);
        assertEquals(pn2, pn1);

        assertNotEquals(pn1, pn3); // Different sequence
        assertNotEquals(pn1, pn4); // Different proposer ID
        assertNotEquals(pn1, null);
        assertNotEquals(pn1, "not a ProposalNumber");
    }

    @Test
    public void testEdgeCases() {
        ProposalNumber pn1 = new ProposalNumber("M0:0");
        assertEquals(0, pn1.proposerId);
        assertEquals(0, pn1.sequence);

        ProposalNumber pn2 = new ProposalNumber("M999:999999");
        assertEquals(999, pn2.proposerId);
        assertEquals(999999, pn2.sequence);
    }

}
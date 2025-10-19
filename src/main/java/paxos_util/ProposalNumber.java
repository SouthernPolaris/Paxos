package paxos_util;

/**
 * Proposal Number used in Paxos
 *
 * Format: "M<proposerId>:<sequenceNumber>"
 * Example: "M1:5" represents proposer ID 1 and sequence number 5
 */
public class ProposalNumber implements Comparable<ProposalNumber> {
    public final int proposerId;
    public final int sequence;

    public ProposalNumber(String raw) {
        String[] parts = raw.split(":");
        this.proposerId = Integer.parseInt(parts[0].replace("M", ""));
        this.sequence = Integer.parseInt(parts[1]);
    }

    /**
     * Compares this ProposalNumber with another
     * @param other The other ProposalNumber to compare against
     * @return comparison result
     */
    @Override
    public int compareTo(ProposalNumber other) {
        // First compare sequence number
        if (this.sequence != other.sequence)
            return Integer.compare(this.sequence, other.sequence);
        // Tie-breaker: proposer ID
        return Integer.compare(this.proposerId, other.proposerId);
    }

    /**
     * String representation of ProposalNumber
     * @return String format "M<proposerId>:<sequenceNumber>"
     */
    @Override
    public String toString() {
        return "M" + proposerId + ":" + sequence;
    }

    /**
     * Equality check based on proposerId and sequence
     * @param obj The object to compare with
     * @return true if equal, false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ProposalNumber)) return false;
        ProposalNumber other = (ProposalNumber) obj;
        return this.proposerId == other.proposerId && this.sequence == other.sequence;
    }

    /**
     * Hash code based on proposerId and sequence
     * @return hash code
     */
    @Override
    public int hashCode() {
        return 31 * proposerId + sequence;
    }
}

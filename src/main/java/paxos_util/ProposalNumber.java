package paxos_util;

public class ProposalNumber implements Comparable<ProposalNumber> {
    public final int proposerId;
    public final int sequence;

    public ProposalNumber(String raw) {
        String[] parts = raw.split(":");
        this.proposerId = Integer.parseInt(parts[0].replace("M", ""));
        this.sequence = Integer.parseInt(parts[1]);
    }

    @Override
    public int compareTo(ProposalNumber other) {
        // First compare sequence number
        if (this.sequence != other.sequence)
            return Integer.compare(this.sequence, other.sequence);
        // Tie-breaker: proposer ID
        return Integer.compare(this.proposerId, other.proposerId);
    }

    @Override
    public String toString() {
        return "M" + proposerId + ":" + sequence;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ProposalNumber)) return false;
        ProposalNumber other = (ProposalNumber) obj;
        return this.proposerId == other.proposerId && this.sequence == other.sequence;
    }

    @Override
    // TODO: Double check if necessary
    public int hashCode() {
        return 31 * proposerId + sequence;
    }

}

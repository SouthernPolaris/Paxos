package paxos_util;

public class Accepted {
    public final ProposalNumber proposalNumber;
    public final String proposalValue;

    public final Integer fromMemberId;

    public Accepted(Integer fromMemberId, ProposalNumber proposalNumber, String proposalValue) {
        this.fromMemberId = fromMemberId;
        this.proposalNumber = proposalNumber;
        this.proposalValue = proposalValue;
    }
}

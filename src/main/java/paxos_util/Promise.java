package paxos_util;

public class Promise {
    public final ProposalNumber proposalNumber;
    public final String acceptedProposalNumber;
    public final Integer acceptedProposalValue;

    public final Integer fromMemberId;

    public Promise(ProposalNumber proposalNumber, String acceptedProposalNumber, Integer acceptedProposalValue, Integer fromMemberId) {
        this.proposalNumber = proposalNumber;
        this.acceptedProposalNumber = acceptedProposalNumber;
        this.acceptedProposalValue = acceptedProposalValue;
        this.fromMemberId = fromMemberId;
    }
}

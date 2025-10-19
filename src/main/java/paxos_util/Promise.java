package paxos_util;

public class Promise {
    public String type = "PROMISE";
    public final ProposalNumber proposalNumber;
    public final String acceptedProposalNumber;
    public final String acceptedProposalValue;

    public final Integer fromMemberId;

    public Promise(ProposalNumber proposalNumber, String acceptedProposalNumber, String acceptedProposalValue, Integer fromMemberId) {
        this.proposalNumber = proposalNumber;
        this.acceptedProposalNumber = acceptedProposalNumber;
        this.acceptedProposalValue = acceptedProposalValue;
        this.fromMemberId = fromMemberId;
    }
}

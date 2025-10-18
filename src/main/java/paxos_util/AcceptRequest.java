package paxos_util;

public class AcceptRequest {
    public final ProposalNumber proposalNumber;
    
    public final String proposalValue;

    public AcceptRequest(ProposalNumber proposalNumber, String proposalValue) {
        this.proposalNumber = proposalNumber;
        this.proposalValue = proposalValue;
    }
}

package paxos_util;

public class Promise extends PaxosMessage {
    public String acceptedProposalNumber; // highest accepted proposal number (can be null)
    public String acceptedProposalValue;  // value of that proposal (can be null)

    public Promise(String fromMemberId, ProposalNumber proposalNumber, String acceptedProposalNumber, String acceptedProposalValue) {
        super("PROMISE", fromMemberId, proposalNumber, null);
        this.acceptedProposalNumber = acceptedProposalNumber;
        this.acceptedProposalValue = acceptedProposalValue;
    }
}

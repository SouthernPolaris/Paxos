package paxos_util;

public class PaxosMessage {
    public String type;        // PREPARE, PROMISE, ACCEPT_REQUEST, ACCEPTED
    public String fromMemberId;
    public ProposalNumber proposalNum;
    public String proposalValue;

    public PaxosMessage() {}
    public PaxosMessage(String type, String fromMemberId, ProposalNumber proposalNum, String proposalValue) {
        this.type = type;
        this.fromMemberId = fromMemberId;
        this.proposalNum = proposalNum;
        this.proposalValue = proposalValue;
    }
}

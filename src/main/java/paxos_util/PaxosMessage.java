package paxos_util;

/**
 * Base class for Paxos messages
 */
public class PaxosMessage {
    public String type;
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

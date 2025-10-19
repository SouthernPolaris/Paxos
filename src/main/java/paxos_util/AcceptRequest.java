package paxos_util;

public class AcceptRequest extends PaxosMessage {
    public AcceptRequest(String fromMemberId, ProposalNumber proposalNumber, String proposalValue) {
        super("ACCEPT_REQUEST", fromMemberId, proposalNumber, proposalValue);
    }
}

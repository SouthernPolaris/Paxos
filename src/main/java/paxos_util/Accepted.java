package paxos_util;

public class Accepted extends PaxosMessage {
    public Accepted(String fromMemberId, ProposalNumber proposalNumber, String proposalValue) {
        super("ACCEPTED", fromMemberId, proposalNumber, proposalValue);
    }
}

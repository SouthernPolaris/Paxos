package paxos_util;

public class Prepare extends PaxosMessage {
    public Prepare(String fromMemberId, ProposalNumber proposalNumber) {
        super("PREPARE", fromMemberId, proposalNumber, null);
    }
}

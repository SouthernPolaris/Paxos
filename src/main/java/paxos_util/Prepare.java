package paxos_util;

/**
 * Prepare Message
 *
 * Sent by Proposers to Acceptors to start proposal process
 */
public class Prepare extends PaxosMessage {
    public Prepare(String fromMemberId, ProposalNumber proposalNumber) {
        super("PREPARE", fromMemberId, proposalNumber, null);
    }
}

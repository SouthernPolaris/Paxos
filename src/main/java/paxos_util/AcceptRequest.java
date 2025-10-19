package paxos_util;

/**
 * Accept Request Message
 *
 * Sent by Proposers to Acceptors to request acceptance of a proposal
 */
public class AcceptRequest extends PaxosMessage {
    public AcceptRequest(String fromMemberId, ProposalNumber proposalNumber, String proposalValue) {
        super("ACCEPT_REQUEST", fromMemberId, proposalNumber, proposalValue);
    }
}

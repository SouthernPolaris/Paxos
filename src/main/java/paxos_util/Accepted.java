package paxos_util;

/**
 * Accepted Message
 *
 * Sent by Proposers to notify learners of the chosen proposal
 */
public class Accepted extends PaxosMessage {
    public Accepted(String fromMemberId, ProposalNumber proposalNumber, String proposalValue) {
        super("ACCEPTED", fromMemberId, proposalNumber, proposalValue);
    }
}

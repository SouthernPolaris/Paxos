package paxos_util;

/**
 * Promise Message
 *
 * Sent by Acceptors to Proposers responding to Prepare messages
 */
public class Promise extends PaxosMessage {
    public String acceptedProposalNumber; 
    public String acceptedProposalValue;

    public Promise(String fromMemberId, ProposalNumber proposalNumber, String acceptedProposalNumber, String acceptedProposalValue) {
        super("PROMISE", fromMemberId, proposalNumber, null);
        this.acceptedProposalNumber = acceptedProposalNumber;
        this.acceptedProposalValue = acceptedProposalValue;
    }
}

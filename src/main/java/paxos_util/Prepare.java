package paxos_util;

public class Prepare {
    public String type = "PREPARE";
    public final ProposalNumber proposalNumber;

    public Prepare(ProposalNumber proposalNumber) {
        this.proposalNumber = proposalNumber;
    }
}

package member;

import network.NetworkTransport;
import paxos_logic.PaxosServerImpl;

public class Member {
    private final String memberId;
    private final NetworkTransport networkTransport;

    private String lastProposalId = null;
    private String lastProposalNumber = null;

    private String acceptedProposalId = null;
    private String acceptedProposalNumber = null;

    private final String proposalValue;
    private Integer numProposals = 0;

    private final PaxosServerImpl paxosServer;

    private int delay = 0;
    private boolean isDead;
    private boolean isFaulty;

    public Member(String memberId, NetworkTransport networkTransport, PaxosServerImpl paxosServer) {
        this.memberId = memberId;
        this.networkTransport = networkTransport;
        this.paxosServer = paxosServer;
        this.proposalValue = memberId;

        switch (Integer.parseInt(memberId)) {
            case 1:
                this.delay = 0;
                break;

            case 2:
                this.delay = 10000;
                break;

            default:
                this.delay = (int)(Math.random() * 5000) + 4000; // 4-9 seconds
                break;
        }
    }

    public String getMemberId() {
        return memberId;
    }

    public NetworkTransport getNetworkTransport() {
        return networkTransport;
    }

    public PaxosServerImpl getPaxosServer() {
        return paxosServer;
    }

    public int getDelay() {
        return delay;
    }

    public String getProposalValue() {
        return proposalValue;
    }

    public String getAcceptedProposalId() {
        return acceptedProposalId;
    }

    public String getAcceptedProposalNum() {
        return acceptedProposalNumber;
    }

    public String createProposalNumber() {
        numProposals++;
        lastProposalNumber = String.format("%d:%s", numProposals, memberId);
        return lastProposalNumber;
    }

    public void setMaxProposalNum(String proposalNum) {
        this.lastProposalNumber = proposalNum;
    }

    public String getMaxProposalNum() {
        return lastProposalNumber;
    }
}

package member;

import java.util.concurrent.locks.ReentrantLock;

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

    public ReentrantLock lock = new ReentrantLock();

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

    public String getAcceptedProposalNumber() {
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

    public void setAcceptedProposal(String currProposalNum, String proposalId, String proposalNum) {
        boolean isObjectAcceptedNull = this.acceptedProposalNumber == null && this.acceptedProposalId == null;

        if (isObjectAcceptedNull || paxosServer.checkProposals(this.acceptedProposalNumber, currProposalNum)) {
            this.acceptedProposalId = proposalId;
            this.acceptedProposalNumber = proposalNum;
        }
    }

    public void setDelay(int delay) {
        this.delay = delay;
    }

    private boolean disconnect() {
        if(isDead) {
            return true;
        }

        if (isFaulty && Integer.parseInt(this.memberId) == 3) {
            return (Math.random() * 100 < 5.0); // 5% chance of failure
        }

        return false;
    }

    public void setDead() {
        isDead = true;
    }

    public void setFaulty() {
        isFaulty = true;
    }

    
    public void sendPrepareMessage() {
        if (disconnect()) {
            System.out.println(String.format("[%s] (disconnected - cannot send PREPARE)", memberId));
            return;
        }

        try {
            Thread.sleep(delay);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(String.format("[%s] Error during delay before sending PREPARE", memberId));
            Thread.currentThread().interrupt();
            return;
        }

        String proposalNumber = createProposalNumber();
        String prepareMessage = String.format("PREPARE %s", proposalNumber);

        try {
            paxosServer.broadcastMessage(prepareMessage);
            System.out.println(String.format("[%s] Sent PREPARE for proposal %s", memberId, proposalNumber));
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(String.format("[%s] Error sending PREPARE message", memberId));
        }
    }

    public void sendPromiseMessage(String proposalId, String proposalNum, String acceptedProposalNum) {
        if (disconnect()) {
            System.out.println(String.format("[%s] (disconnected - cannot send PROMISE)", memberId));
            return;
        }

        try {
            Thread.sleep(delay);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(String.format("[%s] Error during delay before sending PROMISE", memberId));
            Thread.currentThread().interrupt();
        }

        String promiseMessage;
        if (acceptedProposalNum != null) {
            promiseMessage = String.format("PROMISE %s %s %s", proposalNum, acceptedProposalId, acceptedProposalNum);
        } else {
            promiseMessage = String.format("PROMISE %s", proposalNum);
        }

        networkTransport.sendMessage(proposalId, promiseMessage);
    }

    public void sendAcceptRequestMessage(String proposalId, String proposalNum, String proposalValue) {
        lock.lock();

        if (disconnect()) {
            System.out.println(String.format("[%s] (disconnected - cannot send ACCEPT_REQUEST)", memberId));
            return;
        }

        try {
            Thread.sleep(delay);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(String.format("[%s] Error during delay before sending ACCEPT_REQUEST", memberId));
            Thread.currentThread().interrupt();
            return;
        }

        String acceptRequestMessage = String.format("ACCEPT %s %s", proposalNum, proposalValue);

        try {
            paxosServer.broadcastMessage(acceptRequestMessage);
            System.out.println(String.format("[%s] Sent ACCEPT_REQUEST for proposal %s with value %s", memberId, proposalNum, proposalValue));
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(String.format("[%s] Error sending ACCEPT_REQUEST message", memberId));
        }
    }

    public void sendAcceptedMessage(String proposalId, String proposalNum, String incomingAcceptedValue) {
        if (disconnect()) {
            System.out.println(String.format("[%s] (disconnected - cannot send ACCEPTED)", memberId));
            return;
        }

        try {
            Thread.sleep(delay);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(String.format("[%s] Error during delay before sending ACCEPTED", memberId));
            Thread.currentThread().interrupt();
        }

        String acceptedMessage = String.format("ACCEPTED %s %s", proposalNum, incomingAcceptedValue);

        networkTransport.sendMessage(proposalId, acceptedMessage);
    }

    public void sendRejectMessage(String proposalNum) {
        if (disconnect()) {
            System.out.println(String.format("[%s] (disconnected - cannot send REJECT)", memberId));
            return;
        }

        try {
            Thread.sleep(delay);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(String.format("[%s] Error during delay before sending REJECT", memberId));
            Thread.currentThread().interrupt();
        }

        String rejectMessage = String.format("REJECT %s", proposalNum);

        networkTransport.sendMessage(memberId, rejectMessage);
    }

    public void sendConsensusMessage(String proposalNumber, String proposalValue) {
        if (disconnect()) {
            System.out.println(String.format("[%s] (disconnected - cannot send CONSENSUS)", memberId));
            return;
        }

        try {
            Thread.sleep(delay);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(String.format("[%s] Error during delay before sending CONSENSUS", memberId));
            Thread.currentThread().interrupt();
            return;
        }

        String consensusMessage = String.format("CONSENSUS %s %s", proposalNumber, proposalValue);
        paxosServer.broadcastMessage(consensusMessage);
    }
}

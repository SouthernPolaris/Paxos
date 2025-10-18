package paxos_logic;

import java.net.Socket;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantLock;

import member.Member;
import network.NetworkTransport;

public class PaxosServerImpl {
    private String electedLeaderId = null;
    
    private List<Member> members;
    private Map<String, Integer> currentPromises;
    private Map<String, Integer> currentAccepted;

    NetworkTransport networkTransport;

    Map<String, Socket> memberSockets;
    Map<String, Integer> memberPorts;

    private final ReentrantLock lock = new ReentrantLock();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new HashMap<>();

    public PaxosServerImpl() {
        this.currentPromises = new HashMap<>();
        this.currentAccepted = new HashMap<>();

        this.memberSockets = new HashMap<>();
        this.memberPorts = new HashMap<>();
    }

    public void setMembers(List<Member> members, Map<String, Integer> memberPorts) {
        this.members = members;
        this.memberPorts = memberPorts;
    }

    public void setNetworkTransport(NetworkTransport networkTransport) {
        this.networkTransport = networkTransport;
    }

    public Map<String, Integer> getMemberPorts() {
        return this.memberPorts;
    }

    public Map<String, Socket> getMemberSockets() {
        return this.memberSockets;
    }

    public void handleMessage(String senderId, String message) {
        if (electedLeaderId == null) {
            shutdownConnections();
            return;
        }

        String[] parts = message.split(" ");
        String mesStringType = parts[0];

        switch (mesStringType) {
            case "PREPARE":
                handlePrepareMessage(senderId, message);
                break;
            case "PROMISE":
                handlePromiseMessage(senderId, message);
                break;
            case "ACCEPT":
                handleAcceptRequestMessage(senderId, message);
                break;
            case "ACCEPTED":
                handleAcceptedMessage(senderId, message);
                break;
            default:
                System.out.println("Unknown message type received: " + mesStringType);
        }
    }
    
    public void broadcastMessage(String message) {
        for (Member member : members) {
            networkTransport.sendMessage(member.getMemberId(), message);
        }
    }

    private void shutdownConnections() {
        lock.lock();
        try {
            for (Socket socket : memberSockets.values()) {
                try {
                    if (socket != null && !socket.isClosed()) {
                        socket.close();
                    }
                } catch (Exception e) {
                    System.err.println("Error closing socket: " + e.getMessage());
                }
            }
        } finally {
            lock.unlock();
            memberSockets.clear();
        }
    }

    private void handlePrepareMessage(String senderId, String message) {
        String[] parts = message.split(" ");
        String proposalNum = parts[1];
        String proposalId = proposalNum.split(":")[0];

        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String formattedNow = now.format(formatter);
        System.out.println("[" + formattedNow + "] Received PREPARE from " + senderId + " for proposal " + proposalNum);

        Member currMember = members.stream().filter(m -> m.getMemberId().equals(senderId)).findFirst().get();

        boolean proposalBoolean = checkProposals(proposalNum, currMember.getMaxProposalNum());

        if (currMember.getMaxProposalNum() == null || proposalBoolean) {
            currMember.setMaxProposalNum(proposalNum);

            if(currMember.getAcceptedProposalNumber() != null) {
                currMember.sendPromiseMessage(proposalId, proposalNum, currMember.getAcceptedProposalNumber());
            } else {
                currMember.sendPromiseMessage(proposalId, proposalNum, null);
            }
        } else {
            currMember.sendRejectMessage(proposalNum);
        }

        setProposalTimeout(proposalNum);
    }

    private void handlePromiseMessage(String senderId, String message) {
        lock.lock();

        try {
            String[] parts = message.split(" ");
            String proposalNum = parts[1];
            String proposalId = proposalNum.split(":")[0];

            Member currMember = members.stream().filter(m -> m.getMemberId().equals(senderId)).findFirst().get();

            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String formattedNow = now.format(formatter);
            System.out.println("Received PROMISE from " + senderId + " for proposal " + proposalNum + " at " + formattedNow);

            currentPromises.put(proposalNum, currentPromises.getOrDefault(proposalNum, 0) + 1);

            if (parts.length > 2) {
                String acceptedProposalId = parts[2];
                String acceptedProposalNumber = parts[3];
                currMember.setAcceptedProposal(proposalNum, acceptedProposalId, acceptedProposalNumber);
            }

            // Majority (out of 9 members)
            if (currentPromises.get(proposalNum) == 5) {
                now = LocalDateTime.now();
                formattedNow = now.format(formatter);
                System.out.println("[" + formattedNow + "] Received majority PROMISES for proposal " + proposalNum);
                cancelTimeoutEvent("proposal:" + proposalNum);
                String proposalValue;

                if(currMember.getAcceptedProposalNumber() != null) {
                    proposalValue = currMember.getAcceptedProposalNumber();
                } else {
                    proposalValue = null;
                }

                currMember.sendAcceptRequestMessage(proposalId, proposalNum, proposalValue);
            }

            // Additional logic for handling promises can be added here

        } finally {
            lock.unlock();
        }
    }

    private void handleAcceptRequestMessage(String senderId, String message) {
        String[] parts = message.split(" ");
        String proposalNum = parts[1];
        String proposalId = proposalNum.split(":")[0];

        Member currMember = members.stream().filter(m -> m.getMemberId().equals(senderId)).findFirst().get();

        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String formattedNow = now.format(formatter);
        System.out.println("[" + formattedNow + "] Received ACCEPT from " + senderId + " for proposal " + proposalNum);
        
        String proposalValue = parts[2];

        boolean proposalBoolean = checkProposals(proposalNum, currMember.getMaxProposalNum());

        if(currMember.getMaxProposalNum() != null || proposalBoolean) {
            currMember.setMaxProposalNum(proposalNum);
            currMember.sendAcceptedMessage(proposalId, proposalNum, proposalValue);
        } else {
            currMember.sendRejectMessage(proposalNum);
        }

        timeoutAcceptRequestMessage(proposalNum);
    }

    private void handleAcceptedMessage(String senderId, String message) {
        lock.lock();

        try {
            String[] parts = message.split(" ");
            String proposalNum = parts[1];
            String proposalId = proposalNum.split(":")[0];

            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String formattedNow = now.format(formatter);
            System.out.println("[" + formattedNow + "] Received ACCEPTED from " + senderId + " for proposal " + proposalNum);

            String proposerValue = parts[2];
            currentAccepted.put(proposalNum, currentAccepted.getOrDefault(proposalNum, 0) + 1);

            // Majority (out of 9 members)
            if (currentAccepted.get(proposalNum) == 5) {
                now = LocalDateTime.now();
                formattedNow = now.format(formatter);
                System.out.println("[" + formattedNow + "] Received majority ACCEPTED for proposal " + proposalNum);
                cancelTimeoutEvent("accept:" + proposalNum);
                now = LocalDateTime.now();
                formattedNow = now.format(formatter);
                System.out.println("[" + formattedNow + "] Consensus reached for proposal " + proposalNum
                        + ". Broadcasting CONSENSUS message.");
                electedLeaderId = proposerValue;

                shutdownConnections();
                networkTransport.shutdownSocket();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
    }

    public void setElectedLeaderId(String leaderId) {
        this.electedLeaderId = leaderId;
    }

    public String getElectedLeaderId() {
        return this.electedLeaderId;
    }
}
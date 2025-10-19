package paxos_logic;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import network.MemberTransport;
import paxos_util.*;

import java.util.Set;

public class PaxosNode {
    private final String memberId;
    private final Proposer proposer;
    private final Acceptor acceptor;
    private final Learner learner;
    private final MemberTransport memberTransport;

    private final Gson gson = new Gson();

    public PaxosNode(String memberId, Set<Integer> acceptorIds, Set<Integer> learnerIds, MemberTransport memberTransport) {
        this.memberId = memberId;
        this.memberTransport = memberTransport;

        int numericId = Integer.parseInt(memberId.replace("M", ""));
        this.proposer = new Proposer(numericId, acceptorIds, memberTransport);
        this.acceptor = new Acceptor(numericId, memberTransport, learnerIds);
        this.learner = new Learner(numericId, acceptorIds.size());

        if (memberTransport != null) {
            memberTransport.startListening();
        }
    }

    /**
     * Fully Gson-ready message dispatcher
     */
    public void handleMessage(String senderId, String message) {
        try {
            // Try to detect the message type by reading a "type" field
            BaseMessage base = gson.fromJson(message, BaseMessage.class);
            if (base == null || base.type == null) {
                System.out.println("[PaxosNode " + memberId + "] Unknown message format: " + message);
                return;
            }

            int numericSender = Integer.parseInt(senderId.replace("M", ""));

            switch (base.type) {
                case "PREPARE":
                    Prepare prepare = gson.fromJson(message, Prepare.class);
                    acceptor.handlePrepare(prepare, numericSender);
                    break;

                case "PROMISE":
                    Promise promise = gson.fromJson(message, Promise.class);
                    proposer.handlePromise(promise);
                    break;

                case "ACCEPT_REQUEST":
                    AcceptRequest acceptRequest = gson.fromJson(message, AcceptRequest.class);
                    acceptor.handleAcceptRequest(acceptRequest, numericSender);
                    break;

                case "ACCEPTED":
                    Accepted accepted = gson.fromJson(message, Accepted.class);
                    learner.handleAccepted(accepted);
                    break;

                default:
                    System.out.println("[PaxosNode " + memberId + "] Unknown message type: " + base.type);
            }
        } catch (JsonSyntaxException e) {
            System.out.println("[PaxosNode " + memberId + "] Failed to parse JSON message: " + e.getMessage());
        }
    }

    public Proposer getProposer() { 
        return proposer; 
    }

    public Acceptor getAcceptor() { 
        return acceptor; 
    }

    public Learner getLearner() { 
        return learner; 
    }
    
    public String getMemberId() { 
        return memberId; 
    }

    /**
     * Helper class to detect the type of incoming message
     */
    private static class BaseMessage {
        String type;
    }
}

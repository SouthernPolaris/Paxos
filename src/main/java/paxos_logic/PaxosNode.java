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

    public PaxosNode(String memberId, Set<String> acceptorIds, Set<String> learnerIds, MemberTransport memberTransport) {
        this.memberId = memberId;
        this.memberTransport = memberTransport;

        this.proposer = new Proposer(memberId, acceptorIds, memberTransport);
        this.acceptor = new Acceptor(memberId, memberTransport, learnerIds);
        this.learner = new Learner(memberId, acceptorIds.size());

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
            PaxosMessage base = gson.fromJson(message, PaxosMessage.class);
            if (base == null || base.type == null) {
                System.out.println("[PaxosNode " + memberId + "] Unknown message format: " + message);
                return;
            }

            switch (base.type) {
                case "PREPARE":
                    Prepare prepare = gson.fromJson(message, Prepare.class);
                    acceptor.handlePrepare(prepare, senderId);
                    break;

                case "PROMISE":
                    Promise promise = gson.fromJson(message, Promise.class);
                    proposer.handlePromise(promise);
                    break;

                case "ACCEPT_REQUEST":
                    AcceptRequest acceptRequest = gson.fromJson(message, AcceptRequest.class);
                    acceptor.handleAcceptRequest(acceptRequest, senderId);
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

    public void setTransport(MemberTransport transport) {
        if (transport == null) return;
        this.acceptor.setTransport(transport);
        this.proposer.setTransport(transport);
    }

}

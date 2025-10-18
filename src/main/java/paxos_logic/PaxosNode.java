package paxos_logic;

import network.MemberTransport;
import java.util.Set;
import paxos_util.*;

public class PaxosNode {
    private final String memberId;
    private final Proposer proposer;
    private final Acceptor acceptor;
    private final Learner learner;
    private final MemberTransport memberTransport;

    public PaxosNode(String memberId, Set<Integer> acceptorIds, Set<Integer> learnerIds, MemberTransport memberTransport) {
        this.memberId = memberId;
        this.memberTransport = memberTransport;

        this.proposer = new Proposer(Integer.parseInt(memberId.replace("M", "")), acceptorIds, memberTransport);
        this.acceptor = new Acceptor(Integer.parseInt(memberId.replace("M", "")), memberTransport, learnerIds);
        this.learner = new Learner(Integer.parseInt(memberId.replace("M", "")), acceptorIds.size());

        memberTransport.startListening();
    }

    public void handleMessage(String senderId, String message) {
        String[] parts = message.split(":");
        String messageType = parts[0];
        String proposalNum = parts[2];

        String value = parts.length > 4 ? parts[3] : null;

        switch (messageType) {
            case "PREPARE":
                Prepare prepare = new Prepare(new ProposalNumber(proposalNum));
                acceptor.handlePrepare(prepare, Integer.parseInt(senderId.replace("M", "")));
                break;

            case "PROMISE":
                Promise promise = new Promise(
                    new ProposalNumber(proposalNum),
                    parts[3],
                    parts[4].isEmpty() ? null : Integer.parseInt(parts[4]),
                    Integer.parseInt(senderId.replace("M", ""))
                );
                proposer.handlePromise(promise);
                break;

            case "ACCEPT_REQUEST":
                AcceptRequest acceptRequest = new AcceptRequest(new ProposalNumber(proposalNum), value);
                acceptor.handleAcceptRequest(acceptRequest, Integer.parseInt(senderId.replace("M", "")));
                break;

            case "ACCEPTED":
                Accepted accepted = new Accepted(
                    Integer.parseInt(senderId.replace("M", "")),
                    new ProposalNumber(proposalNum),
                    value
                );
                learner.handleAccepted(accepted);
                break;

            default:
                System.out.println("Unknown message type: " + messageType);
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
}

package paxos_logic;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

import network.MemberTransport;
import paxos_util.*;

/**
 * Paxos Acceptor
 *
 * Handles Prepare and Accept Request messages from Proposers and responds accordingly.
 */
public class Acceptor {
    private final String memberId;
    private final Set<String> learnerIds;
    
    private MemberTransport networkTransport;
    private final ReentrantLock lock = new ReentrantLock();

    private ProposalNumber promisedNumber = null;
    private ProposalNumber acceptedNumber = null;
    private String acceptedValue = null;

    public Acceptor(String memberId, MemberTransport networkTransport, Set<String> learnerIds) {
        this.memberId = memberId;
        this.networkTransport = networkTransport;
        this.learnerIds = learnerIds;
    }

    /**
     * Handles a Prepare message from a Proposer
     * @param prepare The Prepare message received
     * @param fromProposerId The ID of the Proposer who sent the message
     */
    public void handlePrepare(Prepare prepare, String fromProposerId) {
        lock.lock();

        try {
            ProposalNumber proposalNum = new ProposalNumber(prepare.proposalNum.toString());
            System.out.println("[Acceptor " + memberId + "] Received Prepare(" + proposalNum + ") from Proposer " + fromProposerId);

            if (promisedNumber == null || proposalNum.compareTo(promisedNumber) >= 0) {

                // Update if incoming greater
                if (promisedNumber == null || proposalNum.compareTo(promisedNumber) > 0) {
                    promisedNumber = proposalNum;
                }

                Promise promise = new Promise(
                    String.valueOf(memberId),
                    proposalNum,
                    acceptedNumber != null ? acceptedNumber.toString() : null,
                    acceptedValue
                );

                networkTransport.sendMessage(fromProposerId, promise);
                System.out.println("[Acceptor " + memberId + "] Sent Promise for " + proposalNum + " to Proposer " + fromProposerId);
            } else {
                System.out.println("[Acceptor " + memberId + "] Ignored Prepare(" + proposalNum + "), promised number is " + promisedNumber);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Handles an Accept Request from a Proposer
     * @param acceptRequest The Accept Request message received
     * @param fromProposerId The ID of the Proposer who sent the message
     */
    public void handleAcceptRequest(AcceptRequest acceptRequest, String fromProposerId) {
        lock.lock();

        try {
            ProposalNumber proposalNum = new ProposalNumber(acceptRequest.proposalNum.toString());
            System.out.println("[Acceptor " + memberId + "] Received AcceptRequest(" + proposalNum + ", '" + acceptRequest.proposalValue + "') from Proposer " + fromProposerId);

            if (promisedNumber == null || proposalNum.compareTo(promisedNumber) >= 0) {
                acceptedNumber = proposalNum;
                acceptedValue = acceptRequest.proposalValue;
                promisedNumber = proposalNum;

                Accepted acceptedMsg = new Accepted(memberId, proposalNum, acceptedValue);

                // Reply to proposer
                networkTransport.sendMessage(fromProposerId, acceptedMsg);

                // Notify all learners
                for (String learnerId : learnerIds) {
                    if (learnerId.equals(fromProposerId)) continue;
                    networkTransport.sendMessage(learnerId, acceptedMsg);
                }

                System.out.println("[Acceptor " + memberId + "] Accepted proposal " + proposalNum + " with value '" + acceptedValue + "'");
            } else {
                System.out.println("[Acceptor " + memberId + "] Ignored AcceptRequest(" + proposalNum + "), promised number is " + promisedNumber);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets the network transport for communication
     * @param transport The MemberTransport instance to use
     */
    public void setTransport(MemberTransport transport) {
        this.networkTransport = transport;
    }

}

package paxos_logic;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import network.MemberTransport;
import paxos_util.*;

/**
 * Paxos Acceptor
 *
 * Handles Prepare and Accept Request messages from Proposers and responds accordingly.
 */
public class Acceptor {
    private final int memberId;
    private final MemberTransport networkTransport;
    private final Set<Integer> learnerIds;

    private final ReentrantLock lock = new ReentrantLock();

    private ProposalNumber promisedNumber = null;
    private ProposalNumber acceptedNumber = null;
    private String acceptedValue = null;

    private final Gson gson = new GsonBuilder().create();

    public Acceptor(int memberId, MemberTransport networkTransport, Set<Integer> learnerIds) {
        this.memberId = memberId;
        this.networkTransport = networkTransport;
        this.learnerIds = learnerIds;
    }

    /**
     * Handles a Prepare message from a Proposer.
     */
    public void handlePrepare(Prepare prepare, int fromProposerId) {
        lock.lock();

        try {
            ProposalNumber proposalNum = new ProposalNumber(prepare.proposalNumber.toString());
            System.out.println("[Acceptor " + memberId + "] Received Prepare(" + proposalNum + ") from Proposer " + fromProposerId);

            if (promisedNumber == null || proposalNum.compareTo(promisedNumber) > 0) {
                promisedNumber = proposalNum;

                Promise promise = new Promise(
                    proposalNum,
                    acceptedNumber != null ? acceptedNumber.toString() : null,
                    acceptedValue,
                    memberId
                );

                String promiseJson = gson.toJson(promise);
                networkTransport.sendMessage(String.valueOf(fromProposerId), promiseJson);
                System.out.println("[Acceptor " + memberId + "] Sent Promise for " + proposalNum + " to Proposer " + fromProposerId);
            } else {
                System.out.println("[Acceptor " + memberId + "] Ignored Prepare(" + proposalNum + "), promised number is " + promisedNumber);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Handles an Accept Request from a Proposer.
     */
    public void handleAcceptRequest(AcceptRequest acceptRequest, int fromProposerId) {
        lock.lock();

        try {
            ProposalNumber proposalNum = new ProposalNumber(acceptRequest.proposalNumber.toString());
            System.out.println("[Acceptor " + memberId + "] Received AcceptRequest(" + proposalNum + ", '" + acceptRequest.proposalValue + "') from Proposer " + fromProposerId);

            if (promisedNumber == null || proposalNum.compareTo(promisedNumber) >= 0) {
                acceptedNumber = proposalNum;
                acceptedValue = acceptRequest.proposalValue;
                promisedNumber = proposalNum;

                Accepted acceptedMsg = new Accepted(memberId, proposalNum, acceptedValue);

                // Reply to proposer
                String acceptedJson = gson.toJson(acceptedMsg);
                networkTransport.sendMessage(String.valueOf(fromProposerId), acceptedJson);

                // Notify learners
                for (int learnerId : learnerIds) {
                    networkTransport.sendMessage(String.valueOf(learnerId), acceptedJson);
                }

                System.out.println("[Acceptor " + memberId + "] Accepted proposal " + proposalNum + " with value '" + acceptedValue + "'");
            } else {
                System.out.println("[Acceptor " + memberId + "] Ignored AcceptRequest(" + proposalNum + "), promised number is " + promisedNumber);
            }
        } finally {
            lock.unlock();
        }
    }
}

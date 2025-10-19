package paxos_logic;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import network.MemberTransport;
import paxos_util.*;

/**
 * Paxos Proposer
 *
 * Responsible for initiating proposals and handling promises/acceptances from Acceptors.
 */
public class Proposer {
    private final int id;
    private final Set<Integer> acceptorIds;
    private final MemberTransport networkTransport;

    private final ReentrantLock lock = new ReentrantLock();

    private final Gson gson = new GsonBuilder().create();

    private ProposalNumber proposalNumber;
    private String proposalValue;

    private final Map<Integer, Promise> promisesReceived = new ConcurrentHashMap<>();
    private final Set<Integer> acceptedReceivedFrom = ConcurrentHashMap.newKeySet();

    private int localSequence = 0;

    public Proposer(int id, Set<Integer> acceptorIds, MemberTransport networkTransport) {
        this.id = id;
        this.acceptorIds = acceptorIds;
        this.networkTransport = networkTransport;
        this.proposalNumber = new ProposalNumber("M" + id + ":0");
    }

    /**
     * Starts a new proposal with a given value.
     */
    public void propose(String value) {
        lock.lock();

        try {
            this.proposalValue = value;
            localSequence++;
            this.proposalNumber = new ProposalNumber("M" + id + ":" + localSequence);

            System.out.println("[Proposer " + id + "] Starting proposal " + proposalNumber + " with value '" + proposalValue + "'");
            sendPrepareMessage();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sends a Prepare message to all Acceptors.
     */
    private void sendPrepareMessage() {
        Prepare prepareMessage = new Prepare(proposalNumber);
        broadcastToAcceptors(prepareMessage);
        System.out.println("[Proposer " + id + "] Sent Prepare for " + proposalNumber + " to " + acceptorIds);
    }

    /**
     * Broadcasts a message to all Acceptors.
     */
    private void broadcastToAcceptors(Object message) {
        String messageJson = gson.toJson(message);
        
        for (int acceptorId : acceptorIds) {
            networkTransport.sendMessage(String.valueOf(acceptorId), messageJson);
        }
    }

    /**
     * Handles a Promise message received from an Acceptor.
     */
    public void handlePromise(Promise promise) {

        lock.lock();

        try {
            // TODO: Check if this causes by reference issues instead of creating a copy
            ProposalNumber incomingNum = new ProposalNumber(promise.proposalNumber.toString());

            if (!incomingNum.equals(proposalNumber)) {
                System.out.println("[Proposer " + id + "] Ignored Promise for " + incomingNum + " (expected " + proposalNumber + ")");
                return;
            }

            promisesReceived.put(promise.fromMemberId, promise);
            System.out.println("[Proposer " + id + "] Received Promise for " + incomingNum + " from Acceptor " + promise.fromMemberId);

            // If any acceptor already accepted a proposal, adopt the value of the highest-numbered one
            Optional<Promise> highestAccepted = promisesReceived.values().stream()
                .filter(p -> p.acceptedProposalNumber != null)
                .max(Comparator.comparing(p -> new ProposalNumber(p.acceptedProposalNumber)));

            if (highestAccepted.isPresent() && highestAccepted.get().acceptedProposalValue != null) {
                proposalValue = highestAccepted.get().acceptedProposalValue;
                System.out.println("[Proposer " + id + "] Updated proposal value to '" + proposalValue + "' based on prior accepted proposal");
            }

            // If majority of promises received, send Accept Request
            if (promisesReceived.size() >= calculateMajority()) {
                sendAcceptRequest();
            }
        } finally {
            lock.unlock();
        }


    }

    /**
     * Sends Accept Request to all Acceptors.
     */
    private void sendAcceptRequest() {
        lock.lock();

        try {
            AcceptRequest acceptRequest = new AcceptRequest(proposalNumber, proposalValue);
            broadcastToAcceptors(acceptRequest);
            System.out.println("[Proposer " + id + "] Sent Accept Request for " + proposalNumber + " with value '" + proposalValue + "'");
        } finally {
            lock.unlock();
        }
    }

    /**
     * Handles Accepted messages from Acceptors.
     */
    public void handleAccepted(Accepted accepted) {
        lock.lock();

        try {
            ProposalNumber incomingNum = new ProposalNumber(accepted.proposalNumber.toString());

            if (!incomingNum.equals(proposalNumber)) {
                System.out.println("[Proposer " + id + "] Ignored Accepted for " + incomingNum + " (expected " + proposalNumber + ")");
                return;
            }

            acceptedReceivedFrom.add(accepted.fromMemberId);
            System.out.println("[Proposer " + id + "] Received Accepted for " + incomingNum + " from Acceptor " + accepted.fromMemberId);

            // If majority of accepteds received, proposal is chosen
            if (acceptedReceivedFrom.size() >= calculateMajority()) {
                System.out.println("[Proposer " + id + "] Proposal " + proposalNumber + " with value '" + proposalValue + "' chosen by majority");
                notifyLearners();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Notifies learners about the chosen proposal (placeholder for actual learner communication).
     */
    private void notifyLearners() {
        Accepted acceptedMsg = new Accepted(id, proposalNumber, proposalValue);

        String acceptedJson = gson.toJson(acceptedMsg);

        System.out.println("[Proposer " + id + "] Notifying learners about chosen proposal " + proposalNumber + " with value '" + proposalValue + "'");

        for (int learnerId : acceptorIds) {
            networkTransport.sendMessage(String.valueOf(learnerId), acceptedJson);
        }
    }

    /**
     * Calculates the majority size based on total acceptors.
     */
    private int calculateMajority() {
        return (acceptorIds.size() / 2) + 1;
    }
}

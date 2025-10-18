package paxos_logic;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import paxos_util.Accepted;

import java.util.*;

public class Learner {
    private final Integer memberId;
    private final Integer totalAcceptors;

    private final ReentrantLock lock = new ReentrantLock();

    private final Map<String, Set<Integer>> acceptedValues = new ConcurrentHashMap<>();

    public Learner(Integer memberId, Integer totalAcceptors) {
        this.memberId = memberId;
        this.totalAcceptors = totalAcceptors;
    }

    public void handleAccepted(Accepted accepted) {

        lock.lock();

        try {
            String key = accepted.proposalNumber + ":" + accepted.proposalValue;
            acceptedValues.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(accepted.fromMemberId);

            Integer count = acceptedValues.get(key).size();

            if (count > computeMajority()) {
                System.out.println("Learner " + memberId + " has learned the value: " + accepted.proposalValue + " for proposal number: " + accepted.proposalNumber);
                // Once learned, we can clear the accepted values to avoid re-learning
                // Unsure if this is the best way to handle it or if even needed
                acceptedValues.remove(key);
            }
        } finally {
            lock.unlock();
        }
    }

    private Integer computeMajority() {
        return (int) ((Math.floor(totalAcceptors / 2) + 1));
    }
}

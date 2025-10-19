package paxos_logic;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import paxos_util.Accepted;
import paxos_util.ProposalNumber;

import java.util.*;

public class Learner {
    private final String memberId;
    private final Integer totalAcceptors;

    private final ReentrantLock lock = new ReentrantLock();

    private Map<ProposalNumber, String> learnedValues = new ConcurrentHashMap<>();
    private ProposalNumber lastLearnedValue;

    private final Map<String, Set<String>> acceptedValues = new ConcurrentHashMap<>();

    public Learner(String memberId, Integer totalAcceptors) {
        this.memberId = memberId;
        this.totalAcceptors = totalAcceptors;
    }

    public void handleAccepted(Accepted accepted) {

        lock.lock();

        try {
            String key = accepted.proposalNum + ":" + accepted.proposalValue;

            acceptedValues.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(accepted.fromMemberId);

            Integer count = acceptedValues.get(key).size();

            if (count >= computeMajority()) {
                System.out.println("Learner " + memberId + " has learned the value: " + accepted.proposalValue + " for proposal number: " + accepted.proposalNum);
                System.out.flush();
                learnedValues.put(accepted.proposalNum, accepted.proposalValue);
                lastLearnedValue = accepted.proposalNum;
                acceptedValues.remove(key);
            }
        } finally {
            lock.unlock();
        }
    }

    private Integer computeMajority() {
        return (int) ((Math.floor(totalAcceptors / 2) + 1));
    }

    public String getLastLearnedValue() {
        lock.lock();
        try {
            if (lastLearnedValue != null) {
                return learnedValues.get(lastLearnedValue);
            } else {
                return null;
            }
        } finally {
            lock.unlock();
        }
    }
}

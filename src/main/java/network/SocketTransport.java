package network;

import paxos_logic.PaxosNode;

import java.io.*;
import java.net.*;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;

import member.Profile;

public class SocketTransport implements MemberTransport {
    private final String memberId;
    private final Integer port;
    private final Map<String, InetSocketAddress> members;

    private final PaxosNode paxosNode;
    private final Profile profile;

    private ServerSocket serverSocket;
    private final ReentrantLock lock = new ReentrantLock();

    public SocketTransport(String memberId, Integer port, Map<String, InetSocketAddress> members, PaxosNode paxosNode, Profile profile) {
        this.memberId = memberId;
        this.port = port;
        this.members = members;
        this.paxosNode = paxosNode;
        this.profile = profile;
    }

    @Override
    public void startListening() {
        lock.lock();
        
        try {

            serverSocket = new ServerSocket(port);
            System.out.println("[Member " + memberId + "] - Listening on port " + port);

            new Thread(() -> {
                while (!serverSocket.isClosed()) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        handleSocket(clientSocket);
                    } catch (IOException e) {
                        if (!serverSocket.isClosed()) e.printStackTrace();
                    }
                }
            }).start();

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
    }

    private void handleSocket(Socket clientSocket) {
        new Thread(() -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
                String message;
                while ((message = in.readLine()) != null) {
                    int delay = simulateDelay();
                    Thread.sleep(delay);
                    paxosNode.handleMessage(getSenderFromMessage(message), message);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try { clientSocket.close(); } catch (IOException ignored) {}
            }
        }).start();
    }

    private int simulateDelay() {
        switch (profile) {
            case RELIABLE: return 10;
            case LATENT: return 1000 + (int)(Math.random() * 3000); // 1 - 4 s
            case STANDARD: return 200 + (int)(Math.random() * 1000); // 200 ms - 1.2 s
            case FAILURE: return 0; // won't send
        }
        return 0;
    }

    private String getSenderFromMessage(String msg) {
        return msg.split(":")[1];
    }

    @Override
    public void sendMessage(String targetId, String message) {
        if (profile == Profile.FAILURE) {
            return;
        }

        InetSocketAddress address = members.get(targetId);
        if (address == null) {
            System.out.println("[Member " + memberId + "] - Unknown target member ID: " + targetId);
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(simulateDelay());
                try (Socket socket = new Socket(address.getHostName(), address.getPort());
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                    out.println(message);
                    out.flush();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void shutdown() {
        lock.lock();
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {

        } finally {
            lock.unlock();
        }
    }
}

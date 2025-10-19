package network;

import paxos_logic.PaxosNode;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.net.*;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;

import member.Profile;
import paxos_util.PaxosMessage;

public class SocketTransport implements MemberTransport {
    private final String memberId;
    private final Integer port;
    private Map<String, InetSocketAddress> members;

    private PaxosNode paxosNode;
    private Profile profile;

    private ServerSocket serverSocket;
    private final ReentrantLock lock = new ReentrantLock();
    private final Gson gson = new GsonBuilder().create();

    private boolean crashAfterSend = false;
    private boolean hasSentFirstMessage = false;

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
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }

        new Thread(() -> {
            while (true) {
                lock.lock();
                try {
                    if (serverSocket == null || serverSocket.isClosed()) break;
                    Socket clientSocket = serverSocket.accept();
                    handleSocket(clientSocket);
                } catch (IOException e) {
                    if (serverSocket == null || serverSocket.isClosed()) break;
                    e.printStackTrace();
                } finally {
                    lock.unlock();
                }
            }
        }).start();
    }

    private void handleSocket(Socket clientSocket) {
        new Thread(() -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
                String jsonMessage;
                while ((jsonMessage = in.readLine()) != null) {
                    int delay = simulateDelay();
                    Thread.sleep(delay);

                    // Deserialize to a generic map to extract senderId
                    PaxosMessage msg = gson.fromJson(jsonMessage, PaxosMessage.class);
                    String senderId = msg.fromMemberId;

                    paxosNode.handleMessage(senderId, jsonMessage);
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

    @Override
    public void sendMessage(String targetId, Object message) {
        if (profile == Profile.FAILURE) {
            // Optionally crash after sending first message
            if (crashAfterSend) {
                if (hasSentFirstMessage) {
                    System.out.println("[Member " + memberId + "] Crashing after sending first message!");
                    System.exit(1);
                } else {
                    hasSentFirstMessage = true;
                }
            } else {
                return; // skip sending
            }
        }

        InetSocketAddress address = members.get(targetId);
        if (address == null) {
            System.out.println("[Member " + memberId + "] - Unknown target member ID: " + targetId);
            return;
        }

        String jsonMessage = gson.toJson(message);

        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(simulateDelay());
                try (Socket socket = new Socket(address.getHostName(), address.getPort());
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                    out.println(jsonMessage);
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

    public void setCrashAfterSend(boolean crash) {
        this.crashAfterSend = crash;
    }
    
}

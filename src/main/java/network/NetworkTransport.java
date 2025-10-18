package network;

import paxos_logic.PaxosServer;
import java.net.*;
import java.util.concurrent.locks.ReentrantLock;
import java.io.*;

public class NetworkTransport {
    private final PaxosServer paxosServer;
    private ServerSocket serverSocket;

    private final ReentrantLock lock = new ReentrantLock();

    public NetworkTransport(PaxosServer paxosServer){
        this.paxosServer = paxosServer;
    }

    public ServerSocket getServerSocket() {
        return serverSocket;
    }

    public void shutdownSocket() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                System.out.println("Server socket closed");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void startServerSocket(int port, String memberID) {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("[" + memberID + "] Server socket started on port " + port);

            // Start a new thread to listen for incoming connections
            new Thread(() -> {
                while (!serverSocket.isClosed()) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        // Handle the client connection in a new thread
                        connectMemberToSocket(clientSocket, memberID);
                    } catch (IOException e) {
                        if (serverSocket.isClosed()) {
                            System.out.println("Server socket is closed, stopping accept loop.");
                        } else {
                            e.printStackTrace();
                        }
                    }
                }
            }).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void connectMemberToSocket(Socket clientSocket, String memberID) {
        paxosServer.getMemberSockets().put(memberID, clientSocket);
        new Thread(() -> {
            handleSocketConnection(clientSocket, memberID);
        }).start();
    }

    private void handleSocketConnection(Socket socket, String memberID) {
        if (socket == null || socket.isClosed()) {
            return;
        }

        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String message;
            while ((message = in.readLine()) != null) {
                paxosServer.handleMessage(memberID, message);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void sendMessage(String targetMemberId, String message) {
        lock.lock();

        try {
            Socket socket = new Socket("localhost", paxosServer.getMemberPorts().get(targetMemberId));

            if (socket != null && !socket.isClosed()) {
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                out.println(message);
                out.flush();
                socket.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
    }
}

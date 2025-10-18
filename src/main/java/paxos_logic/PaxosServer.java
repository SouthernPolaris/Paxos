package paxos_logic;

// TODO: replace String Message with Class Object
import java.net.Socket;
import java.util.Map;

public interface PaxosServer {
    void handleMessage(String senderId, String message);

    public Map<String, Socket> getMemberSockets();
    public Map<String, Integer> getMemberPorts();
}

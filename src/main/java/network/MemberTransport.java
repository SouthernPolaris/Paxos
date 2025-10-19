package network;

public interface MemberTransport {
    public void sendMessage(String targetId, Object message);
    public void startListening();
}

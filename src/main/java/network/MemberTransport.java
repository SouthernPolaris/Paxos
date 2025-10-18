package network;

public interface MemberTransport {
    public void sendMessage(String targetId, String message);
    public void startListening();
}

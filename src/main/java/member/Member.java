package member;

import java.util.UUID;

public interface Member {
    public String name = "";
    public UUID member_Uuid = UUID.randomUUID();

    public void sendMessage(String message, Member recipient);
    public void receiveMessage(String message, Member sender);

}
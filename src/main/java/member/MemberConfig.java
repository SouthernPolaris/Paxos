package member;

import java.net.InetSocketAddress;

/**
 * Configuration for a Council Member
 */
public class MemberConfig {
    public final InetSocketAddress address;
    public final Profile profile;
    public final int port;

    public MemberConfig(String host, int port, Profile profile) {
        this.address = new InetSocketAddress(host, port);
        this.port = port;
        this.profile = profile;
    }
}

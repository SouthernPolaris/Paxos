package member;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.net.InetSocketAddress;

public class MemberConfigTest {

    @Test
    public void testValidConstruction() {
        MemberConfig config = new MemberConfig("localhost", 9001, Profile.RELIABLE);
        
        assertNotNull(config.address);
        assertEquals(9001, config.port);
        assertEquals(Profile.RELIABLE, config.profile);
        
        InetSocketAddress expectedAddress = new InetSocketAddress("localhost", 9001);
        assertEquals(expectedAddress, config.address);
    }

    @Test
    public void testDifferentPorts() {
        MemberConfig config1 = new MemberConfig("localhost", 1, Profile.RELIABLE);
        assertEquals(1, config1.port);
        assertEquals(1, config1.address.getPort());
        
        MemberConfig config2 = new MemberConfig("localhost", 65535, Profile.STANDARD);
        assertEquals(65535, config2.port);
        assertEquals(65535, config2.address.getPort());
        
        MemberConfig config3 = new MemberConfig("localhost", 8080, Profile.LATENT);
        assertEquals(8080, config3.port);
        assertEquals(8080, config3.address.getPort());
    }

    @Test
    public void testAddressConsistency() {
        MemberConfig config = new MemberConfig("localhost", 9001, Profile.RELIABLE);
        
        // The same parameters should create equivalent addresses
        InetSocketAddress manualAddress = new InetSocketAddress("localhost", 9001);
        assertEquals(manualAddress, config.address);
        
        // Port should be consistent between fields
        assertEquals(config.port, config.address.getPort());
    }
}
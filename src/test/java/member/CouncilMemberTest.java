package member;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class CouncilMemberTest {

    @TempDir
    Path tempDir;
    
    private Path configFile;
    private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final InputStream originalIn = System.in;

    @BeforeEach
    void setUp() throws IOException {
        configFile = tempDir.resolve("network.config");
        String configContent = """
            M1,localhost,9001,RELIABLE
            M2,localhost,9002,LATENT
            M3,localhost,9003,FAILURE
            M4,localhost,9004,STANDARD
            M5,localhost,9005,STANDARD
            """;
        Files.write(configFile, configContent.getBytes());
        
        System.setOut(new PrintStream(outputStream));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        System.setIn(originalIn);
    }

    @Test
    public void testNoArguments() throws Exception {
        String[] args = {};
        
        CouncilMember.main(args);
        
        String output = outputStream.toString();
        assertTrue(output.contains("Usage: java CouncilMember <memberId>"));
    }

    @Test
    public void testLoadNetworkConfig() throws Exception {
        // Create config directory structure
        Path confDir = tempDir.resolve("conf");
        Files.createDirectories(confDir);
        Path networkConfig = confDir.resolve("network.config");
        Files.copy(configFile, networkConfig);
        
        assertTrue(Files.exists(networkConfig));
        assertTrue(Files.size(networkConfig) > 0);
    }

    @Test
    public void testMemberIdNotInConfig() throws Exception {
        Path confDir = tempDir.resolve("conf");
        Files.createDirectories(confDir);
        Path networkConfig = confDir.resolve("network.config");
        Files.write(networkConfig, "M1,localhost,9001,RELIABLE\n".getBytes());
        
        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());
            
            // Invalid member
            String[] args = {"M999"};
            
            CouncilMember.main(args);
            
            String output = outputStream.toString();
            assertTrue(output.contains("Error: Member ID M999 not found in network.config"));
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    public void testProfileEnumHandling() {
        for (Profile profile : Profile.values()) {
            assertNotNull(profile);
            assertNotNull(profile.name());
            
            Profile parsed = Profile.valueOf(profile.name());
            assertEquals(profile, parsed);
        }
        
        assertEquals(Profile.RELIABLE, Profile.valueOf("RELIABLE"));
        assertEquals(Profile.LATENT, Profile.valueOf("LATENT"));
        assertEquals(Profile.FAILURE, Profile.valueOf("FAILURE"));
        assertEquals(Profile.STANDARD, Profile.valueOf("STANDARD"));
        
        assertThrows(IllegalArgumentException.class, () -> {
            Profile.valueOf("INVALID");
        });
    }

    @Test
    public void testEmptyConfigLines() throws IOException {
        String configWithBlanks = """
            M1,localhost,9001,RELIABLE
            
            M2,localhost,9002,LATENT
               
            M3,localhost,9003,FAILURE
            \t
            """;
        
        Path testConfig = tempDir.resolve("blanks.config");
        Files.write(testConfig, configWithBlanks.getBytes());
        
        assertTrue(Files.exists(testConfig));
        String content = Files.readString(testConfig);
        assertTrue(content.contains("M1,localhost,9001,RELIABLE"));
        assertTrue(content.contains("M2,localhost,9002,LATENT"));
        assertTrue(content.contains("M3,localhost,9003,FAILURE"));
    }

}

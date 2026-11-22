package campusbite;

import campusbite.protocol.Wire;
import campusbite.server.TcpServer;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TcpFoundationTest {
    @Test void twoClientsAndSplitCombinedFrames() throws Exception {
        try (var server = new TcpServer("127.0.0.1", 0)) {
            server.start();
            try (var a = new Socket("127.0.0.1", server.port()); var b = new Socket("127.0.0.1", server.port())) {
                a.setSoTimeout(3000); b.setSoTimeout(3000);
                a.getOutputStream().write("{\"type\":\"PI".getBytes(StandardCharsets.UTF_8));
                a.getOutputStream().flush();
                a.getOutputStream().write("NG\",\"requestId\":\"a\"}\n{\"type\":\"PING\",\"requestId\":\"b\"}\n".getBytes(StandardCharsets.UTF_8));
                assertEquals("a", Wire.read(a.getInputStream()).path("requestId").asText());
                assertEquals("b", Wire.read(a.getInputStream()).path("requestId").asText());
                Wire.write(b.getOutputStream(), Wire.object().put("type", "PING").put("requestId", "c"));
                assertEquals("PONG", Wire.read(b.getInputStream()).path("data").path("type").asText());
            }
        }
    }
}

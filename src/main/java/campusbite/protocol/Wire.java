package campusbite.protocol;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;

/** One bounded UTF-8 JSON object per LF. TCP packet boundaries are irrelevant. */
public final class Wire {
    public static final ObjectMapper JSON = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public static final int MAX_BYTES = 65_536;
    private Wire() {}
    public static ObjectNode object() { return JSON.createObjectNode(); }
    public static JsonNode read(InputStream in) throws IOException {
        var bytes = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1 && b != '\n') {
            if (bytes.size() >= MAX_BYTES) throw new IOException("Message exceeds 64 KiB");
            bytes.write(b);
        }
        if (b == -1) {
            if (bytes.size() != 0) throw new EOFException("Incomplete message");
            return null;
        }
        String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
        JsonNode node = JSON.readTree(text);
        if (node == null || !node.isObject()) throw new IOException("Expected a JSON object");
        return node;
    }
    public static void write(OutputStream out, JsonNode value) throws IOException {
        byte[] bytes = JSON.writeValueAsBytes(value);
        out.write(bytes); out.write('\n'); out.flush();
    }
    public static ObjectNode ok(String id, JsonNode data) {
        var n = object().put("type", "RESPONSE").put("requestId", id).put("ok", true);
        n.set("data", data); return n;
    }
    public static ObjectNode error(String id, String code, String message) {
        var n = object().put("type", "RESPONSE").put("requestId", id).put("ok", false);
        n.set("error", object().put("code", code).put("message", message)); return n;
    }
    public static ObjectNode event(String type, JsonNode data) {
        var n = object().put("type", "EVENT").put("event", type); n.set("data", data); return n;
    }
}

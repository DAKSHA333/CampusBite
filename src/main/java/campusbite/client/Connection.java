package campusbite.client;

import campusbite.protocol.Wire;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.net.*;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Shared persistent connection with correlation, deadlines and heartbeat. Never call request on the EDT. */
public final class Connection implements AutoCloseable {
    private final Socket socket = new Socket();
    private final ConcurrentHashMap<String,CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());
    private final Consumer<JsonNode> events;
    private final Consumer<String> state;
    private volatile boolean closed;
    public Connection(String host, int port, Consumer<JsonNode> events, Consumer<String> state) throws IOException {
        this.events = events; this.state = state;
        try { socket.connect(new InetSocketAddress(host, port), 5000); socket.setTcpNoDelay(true); socket.setSoTimeout(45_000); }
        catch (IOException e) { socket.close(); heartbeat.shutdownNow(); throw e; }
        Thread.ofVirtual().start(this::readLoop);
        heartbeat.scheduleWithFixedDelay(() -> {
            try { request(Wire.object().put("type", "PING")); }
            catch (Exception e) { close(); }
        }, 15, 15, TimeUnit.SECONDS);
    }
    public JsonNode request(ObjectNode request) throws Exception {
        if (closed) throw new IOException("Disconnected. Reconnect to restore your orders.");
        String id = UUID.randomUUID().toString(); request.put("requestId", id);
        var future = new CompletableFuture<JsonNode>(); pending.put(id, future);
        try {
            synchronized (socket) { Wire.write(socket.getOutputStream(), request); }
            JsonNode response = future.get(10, TimeUnit.SECONDS);
            if (!response.path("ok").asBoolean()) throw new RemoteError(response.path("error").path("code").asText(), response.path("error").path("message").asText());
            return response.path("data");
        } catch (TimeoutException e) { close(); throw new IOException("The server did not respond. Reconnect; an order may already have been accepted."); }
        catch (ExecutionException e) { throw new IOException("Connection lost. Reconnect to recover your orders."); }
        catch (IOException e) { close(); throw e; }
        finally { pending.remove(id); }
    }
    private void readLoop() {
        try (var in = new BufferedInputStream(socket.getInputStream())) {
            JsonNode message;
            while ((message = Wire.read(in)) != null) {
                if (message.path("type").asText().equals("EVENT")) events.accept(message);
                else {
                    var future = pending.get(message.path("requestId").asText());
                    if (future != null) future.complete(message);
                }
            }
        } catch (IOException ignored) {} finally { close(); }
    }
    public boolean isOpen() { return !closed; }
    public JsonNode allOrders(boolean kitchen) throws Exception {
        var result = Wire.JSON.createArrayNode();
        long before = Long.MAX_VALUE;
        while (true) {
            JsonNode page = request(Wire.object().put("type", kitchen ? "GET_KITCHEN_ORDERS" : "GET_MY_ORDERS").put("beforeOrderId", before));
            for (JsonNode order : page) result.add(order);
            if (page.size() < 20) return result;
            before = page.get(page.size()-1).path("orderId").asLong();
        }
    }
    @Override public synchronized void close() {
        if (closed) return; closed = true;
        try { socket.close(); } catch (IOException ignored) {}
        heartbeat.shutdownNow();
        pending.values().forEach(f -> f.completeExceptionally(new IOException("Disconnected")));
        state.accept("Disconnected — reconnect to restore state");
    }
    public static final class RemoteError extends Exception {
        public final String code;
        public RemoteError(String code, String message) { super(message); this.code = code; }
    }
}

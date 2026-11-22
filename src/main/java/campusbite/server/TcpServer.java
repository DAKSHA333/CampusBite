package campusbite.server;

import campusbite.protocol.Wire;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.net.*;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public final class TcpServer implements AutoCloseable {
    private final ServerSocket listener;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final Set<Session> sessions = ConcurrentHashMap.newKeySet();
    private final AtomicLong ids = new AtomicLong();
    private volatile boolean closed;
    private final Store store;
    private final String staffCode;
    // Serializes mutations with their event enqueue order. Socket writes never hold this monitor.
    private final Object mutations = new Object();
    public TcpServer(String host, int port) throws IOException {
        this(host, port, null, "");
    }
    public TcpServer(String host, int port, Store store, String staffCode) throws IOException {
        this.store = store; this.staffCode = staffCode;
        listener = new ServerSocket(); listener.bind(new InetSocketAddress(host, port));
    }
    public int port() { return listener.getLocalPort(); }
    public void start() { workers.submit(this::accept); log("Listening on " + listener.getLocalSocketAddress()); }
    private void accept() {
        while (!closed) {
            try {
                Socket socket = listener.accept(); socket.setSoTimeout(90_000); socket.setTcpNoDelay(true);
                if (sessions.size() >= 200) {
                    try (socket) { Wire.write(socket.getOutputStream(), Wire.error("", "SERVER_BUSY", "Server is full. Try again shortly.")); }
                    continue;
                }
                Session session = new Session(socket);
                sessions.add(session); workers.submit(session::writeLoop); workers.submit(session::run);
            } catch (IOException e) { if (!closed) log("Accept failed: " + e.getMessage()); }
        }
    }
    private final class Session {
        final Socket socket;
        final long id = ids.incrementAndGet();
        final BlockingQueue<JsonNode> outgoing = new ArrayBlockingQueue<>(128);
        volatile Store.Identity customer;
        volatile boolean kitchen;
        volatile boolean authenticated;
        Session(Socket socket) { this.socket = socket; }
        void send(JsonNode message) {
            if (!outgoing.offer(message)) disconnect(); // bounded memory for an unresponsive peer
        }
        void disconnect() { sessions.remove(this); try { socket.close(); } catch (IOException ignored) {} }
        void writeLoop() {
            try {
                while (!socket.isClosed()) {
                    JsonNode message = outgoing.poll(1, TimeUnit.SECONDS);
                    if (message != null) Wire.write(socket.getOutputStream(), message);
                }
            } catch (IOException | InterruptedException e) { disconnect(); }
        }
        void run() {
            log("Connected client=" + id);
            try (socket; var in = new BufferedInputStream(socket.getInputStream())) {
                JsonNode request;
                while ((request = Wire.read(in)) != null) {
                    String requestId = request.path("requestId").asText("");
                    String type = request.path("type").asText("");
                    try {
                        if (!request.path("requestId").isTextual() || requestId.isBlank() || requestId.length() > 100)
                            throw new Problem("INVALID_REQUEST", "A short requestId is required.");
                        // Do not log arbitrary user input: it may contain secrets or control characters.
                        log("Request client=" + id + " type=" + (Set.of("HELLO","PING","GET_MENU","PLACE_ORDER","GET_MY_ORDERS","GET_KITCHEN_ORDERS","UPDATE_ORDER_STATUS").contains(type) ? type : "UNKNOWN"));
                        synchronized (mutations) { handle(requestId, type, request); }
                    } catch (Problem e) {
                        send(Wire.error(requestId, e.code, e.getMessage()));
                        log("Rejected client=" + id + " code=" + e.code);
                    } catch (java.sql.SQLException e) {
                        send(Wire.error(requestId, "SERVER_BUSY", "The database is busy. Retry shortly using the same order reference."));
                        log("Database error client=" + id + " sqlCode=" + e.getErrorCode());
                    }
                }
            } catch (IOException e) { log("Closed client=" + id + " (connection ended or invalid frame)"); }
            finally { disconnect(); log("Disconnected client=" + id); }
        }
        void handle(String requestId, String type, JsonNode request) throws java.sql.SQLException {
            if (type.equals("PING")) { send(Wire.ok(requestId, Wire.object().put("type", "PONG"))); return; }
            if (type.equals("HELLO")) {
                if (authenticated) throw new Problem("INVALID_REQUEST", "This connection already has an identity.");
                if (request.path("role").asText().equals("kitchen")) {
                    if (!java.security.MessageDigest.isEqual(staffCode.getBytes(java.nio.charset.StandardCharsets.UTF_8), request.path("staffCode").asText().getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        throw new Problem("UNAUTHORIZED", "The staff access code is incorrect.");
                    kitchen = true; authenticated = true;
                    send(Wire.ok(requestId, Wire.object().put("role", "kitchen")));
                } else if (request.path("role").asText().equals("student")) {
                    customer = store.identify(request.path("name").asText(""), request.path("token").asText(""));
                    authenticated = true;
                    send(Wire.ok(requestId, Wire.object().put("role", "student").put("customerId", customer.id()).put("name", customer.name()).put("token", customer.token())));
                } else throw new Problem("INVALID_REQUEST", "Choose student or kitchen role.");
                return;
            }
            if (!authenticated) throw new Problem("UNAUTHORIZED", "Connect with HELLO before using the application.");
            switch (type) {
                case "GET_MENU" -> send(Wire.ok(requestId, store.menu()));
                case "GET_MY_ORDERS" -> { studentOnly(); send(Wire.ok(requestId, store.orders(customer.id(), before(request)))); }
                case "GET_KITCHEN_ORDERS" -> { kitchenOnly(); send(Wire.ok(requestId, store.orders(null, before(request)))); }
                case "PLACE_ORDER" -> {
                    studentOnly();
                    var result = store.place(customer.id(), request.path("clientOrderId").asText(""), request.path("items"));
                    send(Wire.ok(requestId, result.order()));
                    log((result.created() ? "Accepted" : "Replayed") + " order=" + result.order().path("orderId").asLong() + " client=" + id);
                    if (result.created()) {
                        broadcast("MENU_CHANGED", store.menu(), 0, true);
                        broadcast("ORDER_PLACED", result.order(), customer.id(), false);
                    }
                }
                case "UPDATE_ORDER_STATUS" -> {
                    kitchenOnly();
                    if (!request.path("orderId").isIntegralNumber() || !request.path("orderId").canConvertToLong()) throw new Problem("INVALID_REQUEST", "A valid order ID is required.");
                    var result = store.transition(request.path("orderId").asLong(), request.path("status").asText(""));
                    send(Wire.ok(requestId, result.order()));
                    if (result.changed()) {
                        log("Status order=" + result.order().path("orderId").asLong() + " state=" + result.order().path("status").asText());
                        broadcast("ORDER_STATUS_CHANGED", result.order(), result.order().path("customerId").asLong(), false);
                    }
                }
                default -> throw new Problem("UNKNOWN_REQUEST", "Unknown request type.");
            }
        }
        void studentOnly() { if (customer == null) throw new Problem("UNAUTHORIZED", "This action requires a student connection."); }
        long before(JsonNode request) {
            if (!request.has("beforeOrderId")) return Long.MAX_VALUE;
            JsonNode value = request.path("beforeOrderId");
            if (!value.isIntegralNumber() || !value.canConvertToLong() || value.asLong() < 1) throw new Problem("INVALID_REQUEST", "beforeOrderId must be a positive integer.");
            return value.asLong();
        }
        void kitchenOnly() { if (!kitchen) throw new Problem("UNAUTHORIZED", "This action requires kitchen access."); }
    }
    private void broadcast(String type, JsonNode data, long owner, boolean menu) {
        for (Session s : sessions) if (s.authenticated && (menu ? !s.kitchen : s.kitchen || (s.customer != null && s.customer.id() == owner)))
            s.send(Wire.event(type, data));
    }
    public static void log(String text) { System.out.println(Instant.now() + " | " + text); }
    @Override public void close() {
        closed = true;
        try { listener.close(); } catch (IOException ignored) {}
        for (Session s : sessions) try { s.socket.close(); } catch (IOException ignored) {}
        workers.shutdownNow();
        try { workers.awaitTermination(5, TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}

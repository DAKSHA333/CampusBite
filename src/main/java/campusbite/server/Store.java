package campusbite.server;

import campusbite.protocol.Wire;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Server-only persistence. One monitor serializes transactions; SQLite also protects each stock update. */
public final class Store implements AutoCloseable {
    private final Connection db;
    private final FileChannel lockChannel;
    private final FileLock lock;
    public Store(Path path, int stock, boolean demo) throws Exception {
        Path absolute = path.toAbsolutePath().normalize();
        Files.createDirectories(absolute.getParent());
        lockChannel = FileChannel.open(Path.of(absolute + ".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock acquired;
        try { acquired = lockChannel.tryLock(); }
        catch (OverlappingFileLockException e) { lockChannel.close(); throw new IllegalStateException("Database is already in use. Stop its server first."); }
        if (acquired == null) { lockChannel.close(); throw new IllegalStateException("Database is already in use. Stop its server first."); }
        lock = acquired;
        Connection opened = null;
        try {
            opened = DriverManager.getConnection("jdbc:sqlite:" + absolute); db = opened;
            try (Statement s = db.createStatement()) {
                s.execute("PRAGMA foreign_keys=ON"); s.execute("PRAGMA busy_timeout=5000"); s.execute("PRAGMA journal_mode=WAL");
                s.execute("CREATE TABLE IF NOT EXISTS menu_items(id INTEGER PRIMARY KEY,name TEXT NOT NULL,description TEXT NOT NULL,price_paise INTEGER NOT NULL CHECK(price_paise>=0),stock INTEGER NOT NULL CHECK(stock>=0),available INTEGER NOT NULL DEFAULT 1)");
                s.execute("CREATE TABLE IF NOT EXISTS customers(id INTEGER PRIMARY KEY,name TEXT NOT NULL,token TEXT NOT NULL UNIQUE)");
                s.execute("CREATE TABLE IF NOT EXISTS orders(id INTEGER PRIMARY KEY AUTOINCREMENT,customer_id INTEGER NOT NULL REFERENCES customers(id),client_order_id TEXT NOT NULL,fingerprint TEXT NOT NULL,status TEXT NOT NULL,total_paise INTEGER NOT NULL,created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),UNIQUE(customer_id,client_order_id))");
                s.execute("CREATE TABLE IF NOT EXISTS order_items(order_id INTEGER NOT NULL REFERENCES orders(id),menu_item_id INTEGER NOT NULL REFERENCES menu_items(id),name TEXT NOT NULL,unit_price INTEGER NOT NULL,quantity INTEGER NOT NULL CHECK(quantity>0),PRIMARY KEY(order_id,menu_item_id))");
                s.execute("CREATE TABLE IF NOT EXISTS order_status_history(id INTEGER PRIMARY KEY,order_id INTEGER NOT NULL REFERENCES orders(id),previous_state TEXT,new_state TEXT NOT NULL,changed_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')))");
            }
            seed(stock, demo);
        } catch (Exception e) { if (opened != null) opened.close(); lock.release(); lockChannel.close(); throw e; }
    }
    private void seed(int stock, boolean demo) throws SQLException {
        String[] names = {"Sandwich", "Samosa", "Veg roll", "Juice", "Tea", "Coffee"};
        String[] descriptions = {"Fresh vegetables & chutney", "Crisp pastry, spiced potato", "Warm flatbread & vegetables", "A refreshing fruit drink", "Freshly brewed masala tea", "A warm coffee break"};
        int[] prices = {4000, 2000, 5000, 2000, 1500, 2500};
        db.setAutoCommit(false);
        try {
            for (int i = 0; i < names.length; i++) execute("INSERT OR IGNORE INTO menu_items(id,name,description,price_paise,stock) VALUES(?,?,?,?,?)", i+1, names[i], descriptions[i], prices[i], demo && i == 0 ? 1 : stock);
            db.commit();
        } catch (SQLException e) { db.rollback(); throw e; } finally { db.setAutoCommit(true); }
    }
    private PreparedStatement statement(String sql, Object... values) throws SQLException {
        PreparedStatement s = db.prepareStatement(sql);
        for (int i = 0; i < values.length; i++) s.setObject(i+1, values[i]);
        return s;
    }
    private int execute(String sql, Object... values) throws SQLException {
        try (var s = statement(sql, values)) { return s.executeUpdate(); }
    }
    public synchronized ArrayNode menu() throws SQLException {
        var result = Wire.JSON.createArrayNode();
        try (var s = statement("SELECT * FROM menu_items ORDER BY id"); var r = s.executeQuery()) {
            while (r.next()) result.add(Wire.object().put("menuItemId", r.getInt("id")).put("name", r.getString("name"))
                .put("description", r.getString("description")).put("pricePaise", r.getInt("price_paise"))
                .put("stock", r.getInt("stock")).put("available", r.getBoolean("available")));
        }
        return result;
    }
    public record Identity(long id, String name, String token) {}
    public synchronized Identity identify(String name, String token) throws SQLException {
        if (!token.isBlank()) {
            try (var s = statement("SELECT * FROM customers WHERE token=?", token); var r = s.executeQuery()) {
                if (r.next()) return new Identity(r.getLong("id"), r.getString("name"), token);
            }
            throw new Problem("INVALID_TOKEN", "This saved identity is not in this database. Use a new profile for a reset demo.");
        }
        if (name.isBlank() || name.length() > 60 || name.chars().anyMatch(Character::isISOControl)) throw new Problem("INVALID_NAME", "Enter a display name of 1–60 characters.");
        String issued = UUID.randomUUID().toString() + UUID.randomUUID();
        execute("INSERT INTO customers(name,token) VALUES(?,?)", name.strip(), issued);
        try (var s = statement("SELECT id FROM customers WHERE token=?", issued); var r = s.executeQuery()) { r.next(); return new Identity(r.getLong(1), name.strip(), issued); }
    }
    public synchronized ArrayNode orders(Long customer) throws SQLException {
        return orders(customer, Long.MAX_VALUE);
    }
    public synchronized ArrayNode orders(Long customer, long beforeOrderId) throws SQLException {
        var result = Wire.JSON.createArrayNode();
        try (var s = customer == null ? statement("SELECT id FROM orders WHERE id<? ORDER BY id DESC LIMIT 20", beforeOrderId) : statement("SELECT id FROM orders WHERE customer_id=? AND id<? ORDER BY id DESC LIMIT 20", customer, beforeOrderId); var r = s.executeQuery()) {
            while (r.next()) result.add(order(r.getLong(1)));
        }
        return result;
    }
    private ObjectNode order(long id) throws SQLException {
        try (var s = statement("SELECT o.*,c.name AS customer_name FROM orders o JOIN customers c ON c.id=o.customer_id WHERE o.id=?", id); var r = s.executeQuery()) {
            if (!r.next()) throw new Problem("NOT_FOUND", "That order does not exist.");
            var result = Wire.object().put("orderId", id).put("customerId", r.getLong("customer_id"))
                .put("studentName", r.getString("customer_name")).put("clientOrderId", r.getString("client_order_id"))
                .put("status", r.getString("status")).put("totalPaise", r.getLong("total_paise"))
                .put("createdAt", r.getString("created_at")).put("updatedAt", r.getString("updated_at"));
            var items = result.putArray("items");
            try (var si = statement("SELECT * FROM order_items WHERE order_id=? ORDER BY menu_item_id", id); var ri = si.executeQuery()) {
                while (ri.next()) items.add(Wire.object().put("menuItemId", ri.getInt("menu_item_id")).put("name", ri.getString("name"))
                    .put("unitPricePaise", ri.getInt("unit_price")).put("quantity", ri.getInt("quantity")));
            }
            return result;
        }
    }
    public record Placement(ObjectNode order, boolean created) {}
    public synchronized Placement place(long customer, String clientId, JsonNode input) throws SQLException {
        if (clientId.isBlank() || clientId.length() > 100) throw new Problem("INVALID_ORDER_ID", "An order reference of 1–100 characters is required.");
        if (!input.isArray() || input.isEmpty() || input.size() > 50) throw new Problem("INVALID_QUANTITY", "Choose between 1 and 50 item entries.");
        var quantities = new TreeMap<Integer,Integer>();
        for (JsonNode item : input) {
            JsonNode q = item.path("quantity"), id = item.path("menuItemId");
            if (!q.isIntegralNumber() || !q.canConvertToInt() || q.intValue() < 1 || q.intValue() > 20) throw new Problem("INVALID_QUANTITY", "Choose a whole quantity between 1 and 20.");
            if (!id.isIntegralNumber() || !id.canConvertToInt() || id.intValue() < 1) throw new Problem("UNKNOWN_ITEM", "One selected item does not exist.");
            int sum = quantities.merge(id.intValue(), q.intValue(), Integer::sum);
            if (sum > 20) throw new Problem("INVALID_QUANTITY", "Choose at most 20 of each item.");
        }
        String fingerprint = quantities.toString();
        db.setAutoCommit(false);
        try {
            try (var s = statement("SELECT id,fingerprint FROM orders WHERE customer_id=? AND client_order_id=?", customer, clientId); var r = s.executeQuery()) {
                if (r.next()) {
                    if (!fingerprint.equals(r.getString("fingerprint"))) throw new Problem("IDEMPOTENCY_CONFLICT", "This order reference was already used for a different cart.");
                    ObjectNode existing = order(r.getLong("id")); db.commit(); return new Placement(existing, false);
                }
            }
            long total = 0;
            var snapshots = Wire.JSON.createArrayNode();
            for (var entry : quantities.entrySet()) {
                try (var s = statement("SELECT * FROM menu_items WHERE id=?", entry.getKey()); var r = s.executeQuery()) {
                    if (!r.next()) throw new Problem("UNKNOWN_ITEM", "One selected item does not exist.");
                    if (!r.getBoolean("available") || r.getInt("stock") < entry.getValue()) throw new Problem("OUT_OF_STOCK", r.getString("name") + " is no longer available in that quantity. Refresh your cart.");
                    long price = r.getLong("price_paise"); total += price * entry.getValue();
                    snapshots.add(Wire.object().put("id", entry.getKey()).put("name", r.getString("name")).put("price", price).put("quantity", entry.getValue()));
                }
                if (execute("UPDATE menu_items SET stock=stock-? WHERE id=? AND available=1 AND stock>=?", entry.getValue(), entry.getKey(), entry.getValue()) != 1)
                    throw new Problem("OUT_OF_STOCK", "That item has just sold out. Please choose something else.");
            }
            execute("INSERT INTO orders(customer_id,client_order_id,fingerprint,status,total_paise) VALUES(?,?,?,'PLACED',?)", customer, clientId, fingerprint, total);
            long id;
            try (var s = statement("SELECT last_insert_rowid()"); var r = s.executeQuery()) { r.next(); id = r.getLong(1); }
            for (JsonNode item : snapshots) execute("INSERT INTO order_items VALUES(?,?,?,?,?)", id, item.path("id").asInt(), item.path("name").asText(), item.path("price").asLong(), item.path("quantity").asInt());
            execute("INSERT INTO order_status_history(order_id,previous_state,new_state) VALUES(?,NULL,'PLACED')", id);
            ObjectNode created = order(id); db.commit(); return new Placement(created, true);
        } catch (SQLException | RuntimeException e) { db.rollback(); throw e; }
        finally { db.setAutoCommit(true); }
    }
    public record Transition(ObjectNode order, boolean changed) {}
    public synchronized Transition transition(long id, String next) throws SQLException {
        db.setAutoCommit(false);
        try {
            ObjectNode existing = order(id); String previous = existing.path("status").asText();
            if (previous.equals(next)) { db.commit(); return new Transition(existing, false); }
            var states = List.of("PLACED", "PREPARING", "READY", "COLLECTED");
            int index = states.indexOf(previous);
            if (index < 0 || index == 3 || !states.get(index+1).equals(next)) throw new Problem("INVALID_TRANSITION", "Advance orders one step: Placed → Preparing → Ready → Collected.");
            execute("UPDATE orders SET status=?,updated_at=strftime('%Y-%m-%dT%H:%M:%fZ','now') WHERE id=?", next, id);
            execute("INSERT INTO order_status_history(order_id,previous_state,new_state) VALUES(?,?,?)", id, previous, next);
            ObjectNode updated = order(id); db.commit(); return new Transition(updated, true);
        } catch (SQLException | RuntimeException e) { db.rollback(); throw e; }
        finally { db.setAutoCommit(true); }
    }
    /** Lock is held, so a running server prevents reset. Only demo CLI exposes this method. */
    public synchronized void resetDemo(int stock) throws SQLException {
        db.setAutoCommit(false);
        try {
            execute("DELETE FROM order_status_history"); execute("DELETE FROM order_items"); execute("DELETE FROM orders");
            execute("DELETE FROM customers"); execute("DELETE FROM menu_items"); db.commit();
        } catch (SQLException e) { db.rollback(); throw e; } finally { db.setAutoCommit(true); }
        seed(stock, true);
    }
    @Override public synchronized void close() throws Exception { try { db.close(); } finally { lock.release(); lockChannel.close(); } }
}

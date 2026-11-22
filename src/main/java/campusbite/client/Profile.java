package campusbite.client;

import campusbite.protocol.Wire;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.Properties;

/** Separate local identity and unresolved order per profile + server endpoint. */
public final class Profile implements AutoCloseable {
    private final Path file;
    private final FileChannel channel;
    private final FileLock lock;
    private final Properties properties = new Properties();
    public Profile(String profile) throws IOException {
        if (!profile.matches("[A-Za-z0-9_-]{1,40}")) throw new IOException("Profile must contain 1–40 letters, numbers, underscores or hyphens.");
        Path directory = Path.of(System.getProperty("user.home"), ".campusbite"); Files.createDirectories(directory);
        file = directory.resolve(profile + ".properties");
        channel = FileChannel.open(directory.resolve(profile + ".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock acquired;
        try { acquired = channel.tryLock(); } catch (OverlappingFileLockException e) { acquired = null; }
        if (acquired == null) { channel.close(); throw new IOException("That profile is already open. Use --profile student-b for another student."); }
        lock = acquired;
        if (Files.exists(file)) try (var in = Files.newInputStream(file)) { properties.load(in); }
    }
    public synchronized String token(String endpoint) { return properties.getProperty(endpoint + ".token", ""); }
    public synchronized void token(String endpoint, String token) throws IOException { properties.setProperty(endpoint + ".token", token); save(); }
    public synchronized ObjectNode pending(String endpoint) throws IOException {
        String text = properties.getProperty(endpoint + ".pending");
        return text == null ? null : (ObjectNode) Wire.JSON.readTree(text);
    }
    public synchronized void pending(String endpoint, ObjectNode request) throws IOException {
        if (request == null) properties.remove(endpoint + ".pending"); else properties.setProperty(endpoint + ".pending", request.toString());
        save();
    }
    private void save() throws IOException {
        Path temporary = Path.of(file + ".tmp");
        try (var out = Files.newOutputStream(temporary)) { properties.store(out, "CampusBite private identity. Do not share this file."); }
        try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
    }
    @Override public void close() throws IOException { lock.release(); channel.close(); }
}

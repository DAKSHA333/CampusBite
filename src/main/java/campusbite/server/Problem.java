package campusbite.server;

public final class Problem extends RuntimeException {
    public final String code;
    public Problem(String code, String message) { super(message); this.code = code; }
}

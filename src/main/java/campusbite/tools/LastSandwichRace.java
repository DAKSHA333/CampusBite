package campusbite.tools;

import campusbite.client.Connection;
import campusbite.protocol.Wire;
import java.util.UUID;
import java.util.concurrent.*;

/** Repeatable terminal demonstration: two real TCP clients released by one barrier. */
public final class LastSandwichRace {
    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 5050;
        try (var first = new Connection(host,port,e->{},s->{});
             var second = new Connection(host,port,e->{},s->{});
             var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            first.request(Wire.object().put("type","HELLO").put("role","student").put("name","Race Alice"));
            second.request(Wire.object().put("type","HELLO").put("role","student").put("name","Race Bob"));
            var barrier = new CyclicBarrier(2);
            var a = pool.submit(()->order(first,barrier,"Alice"));
            var b = pool.submit(()->order(second,barrier,"Bob"));
            String ra = a.get(), rb = b.get();
            System.out.println(ra); System.out.println(rb);
            if (!(ra.contains("ACCEPTED") && rb.contains("OUT_OF_STOCK") || rb.contains("ACCEPTED") && ra.contains("OUT_OF_STOCK")))
                throw new IllegalStateException("Expected one acceptance and one OUT_OF_STOCK. Reset demo data with server stopped before running this demonstration.");
            System.out.println("PASS: exactly one accepted order; one stock-unavailable response.");
        }
    }
    private static String order(Connection client,CyclicBarrier gate,String name) throws Exception {
        var request = Wire.object().put("type","PLACE_ORDER").put("clientOrderId",UUID.randomUUID().toString());
        request.putArray("items").add(Wire.object().put("menuItemId",1).put("quantity",1));
        gate.await();
        try { var result=client.request(request); return name+": ACCEPTED order #"+result.path("orderId").asLong()+", totalPaise="+result.path("totalPaise").asLong(); }
        catch(Connection.RemoteError e) { return name+": "+e.code+" — "+e.getMessage(); }
    }
}

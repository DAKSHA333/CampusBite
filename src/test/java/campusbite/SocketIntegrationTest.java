package campusbite;

import campusbite.client.Connection;
import campusbite.protocol.Wire;
import campusbite.server.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SocketIntegrationTest {
 @TempDir Path temp;
 static ObjectNode request(String type) { return Wire.object().put("type",type); }
 static ObjectNode cart(String id) {
  var n=request("PLACE_ORDER").put("clientOrderId",id);
  n.putArray("items").add(Wire.object().put("menuItemId",1).put("quantity",1)); return n;
 }
 Connection connect(TcpServer server, BlockingQueue<JsonNode> events) throws Exception { return new Connection("127.0.0.1",server.port(),events::add,s -> {}); }
 JsonNode student(Connection c, String token) throws Exception { return c.request(request("HELLO").put("role","student").put("name","Same Name").put("token",token)); }
 @Test void persistedSocketOrderAndReconnectAfterServerRestart() throws Exception {
  Path path=temp.resolve("restart.db"); String token; long orderId;
  try(var db=new Store(path,5,false); var server=new TcpServer("127.0.0.1",0,db,"staff")) {
   server.start();
   try(var c=connect(server,new LinkedBlockingQueue<>())) {
    token=student(c,"").path("token").asText();
    orderId=c.request(cart("stable")).path("orderId").asLong();
    assertEquals(orderId,c.request(cart("stable")).path("orderId").asLong());
   }
  }
  try(var db=new Store(path,9,false); var server=new TcpServer("127.0.0.1",0,db,"staff")) {
   server.start();
   try(var c=connect(server,new LinkedBlockingQueue<>())) {
    student(c,token);
    assertEquals(orderId,c.request(cart("stable")).path("orderId").asLong());
    assertEquals(1,c.request(request("GET_MY_ORDERS")).size());
    assertEquals(4,c.request(request("GET_MENU")).get(0).path("stock").asInt());
   }
  }
 }
 @Test void simultaneousLastSandwichHasExactlyOneWinner() throws Exception {
  try(var db=new Store(temp.resolve("race.db"),10,true); var server=new TcpServer("127.0.0.1",0,db,"staff")) {
   server.start();
   try(var a=connect(server,new LinkedBlockingQueue<>()); var b=connect(server,new LinkedBlockingQueue<>()); var pool=Executors.newVirtualThreadPerTaskExecutor()) {
    student(a,""); student(b,""); var gate=new CyclicBarrier(2);
    Callable<String> first=() -> race(a,gate,"a"); Callable<String> second=() -> race(b,gate,"b");
    var fa=pool.submit(first); var fb=pool.submit(second);
    assertEquals(java.util.Set.of("ACCEPTED","OUT_OF_STOCK"),java.util.Set.of(fa.get(5,TimeUnit.SECONDS),fb.get(5,TimeUnit.SECONDS)));
    assertEquals(0,db.menu().get(0).path("stock").asInt()); assertEquals(1,db.orders(null).size());
   }
  }
 }
 String race(Connection c,CyclicBarrier gate,String id) throws Exception { gate.await(); try { c.request(cart(id)); return "ACCEPTED"; } catch(Connection.RemoteError e) { return e.code; } }
 @Test void authorizationTransitionsAndPrivateLiveEvents() throws Exception {
  var ea=new LinkedBlockingQueue<JsonNode>(); var eb=new LinkedBlockingQueue<JsonNode>(); var ek=new LinkedBlockingQueue<JsonNode>();
  try(var db=new Store(temp.resolve("events.db"),10,false); var server=new TcpServer("127.0.0.1",0,db,"staff")) {
   server.start();
   try(var a=connect(server,ea); var b=connect(server,eb); var kitchen=connect(server,ek)) {
    assertEquals("UNAUTHORIZED",assertThrows(Connection.RemoteError.class,()->a.request(request("GET_MENU"))).code);
    student(a,""); student(b,"");
    assertEquals("UNAUTHORIZED",assertThrows(Connection.RemoteError.class,()->kitchen.request(request("HELLO").put("role","kitchen").put("staffCode","bad"))).code);
    kitchen.request(request("HELLO").put("role","kitchen").put("staffCode","staff"));
    long id=a.request(cart("events")).path("orderId").asLong();
    assertEquals("MENU_CHANGED",ea.poll(3,TimeUnit.SECONDS).path("event").asText());
    assertEquals("ORDER_PLACED",ea.poll(3,TimeUnit.SECONDS).path("event").asText());
    assertEquals("MENU_CHANGED",eb.poll(3,TimeUnit.SECONDS).path("event").asText());
    assertEquals("ORDER_PLACED",ek.poll(3,TimeUnit.SECONDS).path("event").asText());
    assertEquals(0,b.request(request("GET_MY_ORDERS").put("customerId",1)).size());
    assertEquals("UNAUTHORIZED",assertThrows(Connection.RemoteError.class,()->b.request(request("GET_KITCHEN_ORDERS"))).code);
    assertEquals("UNAUTHORIZED",assertThrows(Connection.RemoteError.class,()->a.request(request("UPDATE_ORDER_STATUS").put("orderId",id).put("status","PREPARING"))).code);
    assertEquals("INVALID_TRANSITION",assertThrows(Connection.RemoteError.class,()->kitchen.request(request("UPDATE_ORDER_STATUS").put("orderId",id).put("status","READY"))).code);
    for(String status: new String[]{"PREPARING","READY","COLLECTED"}) {
     var update=request("UPDATE_ORDER_STATUS").put("orderId",id).put("status",status);
     assertEquals(status,kitchen.request(update).path("status").asText());
     assertEquals(status,ea.poll(3,TimeUnit.SECONDS).path("data").path("status").asText());
     assertEquals(status,ek.poll(3,TimeUnit.SECONDS).path("data").path("status").asText());
     assertNull(eb.poll(100,TimeUnit.MILLISECONDS));
     assertEquals(status,kitchen.request(update).path("status").asText());
     assertNull(ea.poll(100,TimeUnit.MILLISECONDS));
    }
    assertEquals(1,kitchen.request(request("GET_KITCHEN_ORDERS")).size());
   }
  }
 }
 @Test void malformedAndOversizePeersCannotCrashServer() throws Exception {
  try(var server=new TcpServer("127.0.0.1",0)) {
   server.start();
   for(String bad:new String[]{"{bad}\n","[]\n","{} {}\n","x".repeat(Wire.MAX_BYTES+1)+"\n"}) {
    try(var socket=new java.net.Socket("127.0.0.1",server.port())) {
     socket.setSoTimeout(3000);
     try { socket.getOutputStream().write(bad.getBytes(java.nio.charset.StandardCharsets.UTF_8)); assertEquals(-1,socket.getInputStream().read()); }
     catch(java.net.SocketException expected) { /* peer may reset a rejected oversized frame */ }
    }
   }
   try(var c=connect(server,new LinkedBlockingQueue<>())) { assertEquals("PONG",c.request(request("PING")).path("type").asText()); }
  }
 }
 @Test void historyPagesRetainEveryOrder() throws Exception {
  try(var db=new Store(temp.resolve("pages.db"),50,false);var server=new TcpServer("127.0.0.1",0,db,"staff")){
   server.start();try(var c=connect(server,new LinkedBlockingQueue<>())){
    student(c,"");for(int i=0;i<23;i++)c.request(cart("page-"+i));
    assertEquals(20,c.request(request("GET_MY_ORDERS")).size());
    JsonNode all=c.allOrders(false);assertEquals(23,all.size());assertEquals(23,all.get(0).path("orderId").asInt());assertEquals(1,all.get(22).path("orderId").asInt());
   }
  }
 }
 @Test void disconnectFailsPendingRequestCleanly() throws Exception {
  try(var listener=new java.net.ServerSocket(0);var executor=Executors.newVirtualThreadPerTaskExecutor()){
   var peer=executor.submit(()->{try(var socket=listener.accept()){Wire.read(socket.getInputStream());}return true;});
   try(var c=new Connection("127.0.0.1",listener.getLocalPort(),e->{},s->{})){
    assertThrows(java.io.IOException.class,()->c.request(request("PING")));
   }
   assertTrue(peer.get(3,TimeUnit.SECONDS));
  }
 }
}

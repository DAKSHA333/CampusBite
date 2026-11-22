package campusbite;

import campusbite.server.*;
import campusbite.protocol.Wire;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class StoreTest {
 @TempDir Path temp;
 @Test void menuSurvivesRestart() throws Exception {
  Path file = temp.resolve("seed.db");
  try (var db = new Store(file, 7, false)) { assertEquals(6, db.menu().size()); assertEquals(7, db.menu().get(0).path("stock").asInt()); }
  try (var db = new Store(file, 99, false)) { assertEquals(7, db.menu().get(0).path("stock").asInt()); }
 }
 @Test void billsValidationRollbackAndPersistentIdempotency() throws Exception {
  Path file = temp.resolve("orders.db"); String token; long customer; long order;
  var items = Wire.JSON.readTree("[{\"menuItemId\":1,\"quantity\":1},{\"menuItemId\":2,\"quantity\":2},{\"menuItemId\":2,\"quantity\":1}]");
  try (var db = new Store(file, 10, true)) {
   var identity = db.identify("Student", ""); token = identity.token(); customer = identity.id();
   var placed = db.place(customer,"stable",items); order = placed.order().path("orderId").asLong();
   assertEquals(10000, placed.order().path("totalPaise").asInt());
   assertFalse(db.place(customer,"stable",items).created());
   assertEquals("IDEMPOTENCY_CONFLICT", assertThrows(Problem.class, () -> db.place(customer,"stable",Wire.JSON.readTree("[{\"menuItemId\":2,\"quantity\":1}]"))).code);
   for (String quantity : new String[]{"0","-1","21","1.5","\"2\"","99999999999999999"}) {
    assertEquals("INVALID_QUANTITY", assertThrows(Problem.class, () -> db.place(customer,"bad",Wire.JSON.readTree("[{\"menuItemId\":2,\"quantity\":"+quantity+"}]"))).code);
   }
   assertEquals("UNKNOWN_ITEM", assertThrows(Problem.class, () -> db.place(customer,"unknown",Wire.JSON.readTree("[{\"menuItemId\":999,\"quantity\":1}]"))).code);
   assertEquals("OUT_OF_STOCK", assertThrows(Problem.class, () -> db.place(customer,"atomic",Wire.JSON.readTree("[{\"menuItemId\":2,\"quantity\":1},{\"menuItemId\":3,\"quantity\":20}]"))).code);
   assertEquals(7, db.menu().get(1).path("stock").asInt());
  }
  try (var db = new Store(file, 99, true)) {
   assertEquals(customer, db.identify("ignored",token).id());
   assertEquals(order, db.place(customer,"stable",items).order().path("orderId").asLong());
   assertEquals(1,db.orders(customer).size()); assertEquals(0,db.menu().get(0).path("stock").asInt());
  }
 }
 @Test void demoResetCannotOpenRunningDatabaseAndRestoresSeed() throws Exception {
  Path file=temp.resolve("locked.db");
  try(var db=new Store(file,3,true)){
   assertThrows(IllegalStateException.class,()->new Store(file,3,true));
   var customer=db.identify("A","");
   db.place(customer.id(),"one",Wire.JSON.readTree("[{\"menuItemId\":1,\"quantity\":1}]"));
  }
  try(var db=new Store(file,3,true)){
   db.resetDemo(8);assertEquals(1,db.menu().get(0).path("stock").asInt());assertEquals(8,db.menu().get(1).path("stock").asInt());assertEquals(0,db.orders(null).size());
  }
 }
}

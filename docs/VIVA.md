# Short viva answers

**Why TCP?** Orders need reliable, ordered delivery. TCP retransmits lost bytes and preserves stream order, simplifying communication. We still implement message boundaries, errors, authentication, transactions and retry semantics ourselves.

**What do Socket and ServerSocket do?** `ServerSocket` binds/listens at the server address and port. `accept()` returns a connected `Socket`. A client constructs a `Socket` to connect; both sides read/write its streams.

**How can multiple clients use port 5050?** Each connection has a distinct tuple of source IP/port and destination IP/port. Clients normally have different ephemeral source ports. One listening socket accepts multiple connected sockets.

**Why application framing?** TCP is a byte stream, not a message protocol. A read may contain part of a JSON message or several messages. LF terminates each UTF-8 JSON object. The reader accumulates bytes until LF with a size limit.

**Why not networking on Swing's EDT?** A socket can block. Blocking the EDT freezes painting and button input. Our network reader and background executor handle I/O; `SwingUtilities.invokeLater` updates components on the EDT.

**How does stock remain consistent?** One serialized database transaction validates and conditionally reduces stock, inserts the order and snapshots, then commits. If any item fails, the whole transaction rolls back. The second last-sandwich request sees stock zero and fails.

**Why conditional stock updates as well as synchronization?** Synchronization prevents overlapping access in this server. `UPDATE ... WHERE stock >= quantity` expresses the invariant in the database, while a nonnegative stock CHECK adds defense. SQLite's transaction keeps all item changes atomic.

**Request ID versus order ID versus client order ID?** `requestId` identifies one network attempt and correlates its response. `clientOrderId` is a stable client-generated UUID for the same cart across retries. `orderId` is the server-assigned persistent order number used by the kitchen and student.

**How are duplicates prevented after restart?** SQLite enforces uniqueness of `(customer_id, client_order_id)`. We store a normalized cart fingerprint. Same reference/same contents returns the existing order; changed contents gets IDEMPOTENCY_CONFLICT. Both facts survive restart.

**What if the server commits but the response is lost?** The client retains its pending UUID and cart on disk. After reconnect, a retry returns the existing order without decrementing stock again. TCP alone cannot resolve this application-level ambiguity.

**What if a client disconnects?** The server removes its session and closes the socket; persisted orders remain. Pending client requests fail. Reconnect authenticates with the saved token and retrieves current menu/history. Events missed offline are recovered through these queries.

**What if the server disconnects/restarts?** Clients become offline on I/O failure, heartbeat failure or timeout. SQLite keeps committed data; incomplete transactions are not committed. Restart with the same database and reconnect. There is no automatic failover or second server.

**How does the correct student get an update?** The session is bound to the customer identified by its random server-issued token. Events are queued only for kitchen sessions and sessions belonging to the order's customer. A display name is not proof of identity.

**Why integer paise?** Integer arithmetic avoids binary floating-point rounding errors in money. For example, 1 sandwich at 4000 paise plus 3 samosas at 2000 paise is exactly 10000 paise (₹100.00). Prices are read from the server database.

**How is status validated?** Only kitchen sessions can update status. The server accepts only the next state in the sequence. Repeating the current state succeeds without an extra history entry; skipping/backtracking fails.

**Why SQLite?** It is an embedded JDBC-accessible database that is easy to distribute for a classroom project. Foreign keys, uniqueness and transactions enforce data rules. This design serializes writes rather than trying to make SQLite handle many simultaneous writers.

**Which tasks belong to TCP?** Connection establishment, ordered reliable bytes, retransmission, flow/congestion control. TCP does not understand customers, stock, JSON boundaries, permissions, order states, application deadlines or exactly-once business effects.

**Is TCP secure here?** No. It is reliable transport, not encryption. Staff codes/tokens are plaintext on the wire in this version. TLS and stronger account/token storage are production improvements.

**How did you test concurrency?** A JUnit test starts the real server on an ephemeral loopback port with a temporary demo database. Two independently authenticated socket clients wait at a barrier, then submit one sandwich each. The assertions require one success, one OUT_OF_STOCK, one stored order and zero remaining stock.

**Why do logs omit raw requests?** Requests contain bearer tokens and staff codes. Logs print controlled request type labels, generated connection IDs, order IDs and error codes; they avoid exposing credentials or arbitrary client strings.

**Main tradeoff?** A simple single-server, serialized mutation path is easy to explain and verify but is not a high-throughput production architecture. Virtual threads keep socket handling lightweight; they do not make serialized database writes parallel.

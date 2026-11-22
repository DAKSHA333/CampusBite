# Architecture and implementation map

```text
Student A ── persistent Socket ─┐
Student B ── persistent Socket ─┼─ ServerSocket :5050 ─ Store ─ SQLite
Kitchen   ── persistent Socket ─┘          │
                                  responses + events
```

## Responsibilities

| Class | Responsibility |
|---|---|
| `Main` | Parse options; choose server/student/kitchen; configure FlatLaf |
| `Wire` | Bounded UTF-8 LF framing, strict JSON object parsing, envelopes |
| `TcpServer` | Accept sockets, authenticate sessions, authorize requests, dispatch and broadcast |
| `Store` | Schema/seed, identity, validation, transactions, snapshots, state history |
| `Problem` | Expected business error code and friendly explanation |
| `Connection` | Client reader, correlated futures, serialized writes, request deadlines and heartbeat |
| `Profile` | Locked local profile, server-specific token and atomic pending-order save |
| `CampusFrame` | Shared Swing shell with role-specific student and kitchen views |
| `LastSandwichRace` | Two terminal clients synchronized at a barrier for a repeatable race |

## Threads and ordering

Java 21 virtual threads serve the accept loop and each session's reader/writer tasks. Each server session has one bounded queue and one writer. Concurrent broadcasts and replies therefore cannot interleave bytes. Slow clients that fill their 128-message queue are disconnected. A 90-second socket read timeout bounds idle sessions; connection admission is capped at 200.

One mutation monitor surrounds request handling and event enqueueing, ensuring committed mutations are queued in order. No network write is performed while this monitor is held. `Store` methods also synchronize access to one JDBC connection, so transaction boundaries cannot overlap across workers. This favors correctness and explainability over maximum throughput.

The client's reader dispatches response IDs to `CompletableFuture`s and forwards events. Swing uses a single background executor for requests; all rendering runs on the EDT via `invokeLater`. Events request a coalesced authoritative refresh. Events arriving during a refresh schedule another refresh, avoiding a stale response overwriting the final state. A generation number ignores callbacks from replaced connections. Buttons are disabled while work is pending.

## Atomic order sequence

1. Validate 1–50 item entries; require integer item IDs and integer quantities 1–20.
2. Merge repeated IDs, enforce the same per-item limit, sort IDs into a canonical fingerprint.
3. Begin transaction. Look up `(customer_id, client_order_id)`.
4. If an existing fingerprint matches, return its current persisted order. Otherwise reject conflicting reuse.
5. Read current names/prices/availability from SQLite. For each item perform `UPDATE menu_items SET stock=stock-? WHERE id=? AND available=1 AND stock>=?` and require one changed row.
6. Insert order, snapshot item rows, and PLACED history entry in the same transaction.
7. Commit, then queue the success response and events. On any error, roll back every stock and order change.

For one sandwich, the first transaction changes stock from 1 to 0 and commits. The second sees zero and rolls back with `OUT_OF_STOCK`. A UNIQUE database constraint backs up the idempotency lookup. SQLite foreign keys, nonnegative stock checks, WAL and a five-second busy timeout are enabled. A sidecar file lock prevents another CampusBite server/reset process from using the same database simultaneously.

## Persistence relationships

```text
customers 1 ── N orders 1 ── N order_items N ── 1 menu_items
                     └─ 1 ── N order_status_history
```

Timestamps are UTC ISO-8601 strings. Money is stored as integer paise and displayed as rupees. Order items preserve the server price and item name at purchase time. Client-supplied prices/totals are not used. State transitions are strictly adjacent; repeating the current state returns it without adding another history entry or event.

## Recovery boundary

TCP delivery does not guarantee the application received a success response. A server might commit and disconnect before the response reaches the student. The durable client UUID and server uniqueness constraint resolve this ambiguity on retry, including after server restart. Events are not a durable message queue; reconnect uses menu/history requests to recover authoritative state.

The server issues a random bearer token, distinct from display name, and attaches its customer ID to the socket session. Order queries never trust a customer ID from the request. Kitchen credentials authorize kitchen-only actions. Secrets are excluded from logs; logs contain generated session IDs, known request-type labels, order IDs, timestamps and controlled error codes.

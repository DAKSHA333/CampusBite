# CampusBite — eight-slide presentation outline

Use these as slide titles, short on-slide bullets and speaker notes. This deliverable is an outline, not a generated slide deck.

## 1 · A short break, a long queue

**On slide:** “Lunch break: 30 minutes. Ordering should take seconds.” Students need menu availability, a confirmed order and a pickup signal. Kitchen staff need one shared queue.

**Visual:** A student moving from menu → order → pickup.

**Speaker note:** Introduce Alice and Bob. Both want the last sandwich. A reliable system must never promise the same sandwich to both.

## 2 · Objectives and roles

**On slide:** Student: browse, order, track. Kitchen: receive, prepare, notify. Server: validate, reserve stock, persist. Multiple clients; one source of truth.

**Visual:** Three role columns with one sentence per role.

**Speaker note:** Explain the scope: Java 21 desktop application, no online payment. Student names are labels; server-issued identities determine ownership.

## 3 · Client–server architecture

**On slide:** Student A/B and kitchen each maintain a TCP socket to port 5050. The central server alone accesses SQLite.

**Visual:** Use the architecture diagram in `ARCHITECTURE.md`.

**Speaker note:** `ServerSocket.accept()` creates a separate connected `Socket` for each client. Reader/writer tasks serve clients; a serialized write transaction keeps stock consistent. The UI never reads the database directly.

## 4 · TCP and our message protocol

**On slide:** Reliable ordered byte stream. UTF-8 JSON + newline framing. `requestId` correlates responses. Events push changes over the same connection.

**Visual:** `{"type":"GET_MENU","requestId":"r1"}\n` alongside a RESPONSE and EVENT envelope.

**Speaker note:** TCP may split one JSON message into several packets or combine several messages. Our reader stops at LF, not at packet boundaries. This is covered by real-socket tests.

## 5 · Student and kitchen workflow

**On slide:** Browse → cart → confirmed order → track. `PLACED → PREPARING → READY → COLLECTED`. Ready for pickup is highlighted; collected orders remain in history.

**Visual:** Show the actual student and kitchen screens during the demo.

**Speaker note:** The server calculates prices in paise and validates each status transition. Only the owner receives private order updates; the kitchen sees the shared queue.

## 6 · Who gets the last sandwich?

**On slide:** One database transaction: check → reduce → insert → commit. Stock never negative. Same order UUID retries safely. Exactly one winner.

**Visual:** Alice and Bob arrows toward stock=1; first commit changes stock to 0; second gets OUT_OF_STOCK.

**Speaker note:** Explain both safeguards: serialized transactions and conditional `stock >= quantity` updates. An unavailable second item rolls back the entire cart. `(customer_id, client_order_id)` is unique across restarts.

## 7 · Live demonstration and evidence

**On slide:** Two students + kitchen. Last sandwich race. Live status update. Reconnect recovers history. Automated unit/integration suite passes; Windows GUI workflow exercised.

**Visual:** Run the demo script; show test summary and the server's accepted/rejected event logs.

**Speaker note:** State the exact current test count from `VERIFICATION.md`, not an invented coverage percentage. A barrier synchronizes two socket clients for the repeatable race. Localhost was verified; physical two-laptop networking and packet capture are separate checks.

## 8 · Limitations and next steps

**On slide:** Classroom LAN, plaintext TCP, one server. Next: TLS, staff accounts, stock management, cancellation/payment, larger-scale testing.

**Visual:** A small “working today / future improvements” comparison.

**Speaker note:** TCP solves transport reliability, not authorization, transactions or duplicate business operations. Those are application responsibilities. End by returning to the story: one sandwich, one accepted order, one correct pickup notification.

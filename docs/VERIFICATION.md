# Verification report

Verified on **22 November 2026**, Windows, Temurin Java **21.0.12.1**, Maven Wrapper **3.9.9**. Final `mvnw.cmd -q verify` completed successfully. **10 tests; 0 failures, 0 errors, 0 skipped.** No test-coverage percentage or throughput claim is made.

## Automated evidence

### Version 1.1 interface refresh

The same 10 automated tests passed after the interface redesign. Windows smoke checks covered the new connection dialog, plus/minus quantity controls, switching Bites/Drinks categories without losing the selected cart, checkout and automatic navigation to My orders, the progress timeline, kitchen preparation/ready actions and live counters, and restoring a saved student identity with a ready order. Final menu and kitchen screenshots are included in `docs/screenshots/`. A final text-only button-symbol correction was packaged after the passing test run.

The UI uses Java-drawn illustrations and retains Swing, the existing TCP protocol, server authorization and transactional stock handling. The earlier functional checks below continue to describe the underlying application; connection controls now reside in the Connection settings dialog.

| Test | Evidence |
|---|---|
| `TcpFoundationTest.twoClientsAndSplitCombinedFrames` | Two real socket clients, one split JSON message, two coalesced messages, correct response IDs and PONG |
| `StoreTest.menuSurvivesRestart` | Seed menu persists; new launch quantities do not overwrite stock |
| `StoreTest.billsValidationRollbackAndPersistentIdempotency` | ₹100 server bill, repeated IDs normalized, invalid quantities/unknown item rejected, earlier stock update rolled back when a later item fails, duplicate/conflicting IDs handled, same order restored after reopen |
| `StoreTest.demoResetCannotOpenRunningDatabaseAndRestoresSeed` | Second opener refused while lock held; stopped database reset restores one sandwich and configured other stock |
| `SocketIntegrationTest.simultaneousLastSandwichHasExactlyOneWinner` | Two independent clients released by barrier; exactly one acceptance, one OUT_OF_STOCK, one stored order, zero stock |
| `SocketIntegrationTest.authorizationTransitionsAndPrivateLiveEvents` | No pre-HELLO access, wrong staff code denied, student kitchen actions denied, same-name students isolated, menu event broadcast, private order events routed correctly, invalid skip denied, valid/repeated states handled |
| `SocketIntegrationTest.persistedSocketOrderAndReconnectAfterServerRestart` | Order submitted/retried through TCP; server and database restarted; token restores one order; replay does not consume stock again |
| `SocketIntegrationTest.malformedAndOversizePeersCannotCrashServer` | Invalid JSON, non-object, trailing object and over-limit frames rejected; later PING still succeeds |
| `SocketIntegrationTest.historyPagesRetainEveryOrder` | 23 orders retrieved through 20-order pages without losing old history |
| `SocketIntegrationTest.disconnectFailsPendingRequestCleanly` | Peer closes after reading request; client receives an I/O failure rather than a hanging request |

Every database test uses a JUnit temporary directory. Socket tests bind ephemeral loopback ports so they do not touch the normal or demo database. Surefire summaries are copied into `docs/test-results/`; generated XML/full output remain in `target/surefire-reports` after building.

## Windows GUI checks performed

- Launched the executable JAR outside an IDE using student and kitchen modes.
- Connected a student, inspected six-item menu, quantity controls, rupee formatting and cart total.
- Placed one sandwich through the GUI; received order #1 / ₹40 confirmation; observed stock decrease from 20 to 19.
- Connected kitchen, retrieved the student's order, then used buttons to advance through PREPARING, READY and COLLECTED.
- Observed the student's green **Ready for pickup** state without a manual refresh.
- Disconnected/reconnected the student; same identity/order and current status returned.
- Observed collected order in kitchen Completed orders and empty active queue.
- Checked final layout after fitting initial window size to the desktop and aligning text consistently; checkout button and footer are visible.
- In a fresh demo with one sandwich, selected two and submitted: received readable stock-unavailable error, with cart preserved and controls enabled for correction.

During requests the UI remained responsive and pending actions were disabled. These are interactive smoke checks, not automated accessibility or exhaustive display-resolution tests.

## Packaged demonstration checks

`java -jar target/campusbite.jar server --demo --reset-demo` reset the stopped demo database. Starting the demo listener on `127.0.0.1:5050` succeeded. A second reset while it was running was rejected with “Database is already in use. Stop its server first.”

Running `scripts\race-demo.cmd localhost 5050` against that packaged server produced:

```text
Alice: ACCEPTED order #1, totalPaise=4000
Bob: OUT_OF_STOCK — Sandwich is no longer available in that quantity. Refresh your cart.
PASS: exactly one accepted order; one stock-unavailable response.
```

Winner identity is nondeterministic; either student may win. Server log showed both requests, one accepted order and one OUT_OF_STOCK rejection. No access code or reconnect token was printed. After checking, the test processes were stopped and the demo database reset again, leaving one sandwich ready for the next demonstration.

## Build-stage record

1. Empty folder inspected; JDK/Maven found; skeleton packaged and all three mode entry points invoked; Maven Wrapper generated.
2. TCP framing/correlation exercised with two socket clients.
3. SQLite menu initialization and restart persistence checked.
4. Ordering bill/validation/rollback/idempotency tests passed; persisted socket confirmation verified.
5. Real-socket barrier race passed with one sandwich.
6. Kitchen authorization, transitions and targeted event tests passed.
7–8. Student and kitchen GUI workflow exercised on Windows.
9. Token-based reconnect, pending request failure and server restart recovery checked.
10. Final suite: 10 passing tests; GUI layout and stock-error check repeated after refinements.
11. Executable JAR and Windows scripts produced; packaged localhost race passed.
12. README, protocol/architecture notes, eight-slide outline, five-minute demo script and viva answers completed.

## Remaining environment checks and limits

- **Physical two-laptop LAN test not performed.** Localhost TCP is verified. Use README LAN steps to check binding, private-network firewall and Wi-Fi isolation in the classroom.
- **Actual Wireshark capture not performed or bundled.** Capture instructions and handshake/Follow TCP Stream walkthrough are supplied in `DEMO.md`.
- No long-running soak/load benchmark, exhaustive accessibility audit or production security review. Plaintext TCP is intentionally documented as a classroom limitation.
- First wrapper/dependency download needs internet; JDK 21 is required on machines running the JAR. Java itself is not bundled.

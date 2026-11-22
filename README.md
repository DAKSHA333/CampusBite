# CampusBite

**A Client–Server Canteen Ordering Application Using TCP Socket Programming in Java**

Students have a short lunch break. CampusBite lets them browse live stock, order food and see when it is ready. A kitchen workspace receives the orders and advances them through `PLACED → PREPARING → READY → COLLECTED`.

This is a classroom Computer Networks project: actual persistent TCP sockets, newline-delimited UTF-8 JSON, server-owned SQLite data, and Swing/FlatLaf desktop clients. The central demonstration is two students competing for the last sandwich: exactly one succeeds.

## Download for evaluation

The complete runnable project ZIP and executable JAR are available in [GitHub Releases](https://github.com/DAKSHA333/CampusBite/releases/tag/v1.0.0). Download **CampusBite-1.0.0.zip**, extract it, then open the `CampusBite/scripts` folder. Start `server.cmd`, then `kitchen.cmd` and `student.cmd`. Java 21 is required. Click Connect in each client; the classroom staff code is `campus123`.

GitHub's **Code → Download ZIP** contains the source project; run the Maven build below to create its executable JAR. The release ZIP already includes the built JAR, source, tests, scripts and all presentation materials.

## Quick start on Windows

Install **JDK 21** and put its `bin` directory on `PATH`. Run commands in this project folder. Maven is downloaded by the included wrapper on the first build; internet access is needed for that build and dependency downloads. Running the packaged application is offline-capable.

```powershell
java -version
.\mvnw.cmd clean verify
```

The executable, dependency-inclusive JAR is `target\campusbite.jar`. Use a separate terminal for each command:

```powershell
# Terminal 1: server (leave running)
java -jar target\campusbite.jar server --demo

# Terminal 2: kitchen
java -jar target\campusbite.jar kitchen

# Terminal 3: student A
java -jar target\campusbite.jar student --profile student-a --name Alice

# Terminal 4: student B
java -jar target\campusbite.jar student --profile student-b --name Bob
```

Click **Connect** in each client. Defaults: `localhost:5050`; classroom staff code **`campus123`**. Choose quantities in Today's menu, review the cart, and click Place order. My orders shows confirmations and live status. Kitchen buttons expose the next valid state; collected orders move to Completed orders.

Windows shortcuts are also provided:

```powershell
.\scripts\server.cmd --demo
.\scripts\kitchen.cmd
.\scripts\student.cmd --profile student-a --name Alice
.\scripts\student.cmd --profile student-b --name Bob
```

Scripts change to the project root so data locations remain predictable. `scripts\build.cmd` builds and tests. On macOS/Linux, use `sh mvnw clean verify` and the same Java commands with forward slashes.

## Reset and run the last-sandwich demonstration

1. Stop the demo server with **Ctrl+C**. Close its student clients.
2. Run the reset command below. A file lock refuses reset if this database is in use.
3. Start the server again with `--demo`.
4. Use **new profile names** after each reset (for example `demo2-a` and `demo2-b`). Reset removes customer identities as well as orders.
5. Set sandwich quantity to one in both student carts **before** either submits. Submit both. One succeeds and the other receives a friendly stock error; a previously selected quantity remains editable even when stock becomes zero.

```powershell
java -jar target\campusbite.jar server --demo --reset-demo
java -jar target\campusbite.jar server --demo
```

Demo data is separate from normal data. Reset restores all six seed items, one sandwich, and `--stock` units (default 20) of each other item. `--demo` does not reset on ordinary startup. The reset command exits without starting a listener. `--demo --db ...` is rejected to prevent confusion about which database is reset.

For a reproducible simultaneous release using two real TCP clients, run this **instead of** buying the sandwich in the GUIs:

```powershell
java -cp target\campusbite.jar campusbite.tools.LastSandwichRace localhost 5050
# or:
.\scripts\race-demo.cmd localhost 5050
```

It prints the winner's order ID and the loser's `OUT_OF_STOCK`, and fails if that is not the outcome. The kitchen also receives the winning order. This diagnostic creates disposable student identities; it does not save their reconnect tokens.

## Configuration

| Option | Mode | Default / behavior |
|---|---|---|
| `--bind ADDRESS` | server | `127.0.0.1`; use `0.0.0.0` or the server's LAN address for other laptops |
| `--host ADDRESS` | clients | `localhost`; editable before connecting |
| `--port N` | all | `5050`, range 1–65535 |
| `--db PATH` | normal server | `data/campusbite.db`, relative to working directory |
| `--demo` | server | fixed separate `data/campusbite-demo.db` |
| `--reset-demo` | server with `--demo` | reset seed data while server is stopped, then exit |
| `--stock N` | server | 20, range 0–100000; applies to new seed data or demo reset, not existing stock |
| `--profile NAME` | student | `default`; 1–40 letters/digits/underscores/hyphens; one open instance per profile |
| `--name NAME` | student | blank; enter display name before first connection |
| `--staff-code CODE` | server/kitchen | overrides environment setting |
| `CAMPUSBITE_STAFF_CODE` | environment | fallback staff code; default is `campus123` |

Set the same staff code in the server and kitchen terminal, or enter it in the kitchen's password field. Avoid putting real secrets in command history. For the classroom default, no configuration is needed. Changing a display name does not change an already saved identity. Two fresh profiles can have the same name and still own different orders.

## Data and recovery

Only the server opens SQLite. Tables: `menu_items`, `customers`, `orders`, `order_items`, and `order_status_history`. Prices and totals are integer paise. Order items snapshot their name and unit price.

Student profiles are stored at `%USERPROFILE%\.campusbite\<profile>.properties`. They contain tokens and any unresolved order, keyed by the exact host string and port. Use the same host spelling on reconnect (`localhost` and `127.0.0.1` are different local profile keys). Do not share profile files. `.lock` files are harmless coordination files; their presence alone does not mean a process is running.

Click Disconnect and Connect/Reconnect to restore authoritative menu and order history. The client saves an order's stable UUID and contents **before** transmitting. If the response is lost, reconnect and click **Retry pending order**. The server returns the existing order for the same contents; a retry cannot charge stock twice. A definitive validation error clears the pending submission so the cart can be changed. A database-busy error keeps the reference for retry. Pending requests fail on socket close or a ten-second request deadline.

Heartbeat: clients send PING every 15 seconds; responses contain PONG. Client reads time out after 45 seconds; the server closes clients idle for 90 seconds. A network break need not be detected immediately. UI data shown while offline is the last snapshot until reconnect.

## Two-laptop LAN setup

On the server laptop:

```powershell
ipconfig
java -jar target\campusbite.jar server --demo --bind 0.0.0.0 --port 5050
```

Read the active Wi-Fi/Ethernet IPv4 address from `ipconfig`. On the other laptop, replace the example address with that address:

```powershell
java -jar target\campusbite.jar student --host 192.168.1.10 --port 5050 --profile lan-a --name Alice
Test-NetConnection 192.168.1.10 -Port 5050
```

Use the same private network; guest Wi-Fi may isolate devices. `localhost` on the second laptop points to that second laptop, not the server.

If Windows Firewall blocks inbound connections, allow the Java executable used by this server on **Private networks** through Windows Security → Firewall & network protection → Allow an app through firewall. Alternatively, an administrator can create an inbound TCP rule for port 5050 scoped to the Private profile and local subnet. Do not disable the firewall. A localhost-only demo does not require a LAN firewall exception. This project does not modify firewall settings.

## Troubleshooting

| Symptom | Check |
|---|---|
| `java` not recognized / class version error | Install JDK 21 and check `java -version` and `JAVA_HOME` |
| Wrapper cannot download | Check internet/proxy access to Maven Central; retry the build. Installed Maven can run `mvn clean verify` |
| Address already in use | Stop your earlier server or choose `--port 5051` on every client; inspect `Get-NetTCPConnection -LocalPort 5050` |
| Connection refused | Start server first; match port, bind address and host |
| Connection times out on LAN | Check private-network firewall allowance and Wi-Fi client isolation |
| Invalid saved identity after reset | Close the student and use a fresh `--profile`; reset intentionally removed customers |
| Profile already open | Each simultaneous student needs its own profile |
| Database already in use | Stop the process using that database before resetting or launching another server |
| Stock did not change with `--stock` | Existing stock is preserved; use explicit demo reset for demonstration data |
| Busy / uncertain order | Reconnect, then retry the pending order with the same saved reference |

## Design and verification

See [architecture](docs/ARCHITECTURE.md), [protocol](docs/PROTOCOL.md), and [verification report](docs/VERIFICATION.md). Tests use isolated temporary databases and actual loopback sockets, including concurrent clients, fragmented/coalesced frames, ownership, idempotency, reconnect and transitions.

```powershell
.\mvnw.cmd test
# Focused race acceptance test:
.\mvnw.cmd '-Dtest=SocketIntegrationTest#simultaneousLastSandwichHasExactlyOneWinner' test
```

JUnit reports are in `target/surefire-reports`. The project uses a small package structure: `protocol` for framing, `server` for listener and persistence/business rules, `client` for shared networking/profile/UI, and `tools` for the terminal race demo. `Store` deliberately combines SQL and the transaction's business rules so the atomic boundary is easy to explain in a viva.

## Presentation materials

- [Eight-slide outline](docs/PRESENTATION.md)
- [Five-minute demonstration, including Wireshark](docs/DEMO.md)
- [Short viva answers](docs/VIVA.md)

## Limits

This is a classroom LAN application, not a payment system. TCP traffic is **unencrypted**: names, staff codes and reconnect tokens can be read in a packet capture. Use demonstration identities and codes. Tokens are also stored in the local profile and server database. There is no TLS, account/password management, payment, cancellation, stock-editing GUI or production audit/security hardening. Menu availability is enforced and stock changes live; replenishment for demonstrations uses stopped-server reset. Production improvements include TLS, hashed tokens, proper staff accounts, backup/migrations and stronger resource/rate controls.

Writes and event enqueueing are serialized for simple, predictable classroom behavior. Up to 200 concurrent connections are admitted, with bounded output queues; this is not a measured throughput claim. History transfers in pages of 20, but the UI retains all returned orders in memory. A second physical laptop and an actual Wireshark capture remain environment-dependent checks; see the verification report for exactly what was exercised.

# Five-minute demonstration

## Before the audience arrives

Build with `.\mvnw.cmd clean verify`. Confirm JDK 21. Stop any demo server, reset with `java -jar target\campusbite.jar server --demo --reset-demo`, and choose unused profiles such as `show3-a` and `show3-b`. Keep the README commands ready. Use a terminal visible to the audience for the server. Use fake names and the classroom staff code.

If showing a packet capture, start Wireshark **before** connecting clients. Use the LAN interface for two laptops or the Npcap loopback interface for localhost. A prepared capture can save time, but identify it as prerecorded. No packet capture is bundled or claimed as verified in this project.

## Timed run

| Time | Action | What to say / expected evidence |
|---|---|---|
| 0:00–0:30 | Start `java -jar target\campusbite.jar server --demo` | Point to listener address, port 5050 and separate demo database |
| 0:30–1:00 | Launch kitchen and two student clients; click Connect | Kitchen code authorizes staff. Distinct `--profile` values preserve separate identities |
| 1:00–1:35 | Alice orders one samosa and one tea | Cart is ₹35; server confirmation gives order ID and PLACED; kitchen card appears |
| 1:35–2:20 | Both students select one sandwich **before** either submits; submit both | One ₹40 order succeeds; the other gets stock unavailable. Menu stock becomes zero. Do not buy the sandwich in the normal-order step |
| 2:20–3:00 | Kitchen marks Alice's normal order Preparing then Ready | Student's My orders updates; Ready for pickup is highlighted. Other student's orders remain private |
| 3:00–3:30 | Disconnect Alice, then Connect | Saved token restores the same order and status. No second order is created |
| 3:30–4:00 | Kitchen marks Collected; show Completed orders | Order remains in history; all state changes were validated by server |
| 4:00–4:30 | Show timestamped server log | Connections, PLACE_ORDER accepted/rejected, and transitions; no tokens or staff code in log |
| 4:30–5:00 | Show Wireshark filter and Follow TCP Stream | Identify handshake and JSON frames. Explain plaintext limitation and distinction between TCP reliability and application correctness |

Launch commands (separate terminals):

```powershell
java -jar target\campusbite.jar kitchen
java -jar target\campusbite.jar student --profile show3-a --name Alice
java -jar target\campusbite.jar student --profile show3-b --name Bob
```

**Repeatable race alternative:** At 1:35 run `.\scripts\race-demo.cmd` instead of the GUI sandwich submissions. It releases two real socket clients from a shared barrier and asserts one acceptance/one rejection. The kitchen receives the winning order, owned by “Race Alice” or “Race Bob.” Continue the pickup/reconnect demonstration with the earlier normal order. Do not run both race variants without another reset; the first consumes the one sandwich.

## Wireshark procedure

1. Select the active Ethernet/Wi-Fi adapter for LAN traffic. For all apps on one Windows laptop, select the Npcap loopback adapter (often labeled “Adapter for loopback traffic capture”). If no loopback interface is available, install/configure Npcap through the normal Wireshark setup outside the live demonstration.
2. Start capture, then start/connect clients so the opening handshake is included.
3. Apply display filter:

   ```text
   tcp.port == 5050
   ```

4. Locate SYN from a client's ephemeral port to server port 5050, SYN/ACK from the server, and ACK from the client. Multiple clients use different ephemeral ports/connection tuples even though the server listens on one port.
5. Right-click a packet in the desired connection and choose **Follow → TCP Stream**. Choose a readable text view. Find HELLO, GET_MENU, PLACE_ORDER, RESPONSE and EVENT lines. The stream reassembles bytes; a TCP packet is not necessarily one JSON message.
6. Show how response request IDs match requests and how an unsolicited event is labeled separately. Use the stream selector to inspect the other client's connection.
7. Stop the capture. If saving/exporting it for presentation, use only demonstration data: the stream includes the staff code and reconnect tokens in plaintext.

For an alternate port, change the display filter accordingly. A display filter hides other captured traffic; it does not prevent it being captured. Capture only in your authorized classroom environment and keep unrelated traffic out of presentation materials.

## Recovery during the demo

- Port busy: stop your previous server or choose another port consistently.
- Stale demo profile: close it and use a fresh profile name after reset.
- Missing sandwich: stop the server, reset, restart; never edit a running database.
- Connection lost while ordering: reconnect and use Retry pending order, preserving the original UUID.
- No packets: check interface and port; capture must begin before handshake if you want to show it.
- GUI scaling: maximize the client; menu and history panes scroll independently.

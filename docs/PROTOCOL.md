# CampusBite protocol v1

Transport: persistent TCP, default port 5050. Each frame is exactly **one UTF-8 JSON object followed by LF** (`0A`); CRLF is accepted as JSON trailing whitespace. Maximum incoming frame is 65,536 bytes excluding LF. Jackson rejects trailing JSON values. Invalid UTF-8, invalid JSON, non-object JSON, oversized frames and EOF mid-frame close that connection; other clients keep running. A syntactically valid unknown request returns `UNKNOWN_REQUEST`.

TCP packets are not messages. `Wire.read` accumulates bytes until LF, handling both fragmentation and several frames in one packet. There is one serialized outgoing writer per connection. Request IDs are nonempty strings of at most 100 characters. Normal responses echo the request ID. Unsolicited events have no request ID. A capacity rejection may send `SERVER_BUSY` with an empty request ID before closing.

## Envelopes

```json
{"type":"RESPONSE","requestId":"r1","ok":true,"data":{"type":"PONG"}}
{"type":"RESPONSE","requestId":"r2","ok":false,"error":{"code":"OUT_OF_STOCK","message":"Sandwich is no longer available in that quantity. Refresh your cart."}}
{"type":"EVENT","event":"ORDER_STATUS_CHANGED","data":{"orderId":101,"status":"READY"}}
```

The event above is abbreviated; order events contain the full order snapshot described below. Each example is transmitted on its own line.

## Requests

| Type | Additional fields | Access / response data |
|---|---|---|
| `HELLO` | `role:"student"`, `name`, optional `token` | First identity: `{role,customerId,name,token}`; existing token restores original identity |
| `HELLO` | `role:"kitchen"`, `staffCode` | Staff credential check; `{role:"kitchen"}` |
| `GET_MENU` | none | Authenticated session; menu array |
| `PLACE_ORDER` | `clientOrderId`, `items:[{menuItemId,quantity}]` | Student only; full order snapshot |
| `GET_MY_ORDERS` | optional `beforeOrderId` | Student only; own orders, newest first, maximum 20 |
| `GET_KITCHEN_ORDERS` | optional `beforeOrderId` | Kitchen only; orders of all statuses, maximum 20 |
| `UPDATE_ORDER_STATUS` | integer `orderId`, `status` | Kitchen only; full current order snapshot |
| `PING` | none | Allowed before HELLO; `{type:"PONG"}` inside response data |

One successful HELLO per socket. A rejected staff code may be retried. Unknown tokens fail with `INVALID_TOKEN` rather than silently creating a new customer. Requests containing a customer ID cannot change the authenticated identity.

History pagination: omit `beforeOrderId` for the newest 20 orders. For the next page send the last returned order ID as `beforeOrderId`. Continue until a page has fewer than 20 entries. This cursor stays stable when new orders are inserted. The Swing client fetches all pages on refresh.

```json
{"type":"HELLO","requestId":"r1","role":"student","name":"Alice"}
{"type":"GET_MENU","requestId":"r2"}
{"type":"PLACE_ORDER","requestId":"r3","clientOrderId":"7d8823f4-7a19-4485-a237-459f87904630","items":[{"menuItemId":1,"quantity":1},{"menuItemId":2,"quantity":2}]}
{"type":"GET_MY_ORDERS","requestId":"r4"}
```

On a separate kitchen connection:

```json
{"type":"HELLO","requestId":"k1","role":"kitchen","staffCode":"campus123"}
{"type":"GET_KITCHEN_ORDERS","requestId":"k2"}
{"type":"UPDATE_ORDER_STATUS","requestId":"k3","orderId":1,"status":"PREPARING"}
```

## Data shapes

Menu entry:

```json
{"menuItemId":1,"name":"Sandwich","description":"Fresh vegetables & chutney","pricePaise":4000,"stock":1,"available":true}
```

Order snapshot:

```json
{"orderId":1,"customerId":1,"studentName":"Alice","clientOrderId":"stable-cart-id","status":"PLACED","totalPaise":8000,"createdAt":"2026-11-22T04:00:00.000Z","updatedAt":"2026-11-22T04:00:00.000Z","items":[{"menuItemId":1,"name":"Sandwich","unitPricePaise":4000,"quantity":1},{"menuItemId":2,"name":"Samosa","unitPricePaise":2000,"quantity":2}]}
```

## Events

| Event | Audience | Data |
|---|---|---|
| `MENU_CHANGED` | All authenticated students | Full menu array |
| `ORDER_PLACED` | Kitchen sessions and the owning student's sessions | Full committed order |
| `ORDER_STATUS_CHANGED` | Kitchen sessions and the owning student's sessions | Full committed order |

Order events never reach other students. Events follow commit. A replayed order or repeated current-state update returns a response without duplicating events. Events can interleave with responses; clients must correlate by `requestId`, not by assuming the next frame is the response.

## Errors and retry rules

| Code | Meaning / action |
|---|---|
| `OUT_OF_STOCK` | Whole cart rejected; change quantities/items |
| `INVALID_QUANTITY` | Noninteger, nonpositive, over 20 per item, empty cart or too many entries |
| `UNKNOWN_ITEM` | Invalid/nonexistent menu ID |
| `INVALID_ORDER_ID` | Missing/oversized stable order reference |
| `IDEMPOTENCY_CONFLICT` | Same customer/reference reused with different normalized contents |
| `INVALID_TRANSITION` | Skipped/backward/unknown status |
| `UNAUTHORIZED` | Missing identity, bad staff code or wrong role |
| `INVALID_TOKEN` | Token does not belong to this database |
| `INVALID_NAME` | Blank, over 60 characters or control characters |
| `INVALID_REQUEST` | Invalid request ID, cursor, order ID or repeated HELLO |
| `UNKNOWN_REQUEST` | Unrecognized request type |
| `NOT_FOUND` | Order does not exist |
| `SERVER_BUSY` | Database failure/contention or capacity reached; retry later |

Every PLACE_ORDER attempt needs a fresh **requestId**; a retry of the same cart retains **clientOrderId**. Idempotency is scoped to customer and compares normalized item quantities, so entry order/repeated item representation does not change the cart identity. Database state persists this rule through restarts. Clients must not clear an uncertain pending order and silently create a new UUID.

Heartbeat requests every 15 seconds, request deadline 10 seconds, client read timeout 45 seconds, server read timeout 90 seconds. No automatic reconnect loop: the UI provides Connect/Reconnect and refreshes state after authentication. Plain TCP provides no confidentiality; use fake credentials in captures.

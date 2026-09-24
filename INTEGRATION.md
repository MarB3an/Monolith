# INTEGRATION.md — LegacySupply Partner Interface

## Part A: Pre-Integration Probe Results

### Product–Supplier Mapping Table

| Our Product ID | Our Product Name  | LegacySupply SupplierSku | Description (Supplier)    | PackSize |
|---------------|-------------------|--------------------------|---------------------------|----------|
| P100          | Wireless Mouse    | ZHF-8111                 | WIRELESS MOUSE 2.4GHZ     | 6        |
| P200          | Mechanical Keyboard | ZHF-1964               | KEYBOARD MECH TKL         | 6        |
| P300          | USB-C Hub         | ZHF-1746                 | USB HUB 4-PORT            | 24       |

All prices are in PHP.  
`UnitCost` for ZHF-8111 = PHP 450.00 per case; ZHF-1964 = PHP 1,899.00; ZHF-1746 = PHP 399.00.

---

### Session Lifetime Measurement

**How a session works:**
1. POST `/api/v1/auth/token` with an `<AuthRequest>` containing `<ClientId>` and `<ApiKey>`.
2. Receive `<AuthResponse>` containing `<SessionToken>` and `<IssuedAt>`.
3. Send `X-LS-Session: <token>` header on every subsequent request.
4. When the session expires, LegacySupply returns HTTP 401 with error code `E-AUTH-07` ("Session not valid.").

**Measured lifetime:**
I measured two sessions by polling `GET /catalog` every 5–10 seconds:
- Session 1 (`b6a700c0f4c4849e4519a8e0b355ca5fc44d`): still valid at 85s, then returned `E-SYS-50` (transient server error, NOT a session error). After the server recovered at ~173s, the session was accepted again. Final 401 `E-AUTH-07` arrived at approximately **247 seconds** from issue.
- Session 2 (`9216efdd4a14c1b2894021f9168491774f68`): same pattern. 401 `E-AUTH-07` at **~247 seconds**.

**Measured TTL: ~240 seconds (4 minutes).**  
The manual does not state this — it was measured empirically.

**How the adapter decides to re-authenticate:**
The `LegacySupplySessionManager` tracks the `issuedAt` timestamp of the current token. Before any request, if the token age exceeds **180 seconds** (3 minutes), it proactively calls `POST /auth/token` to get a fresh session. Additionally, if any request returns HTTP 401 with `E-AUTH-03` ("Session not recognized") or `E-AUTH-07` ("Session not valid."), the adapter immediately re-authenticates and retries the original call once. This dual strategy covers both time-based expiry and edge-case immediate invalidation.

---

### Error Codes Observed

| Error Code | HTTP Status | Cause (what actually triggered it)                                      |
|------------|-------------|-------------------------------------------------------------------------|
| E-AUTH-01  | 401         | Wrong `ClientId` (e.g., `"wrong-id"`) submitted to `POST /auth/token`  |
| E-AUTH-02  | 401         | Missing `X-LS-Session` header on any authenticated endpoint             |
| E-AUTH-03  | 401         | `X-LS-Session` value that was never issued (completely fabricated token)|
| E-AUTH-07  | 401         | A previously-valid session token used after it expired (~247s after issue)|
| E-FMT-01   | 415         | Sending `Content-Type: application/json` instead of `application/xml`  |
| E-FMT-02   | 400         | Sending malformed XML (e.g., `<AuthRequest><UnclosedTag>`) or a BuyerRef >40 chars |
| E-SKU-02   | 422         | Ordering a SupplierSku that does not appear in the partner's catalog (e.g., `NONEXISTENT-999`) |
| E-QTY-11   | 422         | Sending `<Qty>0</Qty>` (quantity must be 1–99 whole number)            |
| E-IDEM-04  | 409         | Reusing the same `X-Request-Id` header value but with **different** order content (different Qty or BuyerRef) |
| E-PO-04    | 404         | Calling `GET /purchase-orders/{PoNumber}` with a PoNumber that does not exist |
| E-QRY-06   | 400         | Calling `GET /purchase-orders` without the required `buyerRef` query parameter |
| E-SYS-50   | 503         | Transient server-side processing error (intermittent during chaos/outages) |
| E-SYS-99   | 503         | Full service unavailability (e.g., LegacySupply was in maintenance outage) |
| E-RATE-03  | 429         | (Documented but not triggered) — would appear if partner's request quota is exceeded |

**Notable observations:**
- `E-SYS-50` is transient and NOT a session error. The same session remained valid after the server recovered.
- `E-REF-05` (BuyerRef invalid) is documented but a BuyerRef >40 chars returned `E-FMT-02` instead. BuyerRef must be ≤40 characters and well-formed.
- The idempotent replay (same `X-Request-Id` + same content) returns HTTP **200** (not 201) with the original acknowledgement — no duplicate order is created.

---

### Qty and Uom — Meaning and Worked Example

**Qty** is the number of *cases* to order, not individual units. It must be a whole number from 1 to 99.

**Uom** (Unit of Measure) is always `CS` (Case) in responses. Every item in the catalog has a `PackSize` field that tells you how many individual units are in one case.

**Worked Example — Wireless Mouse (ZHF-8111, PackSize=6):**

Our Inventory `P100` (Wireless Mouse) stock dropped to 3, which is below threshold 5. The auto-reorder rule decides to order 10 units to bring stock back up.

1. **Units needed by Inventory:** 10
2. **Translate to cases:** `ceil(10 / 6) = ceil(1.667) = 2 cases`
3. **What we send:** `<Qty>2</Qty>` to LegacySupply
4. **What LegacySupply confirms:** `<Qty>2</Qty><Uom>CS</Uom>`
5. **Units that arrive on delivery:** `2 cases × 6 units/case = 12 units`
6. **Our Inventory receives:** 12 units restocked to P100

So even though we needed 10 units, we receive 12 (rounded up to whole cases). This is the "round up" requirement from the spec.

---

## Part D: Handling Unexpected Order Statuses

The known status codes from the LegacySupply interface are:

| StatusCode | LegacySupply meaning | Our domain status    |
|------------|----------------------|----------------------|
| 10         | Accepted             | `ORDERED`            |
| 20         | Picking              | `IN_FULFILLMENT`     |
| 30         | Shipped              | `IN_FULFILLMENT`     |
| 40         | Delivered            | `DELIVERED`          |

**Decision for unexpected/undocumented status codes:**
If the `StatusCode` in a `PurchaseOrderStatus` response is not one of `{10, 20, 30, 40}`, the adapter sets the supplier order status to `UNKNOWN`. An `UNKNOWN` order is:
- **Logged as a warning** (`log.warn`) for operator attention.
- **Not retried** automatically (to prevent accidental duplication).
- **Not treated as delivered** — Inventory is NOT restocked.
- **Left open** for a human operator to investigate and either manually restock or mark as FAILED.

If LegacySupply ever introduces a status code for "cancelled by supplier", it would need to be explicitly added to the mapping; until then, it falls into `UNKNOWN`. Operators should check the `supplier_orders` table for `UNKNOWN` rows regularly.

---

## Reflection Answers

**Q1: LegacySupply never tells you how long a session lasts. Measure your session lifetime from your own logs, state the number, and explain how your adapter decides when to sign in again.**

Measured TTL: approximately **240 seconds** (both measured sessions expired with `E-AUTH-07` at 247s). Our adapter re-signs in proactively after **180 seconds** (3 min) from the `IssuedAt` timestamp. If a request still gets `E-AUTH-03` or `E-AUTH-07`, the adapter invalidates the cached token and re-authenticates immediately before retrying the failed call.

**Q2: The catalog reports PackSize and orders report Uom "CS". Using one of your own orders, show the arithmetic from "units your Inventory needed" to the Qty you sent, and to the units your Inventory received on delivery.**

See the "Worked Example" in the Qty/Uom section above. For order `PO-100064`: Inventory needed restock. `ZHF-8111` has PackSize=6. We sent `Qty=1` (1 case). Inventory received `1 × 6 = 6 units` of P100 on delivery.

**Q3: Suppose LegacySupply is replaced next semester by a supplier with a JSON API and different status codes. List every class in your project that would have to change, and explain why your Order and Inventory modules are not on that list.**

Classes that would change — all in `edu.cit.pescante.supplier`:
- `LegacySupplyClient` — currently sends XML requests and parses XML responses; would need to send/parse JSON.
- `LegacySupplySessionManager` — auth mechanism might change (e.g., Bearer token, OAuth).
- `ProductSupplierCatalog` — SKU codes would change for the new supplier.
- `SupplierGatewayImpl` — the logic translating our domain call to the supplier protocol would adapt.
- `OrderTrackingScheduler` — status code mapping from new supplier's codes to our enum.

**Why Order and Inventory are NOT on the list:**  
`edu.cit.pescante.shop.OrderService` and `edu.cit.pescante.inventory.InventoryServiceImpl` never import, reference, or know about LegacySupply. The Inventory module calls `SupplierGateway.reorder(productId, units)` — a domain interface that speaks our language only. The Order module doesn't even know suppliers exist. Both modules respond to `SupplierDeliveryEvent` (our domain event with `productId` and `units`), which is supplier-agnostic. Swapping suppliers requires touching zero lines outside the `supplier` package.

# Idempotency Design for abstrapact

## 1. Problem Statement

Distributed systems retry. HTTP clients retry on timeouts. Load balancers replay requests. Message brokers redeliver. Without idempotency, these retries silently corrupt data.

In abstrapact, the following mutations are non-idempotent today:

| Endpoint | Side Effect if Retried |
|---|---|
| `POST /api/public/sales/contracts` | Duplicate draft contracts |
| `POST /api/public/sales/contracts/{id}/accept` | Duplicate `PaymentTransaction` rows + duplicate Stripe Checkout Sessions |
| `POST /api/public/sales/contracts/{id}/offer` | Duplicate state-transition audit steps (contract state machine may protect this, but it is not guaranteed) |

A concrete example: a B2C user clicks "Pay now". The frontend calls `accept`. The network times out. The frontend retries. Without idempotency, two `PaymentTransaction` rows are created, two Stripe Checkout Sessions are opened, and the user sees two checkout pages.

## 2. Design Principles

The design follows:

- **IETF draft** `draft-ietf-httpapi-idempotency-key-header` — the `Idempotency-Key` request header.
- **Stripe API idempotency** — idempotency keys on `POST` requests, 24-hour retention, cached result replay. See [Stripe error handling: idempotency and retries](https://docs.stripe.com/error-low-level.md#idempotency) and [Stripe API: idempotent requests](https://docs.stripe.com/api/idempotent_requests).
- **The Quarkus idempotency pattern** — database-backed records with unique-key concurrency control and request fingerprinting. See [Why Retries Break Your Java APIs (And How Idempotency Fixes It)](https://www.the-main-thread.com/p/java-idempotency-keys-ietf-quarkus).

Key rules:

1. **Client generates the key** — UUIDv4 or UUIDv7. The key represents one logical operation.
2. **Server caches the result** — status code, response body, request fingerprint, created at, expires at.
3. **Same key + same payload = replay** — return the cached result without re-executing.
4. **Same key + different payload = error** — HTTP 422, key reused with different parameters.
5. **Race conditions resolved by the database** — unique constraint on `(idempotency_key)`; the first writer wins, others replay.

## 3. Scope — What Needs Idempotency

### 3.1 Outbound: Stripe API calls

The `StripePSPService.createPayment()` method calls Stripe to create a Checkout Session. Stripe natively supports idempotency via the `Idempotency-Key` header.

**Action**: Pass an idempotency key when creating the Stripe session. On retry, Stripe returns the same session instead of creating a duplicate.

### 3.2 Inbound: Our own REST endpoints

The following endpoints mutate state and must accept an `Idempotency-Key` header:

| Endpoint | Key Scope | Rationale |
|---|---|---|
| `POST /api/public/sales/contracts` | Account-scoped | Prevents duplicate draft contracts on retry |
| `POST /api/public/sales/contracts/{id}/accept` | Contract-scoped | Prevents duplicate payment transactions on retry |

The `offer` endpoint (`POST /api/public/sales/contracts/{id}/offer`) is borderline — the contract state machine already prevents transitions from non-DRAFT states, so retries after success are naturally safe (they fail with 422). However, retries during the narrow window before the transaction commits could still create duplicate audit steps. For completeness, `offer` should also accept the header.

## 4. Data Model

### 4.1 Table: `T_idempotency_record`

```sql
CREATE TABLE T_idempotency_record (
    id              VARCHAR(36) PRIMARY KEY,
    idempotency_key VARCHAR(255) NOT NULL,
    scope           VARCHAR(50) NOT NULL,        -- 'account', 'contract', 'stripe_api'
    scope_id        VARCHAR(36),                  -- the account id or contract id
    request_fingerprint VARCHAR(64) NOT NULL,    -- SHA-256 of the request payload
    status_code     INT NOT NULL,
    response_body   TEXT,
    created_at      TIMESTAMP NOT NULL,
    expires_at      TIMESTAMP NOT NULL,
    CONSTRAINT UQ_idempotency_key_scope UNIQUE (idempotency_key, scope)
);

CREATE INDEX I_idempotency_record_key ON T_idempotency_record(idempotency_key);
CREATE INDEX I_idempotency_record_scope ON T_idempotency_record(scope, scope_id);
CREATE INDEX I_idempotency_record_expires ON T_idempotency_record(expires_at);
```

**Fields explained:**

- `idempotency_key` — the key supplied by the client in the `Idempotency-Key` header.
- `scope` — prevents key collisions across different operation types. A client may reuse the same UUID for different endpoints; the scope disambiguates.
- `scope_id` — for account-scoped keys, the account id. For contract-scoped keys, the contract id. For Stripe outbound, a fixed value like `'stripe'`.
- `request_fingerprint` — SHA-256 hash of the request payload. Detects key reuse with different parameters.
- `status_code` + `response_body` — the cached result to replay on retry.
- `expires_at` — records older than 24 hours are eligible for deletion (Stripe's retention window is 24 hours; we align with that).

**No Envers audit table is needed.** Idempotency records are operational data, not business data.

### 4.2 Entity

```java
@Entity
@Table(name = "T_idempotency_record")
public class IdempotencyRecord {

    @Id
    private String id;

    @Column(name = "idempotency_key", nullable = false)
    private String key;

    @Column(nullable = false)
    private String scope;

    @Column(name = "scope_id")
    private String scopeId;

    @Column(name = "request_fingerprint", nullable = false)
    private String requestFingerprint;

    @Column(name = "status_code", nullable = false)
    private int statusCode;

    @Column(name = "response_body")
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
```

## 5. Implementation

### 5.1 `IdempotencyService`

```java
@ApplicationScoped
public class IdempotencyService {

    @Inject
    EntityManager em;

    @Transactional
    public Optional<IdempotencyRecord> find(String key, String scope) {
        // …
    }

    /**
     * Executes the processor if no record exists. If a record exists, validates the
     * fingerprint and returns the cached result. On constraint violation (race),
     * finds the existing record and returns it.
     */
    @Transactional
    public IdempotencyRecord execute(
            String key,
            String scope,
            String scopeId,
            String requestFingerprint,
            Supplier<ProcessedResponse> processor) {
        // …
    }
}
```

### 5.2 REST endpoint integration

For each idempotent endpoint, the flow is:

1. Read `Idempotency-Key` header. If missing → `400 Bad Request`.
2. Compute `requestFingerprint = SHA-256(requestBody)`.
3. Call `idempotencyService.execute(key, scope, scopeId, fingerprint, processor)`.
4. If the record already exists and the fingerprint does not match → `422 Unprocessable Entity`.
5. If the record already exists and the fingerprint matches → replay `statusCode` + `responseBody`.
6. If no record exists → execute the processor, cache the result, return it.

Example for `accept`:

```java
@POST
@Path("/{id}/accept")
public Response accept(
        @PathParam("id") String contractId,
        @HeaderParam("Idempotency-Key") String idempotencyKey,
        String body) {

    if (idempotencyKey == null || idempotencyKey.isBlank()) {
        throw new BadRequestException("Missing Idempotency-Key header");
    }

    String fingerprint = Hashing.sha256(body);
    String accountId = accountId();

    IdempotencyRecord record = idempotencyService.execute(
        idempotencyKey,
        "contract_accept",
        contractId,
        fingerprint,
        () -> {
            String checkoutUrl = salesProcessService.acceptContract(contractId, accountId);
            CustomerContractResponse response = contractService.getContract(contractId, accountId);
            response.setCheckoutUrl(checkoutUrl);
            String json = objectMapper.writeValueAsString(response);
            return new ProcessedResponse(200, json);
        });

    return Response.status(record.getStatusCode())
        .entity(record.getResponseBody())
        .build();
}
```

### 5.3 Stripe outbound integration

The `StripePSPService.createPayment()` method should pass an idempotency key to Stripe:

```java
RequestOptions options = RequestOptions.builder()
    .setIdempotencyKey(request.getCorrelationId()) // or a separate idempotency key
    .build();

Session session = client.v1().checkout().sessions().create(params, options);
```

Using `correlation_id` as the Stripe idempotency key is natural: it is already a secret UUID that uniquely identifies one payment attempt. If the `accept` endpoint is retried, the same `correlation_id` is generated (because the same `PaymentTransaction` is reused, see §5.4). Stripe then returns the same Checkout Session.

Alternatively, generate a separate idempotency key for the Stripe call and store it on the `PaymentTransaction`. Using `correlation_id` is simpler and equally safe because it is already unique per payment attempt.

### 5.4 `createPaymentForContract` idempotency

The `PaymentService.createPaymentForContract()` method must be made idempotent at the application layer, not just at the Stripe layer. If `accept` is retried after the `PaymentTransaction` was created but before the response reached the client, we must not create a second transaction.

**Approach**: Before creating a new `PaymentTransaction`, check if one already exists for the contract in `PENDING` status. If yes, reuse it (return the existing checkout URL from the existing `psp_session_id`). If no, create one.

```java
@Transactional
public CreatePaymentResponse createPaymentForContract(String contractId, String actorAccountId) {
    // Check for existing PENDING transaction (idempotency guard)
    Optional<PaymentTransaction> existing = findPendingTransactionForContract(contractId);
    if (existing.isPresent()) {
        // Reuse the existing transaction. If the Stripe session was already created,
        // return its URL. If not, create the Stripe session now.
        PaymentTransaction tx = existing.get();
        if (tx.getPspSessionId() != null) {
            return new CreatePaymentResponse(resolveCheckoutUrl(tx.getPspSessionId()), tx.getPspSessionId());
        }
        // Stripe session not yet created — create it now and store the session id.
        return createStripeSessionAndStore(tx);
    }

    // No existing transaction — create new one (original logic)
    // ...
}
```

This is a **state-based idempotency** guard, distinct from the header-based idempotency record. Both are needed:

- **Header-based idempotency** prevents the `accept` endpoint from being called twice with different payloads.
- **State-based idempotency** prevents the `PaymentTransaction` from being created twice even if the endpoint is called with a different key (edge case: two different clients accepting the same contract — which should be blocked by authorization anyway).

### 5.5 Concurrent Requests and Race Conditions

This is the most critical detail. With Hibernate's default transaction isolation (READ COMMITTED), the idempotency record is **not visible** to other transactions until the first transaction commits. Two simultaneous requests with the same key can both execute the processor before either inserts the record.

#### Step-by-step race scenario

```
Time  Request A                                  Request B
----  ---------                                  ---------
 t1   find(key) → null                           
 t2   start processor (Stripe network call)      
 t3                                                find(key) → null (A not committed)
 t4                                                start processor (Stripe network call)
 t5   PaymentTransaction created                  PaymentTransaction created
 t6   Stripe session created (idempotency key=K)  Stripe returns SAME session (idempotency key=K)
 t7   INSERT idempotency record → succeeds       
 t8   COMMIT                                      
 t9                                                INSERT idempotency record → constraint violation
 t10                                               Rollback; read A's record → replay A's result
```

**What happens exactly:**

1. Both requests check the database. Neither sees a record (uncommitted inserts are invisible).
2. Both execute the processor. Both create a `PaymentTransaction`.
3. Both call Stripe with the **same idempotency key** (the `correlation_id`). Stripe returns the **same Checkout Session** for both calls.
4. Request A commits first, inserting the idempotency record.
5. Request B tries to insert the same record and gets a `UniqueConstraintViolationException`.
6. B rolls back its transaction (which discards its `PaymentTransaction`), reads A's committed record, and replays A's response.

**Result:** Only one idempotency record exists, only one `PaymentTransaction` remains, and both clients receive the same checkout URL.

#### Why this is safe despite the race

Even though both processors run, the system is safe because of **three independent guards**:

| Guard | What it prevents |
|---|---|
| **Stripe idempotency key** | Both calls to Stripe use the same key → Stripe returns the same session. No duplicate Stripe session is created. |
| **Database unique constraint** | Only one idempotency record is persisted. The loser's transaction rolls back, discarding its `PaymentTransaction`. |
| **State-based guard** | If the loser somehow avoids rollback (should not happen), `findPendingTransactionForContract()` in `PaymentService` detects the existing PENDING transaction and reuses it instead of creating a new one. |

#### If the first request dies mid-flight

What if Request A dies after creating the Stripe session but before committing the idempotency record?

1. Request A's transaction rolls back. The `PaymentTransaction` is NOT persisted.
2. The Stripe session exists on Stripe's side but is orphaned (no matching row in our database).
3. Request B retries with the same key.
4. B's `find(key)` still returns null (A never committed).
5. B executes the processor, creates a new `PaymentTransaction`, calls Stripe with the same idempotency key.
6. Stripe returns the **same session** (because the idempotency key is the same).
7. B persists the record and returns the checkout URL.

**Result:** The Stripe session is reused; no duplicate session is created. The orphan session on Stripe's side is harmless and auto-expires.

#### What if the first request fails with a business error

If Request A's processor throws (e.g., contract not in APPROVED state), the transaction rolls back and no idempotency record is inserted. Request B with the same key will:

1. `find(key)` → null (A never committed).
2. Execute the processor.
3. If B succeeds, insert the record with the successful result.
4. If B also fails, throw the error to the client.

This is correct: a key associated with a failed operation should not be "poisoned." The client may fix the underlying issue and retry with the same key.

#### What if the first request returns a 4xx error

Per the [Stripe documentation](https://docs.stripe.com/error-low-level.md#idempotency), `4xx` responses **are** cached by Stripe's idempotency layer. We follow the same rule:

- If the processor returns a `4xx` status (e.g., `422 Unprocessable Entity`), the idempotency record is still persisted with that status code and body.
- Retries with the same key replay the `4xx` response without re-executing the processor.
- The client must generate a **new** key if it changes the request payload.

This prevents a "thundering herd" of retries all hitting the same validation error.

### 5.6 Retry Strategy — Server vs Client

The question is: who retries when a Stripe call fails? The answer is **both, at different layers, with clear responsibilities**.

#### Current problem

The `StripePSPService` creates the Stripe client with **no retry configuration**:

```java
StripeClient.builder()
    .setApiKey(secretKey)
    .setApiBase(apiBase)
    .build();  // default: 0 retries
```

Any `StripeException` (network timeout, Stripe 500, Stripe 400) is immediately thrown as `WebApplicationException(400)`. This is wrong:

| Stripe response | What it means | Current server behavior | Should be |
|---|---|---|---|
| `5xx` | Stripe transient failure | Throws `400 Bad Request` | `503 Service Unavailable` + Stripe SDK retries |
| Network timeout | TCP/HTTP timeout | Throws `400 Bad Request` | `503 Service Unavailable` + Stripe SDK retries |
| `4xx` | Bad request (our fault) | Throws `400 Bad Request` | `400/422` (correct) |

A `400` tells the B2C client "don't retry, you sent bad data." But a Stripe 500 is NOT the client's fault — the client SHOULD retry. The server must distinguish these cases.

#### Server-side retries (Stripe SDK)

Enable `setMaxNetworkRetries(2)` on the Stripe client. This handles:

- Network timeouts between our server and Stripe
- Stripe `500` / `502` / `503` / `504` responses
- Connection resets and socket errors

```java
StripeClient.builder()
    .setApiKey(secretKey)
    .setApiBase(apiBase)
    .setMaxNetworkRetries(2)
    .build();
```

**Critical requirement:** Server-side retries are only safe if we pass an `Idempotency-Key` to Stripe. Without it, a retry creates a duplicate Checkout Session. See §5.3 for how to pass the key.

The Stripe SDK retries with exponential backoff:
- Retry 1: ~1 second after failure
- Retry 2: ~2 seconds after retry 1 fails

If all retries fail, the SDK throws `StripeException` with the last error. Our server should then return `503 Service Unavailable` to the B2C client.

#### Client-side retries (B2C Angular)

The Angular HTTP client should retry on:

| Condition | Retry? | Reason |
|---|---|---|
| Browser network timeout (no HTTP response) | Yes | Request may not have reached our server |
| Our server returns `503` | Yes | Transient server/Stripe failure |
| Our server returns `504` | Yes | Gateway timeout |
| Our server returns `400` / `422` | **No** | Permanent business error |
| Our server returns `200` | No | Success |

Retry policy for the Angular client:

```typescript
// Angular HTTP interceptor
intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    const idempotencyKey = this.idempotencyKeyFor(req);
    req = req.clone({
        headers: req.headers.set('Idempotency-Key', idempotencyKey)
    });

    return next.handle(req).pipe(
        retry({
            count: 2,
            delay: (error, retryCount) => {
                // Only retry on network errors or 5xx
                if (error instanceof HttpErrorResponse) {
                    if (!error.status || error.status >= 500) {
                        return timer(retryCount * 1000); // 1s, then 2s
                    }
                }
                throw error; // Don't retry 4xx
            }
        })
    );
}
```

#### Why both are needed

The failure can happen at two different network hops:

```
Browser ──[1]──► Quarkus Server ──[2]──► Stripe
```

| Failure at | Who sees it | Who retries |
|---|---|---|
| Hop [1] (browser → server) | Browser | **Client** retries with same key |
| Hop [2] (server → Stripe) | Our server | **Stripe SDK** retries with same key |

**Example:**
1. Browser sends `accept` request.
2. Server calls Stripe, Stripe creates session.
3. **Hop [1] fails**: WiFi drops, browser never sees the `200`.
4. Browser retries with same `Idempotency-Key`.
5. Server's idempotency layer replays the cached `200` + checkout URL.

**Another example:**
1. Browser sends `accept` request.
2. **Hop [2] fails**: Server → Stripe network timeout.
3. Stripe SDK retries (transparent to our business code).
4. If Stripe SDK succeeds, our server returns `200` to browser.
5. If Stripe SDK exhausts retries, our server returns `503`.
6. Browser sees `503`, retries with same `Idempotency-Key`.
7. Server replays... but wait — the idempotency record was never inserted because the processor threw!

**Important edge case:** If the Stripe SDK exhausts all retries, the processor throws an exception. The transaction rolls back. No idempotency record is inserted. The client's retry with the same key will execute the processor again. This is correct — the previous attempt never succeeded, so a fresh attempt is warranted.

#### Updated error mapping

```java
try {
    Session session = client.v1().checkout().sessions().create(params, options);
    return new CreatePaymentResponse(session.getUrl(), session.getId());
} catch (StripeException e) {
    int stripeStatus = e.getStatusCode(); // may be 0 for network errors
    if (stripeStatus == 0 || stripeStatus >= 500) {
        // Stripe 5xx or network error — client MAY retry
        throw new WebApplicationException(
            Response.status(503)
                .entity("Payment provider temporarily unavailable. Retry with the same Idempotency-Key.")
                .build());
    } else {
        // Stripe 4xx — client should NOT retry
        throw new WebApplicationException(
            Response.status(400)
                .entity("Payment provider rejected the request: " + e.getMessage())
                .build());
    }
}
```

## 6. Key Generation

Clients (the Angular B2C app) generate idempotency keys as **UUIDv4** or **UUIDv7**.

Rules:
- Generate the key **before** the first request.
- **Persist the key across retries** — store it in component state, localStorage, or the URL fragment.
- **Never reuse a key for a different logical operation** — e.g., accepting contract A and then contract B must use different keys.
- A good scoped key format: `{userId}:{operation}:{uuid}` (e.g., `acct_123:accept:550e8400-e29b-41d4-a716-446655440000`).

The server does not validate key format beyond non-blankness.

## 7. Expiration and Cleanup

Idempotency records expire after **24 hours** (matching Stripe's cache window). After expiry:

- A retry with the same key is treated as a **new** request.
- Old records are deleted by a scheduled job or by the database (e.g., MySQL `EVENT` or application `@Scheduled`).

```sql
-- Periodic cleanup (application-scheduled or DB event)
DELETE FROM T_idempotency_record WHERE expires_at < NOW();
```

## 8. Security Considerations

1. **Key values are not sensitive** — they are UUIDs, not passwords or tokens. No encryption required.
2. **Fingerprinting prevents parameter tampering** — an attacker who intercepts a key cannot replay it with a different payload (e.g., changing the contract amount). The fingerprint mismatch yields 422.
3. **Scope prevents cross-endpoint replay** — a key used for `create` cannot be replayed against `accept`.
4. **No secret data in response body** — the cached response body must not contain API keys, session secrets, or personal data that should not be replayed. (The `accept` response is a `CustomerContractResponse`, which is safe.)

## 9. Testing Idempotency and Race Conditions

### 9.1 Unit Tests for `IdempotencyService`

| Test | What it asserts |
|---|---|
| `executeWithNewKeyCreatesRecord` | A new key executes the processor, persists the record, and returns the result. |
| `executeWithExistingKeyReplaysResult` | The same key returns the cached result; the processor is NOT invoked a second time. |
| `executeWithExistingKeyAndDifferentFingerprintThrows422` | Key reuse with a tampered payload is rejected. |
| `executeWithExpiredKeyCreatesNewRecord` | An expired key is treated as a new request. |
| `executePropagatesProcessorException` | If the processor throws, the exception bubbles up and NO record is persisted. |

Use a `Spy` or `AtomicInteger` counter injected into the processor lambda to verify invocation count.

```java
AtomicInteger callCount = new AtomicInteger(0);
Supplier<ProcessedResponse> processor = () -> {
    callCount.incrementAndGet();
    return new ProcessedResponse(201, "{\"id\":\"1\"}");
};

// First call — executes
ProcessedResponse r1 = idempotencyService.execute(key, scope, scopeId, fingerprint, processor);
assertEquals(1, callCount.get());

// Second call — replays
ProcessedResponse r2 = idempotencyService.execute(key, scope, scopeId, fingerprint, processor);
assertEquals(1, callCount.get()); // processor NOT called again
```

### 9.2 Integration Tests for REST Endpoints

| Test | What it asserts |
|---|---|
| `acceptWithIdempotencyKeyCreatesPayment` | Valid key → `PaymentTransaction` created, Stripe session opened, idempotency record persisted. |
| `acceptWithSameKeyReturnsSameCheckoutUrl` | Retry with same key → same checkout URL, no new Stripe session, no new `PaymentTransaction`. |
| `acceptWithSameKeyAndDifferentBodyReturns422` | Key reused with different payload → `422 Unprocessable Entity`. |
| `acceptWithoutIdempotencyKeyReturns400` | Missing header → `400 Bad Request`. |
| `acceptWithExpiredKeyCreatesNewPayment` | Expired key → new `PaymentTransaction` created. |

WireMock the Stripe create-session endpoint so tests run without network calls. Assert the `Idempotency-Key` header is present in the outgoing Stripe request.

### 9.3 Race Condition Tests

Race conditions are the hardest to test. Use the following strategies.

#### 9.3.1 Thread-safety test with `CompletableFuture`

Fire two requests concurrently from the same JVM. This exercises the database-level race without needing a full distributed setup.

```java
@Test
void concurrentRequestsWithSameKeyRaceSafely() throws Exception {
    String key = UUID.randomUUID().toString();
    String contractId = createApprovedContract();
    String body = acceptBody(contractId);
    String fingerprint = Hashing.sha256(body);

    // Use a CountDownLatch to maximise the chance of a race
    CountDownLatch latch = new CountDownLatch(2);
    AtomicReference<Response> responseA = new AtomicReference<>();
    AtomicReference<Response> responseB = new AtomicReference<>();

    Callable<Void> task = () -> {
        latch.countDown();
        latch.await(); // both threads wait here
        Response r = given()
            .header("Idempotency-Key", key)
            .body(body)
            .post("/api/public/sales/contracts/" + contractId + "/accept");
        responseA.compareAndSet(null, r); // only first caller sets
        responseB.set(r); // last caller wins
        return null;
    };

    Future<Void> f1 = executor.submit(task);
    Future<Void> f2 = executor.submit(task);
    f1.get(10, TimeUnit.SECONDS);
    f2.get(10, TimeUnit.SECONDS);

    // Both should succeed (200)
    assertEquals(200, responseA.get().getStatusCode());
    assertEquals(200, responseB.get().getStatusCode());

    // Both should return the SAME checkout URL
    assertEquals(responseA.get().getBody(), responseB.get().getBody());

    // Exactly ONE PaymentTransaction exists
    assertEquals(1, countPaymentTransactionsForContract(contractId));

    // Exactly ONE Stripe session was created
    verify(1, postRequestedFor(urlEqualTo("/v1/checkout/sessions")));
}
```

**Why this works:** The `CountDownLatch` synchronises both threads at the `await()` barrier, then releases them simultaneously. This maximises the chance that both threads execute the processor before either commits.

#### 9.3.2 Slow-processor test

Force the processor to pause mid-flight (e.g., with a `Thread.sleep` or a blocking mock), then submit the second request while the first is still running.

```java
@Test
void secondRequestArrivesWhileFirstIsInFlight() throws Exception {
    CountDownLatch firstStarted = new CountDownLatch(1);
    CountDownLatch firstMayContinue = new CountDownLatch(1);

    // Stub the Stripe PSP to block until we release it
    when(stripePSP.createPayment(any()))
        .thenAnswer(inv -> {
            firstStarted.countDown();
            firstMayContinue.await(); // block the first request
            return new CreatePaymentResponse("http://stripe/checkout/1", "cs_1");
        });

    // Request A starts, blocks at Stripe
    Future<Response> futureA = executor.submit(() ->
        given().header("Idempotency-Key", key).body(body)
            .post("/api/public/sales/contracts/" + contractId + "/accept"));

    firstStarted.await(); // wait until A has entered the processor

    // Request B arrives while A is still blocked
    Response responseB = given()
        .header("Idempotency-Key", key)
        .body(body)
        .post("/api/public/sales/contracts/" + contractId + "/accept");

    // B should NOT execute the processor — it must wait or replay.
    // With the current design, B will find no record (A not committed),
    // execute the processor, and then hit the unique constraint.
    // This is the race described in §5.5.

    firstMayContinue.countDown(); // release A
    Response responseA = futureA.get(10, TimeUnit.SECONDS);

    // Both succeed, same result
    assertEquals(200, responseA.getStatusCode());
    assertEquals(200, responseB.getStatusCode());
    assertEquals(responseA.getBody(), responseB.getBody());

    // Only one transaction
    assertEquals(1, countPaymentTransactionsForContract(contractId));
}
```

#### 9.3.3 Database isolation test

Use two separate `EntityManager` instances (or two `@QuarkusTest` threads with separate transactions) to simulate the exact isolation level. Verify that:

1. Transaction T1 inserts the idempotency record.
2. Transaction T2 (running concurrently) cannot see T1's uncommitted insert.
3. T2's insert fails with `ConstraintViolationException` after T1 commits.
4. T2 rolls back and replays T1's result.

```java
@Test
void databaseIsolationPreventsPhantomRead() {
    String key = "race-test-key";

    // T1: begin, check key (null), start slow work
    em1.getTransaction().begin();
    assertNull(idempotencyService.find(key, "test"));

    // T2: begin, check key (still null — T1 not committed)
    em2.getTransaction().begin();
    assertNull(idempotencyService.find(key, "test"));

    // T1: finish work, insert record, commit
    idempotencyService.executeInTransaction(em1, key, ...);
    em1.getTransaction().commit();

    // T2: try to insert → constraint violation
    assertThrows(PersistenceException.class,
        () -> idempotencyService.executeInTransaction(em2, key, ...));

    em2.getTransaction().rollback();
}
```

#### 9.3.4 Chaos test: first request dies mid-flight

Simulate the first request dying after the Stripe call but before commit:

```java
@Test
void retryAfterFirstRequestDiesMidFlightReusesStripeSession() {
    // Request A: calls Stripe, then JVM is killed (simulated by throwing)
    assertThrows(RuntimeException.class, () ->
        idempotencyService.execute(key, scope, scopeId, fingerprint, () -> {
            stripeClient.createSession(...); // succeeds on Stripe
            throw new SimulatedJVMKill();      // dies before commit
        }));

    // No idempotency record, no PaymentTransaction
    assertNull(idempotencyService.find(key, scope));
    assertEquals(0, countPaymentTransactionsForContract(contractId));

    // Request B retries with the same key
    ProcessedResponse result = idempotencyService.execute(key, scope, scopeId, fingerprint, () -> {
        // This calls Stripe again with the SAME idempotency key
        // Stripe returns the SAME session
        return stripeClient.createSession(...);
    });

    // B succeeds, reusing the Stripe session from A
    assertNotNull(result);
    assertEquals(1, countPaymentTransactionsForContract(contractId));
}
```

### 9.4 Metrics and Observability

Add Micrometer counters to observe idempotency behaviour in production:

| Metric | Name | Labels |
|---|---|---|
| Processor executions | `idempotency.processor.executions` | `endpoint`, `scope` |
| Replays | `idempotency.processor.replays` | `endpoint`, `scope` |
| Fingerprint mismatches | `idempotency.fingerprint.mismatch` | `endpoint`, `scope` |
| Race-condition rollbacks | `idempotency.race.rollback` | `endpoint`, `scope` |

In tests, read these counters to verify behaviour:

```java
assertEquals(1, registry.counter("idempotency.processor.executions",
    "endpoint", "accept", "scope", "contract_accept").count());
assertEquals(1, registry.counter("idempotency.processor.replays",
    "endpoint", "accept", "scope", "contract_accept").count());
```

### 9.5 E2E Test

Add a Playwright E2E test that:

1. The user clicks "Pay now".
2. The frontend generates an `Idempotency-Key` and sends it with the `accept` request.
3. The network is artificially throttled (or the backend is paused with a debug breakpoint).
4. The user clicks "Pay now" again (frontend retries with the same key).
5. Assert that only one `PaymentTransaction` row exists and only one Stripe Checkout page is shown.

This validates the full stack: frontend key generation, header transmission, backend deduplication, and Stripe session reuse.

## 10. Native Image Compatibility

The implementation uses only constructs compatible with GraalVM native image:

- Plain JPA entities (no reflection-heavy frameworks)
- `MessageDigest` for SHA-256 (available in substrate VM)
- `java.util.UUID` for key generation
- No dynamic proxies or runtime bytecode generation

## 11. Migration Order

The implementation requires:

1. Migration `V01.030__createIdempotencyRecordTable.sql` — create `T_idempotency_record`.
2. `IdempotencyRecord` entity + `IdempotencyService` + `ProcessedResponse` record.
3. Update `StripePSPService.createPayment()` to pass `Idempotency-Key` to Stripe.
4. Update `PaymentService.createPaymentForContract()` with state-based idempotency guard.
5. Update `NonMultitenancyCustomerContractResource.create()` and `.accept()` to read and enforce `Idempotency-Key`.
6. Add a scheduled cleanup job for expired records.
7. Update the Angular B2C app to generate and persist idempotency keys.
8. Tests for all of the above.

## 12. Alternatives Considered

| Approach | Pros | Cons | Rejected Because |
|---|---|---|---|
| Redis-backed idempotency store | Faster than DB, TTL built-in | Adds infrastructure dependency | We already have MySQL; no need for another store |
| In-memory `ConcurrentHashMap` | Simple, fast | Lost on restart, not shared across pods | Not suitable for production |
| Stripe-only idempotency (no app-layer) | Minimal code | Does not prevent duplicate `PaymentTransaction` rows | Insufficient — need app-layer guards too |
| Idempotency on `offer` only | Simpler | Misses the `create` and `accept` duplicates | The `accept` endpoint is the most critical |

## 13. Summary

Idempotency is implemented at **two layers**:

1. **Application layer** — `Idempotency-Key` header on `POST` endpoints, database-backed records with fingerprint validation.
2. **Stripe layer** — `Idempotency-Key` passed to Stripe Checkout Session creation.

Additionally, a **state-based guard** in `PaymentService.createPaymentForContract()` prevents duplicate `PaymentTransaction` rows even in edge cases.

This design is watertight against:
- Network timeouts and client retries
- Concurrent duplicate submissions
- Malicious key reuse with different payloads
- Server restarts (persisted in database)

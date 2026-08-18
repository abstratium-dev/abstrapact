# Payment Handling Implementation Plan

Implement the full PSP-agnostic payment handling feature described in `docs/DESIGN_OF_PAYMENT.md`: per-product Stripe Checkout Sessions, signed webhook processing with idempotency + staleness checks, browser redirect endpoints, CSV export, and E2E tests. Native-image verification is deferred (reflection config stub added only).

## Decisions (confirmed with user)
- **Scope**: Everything including E2E (`05-payment-flow.spec.ts`). Native-image build verification deferred.
- **Webhook/redirect auth**: Endpoints live at `/api/public/payment/*` and use `@PermitAll` to override the path-level `authenticated` policy on `/api/*`. CSV export stays authenticated (`@RolesAllowed(Roles.USER)`).
- **Stripe SDK in tests**: Real `com.stripe:stripe-java` dependency; tests point `StripeClient` at a Wiremock server via `StripeClient.builder().setApiBase(wiremockUrl)`. Webhook signature verification (`Webhook.constructEvent`) is local crypto — tests generate valid HMAC-SHA256 signatures with a test `whsec_` secret via a helper (no Wiremock needed for the crypto).
- **Stripe SDK version**: `33.3.0` (latest stable, published >7 days ago).
- **Package root**: `dev.abstratium.abstrapact.non_multitenancy.sales.payment` (per design). Entities use plain `organisation_id`/`contract_id` columns (no `@TenantId`), mirroring `NonMultitenancyProcessInstance`.
- **Product credentials resolution**: A contract's Stripe credentials come from its **first line item's** product definition (`contract.lineItems[0].productInstance.productDefinition`). Multi-product contracts with different Stripe accounts are out of scope for the initial implementation (noted as a risk).

## Implementation Steps

### 1. Dependencies & config
- Add `com.stripe:stripe-java:33.3.0` to `pom.xml` (compile scope).
- Add to `src/main/resources/application.properties`:
  - `abstrapact.payment.psp=stripe`
  - `abstrapact.payment.stripe.success-url=${ABSTRAPACT_PAYMENT_STRIPE_SUCCESS_URL}`
  - `abstrapact.payment.stripe.cancel-url=${ABSTRAPACT_PAYMENT_STRIPE_CANCEL_URL}`
  - `abstrapact.payment.webhook.stale-after-hours=${ABSTRAPACT_PAYMENT_STALE_AFTER_HOURS:24}`
  - Test-profile overrides (`%test.`) pointing success/cancel URLs at `http://localhost:10088/api/public/payment/{success,cancel}?session_id={CHECKOUT_SESSION_ID}`.
- Add native-image reflection stub `src/main/resources/META-INF/native-image/reflect-config-stripe.json` (PaymentIntent, Charge, checkout.Session, Event, BalanceTransaction, ApiResource) and append `--initialize-at-build-time=com.stripe,com.google.gson` to `quarkus.native.additional-build-args`. **Not verified by building** — deferred.

### 2. Database migration `V01.025__createPaymentTransactionTable.sql`
- `ALTER TABLE T_product_definition ADD COLUMN` the 4 columns: `stripe_secret_key VARCHAR(100)`, `stripe_webhook_secret VARCHAR(100)`, `payment_success_redirect_url VARCHAR(500)`, `payment_cancel_redirect_url VARCHAR(500)`.
- `CREATE TABLE T_payment_transaction` (+ indexes + FK to `T_contract`) per design.
- `CREATE TABLE T_webhook_event` (+ indexes + unique `(psp_identifier, psp_event_id)` + FK to `T_payment_transaction`) per design.
- Envers audit tables `T_payment_transaction_AUD` and `T_webhook_event_AUD` (mirror `V01.020` pattern: `REV BIGINT NOT NULL, REVTYPE TINYINT, PRIMARY KEY(id, REV)`, FK to `REVINFO`).

### 3. Entities
- `non_multitenancy/sales/payment/entity/PaymentTransaction.java` — per design (id, organisationId, contractId, pspIdentifier, correlationId unique, pspSessionId, pspTransactionRef, grossAmount, feeAmount, netAmount, currency, status, createdAt, updatedAt). Nested `enum PaymentStatus { PENDING, SUCCEEDED, FAILED, STALE }`. `@Audited`.
- `non_multitenancy/sales/payment/entity/WebhookEvent.java` — per design. Nested `enum ProcessingResult { PROCESSED, DUPLICATE, UNMATCHED, STALE, IGNORED }`. `@Audited`. `rawPayload` as `@Lob`.
- Extend **both** `NonMultitenancyProductDefinition` and the tenant-bound `ProductDefinition` with the 4 new fields (mirror rule from `non_multitenancy/AGENTS.md`). `stripeSecretKey` and `stripeWebhookSecret` get `@JsonIgnore`. Getters/setters for all four.

### 4. PSP interface & DTOs
- `service/PSPInterface.java` — `createPayment(CreatePaymentRequest)`, `processWebhookEvent(String payload, String signature)`, `getPspIdentifier()`.
- `boundary/dto/CreatePaymentRequest.java` (contractId, correlationId, amount, currency, description, successUrl, cancelUrl, stripeSecretKey).
- `boundary/dto/CreatePaymentResponse.java` (checkoutUrl, pspSessionId).
- `boundary/dto/PaymentEventResult.java` (pspEventId, eventType, correlationId, pspTransactionRef, grossAmount, feeAmount, currency, status, matched, rawPayload).
- `boundary/dto/PaymentExportRow.java` (date, partner, description, grossAmount, feeAmount, stripeTxn, contractId).

### 5. StripePSPService
- `service/stripe/StripePSPService.java` implements `PSPInterface`, `@ApplicationScoped`, `@Alternative`-style selection by config (`@IfBuildProfile`/`@ConfigProperty` name match) — use a CDI producer or `@Inject Instance<PSPInterface>` filtered by `getPspIdentifier()` equals `abstrapact.payment.psp`. Simplest: a `PSPSelector` CDI bean that injects all `PSPInterface` beans and exposes `getActive()`.
- `createPayment`: build `StripeClient` via `StripeClient.builder().setApiKey(request.getStripeSecretKey()).setApiBase(apiBase)` (apiBase configurable for tests, default `https://api.stripe.com`). Build `SessionCreateParams` per design (mode=PAYMENT, metadata + paymentIntentData.metadata correlation_id, clientReferenceId=contractId, one line item with priceData from amount/currency/description, successUrl/cancelUrl). Return `CreatePaymentResponse(checkoutUrl=session.getUrl(), pspSessionId=session.getId())`.
- `processWebhookEvent`: parse raw JSON to extract correlation id from `data.object.metadata.correlation_id` (pure Jackson, untrusted). If correlation id present, look up `PaymentTransaction` → contract → product def → `stripeWebhookSecret` (via `PaymentTransactionService` + a new query). Verify signature with that secret using `Webhook.constructEvent`. If no correlation id / no match, try all product webhook secrets (`ProductDefinitionService`-style query for non-null `stripe_webhook_secret`) until one verifies. On failure throw `WebApplicationException(400)`. Map the verified `Event` to `PaymentEventResult` per event type table (`checkout.session.completed`, `payment_intent.succeeded`, `checkout.session.async_payment_succeeded`, `checkout.session.async_payment_failed`, `charge.updated`); other types → result with `matched` set but status PENDING/ignored (PaymentService decides IGNORED). Extract fee from `balance_transaction` when present.

### 6. Services
- `service/PaymentTransactionService.java` — `@ApplicationScoped`: `persist`, `findByCorrelationId`, `findByPspSessionId`, `updateStatus`, `updateFeeAndRef`, `findSucceededInRange(from,to)` for export.
- `service/WebhookEventService.java` — `@ApplicationScoped`: `persist` (catches `ConstraintViolationException`/unique violation on `(psp_identifier, psp_event_id)` → returns `DUPLICATE` flag), `existsByPspEventId`.
- `service/PaymentService.java` — `@ApplicationScoped`:
  - `createPaymentForContract(contractId, actorAccountId)`: load contract (via `loadContractForAccount` pattern), resolve product def from first line item, generate `correlationId=UUID`, persist `PaymentTransaction(PENDING)`, build `CreatePaymentRequest` (amount=grandTotal, currency, description="Contract "+contractReference, successUrl/cancelUrl from config, stripeSecretKey from product), call `psp.createPayment`, store `pspSessionId` on the transaction, return `CreatePaymentResponse` (so the resource can populate `checkoutUrl`).
  - `handlePaymentResult(PaymentEventResult)`: persist `WebhookEvent` row (matched=false, IGNORED placeholder) first; find transaction by correlationId; if not found → UNMATCHED; if found and terminal → DUPLICATE; if event type not handled → IGNORED; if success + stale (createdAt older than `stale-after-hours`) → STALE (mark transaction STALE, no transition); if success + fresh → SUCCEEDED, store fee+ref, call `salesProcessService.transitionToRunning(contractId, "system")`; if failure → FAILED. Dedup via `WebhookEventService`.
  - `findPaymentBySessionId(sessionId)` for redirect resource.
- Update `SalesProcessService`:
  - `triggerPaymentHandling(contractId, actorAccountId)` returns `String checkoutUrl` (or a small result object): load contract, check `paymentModel`; PREPAID → transition APPROVED→AWAITING_PAYMENT (record step), call `paymentService.createPaymentForContract`, return checkoutUrl; POSTPAID → throw `UnsupportedPaymentModelException` (contract stays APPROVED).
  - New `transitionToRunning(contractId, actorAccountId)`: AWAITING_PAYMENT → RUNNING (record step). Reuses `loadContractForAccount`? No — called by webhook with no user; add an internal variant that loads by id only (system actor).
  - `acceptContract` now returns the checkoutUrl up to the resource (change return type from void to `String` or a response object).
- New `UnsupportedPaymentModelException` (`non_multitenancy/sales/payment/service/`) extends `WebApplicationException` with status 422, + `UnsupportedPaymentModelExceptionMapper` (`@Provider`) returning RFC 7807 problem+json (mirror `IllegalArgumentExceptionMapper`).

### 7. REST resources
- `boundary/PaymentWebhookResource.java` — `@Path("/api/public/payment/webhook")`, `@PermitAll`, `@POST` consumes `*/*` (raw payload string) + `@HeaderParam("Stripe-Signature")`. Calls `psp.processWebhookEvent` then `paymentService.handlePaymentResult`; returns 200. 400 on signature failure. Per design resource snippet.
- `boundary/PaymentRedirectResource.java` — `@Path("/api/public/payment")`, `@PermitAll`: `GET /success?session_id=` (SUCCEEDED→302 to product success URL with `{contractId}` replaced, else HTML page; PENDING→302 with `status=processing` or HTML; not found→404); `GET /cancel?session_id=` (302 to product cancel URL or HTML page; not found→404). No state transitions.
- `boundary/PaymentExportResource.java` — `@Path("/api/public/payment/export")`, `@RolesAllowed(Roles.USER)`: `GET` with `from`/`to` query params (ISO date), returns `text/csv` with `Content-Disposition: attachment; filename="payments-{from}-to-{to}.csv"`. One row per `PaymentExportRow`. Scoped to caller's org (resolve orgId from identity).
- Update `CustomerContractResponse` with optional `String checkoutUrl` field + getter/setter.
- Update `NonMultitenancyCustomerContractResource.accept(...)`: now returns `Response.ok(contractResponseWithCheckoutUrl).build()` instead of `Response.ok().build()`. `acceptContract` returns the checkoutUrl; resource builds the `CustomerContractResponse` (reuse `contractService.getContract` + set checkoutUrl, or have `acceptContract` return a small DTO).

### 8. TestDataCleaner update
- Add `removeAll(PaymentTransaction.class)` and `removeAll(WebhookEvent.class)` **before** `Contract` removal (FK ordering). WebhookEvent has FK to PaymentTransaction, so remove WebhookEvent first, then PaymentTransaction, then the existing chain.

### 9. Unit tests (plain JUnit, no @QuarkusTest where possible)
- `StripePSPServiceTest` (@QuarkusTest + Wiremock): verify `createPayment` builds correct params and parses the wiremocked checkout session response; verify `processWebhookEvent` verifies a locally-signed payload and maps event types correctly; verify unmatched → tries all secrets → 400 when none verify.
- `WebhookSignatureTestHelper` (test util): generates a valid `Stripe-Signature` header (`t=...,v1=...`) from a payload + `whsec_` secret using HMAC-SHA256 (replicates Stripe's signing scheme) so integration tests can send signed webhooks without the Stripe CLI.

### 10. Integration tests (@QuarkusTest)
- `PaymentWebhookResourceTest`: signed success → contract RUNNING + WebhookEvent PROCESSED; unsigned → 400 + no WebhookEvent row; unknown correlation → UNMATCHED + contract unchanged; same event twice → one row, second DUPLICATE; old PENDING + success → STALE + contract not transitioned; failure event → FAILED + contract AWAITING_PAYMENT.
- `PaymentRedirectResourceTest`: success + configured redirect → 302 with `{contractId}` replaced; PENDING → `status=processing`; no redirect URL → HTML page; unknown session → 404; cancel → 302 to cancel URL or HTML.
- `PaymentExportResourceTest`: create succeeded transactions, call export, verify CSV format + headers + Content-Disposition.
- Update `SalesProcessServiceTest`: replace `triggerPaymentHandlingLeavesContractInApproved` with: PREPAID → AWAITING_PAYMENT + PaymentTransaction PENDING + checkoutUrl returned (Stripe wiremocked); POSTPAID → 422 `UnsupportedPaymentModelException` + contract stays APPROVED. Update the `acceptContract...` tests to assert the new checkoutUrl-bearing response shape. Update `triggerPaymentHandlingFailsWhenAccountNotLinked` (still 403).

### 11. E2E test `e2e-tests/tests/05-payment-flow.spec.ts`
- Happy path, cancel path, success redirect, postpaid rejection per design. Uses Stripe CLI (`stripe listen --forward-to`, `stripe trigger checkout.session.completed`) — **requires Stripe CLI installed + `STRIPE_TEST_API_KEY` + a test product with `stripe_secret_key`/`stripe_webhook_secret` set to the CLI's test values**. Document the env requirement in the test file header and in `docs/DESIGN_OF_PAYMENT.md` testing section if missing. Marked to run under the existing `e2e` profile.

### 12. Docs
- Update `docs/DESIGN_OF_PAYMENT.md` only if implementation reveals inaccuracies (per project rules — don't create new docs). Update `non_multitenancy/AGENTS.md` approved-exceptions table: `SalesProcessService` references `PaymentService`/`PaymentTransaction` (non_multitenancy payment types) — add row with justification "payment handling is part of the cross-tenant sales flow".

## Files to Modify
- `pom.xml` — add stripe-java dependency.
- `src/main/resources/application.properties` — payment config + test overrides + native args.
- `src/main/resources/db/migration/V01.025__createPaymentTransactionTable.sql` — new.
- `src/main/resources/META-INF/native-image/reflect-config-stripe.json` — new (stub).
- `src/main/java/.../non_multitenancy/sales/entity/NonMultitenancyProductDefinition.java` — 4 new fields.
- `src/main/java/.../product/entity/ProductDefinition.java` — 4 new fields (mirror).
- `src/main/java/.../non_multitenancy/sales/service/SalesProcessService.java` — triggerPaymentHandling + transitionToRunning + acceptContract return type.
- `src/main/java/.../non_multitenancy/sales/boundary/dto/CustomerContractResponse.java` — checkoutUrl.
- `src/main/java/.../non_multitenancy/sales/boundary/NonMultitenancyCustomerContractResource.java` — accept returns checkoutUrl.
- `src/test/java/dev/abstratium/test/TestDataCleaner.java` — clean payment tables.
- `src/test/java/.../SalesProcessServiceTest.java` — update for new behaviour.

## Files to Create
- `non_multitenancy/sales/payment/entity/PaymentTransaction.java`
- `non_multitenancy/sales/payment/entity/WebhookEvent.java`
- `non_multitenancy/sales/payment/service/PSPInterface.java`
- `non_multitenancy/sales/payment/service/PSPSelector.java`
- `non_multitenancy/sales/payment/service/PaymentService.java`
- `non_multitenancy/sales/payment/service/PaymentTransactionService.java`
- `non_multitenancy/sales/payment/service/WebhookEventService.java`
- `non_multitenancy/sales/payment/service/UnsupportedPaymentModelException.java`
- `non_multitenancy/sales/payment/service/stripe/StripePSPService.java`
- `non_multitenancy/sales/payment/boundary/PaymentWebhookResource.java`
- `non_multitenancy/sales/payment/boundary/PaymentRedirectResource.java`
- `non_multitenancy/sales/payment/boundary/PaymentExportResource.java`
- `non_multitenancy/sales/payment/boundary/dto/{CreatePaymentRequest,CreatePaymentResponse,PaymentEventResult,PaymentExportRow}.java`
- `core/filter/UnsupportedPaymentModelExceptionMapper.java`
- Tests: `StripePSPServiceTest`, `PaymentServiceTest`, `PaymentTransactionServiceTest`, `WebhookEventServiceTest`, `PaymentWebhookResourceTest`, `PaymentRedirectResourceTest`, `PaymentExportResourceTest`, `WebhookSignatureTestHelper`
- `e2e-tests/tests/05-payment-flow.spec.ts`

## Verification
- [ ] `./scripts/run-java-tests.py` — all existing + new backend tests pass.
- [ ] `./scripts/show-java-coverage.py` — payment packages ≥80% statement / ≥70% branch; fix gaps with meaningful tests.
- [ ] `./scripts/run-ng-tests.py` — Angular tests unaffected (no UI in this change).
- [ ] `mvn verify -Pe2e` (with Stripe CLI + `STRIPE_TEST_API_KEY`) — `05-payment-flow.spec.ts` passes; document env requirement if CLI unavailable.
- [ ] Migration applies cleanly on MySQL (existing dev DB) — verify via a test run.
- [ ] No `@Disabled` tests; no deleted tests.

## Risks / Considerations
- **Multi-product contracts**: credentials taken from first line item's product def. If a contract mixes products from different Stripe accounts, only one account is charged. Documented limitation; out of scope.
- **`@PermitAll` vs path policy**: relying on `@PermitAll` overriding `/api/*` authenticated policy. If Quarkus does not honour the override, fallback is to add `/api/public/payment/webhook,/api/public/payment/success,/api/public/payment/cancel` to `public.paths` — verify with the `PaymentWebhookResourceTest` (unsigned request must reach the handler, not be 401'd).
- **Stripe SDK reflection/native**: deferred. The reflect-config stub is best-effort; native build may still fail and require iteration (out of scope per user decision).
- **Webhook 10s timeout**: handler is synchronous; deferred async processing is a future optimisation (per design).
- **Wiremock + Stripe SDK**: `StripeClient.builder().setApiBase(...)` is supported (confirmed via SDK docs). Test profile sets a configurable api base; `StripePSPService` reads `abstrapact.payment.stripe.api-base` (default `https://api.stripe.com`, overridden to wiremock URL in tests).
- **TestDataCleaner ordering**: WebhookEvent → PaymentTransaction must be removed before Contract (FKs). Verify no orphaned rows cause test flakiness.

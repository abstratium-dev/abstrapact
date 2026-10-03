package dev.abstratium.abstrapact.non_multitenancy.sales.payment.service;

import dev.abstratium.abstrapact.non_multitenancy.sales.payment.entity.IdempotencyRecord;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link IdempotencyService}.
 *
 * <p>These are {@code @QuarkusTest} because they need the JPA EntityManager, but each
 * test method is wrapped in a transaction that is rolled back at the end.
 */
@QuarkusTest
class IdempotencyServiceTest {

    @Inject
    IdempotencyService service;

    @Inject
    EntityManager em;

    @Test
    @TestTransaction
    void executeWithNewKeyCreatesRecord() {
        AtomicInteger callCount = new AtomicInteger(0);
        String key = "key-new";
        String scope = "test";
        String fingerprint = "abc123";

        IdempotencyRecord record = service.execute(
            key, scope, "scope-1", fingerprint,
            () -> {
                callCount.incrementAndGet();
                return new ProcessedResponse(201, "{\"created\":true}");
            });

        assertEquals(1, callCount.get());
        assertEquals(201, record.getStatusCode());
        assertEquals("{\"created\":true}", record.getResponseBody());
        assertEquals(key, record.getKey());
        assertEquals(scope, record.getScope());
        assertEquals(fingerprint, record.getRequestFingerprint());
        assertNotNull(record.getCreatedAt());
        assertNotNull(record.getExpiresAt());
        assertTrue(record.getExpiresAt().isAfter(record.getCreatedAt()));
    }

    @Test
    @TestTransaction
    void executeWithExistingKeyReplaysResult() {
        AtomicInteger callCount = new AtomicInteger(0);
        String key = "key-replay";
        String scope = "test";
        String fingerprint = "abc123";

        // First call — executes the processor
        IdempotencyRecord r1 = service.execute(
            key, scope, "scope-1", fingerprint,
            () -> {
                callCount.incrementAndGet();
                return new ProcessedResponse(201, "{\"created\":true}");
            });

        // Second call — replays the cached result
        IdempotencyRecord r2 = service.execute(
            key, scope, "scope-1", fingerprint,
            () -> {
                callCount.incrementAndGet();
                return new ProcessedResponse(999, "should-not-execute");
            });

        assertEquals(1, callCount.get(), "Processor should be invoked exactly once");
        assertEquals(r1.getId(), r2.getId());
        assertEquals(201, r2.getStatusCode());
        assertEquals("{\"created\":true}", r2.getResponseBody());
    }

    @Test
    @TestTransaction
    void executeWithExistingKeyAndDifferentFingerprintThrows422() {
        String key = "key-mismatch";
        String scope = "test";
        String fingerprint1 = "abc123";
        String fingerprint2 = "def456";

        // First call — succeeds
        service.execute(key, scope, "scope-1", fingerprint1,
            () -> new ProcessedResponse(201, "ok"));

        // Second call with same key but different fingerprint — rejected
        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> service.execute(key, scope, "scope-1", fingerprint2,
                () -> new ProcessedResponse(201, "ok")));

        assertEquals(422, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void executeWithExpiredKeyCreatesNewRecord() {
        String key = "key-expired";
        String scope = "test";
        String fingerprint = "abc123";

        // Insert an expired record manually
        IdempotencyRecord expired = new IdempotencyRecord();
        expired.setId("expired-id");
        expired.setKey(key);
        expired.setScope(scope);
        expired.setRequestFingerprint(fingerprint);
        expired.setStatusCode(201);
        expired.setResponseBody("old");
        expired.setCreatedAt(LocalDateTime.now().minusHours(48));
        expired.setExpiresAt(LocalDateTime.now().minusHours(24));
        em.persist(expired);
        em.flush();

        AtomicInteger callCount = new AtomicInteger(0);

        // An expired record must be treated as a new request: it is deleted and
        // the processor executes again.
        IdempotencyRecord record = service.execute(
            key, scope, "scope-1", fingerprint,
            () -> {
                callCount.incrementAndGet();
                return new ProcessedResponse(200, "new");
            });

        assertEquals(1, callCount.get(),
            "Processor must execute for an expired idempotency key");
        assertEquals(200, record.getStatusCode());
        assertEquals("new", record.getResponseBody());

        // The expired record row must have been replaced by the fresh one.
        IdempotencyRecord stored = em.find(IdempotencyRecord.class, record.getId());
        assertEquals("new", stored.getResponseBody());
        assertNull(em.find(IdempotencyRecord.class, "expired-id"));
    }

    @Test
    @TestTransaction
    void executePropagatesProcessorException() {
        String key = "key-exception";
        String scope = "test";
        String fingerprint = "abc123";

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> service.execute(key, scope, "scope-1", fingerprint,
                () -> {
                    throw new RuntimeException("boom");
                }));

        assertEquals("boom", ex.getMessage());

        // No record should be persisted
        Optional<IdempotencyRecord> found = service.find(key, scope);
        assertTrue(found.isEmpty(), "No record should be persisted on exception");
    }

    @Test
    @TestTransaction
    void executeCaches4xxResponse() {
        String key = "key-4xx";
        String scope = "test";
        String fingerprint = "abc123";

        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> service.execute(key, scope, "scope-1", fingerprint,
                () -> {
                    throw new WebApplicationException(
                        jakarta.ws.rs.core.Response.status(422)
                            .entity("validation error")
                            .build());
                }));

        assertEquals(422, ex.getResponse().getStatus());

        // The 4xx response should be cached
        Optional<IdempotencyRecord> found = service.find(key, scope);
        assertTrue(found.isPresent(), "4xx response should be cached");
        assertEquals(422, found.get().getStatusCode());
        assertEquals("validation error", found.get().getResponseBody());
    }

    @Test
    @TestTransaction
    void findReturnsEmptyWhenNoRecordExists() {
        Optional<IdempotencyRecord> result = service.find("nonexistent-key", "test");
        assertTrue(result.isEmpty());
    }

    @Test
    @TestTransaction
    void findReturnsRecordWhenExists() {
        String key = "key-find";
        String scope = "test";
        service.execute(key, scope, "scope-1", "fp",
            () -> new ProcessedResponse(200, "ok"));

        Optional<IdempotencyRecord> result = service.find(key, scope);
        assertTrue(result.isPresent());
        assertEquals(key, result.get().getKey());
    }
}

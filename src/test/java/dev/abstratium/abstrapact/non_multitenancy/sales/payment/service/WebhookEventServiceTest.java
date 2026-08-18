package dev.abstratium.abstrapact.non_multitenancy.sales.payment.service;

import dev.abstratium.abstrapact.non_multitenancy.sales.payment.entity.WebhookEvent;
import dev.abstratium.test.TestDataCleaner;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests {@link WebhookEventService} persistence and deduplication.
 */
@QuarkusTest
class WebhookEventServiceTest {

    @Inject
    WebhookEventService service;

    @Inject
    TestDataCleaner cleaner;

    @BeforeEach
    void setUp() {
        service.persistOrFindDuplicate(newEvent("evt_1", "checkout.session.completed"));
    }

    @AfterEach
    void tearDown() throws Exception {
        cleaner.deleteAll();
    }

    @Test
    void persistOrFindDuplicateReturnsEventForNewEvent() {
        WebhookEvent event = newEvent("evt_2", "payment_intent.succeeded");
        WebhookEvent persisted = service.persistOrFindDuplicate(event);
        assertNotNull(persisted);
        assertEquals("evt_2", persisted.getPspEventId());
    }

    @Test
    void persistOrFindDuplicateReturnsNullForDuplicateEvent() {
        WebhookEvent duplicate = newEvent("evt_1", "checkout.session.completed");
        WebhookEvent result = service.persistOrFindDuplicate(duplicate);
        assertNull(result);
    }

    @Test
    void existsByPspEventIdReturnsTrueForExisting() {
        assertTrue(service.existsByPspEventId("stripe", "evt_1"));
    }

    @Test
    void existsByPspEventIdReturnsFalseForUnknown() {
        assertFalse(service.existsByPspEventId("stripe", "evt_unknown"));
    }

    @Test
    void existsByPspEventIdReturnsFalseForDifferentPsp() {
        assertFalse(service.existsByPspEventId("paypal", "evt_1"));
    }

    @Test
    void duplicateDetectionIsByPspAndEventId() {
        // Same event id but different PSP is not a duplicate
        WebhookEvent otherPsp = newEvent("evt_1", "checkout.session.completed");
        otherPsp.setPspIdentifier("paypal");
        WebhookEvent result = service.persistOrFindDuplicate(otherPsp);
        assertNotNull(result);
    }

    private WebhookEvent newEvent(String eventId, String eventType) {
        WebhookEvent event = new WebhookEvent();
        event.setId(UUID.randomUUID().toString());
        event.setPspIdentifier("stripe");
        event.setPspEventId(eventId);
        event.setEventType(eventType);
        event.setCorrelationId("corr-test");
        event.setMatched(false);
        event.setProcessingResult(WebhookEvent.ProcessingResult.PROCESSED);
        event.setRawPayload("{\"id\":\"" + eventId + "\"}");
        event.setReceivedAt(LocalDateTime.now());
        return event;
    }
}

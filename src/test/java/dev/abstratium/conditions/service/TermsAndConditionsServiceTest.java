package dev.abstratium.conditions.service;

import dev.abstratium.conditions.entity.TermsAndConditions;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.UserTransaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class TermsAndConditionsServiceTest {

    @Inject
    TermsAndConditionsService service;

    @Inject
    EntityManager em;

    @Inject
    UserTransaction userTransaction;

    @BeforeEach
    void setUp() throws Exception {
        TermsAndConditions t1 = new TermsAndConditions();
        t1.setId(UUID.randomUUID().toString());

        t1.setCode("GENERAL-001");
        t1.setTitle("General Terms");
        t1.setContentEn("General terms content");
        t1.setCurrentVersion("1.0");
        t1.setEffectiveFrom(LocalDate.now());
        service.create(t1);

        TermsAndConditions t2 = new TermsAndConditions();
        t2.setId(UUID.randomUUID().toString());

        t2.setCode("SPECIAL-001");
        t2.setTitle("Special Terms");
        t2.setContentEn("Special terms content");
        t2.setCurrentVersion("2.0");
        t2.setEffectiveFrom(LocalDate.now());
        service.create(t2);
    }

    @AfterEach
    void tearDown() throws Exception {
        userTransaction.begin();
        try {
            List<TermsAndConditions> all = em.createQuery(
                "SELECT t FROM TermsAndConditions t", TermsAndConditions.class)
                .getResultList();
            for (TermsAndConditions t : all) {
                em.remove(t);
            }
            em.flush();
            userTransaction.commit();
        } catch (Exception e) {
            userTransaction.rollback();
            throw e;
        }
    }

    @Test
    void shouldCreateTermsAndConditions() {
        TermsAndConditions terms = new TermsAndConditions();

        terms.setCode("NEW-TERMS");
        terms.setTitle("New Terms");
        terms.setContentEn("New content");
        terms.setCurrentVersion("1.0");
        terms.setEffectiveFrom(LocalDate.now());

        TermsAndConditions created = service.create(terms);

        assertNotNull(created.getId());
        assertEquals("NEW-TERMS", created.getCode());
        assertEquals("New Terms", created.getTitle());
    }

    @Test
    void shouldFindById() {
        List<TermsAndConditions> all = service.findAll();
        assertFalse(all.isEmpty());

        String id = all.get(0).getId();
        Optional<TermsAndConditions> found = service.findById(id);

        assertTrue(found.isPresent());
        assertEquals(id, found.get().getId());
    }

    @Test
    void shouldFindByCode() {
        List<TermsAndConditions> found = service.findByCode("GENERAL-001");

        assertFalse(found.isEmpty());
        assertEquals("GENERAL-001", found.get(0).getCode());
        assertEquals("General Terms", found.get(0).getTitle());
    }

    @Test
    void shouldReturnAllTerms() {
        List<TermsAndConditions> all = service.findAll();

        assertTrue(all.size() >= 2);
        assertTrue(all.stream().anyMatch(t -> "GENERAL-001".equals(t.getCode())));
        assertTrue(all.stream().anyMatch(t -> "SPECIAL-001".equals(t.getCode())));
    }

    @Test
    void shouldCheckExistenceByCode() {
        assertTrue(service.existsByCode("GENERAL-001"));
        assertTrue(service.existsByCode("SPECIAL-001"));
        assertFalse(service.existsByCode("NON-EXISTENT"));
    }

    @Test
    void shouldUpdateTermsAndConditions() throws Exception {
        List<TermsAndConditions> existingList = service.findByCode("GENERAL-001");
        assertFalse(existingList.isEmpty());

        TermsAndConditions terms = existingList.get(0);
        terms.setTitle("Updated Title");

        userTransaction.begin();
        try {
            TermsAndConditions updated = service.update(terms);
            userTransaction.commit();

            assertEquals("Updated Title", updated.getTitle());

            Optional<TermsAndConditions> found = service.findById(terms.getId());
            assertTrue(found.isPresent());
            assertEquals("Updated Title", found.get().getTitle());
        } catch (Exception e) {
            userTransaction.rollback();
            throw e;
        }
    }

    @Test
    void shouldUpdateEffectiveUntilWithoutEffectiveFrom() throws Exception {
        List<TermsAndConditions> existingList = service.findByCode("GENERAL-001");
        assertFalse(existingList.isEmpty());

        TermsAndConditions terms = existingList.get(0);
        terms.setEffectiveFrom(null);
        terms.setEffectiveUntil(LocalDate.of(2025, 12, 31));

        userTransaction.begin();
        try {
            TermsAndConditions updated = service.update(terms);
            userTransaction.commit();

            assertNull(updated.getEffectiveFrom());
            assertEquals(LocalDate.of(2025, 12, 31), updated.getEffectiveUntil());

            Optional<TermsAndConditions> found = service.findById(terms.getId());
            assertTrue(found.isPresent());
            assertNull(found.get().getEffectiveFrom());
            assertEquals(LocalDate.of(2025, 12, 31), found.get().getEffectiveUntil());
        } catch (Exception e) {
            userTransaction.rollback();
            throw e;
        }
    }

    @Test
    void shouldDeleteTermsAndConditions() {
        List<TermsAndConditions> existingList = service.findByCode("GENERAL-001");
        assertFalse(existingList.isEmpty());

        String id = existingList.get(0).getId();
        String code = existingList.get(0).getCode();

        service.delete(id);
        em.clear();

        assertFalse(service.existsByCode(code));
        assertFalse(service.findById(id).isPresent());
    }

    @Test
    void shouldReturnEmptyWhenNotFound() {
        Optional<TermsAndConditions> found = service.findById("non-existent-id");
        assertFalse(found.isPresent());
    }

    @Test
    void shouldReturnEmptyListForNonExistentCode() {
        List<TermsAndConditions> found = service.findByCode("NON-EXISTENT-CODE");
        assertTrue(found.isEmpty());
    }

    @Test
    void shouldPreservePreSetIdOnCreate() {
        String preSetId = UUID.randomUUID().toString();
        TermsAndConditions terms = new TermsAndConditions();
        terms.setId(preSetId);

        terms.setCode("PRESET-ID-TERMS");
        terms.setTitle("Preset ID Terms");
        terms.setContentEn("Content");

        TermsAndConditions created = service.create(terms);

        assertEquals(preSetId, created.getId());
    }

    @Test
    void shouldSilentlyHandleDeleteOfNonExistentId() {
        service.delete("non-existent-id-that-does-not-exist");
    }

    @Test
    void shouldAllowContinuousChainForSameCode() {
        String code = "CHAIN-001";

        TermsAndConditions t1 = new TermsAndConditions();
        t1.setId(UUID.randomUUID().toString());

        t1.setCode(code);
        t1.setTitle("First");
        t1.setEffectiveFrom(LocalDate.of(2024, 1, 1));
        t1.setEffectiveUntil(LocalDate.of(2024, 6, 30));
        service.create(t1);

        TermsAndConditions t2 = new TermsAndConditions();
        t2.setId(UUID.randomUUID().toString());

        t2.setCode(code);
        t2.setTitle("Second");
        t2.setEffectiveFrom(LocalDate.of(2024, 7, 1));
        t2.setEffectiveUntil(null);
        service.create(t2);

        List<TermsAndConditions> found = service.findByCode(code);
        assertEquals(2, found.size());
    }

    @Test
    void shouldRejectOverlapInDates() {
        String code = "OVERLAP-001";

        TermsAndConditions t1 = new TermsAndConditions();
        t1.setId(UUID.randomUUID().toString());

        t1.setCode(code);
        t1.setTitle("First");
        t1.setEffectiveFrom(LocalDate.of(2024, 1, 1));
        t1.setEffectiveUntil(LocalDate.of(2024, 12, 31));
        service.create(t1);

        TermsAndConditions t2 = new TermsAndConditions();
        t2.setId(UUID.randomUUID().toString());

        t2.setCode(code);
        t2.setTitle("Second");
        t2.setEffectiveFrom(LocalDate.of(2024, 6, 1));
        t2.setEffectiveUntil(null);

        assertThrows(IllegalArgumentException.class, () -> service.create(t2));
    }

    @Test
    void shouldRejectGapInDates() {
        String code = "GAP-001";

        TermsAndConditions t1 = new TermsAndConditions();
        t1.setId(UUID.randomUUID().toString());

        t1.setCode(code);
        t1.setTitle("First");
        t1.setEffectiveFrom(LocalDate.of(2024, 1, 1));
        t1.setEffectiveUntil(LocalDate.of(2024, 6, 30));
        service.create(t1);

        TermsAndConditions t2 = new TermsAndConditions();
        t2.setId(UUID.randomUUID().toString());

        t2.setCode(code);
        t2.setTitle("Second");
        t2.setEffectiveFrom(LocalDate.of(2024, 8, 1));
        t2.setEffectiveUntil(null);

        assertThrows(IllegalArgumentException.class, () -> service.create(t2));
    }

    @Test
    void shouldRejectMultipleNullEffectiveFrom() {
        String code = "NULL-FROM-001";

        TermsAndConditions t1 = new TermsAndConditions();
        t1.setId(UUID.randomUUID().toString());

        t1.setCode(code);
        t1.setTitle("First");
        t1.setEffectiveFrom(null);
        t1.setEffectiveUntil(LocalDate.of(2024, 6, 30));
        service.create(t1);

        TermsAndConditions t2 = new TermsAndConditions();
        t2.setId(UUID.randomUUID().toString());

        t2.setCode(code);
        t2.setTitle("Second");
        t2.setEffectiveFrom(null);
        t2.setEffectiveUntil(LocalDate.of(2024, 12, 31));

        assertThrows(IllegalArgumentException.class, () -> service.create(t2));
    }

    @Test
    void shouldAllowOpenEndedChain() {
        String code = "OPEN-001";

        TermsAndConditions t1 = new TermsAndConditions();
        t1.setId(UUID.randomUUID().toString());

        t1.setCode(code);
        t1.setTitle("First");
        t1.setEffectiveFrom(null);
        t1.setEffectiveUntil(LocalDate.of(2024, 12, 31));
        service.create(t1);

        TermsAndConditions t2 = new TermsAndConditions();
        t2.setId(UUID.randomUUID().toString());

        t2.setCode(code);
        t2.setTitle("Second");
        t2.setEffectiveFrom(LocalDate.of(2025, 1, 1));
        t2.setEffectiveUntil(null);
        service.create(t2);

        List<TermsAndConditions> found = service.findByCode(code);
        assertEquals(2, found.size());
    }

    @Test
    void shouldRejectUnboundedOverlap() {
        String code = "UNBOUNDED-001";

        TermsAndConditions t1 = new TermsAndConditions();
        t1.setId(UUID.randomUUID().toString());

        t1.setCode(code);
        t1.setTitle("First");
        t1.setEffectiveFrom(LocalDate.of(2024, 1, 1));
        t1.setEffectiveUntil(null);
        service.create(t1);

        TermsAndConditions t2 = new TermsAndConditions();
        t2.setId(UUID.randomUUID().toString());

        t2.setCode(code);
        t2.setTitle("Second");
        t2.setEffectiveFrom(LocalDate.of(2024, 7, 1));
        t2.setEffectiveUntil(null);

        assertThrows(IllegalArgumentException.class, () -> service.create(t2));
    }

    @Test
    void shouldRejectUpdateCreatingGap() {
        String code = "UPDATE-GAP-001";

        TermsAndConditions t1 = new TermsAndConditions();
        t1.setId(UUID.randomUUID().toString());

        t1.setCode(code);
        t1.setTitle("First");
        t1.setEffectiveFrom(LocalDate.of(2024, 1, 1));
        t1.setEffectiveUntil(LocalDate.of(2024, 6, 30));
        service.create(t1);

        TermsAndConditions t2 = new TermsAndConditions();
        t2.setId(UUID.randomUUID().toString());

        t2.setCode(code);
        t2.setTitle("Second");
        t2.setEffectiveFrom(LocalDate.of(2024, 7, 1));
        t2.setEffectiveUntil(LocalDate.of(2024, 12, 31));
        service.create(t2);

        TermsAndConditions t3 = new TermsAndConditions();
        t3.setId(UUID.randomUUID().toString());

        t3.setCode(code);
        t3.setTitle("Third");
        t3.setEffectiveFrom(LocalDate.of(2025, 1, 1));
        t3.setEffectiveUntil(null);
        service.create(t3);

        // Update t2 to end earlier, creating a gap between t2 and t3
        t2.setEffectiveUntil(LocalDate.of(2024, 10, 31));

        assertThrows(IllegalArgumentException.class, () -> service.update(t2));
    }

    @Test
    void shouldRejectDeleteCreatingGap() {
        String code = "DELETE-GAP-001";

        TermsAndConditions t1 = new TermsAndConditions();
        t1.setId(UUID.randomUUID().toString());

        t1.setCode(code);
        t1.setTitle("First");
        t1.setEffectiveFrom(LocalDate.of(2024, 1, 1));
        t1.setEffectiveUntil(LocalDate.of(2024, 6, 30));
        service.create(t1);

        TermsAndConditions t2 = new TermsAndConditions();
        t2.setId(UUID.randomUUID().toString());

        t2.setCode(code);
        t2.setTitle("Second");
        t2.setEffectiveFrom(LocalDate.of(2024, 7, 1));
        t2.setEffectiveUntil(LocalDate.of(2024, 12, 31));
        service.create(t2);

        TermsAndConditions t3 = new TermsAndConditions();
        t3.setId(UUID.randomUUID().toString());

        t3.setCode(code);
        t3.setTitle("Third");
        t3.setEffectiveFrom(LocalDate.of(2025, 1, 1));
        t3.setEffectiveUntil(null);
        service.create(t3);

        // Deleting t2 creates a gap between t1 and t3
        assertThrows(IllegalArgumentException.class, () -> service.delete(t2.getId()));
    }
}

package dev.abstratium.conditions.service;

import dev.abstratium.conditions.entity.TermsAndConditions;
import dev.abstratium.core.service.JwtOrgResolver;
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
        t1.setOrganisationId(JwtOrgResolver.DEFAULT_ORG_ID);
        t1.setCode("GENERAL-001");
        t1.setTitle("General Terms");
        t1.setContentEn("General terms content");
        t1.setCurrentVersion("1.0");
        t1.setEffectiveFrom(LocalDate.now());
        service.create(t1);

        TermsAndConditions t2 = new TermsAndConditions();
        t2.setId(UUID.randomUUID().toString());
        t2.setOrganisationId(JwtOrgResolver.DEFAULT_ORG_ID);
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
        terms.setOrganisationId(JwtOrgResolver.DEFAULT_ORG_ID);
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
        Optional<TermsAndConditions> found = service.findByCode("GENERAL-001");

        assertTrue(found.isPresent());
        assertEquals("GENERAL-001", found.get().getCode());
        assertEquals("General Terms", found.get().getTitle());
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
        Optional<TermsAndConditions> existing = service.findByCode("GENERAL-001");
        assertTrue(existing.isPresent());

        TermsAndConditions terms = existing.get();
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
    void shouldDeleteTermsAndConditions() {
        Optional<TermsAndConditions> existing = service.findByCode("GENERAL-001");
        assertTrue(existing.isPresent());

        String id = existing.get().getId();
        String code = existing.get().getCode();

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
    void shouldReturnEmptyOptionalForNonExistentCode() {
        Optional<TermsAndConditions> found = service.findByCode("NON-EXISTENT-CODE");
        assertFalse(found.isPresent());
    }

    @Test
    void shouldPreservePreSetIdOnCreate() {
        String preSetId = UUID.randomUUID().toString();
        TermsAndConditions terms = new TermsAndConditions();
        terms.setId(preSetId);
        terms.setOrganisationId(JwtOrgResolver.DEFAULT_ORG_ID);
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
}

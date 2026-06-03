package dev.abstratium.conditions.service;

import dev.abstratium.conditions.entity.TermsAndConditions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TermsAndConditionsService {

    @Inject
    EntityManager em;

    @Transactional
    public TermsAndConditions create(TermsAndConditions terms) {
        if (terms.getId() == null) {
            terms.setId(UUID.randomUUID().toString());
        }
        em.persist(terms);
        validateNoGaps(terms.getCode());
        return terms;
    }

    @Transactional
    public TermsAndConditions update(TermsAndConditions terms) {
        // multi-tenancy safe: find by id (which either causes sql with "where orgId=?" to run, or that was already run and it is in the cache)
        TermsAndConditions existing = em.find(TermsAndConditions.class, terms.getId());
        if (existing == null) {
            throw new IllegalArgumentException("TermsAndConditions not found: " + terms.getId());
        } // else it exists in the org, so safe to merge the incoming data
        TermsAndConditions updated = em.merge(terms);

        validateNoGaps(updated.getCode());

        // if code changed, check that chain too
        String oldCode = existing.getCode();
        if (!oldCode.equals(updated.getCode())) {
            validateNoGaps(oldCode);
        }
        return updated;
    }

    @Transactional
    public void delete(String id) {
        // multi-tenant safe - ensures the id exists in the tenant (org) before deleting it
        TermsAndConditions existing = em.find(TermsAndConditions.class, id);
        if (existing != null) {
            String code = existing.getCode();
            em.remove(existing);
            em.flush();
            validateNoGaps(code);
        }
    }

    public Optional<TermsAndConditions> findById(String id) {
        return Optional.ofNullable(em.find(TermsAndConditions.class, id));
    }

    public List<TermsAndConditions> findByCode(String code) {
        return em.createQuery(
                "SELECT t FROM TermsAndConditions t WHERE t.code = :code ORDER BY t.effectiveFrom",
                TermsAndConditions.class)
            .setParameter("code", code)
            .getResultList();
    }

    public List<TermsAndConditions> findAll() {
        return em.createQuery(
                "SELECT t FROM TermsAndConditions t ORDER BY t.code",
                TermsAndConditions.class)
            .getResultList();
    }

    public boolean existsByCode(String code) {
        Long count = em.createQuery(
                "SELECT COUNT(t) FROM TermsAndConditions t WHERE t.code = :code",
                Long.class)
            .setParameter("code", code)
            .getSingleResult();
        return count > 0;
    }

    void validateNoGaps(String code) {
        List<TermsAndConditions> terms = em.createQuery(
                "SELECT t FROM TermsAndConditions t WHERE t.code = :code",
                TermsAndConditions.class)
            .setParameter("code", code)
            .getResultList();

        if (terms.size() <= 1) {
            return;
        }

        terms.sort(Comparator
            .comparing(TermsAndConditions::getEffectiveFrom,
                Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(TermsAndConditions::getEffectiveUntil,
                Comparator.nullsLast(Comparator.naturalOrder())));

        long nullFromCount = terms.stream()
            .filter(t -> t.getEffectiveFrom() == null)
            .count();
        if (nullFromCount > 1) {
            throw new IllegalArgumentException(
                "Multiple terms with code '" + code + "' have no effective from date");
        }

        for (int i = 0; i < terms.size() - 1; i++) {
            TermsAndConditions current = terms.get(i);
            TermsAndConditions next = terms.get(i + 1);

            if (current.getEffectiveUntil() == null) {
                throw new IllegalArgumentException(
                    "Term with code '" + code + "' has no effective until date, creating a gap with the next term");
            }

            if (next.getEffectiveFrom() == null) {
                throw new IllegalArgumentException(
                    "Multiple terms with code '" + code + "' have no effective from date");
            }

            if (!next.getEffectiveFrom().isAfter(current.getEffectiveUntil())) {
                throw new IllegalArgumentException(
                    "Terms with code '" + code + "' overlap: one ends on "
                        + current.getEffectiveUntil() + " and the next starts on " + next.getEffectiveFrom());
            }

            LocalDate expectedNextFrom = current.getEffectiveUntil().plusDays(1);
            if (!next.getEffectiveFrom().equals(expectedNextFrom)) {
                throw new IllegalArgumentException(
                    "Gap in terms with code '" + code + "': next term should start on "
                        + expectedNextFrom + " but starts on " + next.getEffectiveFrom());
            }
        }
    }
}

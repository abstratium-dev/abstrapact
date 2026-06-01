package dev.abstratium.conditions.service;

import dev.abstratium.conditions.entity.TermsAndConditions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

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
        return terms;
    }

    @Transactional
    public TermsAndConditions update(TermsAndConditions terms) {
        // multi-tenancy safe: find by id (which either causes sql with "where orgId=?" to run, or that was already run and it is in the cache)
        TermsAndConditions existing = em.find(TermsAndConditions.class, terms.getId());
        if (existing == null) {
            throw new IllegalArgumentException("TermsAndConditions not found: " + terms.getId());
        } // else it exists in the org, so safe to merge the incoming data
        return em.merge(terms);
    }

    @Transactional
    public void delete(String id) {
        // multi-tenant safe - ensures the id exists in the tenant (org) before deleting it
        TermsAndConditions existing = em.find(TermsAndConditions.class, id);
        if (existing != null) {
            em.remove(existing);
        }
    }

    public Optional<TermsAndConditions> findById(String id) {
        return Optional.ofNullable(em.find(TermsAndConditions.class, id));
    }

    public Optional<TermsAndConditions> findByCode(String code) {
        return em.createQuery(
                "SELECT t FROM TermsAndConditions t WHERE t.code = :code",
                TermsAndConditions.class)
            .setParameter("code", code)
            .getResultStream()
            .findFirst();
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
}

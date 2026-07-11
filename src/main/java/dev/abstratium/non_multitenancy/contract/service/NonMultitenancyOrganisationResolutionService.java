package dev.abstratium.non_multitenancy.contract.service;

import dev.abstratium.core.service.OrgScopedCodec;
import dev.abstratium.product.non_multitenancy.NonMultitenancyProductDefinition;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * Resolves and validates the seller organisation from a list of prefixed product codes.
 *
 * Validation rules:
 * <ol>
 *   <li>Each prefixed code must exist in the non-tenant ProductDefinition table.</li>
 *   <li>The org-id prefix must match the stored {@code organisation_id} column.</li>
 *   <li>All codes must resolve to the same seller organisation.</li>
 *   <li>Every resolved product must have {@code crossTenantApiAllowed = true}.</li>
 * </ol>
 *
 * On any failure a {@code 422 Unprocessable Entity} is thrown.
 */
@ApplicationScoped
public class NonMultitenancyOrganisationResolutionService {

    @Inject
    EntityManager em;

    /**
     * Resolves the common seller {@code orgId} for the supplied prefixed product codes.
     *
     * @param prefixedProductCodes one or more codes in {@code {orgId}::{rawCode}} format
     * @return the validated seller organisation id
     * @throws WebApplicationException (422) when validation fails
     */
    public String resolveSellerOrgId(List<String> prefixedProductCodes) {
        if (prefixedProductCodes == null || prefixedProductCodes.isEmpty()) {
            throw unprocessable("At least one product code is required");
        }

        String resolvedOrgId = null;

        for (String prefixedCode : prefixedProductCodes) {
            NonMultitenancyProductDefinition pd = findByPrefixedCode(prefixedCode);

            String prefixOrgId;
            try {
                prefixOrgId = OrgScopedCodec.extractOrgId(prefixedCode, "Product");
            } catch (IllegalArgumentException e) {
                throw unprocessable("Invalid product code format (expected {orgId}::{code}): " + prefixedCode);
            }

            if (!prefixOrgId.equals(pd.getOrganisationId())) {
                throw unprocessable("Product code prefix does not match stored organisation: " + prefixedCode);
            }

            if (!pd.isCrossTenantApiAllowed()) {
                throw unprocessable("Product is not available via the cross-tenant API: " + prefixedCode);
            }

            if (resolvedOrgId == null) {
                resolvedOrgId = prefixOrgId;
            } else if (!resolvedOrgId.equals(prefixOrgId)) {
                throw unprocessable("All product codes must belong to the same seller organisation");
            }
        }

        return resolvedOrgId;
    }

    private NonMultitenancyProductDefinition findByPrefixedCode(String prefixedCode) {
        try {
            return em.createQuery(
                    "SELECT p FROM NonMultitenancyProductDefinition p WHERE p.productCode = :code",
                    NonMultitenancyProductDefinition.class)
                .setParameter("code", prefixedCode)
                .getSingleResult();
        } catch (NoResultException e) {
            throw unprocessable("Product not found: " + prefixedCode);
        }
    }

    private static WebApplicationException unprocessable(String message) {
        return new WebApplicationException(
            Response.status(422).entity(message).build());
    }
}

package dev.abstratium.non_multitenancy.contract.service;

import dev.abstratium.non_multitenancy.contract.boundary.dto.PartInstanceAttributeRequest;
import dev.abstratium.non_multitenancy.contract.boundary.dto.PartInstanceRequest;
import dev.abstratium.product.non_multitenancy.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Creates and validates {@link NonMultitenancyProductInstance} trees from customer requests.
 *
 * Validation rules enforced per the design:
 * <ul>
 *   <li>Each requested {@link PartInstanceRequest} must reference a valid child {@link NonMultitenancyPartDefinition}.</li>
 *   <li>Cardinality: the number of instances per part definition must satisfy {@code minCardinality} and {@code maxCardinality}.</li>
 *   <li>Choice groups: for each {@link NonMultitenancyPartDefinitionChoiceGroup} on a parent part, the number of selected
 *       children from that group must satisfy {@code minChoices} and {@code maxChoices}.</li>
 *   <li>Required attributes must be present.</li>
 *   <li>Attribute names must exist on the part definition.</li>
 *   <li>If allowed values are defined, the supplied value must be one of them.</li>
 * </ul>
 *
 * All persisted entities carry the supplied {@code sellerOrgId} as their {@code organisationId}.
 * This service must be called from within an active {@code @Transactional} boundary.
 */
@ApplicationScoped
public class NonMultitenancyCustomerProductInstanceService {

    @Inject
    EntityManager em;

    /**
     * Creates a {@link NonMultitenancyProductInstance} for the given product definition,
     * persists it together with its part instance tree, and returns the total price
     * summed across all leaf part instances.
     *
     * @param productDefinition the resolved non-tenant product definition
     * @param partInstanceRequests root-level part instance requests from the caller
     * @param sellerOrgId the seller organisation id to stamp on every persisted entity
     * @return the persisted product instance
     */
    @Transactional
    public NonMultitenancyProductInstance createProductInstance(
            NonMultitenancyProductDefinition productDefinition,
            List<PartInstanceRequest> partInstanceRequests,
            String sellerOrgId) {

        NonMultitenancyProductInstance productInstance = new NonMultitenancyProductInstance();
        productInstance.setId(UUID.randomUUID().toString());
        productInstance.setOrganisationId(sellerOrgId);
        productInstance.setProductDefinition(productDefinition);
        em.persist(productInstance);

        List<NonMultitenancyPartDefinition> rootPartDefs = loadRootPartDefinitions(productDefinition.getId());

        List<NonMultitenancyPartInstance> rootInstances = buildAndValidatePartInstances(
            rootPartDefs, partInstanceRequests, productInstance, null, sellerOrgId);

        productInstance.setPartInstances(rootInstances);
        return productInstance;
    }

    /**
     * Calculates the line total (sum of all resolved unit prices in the part instance tree).
     */
    public BigDecimal calculateLineTotal(NonMultitenancyProductInstance productInstance) {
        return sumPartInstances(productInstance.getPartInstances());
    }

    // ==================== private helpers ====================

    private List<NonMultitenancyPartDefinition> loadRootPartDefinitions(String productDefinitionId) {
        return em.createQuery(
                "SELECT p FROM NonMultitenancyPartDefinition p " +
                "WHERE p.productDefinition.id = :pdId AND p.parentPart IS NULL " +
                "ORDER BY p.displayOrder",
                NonMultitenancyPartDefinition.class)
            .setParameter("pdId", productDefinitionId)
            .getResultList();
    }

    private List<NonMultitenancyPartInstance> buildAndValidatePartInstances(
            List<NonMultitenancyPartDefinition> allowedDefs,
            List<PartInstanceRequest> requests,
            NonMultitenancyProductInstance productInstance,
            NonMultitenancyPartInstance parentPartInstance,
            String sellerOrgId) {

        if (requests == null) {
            requests = List.of();
        }

        // allowedDefs store partCode as prefixed ({orgId}::rawCode); match by stored value
        Map<String, NonMultitenancyPartDefinition> defByPartCode = allowedDefs.stream()
            .collect(Collectors.toMap(NonMultitenancyPartDefinition::getPartCode, d -> d));

        Map<String, List<PartInstanceRequest>> requestsByPartCode = new LinkedHashMap<>();
        for (PartInstanceRequest req : requests) {
            if (req.getPartCode() == null) {
                throw unprocessable("partCode is required for every part instance");
            }
            if (!defByPartCode.containsKey(req.getPartCode())) {
                throw unprocessable("Part code not valid in this context: " + req.getPartCode());
            }
            requestsByPartCode.computeIfAbsent(req.getPartCode(), k -> new ArrayList<>()).add(req);
        }

        validateCardinality(allowedDefs, requestsByPartCode);

        if (parentPartInstance != null) {
            NonMultitenancyPartDefinition parentDef = parentPartInstance.getPartDefinition();
            validateChoiceGroups(parentDef, allowedDefs, requestsByPartCode);
        }

        List<NonMultitenancyPartInstance> result = new ArrayList<>();
        for (PartInstanceRequest req : requests) {
            NonMultitenancyPartDefinition def = defByPartCode.get(req.getPartCode());
            NonMultitenancyPartInstance partInstance = buildPartInstance(
                def, req, productInstance, parentPartInstance, sellerOrgId);
            result.add(partInstance);
        }
        return result;
    }

    private void validateCardinality(
            List<NonMultitenancyPartDefinition> allowedDefs,
            Map<String, List<PartInstanceRequest>> requestsByPartCode) {

        for (NonMultitenancyPartDefinition def : allowedDefs) {
            List<PartInstanceRequest> reqs = requestsByPartCode.getOrDefault(def.getPartCode(), List.of());
            int count = reqs.size();
            if (count < def.getMinCardinality()) {
                throw unprocessable("Part '" + def.getPartCode() + "' requires at least "
                    + def.getMinCardinality() + " instance(s) but got " + count);
            }
            if (count > def.getMaxCardinality()) {
                throw unprocessable("Part '" + def.getPartCode() + "' allows at most "
                    + def.getMaxCardinality() + " instance(s) but got " + count);
            }
        }
    }

    private void validateChoiceGroups(
            NonMultitenancyPartDefinition parentDef,
            List<NonMultitenancyPartDefinition> childDefs,
            Map<String, List<PartInstanceRequest>> requestsByPartCode) {

        List<NonMultitenancyPartDefinitionChoiceGroup> groups = loadChoiceGroups(parentDef.getId());

        for (NonMultitenancyPartDefinitionChoiceGroup group : groups) {
            long chosen = childDefs.stream()
                .filter(d -> group.equals(d.getChoiceGroup()))
                .mapToLong(d -> requestsByPartCode.getOrDefault(d.getPartCode(), List.of()).size())
                .sum();

            if (chosen < group.getMinChoices()) {
                throw unprocessable("Choice group on part '" + parentDef.getPartCode()
                    + "' requires at least " + group.getMinChoices() + " selection(s) but got " + chosen);
            }
            if (chosen > group.getMaxChoices()) {
                throw unprocessable("Choice group on part '" + parentDef.getPartCode()
                    + "' allows at most " + group.getMaxChoices() + " selection(s) but got " + chosen);
            }
        }
    }

    private List<NonMultitenancyPartDefinitionChoiceGroup> loadChoiceGroups(String parentPartDefId) {
        return em.createQuery(
                "SELECT g FROM NonMultitenancyPartDefinitionChoiceGroup g " +
                "WHERE g.parentPartDefinition.id = :parentId",
                NonMultitenancyPartDefinitionChoiceGroup.class)
            .setParameter("parentId", parentPartDefId)
            .getResultList();
    }

    private NonMultitenancyPartInstance buildPartInstance(
            NonMultitenancyPartDefinition def,
            PartInstanceRequest req,
            NonMultitenancyProductInstance productInstance,
            NonMultitenancyPartInstance parentPartInstance,
            String sellerOrgId) {

        NonMultitenancyPartInstance partInstance = new NonMultitenancyPartInstance();
        partInstance.setId(UUID.randomUUID().toString());
        partInstance.setOrganisationId(sellerOrgId);
        partInstance.setProductInstance(productInstance);
        partInstance.setPartDefinition(def);
        partInstance.setParentPartInstance(parentPartInstance);
        partInstance.setResolvedUnitPrice(def.getUnitPrice());
        partInstance.setDisplayOrder(def.getDisplayOrder());
        em.persist(partInstance);

        List<NonMultitenancyPartInstanceAttribute> attrInstances =
            buildAndValidateAttributes(def, req.getAttributeValues(), partInstance, sellerOrgId);
        partInstance.setAttributes(attrInstances);

        List<NonMultitenancyPartDefinition> childDefs = loadChildPartDefinitions(def.getId());
        List<NonMultitenancyPartInstance> childInstances = buildAndValidatePartInstances(
            childDefs, req.getChildPartInstances(), productInstance, partInstance, sellerOrgId);
        partInstance.setChildPartInstances(childInstances);

        return partInstance;
    }

    private List<NonMultitenancyPartDefinition> loadChildPartDefinitions(String parentPartDefId) {
        return em.createQuery(
                "SELECT p FROM NonMultitenancyPartDefinition p " +
                "WHERE p.parentPart.id = :parentId ORDER BY p.displayOrder",
                NonMultitenancyPartDefinition.class)
            .setParameter("parentId", parentPartDefId)
            .getResultList();
    }

    private List<NonMultitenancyPartInstanceAttribute> buildAndValidateAttributes(
            NonMultitenancyPartDefinition def,
            List<PartInstanceAttributeRequest> attrRequests,
            NonMultitenancyPartInstance partInstance,
            String sellerOrgId) {

        if (attrRequests == null) {
            attrRequests = List.of();
        }

        List<NonMultitenancyPartAttributeDefinition> attrDefs = loadAttributeDefinitions(def.getId());

        Map<String, String> suppliedByName = attrRequests.stream()
            .filter(r -> r.getAttributeName() != null)
            .collect(Collectors.toMap(
                PartInstanceAttributeRequest::getAttributeName,
                r -> r.getAttributeValue() != null ? r.getAttributeValue() : "",
                (a, b) -> b));

        Map<String, NonMultitenancyPartAttributeDefinition> defsByName = attrDefs.stream()
            .collect(Collectors.toMap(NonMultitenancyPartAttributeDefinition::getAttributeName, d -> d));

        for (String name : suppliedByName.keySet()) {
            if (!defsByName.containsKey(name)) {
                throw unprocessable("Attribute '" + name + "' does not exist on part '" + def.getPartCode() + "'");
            }
        }

        List<NonMultitenancyPartInstanceAttribute> result = new ArrayList<>();

        for (NonMultitenancyPartAttributeDefinition attrDef : attrDefs) {
            String value = suppliedByName.get(attrDef.getAttributeName());

            if (Boolean.TRUE.equals(attrDef.getIsRequired()) && (value == null || value.isBlank())) {
                throw unprocessable("Required attribute '" + attrDef.getAttributeName()
                    + "' is missing on part '" + def.getPartCode() + "'");
            }

            if (value != null && !value.isBlank()) {
                List<NonMultitenancyPartAttributeAllowedValue> allowed = attrDef.getAllowedValues();
                if (!allowed.isEmpty()) {
                    boolean valid = allowed.stream()
                        .anyMatch(av -> av.getAllowedValue().equals(value));
                    if (!valid) {
                        throw unprocessable("Value '" + value + "' is not allowed for attribute '"
                            + attrDef.getAttributeName() + "' on part '" + def.getPartCode() + "'");
                    }
                }

                NonMultitenancyPartInstanceAttribute attr = new NonMultitenancyPartInstanceAttribute();
                attr.setId(UUID.randomUUID().toString());
                attr.setOrganisationId(sellerOrgId);
                attr.setPartInstance(partInstance);
                attr.setAttributeName(attrDef.getAttributeName());
                attr.setAttributeValue(value);
                em.persist(attr);
                result.add(attr);
            }
        }

        return result;
    }

    private List<NonMultitenancyPartAttributeDefinition> loadAttributeDefinitions(String partDefId) {
        return em.createQuery(
                "SELECT a FROM NonMultitenancyPartAttributeDefinition a " +
                "WHERE a.partDefinition.id = :partDefId",
                NonMultitenancyPartAttributeDefinition.class)
            .setParameter("partDefId", partDefId)
            .getResultList();
    }

    private BigDecimal sumPartInstances(List<NonMultitenancyPartInstance> instances) {
        BigDecimal total = BigDecimal.ZERO;
        for (NonMultitenancyPartInstance pi : instances) {
            total = total.add(pi.getResolvedUnitPrice());
            total = total.add(sumPartInstances(pi.getChildPartInstances()));
        }
        return total;
    }

    private static WebApplicationException unprocessable(String message) {
        return new WebApplicationException(
            Response.status(422).entity(message).build());
    }
}

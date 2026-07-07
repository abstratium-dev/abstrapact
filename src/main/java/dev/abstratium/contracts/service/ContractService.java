package dev.abstratium.contracts.service;

import dev.abstratium.conditions.entity.Contract;
import dev.abstratium.conditions.entity.ContractLineItem;
import dev.abstratium.conditions.entity.ContractState;
import dev.abstratium.contracts.boundary.dto.ContractSummary;
import dev.abstratium.contracts.boundary.dto.CreateDraftContractRequest;
import dev.abstratium.contracts.boundary.dto.LineItemRequest;
import dev.abstratium.contracts.boundary.dto.PartInstanceAttributeRequest;
import dev.abstratium.core.service.ConfigService;
import dev.abstratium.product.entity.PartDefinition;
import dev.abstratium.product.entity.PartInstance;
import dev.abstratium.product.entity.PartInstanceAttribute;
import dev.abstratium.product.entity.ProductDefinition;
import dev.abstratium.product.entity.ProductInstance;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ContractService {

    @Inject
    EntityManager em;

    @Inject
    ConfigService configService;

    @Transactional
    public Contract createDraft(CreateDraftContractRequest request, String orgId) {
        Contract contract = new Contract();
        contract.setId(UUID.randomUUID().toString());
        contract.setOrganisationId(orgId);
        contract.setContractReference(request.getContractReference());
        contract.setContractDate(LocalDate.now(ZoneOffset.UTC));
        contract.setCurrency(configService.getOrCreate().getCurrencyCode());
        contract.setPaymentModel(calculatePaymentModel(request.getLineItems()));
        contract.setPublicNotes(request.getPublicNotes());
        contract.setState(ContractState.DRAFT);
        contract.setGrandTotal(BigDecimal.ZERO);
        contract.setCreatedAt(LocalDateTime.now());
        contract.setUpdatedAt(LocalDateTime.now());
        em.persist(contract);

        if (request.getLineItems() != null) {
            int order = 0;
            for (LineItemRequest lineItemRequest : request.getLineItems()) {
                addLineItem(contract, lineItemRequest, order++, orgId);
            }
        }

        recalculateGrandTotal(contract, orgId);
        return contract;
    }

    public Optional<Contract> findById(String id) {
        return Optional.ofNullable(em.find(Contract.class, id));
    }

    public List<ContractSummary> findAll(String orgId) {
        return em.createQuery(
                "SELECT c FROM Contract c ORDER BY c.createdAt DESC",
                Contract.class)
            .getResultList()
            .stream()
            .map(this::toSummary)
            .toList();
    }

    public List<ContractSummary> findByState(ContractState state) {
        return em.createQuery(
                "SELECT c FROM Contract c WHERE c.state = :state ORDER BY c.createdAt DESC",
                Contract.class)
            .setParameter("state", state)
            .getResultList()
            .stream()
            .map(this::toSummary)
            .toList();
    }

    @Transactional
    public void delete(String id) {
        Contract contract = em.find(Contract.class, id);
        if (contract != null) {
            if (contract.getState() != ContractState.DRAFT) {
                throw new IllegalStateException("Only DRAFT contracts can be deleted");
            }
            deleteProductInstancesForContract(id, contract.getOrganisationId());
            em.remove(contract);
            em.flush();
        }
    }

    private void addLineItem(Contract contract, LineItemRequest request, int displayOrder, String orgId) {
        ProductDefinition productDef = em.find(ProductDefinition.class, request.getProductDefinitionId());
        if (productDef == null) {
            throw new IllegalArgumentException("Product definition not found: " + request.getProductDefinitionId());
        }

        ProductInstance productInstance = new ProductInstance();
        productInstance.setId(UUID.randomUUID().toString());
        productInstance.setOrganisationId(orgId);
        productInstance.setProductDefinition(productDef);
        em.persist(productInstance);

        List<PartDefinition> rootParts = em.createQuery(
                "SELECT p FROM PartDefinition p WHERE p.productDefinition.id = :productId AND p.parentPart IS NULL ORDER BY p.displayOrder",
                PartDefinition.class)
            .setParameter("productId", productDef.getId())
            .getResultList();

        for (PartDefinition rootPart : rootParts) {
            createPartInstanceRecursive(productInstance, rootPart, null, request, orgId);
        }

        ContractLineItem lineItem = new ContractLineItem();
        lineItem.setId(UUID.randomUUID().toString());
        lineItem.setOrganisationId(orgId);
        lineItem.setContract(contract);
        lineItem.setProductInstanceId(productInstance.getId());
        lineItem.setDisplayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : displayOrder);
        lineItem.setLineTotal(calculateProductInstanceTotal(productInstance.getId(), orgId));
        em.persist(lineItem);
        contract.getLineItems().add(lineItem);
    }

    private void createPartInstanceRecursive(ProductInstance productInstance, PartDefinition partDef,
                                              PartInstance parent, LineItemRequest request, String orgId) {
        PartInstance partInstance = new PartInstance();
        partInstance.setId(UUID.randomUUID().toString());
        partInstance.setOrganisationId(orgId);
        partInstance.setProductInstance(productInstance);
        partInstance.setPartDefinition(partDef);
        partInstance.setParentPartInstance(parent);
        partInstance.setResolvedUnitPrice(partDef.getUnitPrice());
        partInstance.setDisplayOrder(partDef.getDisplayOrder());
        em.persist(partInstance);

        if (request.getAttributes() != null) {
            for (PartInstanceAttributeRequest attrRequest : request.getAttributes()) {
                boolean belongsToThisPart = partDef.getAttributes().stream()
                    .anyMatch(a -> a.getAttributeName().equals(attrRequest.getAttributeName()));
                if (belongsToThisPart) {
                    PartInstanceAttribute attr = new PartInstanceAttribute();
                    attr.setId(UUID.randomUUID().toString());
                    attr.setOrganisationId(orgId);
                    attr.setPartInstance(partInstance);
                    attr.setAttributeName(attrRequest.getAttributeName());
                    attr.setAttributeValue(attrRequest.getAttributeValue());
                    em.persist(attr);
                }
            }
        }

        List<PartDefinition> children = em.createQuery(
                "SELECT p FROM PartDefinition p WHERE p.parentPart.id = :parentId ORDER BY p.displayOrder",
                PartDefinition.class)
            .setParameter("parentId", partDef.getId())
            .getResultList();

        for (PartDefinition child : children) {
            createPartInstanceRecursive(productInstance, child, partInstance, request, orgId);
        }
    }

    private BigDecimal calculateProductInstanceTotal(String productInstanceId, String orgId) {
        List<PartInstance> parts = em.createQuery(
                "SELECT p FROM PartInstance p WHERE p.productInstance.id = :id",
                PartInstance.class)
            .setParameter("id", productInstanceId)
            .getResultList();
        return parts.stream()
            .map(PartInstance::getResolvedUnitPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void recalculateGrandTotal(Contract contract, String orgId) {
        BigDecimal total = contract.getLineItems().stream()
            .map(ContractLineItem::getLineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        contract.setGrandTotal(total);
    }

    private Contract.PaymentModel calculatePaymentModel(List<LineItemRequest> lineItems) {
        if (lineItems == null || lineItems.isEmpty()) {
            return Contract.PaymentModel.PREPAID;
        }

        boolean anyPrepaid = false;
        for (LineItemRequest lineItem : lineItems) {
            ProductDefinition productDef = em.find(ProductDefinition.class, lineItem.getProductDefinitionId());
            if (productDef == null) {
                throw new IllegalArgumentException("Product definition not found: " + lineItem.getProductDefinitionId());
            }
            if (productDef.getPaymentModel() == ProductDefinition.PaymentModel.PREPAID) {
                anyPrepaid = true;
            }
        }

        return anyPrepaid ? Contract.PaymentModel.PREPAID : Contract.PaymentModel.POSTPAID;
    }

    private void deleteProductInstancesForContract(String contractId, String orgId) {
        List<ContractLineItem> lineItems = em.createQuery(
                "SELECT li FROM ContractLineItem li WHERE li.contract.id = :contractId",
                ContractLineItem.class)
            .setParameter("contractId", contractId)
            .getResultList();

        for (ContractLineItem li : lineItems) {
            deleteProductInstance(li.getProductInstanceId());
        }
    }

    private void deleteProductInstance(String productInstanceId) {
        List<PartInstance> partInstances = em.createQuery(
                "SELECT p FROM PartInstance p WHERE p.productInstance.id = :id",
                PartInstance.class)
            .setParameter("id", productInstanceId)
            .getResultList();

        for (PartInstance pi : partInstances) {
            em.remove(pi);
        }
        em.flush();

        ProductInstance productInstance = em.find(ProductInstance.class, productInstanceId);
        if (productInstance != null) {
            em.remove(productInstance);
            em.flush();
        }
    }

    private ContractSummary toSummary(Contract contract) {
        ContractSummary summary = new ContractSummary();
        summary.setId(contract.getId());
        summary.setContractReference(contract.getContractReference());
        summary.setContractDate(contract.getContractDate());
        summary.setCurrency(contract.getCurrency());
        summary.setGrandTotal(contract.getGrandTotal());
        summary.setPaymentModel(contract.getPaymentModel());
        summary.setState(contract.getState());
        summary.setCreatedAt(contract.getCreatedAt());
        summary.setUpdatedAt(contract.getUpdatedAt());
        return summary;
    }
}

package dev.abstratium.abstrapact.contracts.service;

import dev.abstratium.abstrapact.contracts.entity.Contract;
import dev.abstratium.abstrapact.contracts.entity.ContractLineItem;
import dev.abstratium.abstrapact.contracts.entity.ContractState;
import dev.abstratium.abstrapact.contracts.boundary.dto.ContractSummary;
import dev.abstratium.abstrapact.contracts.boundary.dto.CreateDraftContractRequest;
import dev.abstratium.abstrapact.contracts.boundary.dto.LineItemRequest;
import dev.abstratium.abstrapact.contracts.boundary.dto.PartInstanceAttributeRequest;
import dev.abstratium.abstrapact.non_multitenancy.sales.boundary.dto.ContractStateChangeResponse;
import dev.abstratium.abstrapact.non_multitenancy.sales.boundary.dto.PaymentAttemptResponse;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.entity.PaymentTransaction;
import dev.abstratium.abstrapact.process.entity.ProcessInstanceStep;
import dev.abstratium.core.service.ConfigService;
import dev.abstratium.abstrapact.product.entity.PartDefinition;
import dev.abstratium.abstrapact.product.entity.PartInstance;
import dev.abstratium.abstrapact.product.entity.PartInstanceAttribute;
import dev.abstratium.abstrapact.product.entity.ProductDefinition;
import dev.abstratium.abstrapact.product.entity.ProductInstance;
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

    /**
     * Returns the state changes recorded for a contract's sales process instance,
     * ordered chronologically. The contract is loaded through the tenant-scoped
     * {@link Contract} entity, so the caller's organisation is enforced by the
     * Hibernate discriminator.
     */
    public List<ContractStateChangeResponse> listStateChanges(String contractId) {
        Contract contract = em.find(Contract.class, contractId);
        if (contract == null) {
            throw notFound("Contract not found: " + contractId);
        }
        return em.createQuery(
                "SELECT s FROM ProcessInstanceStep s " +
                "WHERE s.processInstance.contractId = :contractId " +
                "ORDER BY s.stepTimestamp ASC",
                ProcessInstanceStep.class)
            .setParameter("contractId", contractId)
            .getResultStream()
            .map(this::toStateChangeResponse)
            .toList();
    }

    /**
     * Returns all payment attempts recorded for the contract, ordered by creation
     * time descending (most recent first). The tenant-scoped {@link Contract}
     * lookup already guarantees that the contract belongs to the caller's
     * organisation; the non-tenant payment transactions are then filtered by the
     * contract id, which is a UUID and therefore cannot collide across tenants.
     */
    public List<PaymentAttemptResponse> listPaymentAttempts(String contractId) {
        Contract contract = em.find(Contract.class, contractId);
        if (contract == null) {
            throw notFound("Contract not found: " + contractId);
        }
        return em.createQuery(
                "SELECT t FROM PaymentTransaction t " +
                "WHERE t.contractId = :contractId " +
                "ORDER BY t.createdAt DESC",
                PaymentTransaction.class)
            .setParameter("contractId", contractId)
            .getResultStream()
            .map(this::toPaymentAttemptResponse)
            .toList();
    }

    /**
     * Returns a single payment attempt by id, scoped to the contract. The
     * tenant-scoped {@link Contract} lookup already guarantees the organisation.
     */
    public PaymentAttemptResponse getPaymentAttempt(String contractId, String txId) {
        Contract contract = em.find(Contract.class, contractId);
        if (contract == null) {
            throw notFound("Contract not found: " + contractId);
        }
        PaymentTransaction tx = em.find(PaymentTransaction.class, txId);
        if (tx == null || !tx.getContractId().equals(contractId)) {
            throw notFound("Payment attempt not found: " + txId);
        }
        return toPaymentAttemptResponse(tx);
    }

    @Transactional
    public void delete(String id) {
        Contract contract = em.find(Contract.class, id);
        if (contract != null) {
            if (contract.getState() != ContractState.DRAFT) {
                throw new IllegalStateException("Only DRAFT contracts can be deleted");
            }
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
        lineItem.setProductInstance(productInstance);
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

    private ContractStateChangeResponse toStateChangeResponse(ProcessInstanceStep s) {
        ContractStateChangeResponse r = new ContractStateChangeResponse();
        r.setId(s.getId());
        r.setProcessInstanceId(s.getProcessInstance().getId());
        r.setStepTimestamp(s.getStepTimestamp());
        r.setFromState(s.getFromState());
        r.setToState(s.getToState());
        r.setActorUserId(s.getActorUserId());
        r.setReason(s.getReason());
        return r;
    }

    private PaymentAttemptResponse toPaymentAttemptResponse(PaymentTransaction t) {
        PaymentAttemptResponse r = new PaymentAttemptResponse();
        r.setId(t.getId());
        r.setStatus(t.getStatus());
        r.setGrossAmount(t.getGrossAmount());
        r.setFeeAmount(t.getFeeAmount());
        r.setNetAmount(t.getNetAmount());
        r.setCurrency(t.getCurrency());
        r.setPspIdentifier(t.getPspIdentifier());
        r.setPspSessionId(t.getPspSessionId());
        r.setPspTransactionRef(t.getPspTransactionRef());
        r.setCheckoutUrl(t.getCheckoutUrl());
        r.setCorrelationId(t.getCorrelationId());
        r.setCreatedAt(t.getCreatedAt());
        r.setUpdatedAt(t.getUpdatedAt());
        return r;
    }

    private static jakarta.ws.rs.WebApplicationException notFound(String message) {
        return new jakarta.ws.rs.WebApplicationException(
            jakarta.ws.rs.core.Response.status(jakarta.ws.rs.core.Response.Status.NOT_FOUND)
                .entity(message)
                .build());
    }
}

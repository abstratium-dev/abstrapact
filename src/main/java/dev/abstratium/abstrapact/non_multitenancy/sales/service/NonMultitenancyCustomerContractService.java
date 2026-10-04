package dev.abstratium.abstrapact.non_multitenancy.sales.service;

import dev.abstratium.abstrapact.contracts.entity.ContractState;
import dev.abstratium.abstrapact.contracts.entity.ContractTermsLink.TermsScope;
import dev.abstratium.abstrapact.non_multitenancy.sales.entity.*;
import dev.abstratium.abstrapact.non_multitenancy.sales.boundary.dto.*;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.entity.PaymentTransaction;
import dev.abstratium.abstrapact.non_multitenancy.sales.entity.NonMultitenancyProductDefinition;
import dev.abstratium.abstrapact.non_multitenancy.sales.entity.NonMultitenancyProductInstance;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates creation and updates of customer contracts via the cross-tenant API.
 *
 * All writes happen inside an active {@code @Transactional} boundary with the seller's
 * {@code CurrentOrgContext} already set by the resource layer before calling this service.
 */
@ApplicationScoped
public class NonMultitenancyCustomerContractService {

    @Inject
    EntityManager em;

    @Inject
    NonMultitenancyOrganisationResolutionService orgResolutionService;

    @Inject
    NonMultitenancyCustomerProductInstanceService productInstanceService;

    @Inject
    SalesProcessService salesProcessService;

    // ==================== Create ====================

    /**
     * Creates a new contract draft.
     *
     * @param request      the create request containing product codes and line items
     * @param sellerOrgId  the resolved seller organisation id
     * @param accountId    the caller's JWT {@code sub} claim
     * @return a response DTO for the created contract
     */
    @Transactional
    public CustomerContractResponse createContract(
            CreateCustomerContractRequest request,
            String sellerOrgId,
            String accountId) {

        String currency = resolveCurrency(sellerOrgId);

        NonMultitenancyContract contract = new NonMultitenancyContract();
        contract.setId(UUID.randomUUID().toString());
        contract.setOrganisationId(sellerOrgId);
        contract.setContractReference(request.getContractReference() != null
            ? request.getContractReference()
            : "REF-" + System.currentTimeMillis());
        contract.setContractDate(LocalDate.now());
        contract.setCurrency(currency);
        contract.setState(ContractState.DRAFT);
        contract.setPublicNotes(request.getPublicNotes());
        contract.setCreatedAt(LocalDateTime.now());
        contract.setUpdatedAt(LocalDateTime.now());

        BigDecimal grandTotal = BigDecimal.ZERO;
        List<NonMultitenancyContractLineItem> lineItems = new ArrayList<>();
        boolean anyPrepaid = false;
        boolean allPostpaid = true;

        contract.setGrandTotal(BigDecimal.ZERO);
        contract.setPaymentModel(NonMultitenancyContract.PaymentModel.PREPAID);
        em.persist(contract);

        for (CustomerLineItemRequest liReq : request.getLineItems()) {
            NonMultitenancyProductDefinition pd = findProductByPrefixedCode(liReq.getProductCode());

            if (pd.getPaymentModel() == NonMultitenancyProductDefinition.PaymentModel.PREPAID) {
                anyPrepaid = true;
            } else {
                allPostpaid = allPostpaid && true;
            }

            NonMultitenancyProductInstance productInstance =
                productInstanceService.createProductInstance(pd, liReq.getPartInstances(), sellerOrgId);

            BigDecimal lineTotal = productInstanceService.calculateLineTotal(productInstance);
            grandTotal = grandTotal.add(lineTotal);

            NonMultitenancyContractLineItem lineItem = new NonMultitenancyContractLineItem();
            lineItem.setId(UUID.randomUUID().toString());
            lineItem.setOrganisationId(sellerOrgId);
            lineItem.setContract(contract);
            lineItem.setProductInstance(productInstance);
            lineItem.setLineTotal(lineTotal);
            lineItem.setDisplayOrder(liReq.getDisplayOrder() != null ? liReq.getDisplayOrder() : lineItems.size());
            em.persist(lineItem);
            lineItems.add(lineItem);

            linkGeneralTerms(contract, pd, sellerOrgId);
        }

        contract.setGrandTotal(grandTotal);
        contract.setPaymentModel(anyPrepaid
            ? NonMultitenancyContract.PaymentModel.PREPAID
            : NonMultitenancyContract.PaymentModel.POSTPAID);
        contract.setLineItems(lineItems);
        em.merge(contract);

        NonMultitenancyContractAccountRole accountRole = new NonMultitenancyContractAccountRole();
        accountRole.setId(UUID.randomUUID().toString());
        accountRole.setOrganisationId(sellerOrgId);
        accountRole.setContract(contract);
        accountRole.setAccountId(accountId);
        accountRole.setRoleType(NonMultitenancyContractAccountRole.RoleType.CUSTOMER);
        em.persist(accountRole);

        salesProcessService.startSalesProcess(contract, accountId);

        return toResponse(contract, lineItems);
    }

    // ==================== Update ====================

    /**
     * Replaces line items on a contract still in {@code DRAFT}.
     */
    @Transactional
    public CustomerContractResponse updateContract(
            String contractId,
            CreateCustomerContractRequest request,
            String sellerOrgId,
            String accountId) {

        NonMultitenancyContract contract = loadContractForAccount(contractId, accountId);
        requireDraft(contract);

        for (NonMultitenancyContractLineItem li : new ArrayList<>(contract.getLineItems())) {
            em.remove(em.contains(li) ? li : em.merge(li));
        }
        em.flush();

        if (request.getContractReference() != null) {
            contract.setContractReference(request.getContractReference());
        }
        contract.setPublicNotes(request.getPublicNotes());
        contract.setUpdatedAt(LocalDateTime.now());

        BigDecimal grandTotal = BigDecimal.ZERO;
        List<NonMultitenancyContractLineItem> lineItems = new ArrayList<>();

        for (CustomerLineItemRequest liReq : request.getLineItems()) {
            NonMultitenancyProductDefinition pd = findProductByPrefixedCode(liReq.getProductCode());

            NonMultitenancyProductInstance productInstance =
                productInstanceService.createProductInstance(pd, liReq.getPartInstances(), sellerOrgId);

            BigDecimal lineTotal = productInstanceService.calculateLineTotal(productInstance);
            grandTotal = grandTotal.add(lineTotal);

            NonMultitenancyContractLineItem lineItem = new NonMultitenancyContractLineItem();
            lineItem.setId(UUID.randomUUID().toString());
            lineItem.setOrganisationId(sellerOrgId);
            lineItem.setContract(contract);
            lineItem.setProductInstance(productInstance);
            lineItem.setLineTotal(lineTotal);
            lineItem.setDisplayOrder(liReq.getDisplayOrder() != null ? liReq.getDisplayOrder() : lineItems.size());
            em.persist(lineItem);
            lineItems.add(lineItem);

            linkGeneralTerms(contract, pd, sellerOrgId);
        }

        contract.setGrandTotal(grandTotal);
        contract.setLineItems(lineItems);
        em.merge(contract);

        return toResponse(contract, lineItems);
    }

    // ==================== Delete line item ====================

    /**
     * Removes a single line item from a {@code DRAFT} contract and recalculates the total.
     */
    @Transactional
    public CustomerContractResponse deleteLineItem(
            String contractId,
            String lineItemId,
            String sellerOrgId,
            String accountId) {

        NonMultitenancyContract contract = loadContractForAccount(contractId, accountId);
        requireDraft(contract);

        NonMultitenancyContractLineItem toRemove = contract.getLineItems().stream()
            .filter(li -> li.getId().equals(lineItemId))
            .findFirst()
            .orElseThrow(() -> new WebApplicationException(
                Response.status(Response.Status.NOT_FOUND)
                    .entity("Line item not found: " + lineItemId)
                    .build()));

        em.remove(em.contains(toRemove) ? toRemove : em.merge(toRemove));
        contract.getLineItems().remove(toRemove);

        BigDecimal grandTotal = contract.getLineItems().stream()
            .map(NonMultitenancyContractLineItem::getLineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        contract.setGrandTotal(grandTotal);
        contract.setUpdatedAt(LocalDateTime.now());
        em.merge(contract);

        return toResponse(contract, contract.getLineItems());
    }

    // ==================== Read ====================

    /**
     * Returns a list of contract summaries for the given account.
     *
     * @param accountId   the caller's JWT {@code sub} claim
     * @param sellerOrgId optional filter by seller organisation id
     * @return list of summaries ordered by createdAt DESC
     */
    public List<CustomerContractSummary> listContracts(String accountId, String sellerOrgId) {
        String jpql = "SELECT c FROM NonMultitenancyContract c " +
            "JOIN NonMultitenancyContractAccountRole r ON r.contract.id = c.id " +
            "WHERE r.accountId = :accountId AND r.roleType = 'CUSTOMER'" +
            (sellerOrgId != null ? " AND c.organisationId = :orgId" : "") +
            " ORDER BY c.createdAt DESC";

        var query = em.createQuery(jpql, NonMultitenancyContract.class)
            .setParameter("accountId", accountId);

        if (sellerOrgId != null) {
            query.setParameter("orgId", sellerOrgId);
        }

        return query.getResultList().stream()
            .map(this::toSummary)
            .toList();
    }

    /**
     * Returns the full contract detail, scoped to the caller's account.
     */
    public CustomerContractResponse getContract(String contractId, String accountId) {
        NonMultitenancyContract contract = loadContractForAccount(contractId, accountId);
        return toResponse(contract, contract.getLineItems());
    }

    /**
     * Returns the state changes recorded for the contract's sales process instance,
     * ordered chronologically.
     *
     * @param contractId the contract id
     * @param accountId  the caller's JWT {@code sub} claim
     * @return list of state change responses
     */
    public List<ContractStateChangeResponse> listStateChanges(String contractId, String accountId, String callerOrgId) {
        loadContractForAccountOrOrg(contractId, accountId, callerOrgId);
        return em.createQuery(
                "SELECT s FROM NonMultitenancyProcessInstanceStep s " +
                "WHERE s.processInstance.contractId = :contractId " +
                "ORDER BY s.stepTimestamp ASC",
                NonMultitenancyProcessInstanceStep.class)
            .setParameter("contractId", contractId)
            .getResultStream()
            .map(this::toStateChangeResponse)
            .toList();
    }

    /**
     * Returns all payment attempts recorded for the contract, ordered by creation
     * time descending (most recent first).
     *
     * @param contractId the contract id
     * @param accountId  the caller's JWT {@code sub} claim
     * @param callerOrgId the caller's organisation id; may be null when not available
     * @return list of payment attempt responses
     */
    public List<PaymentAttemptResponse> listPaymentAttempts(String contractId, String accountId, String callerOrgId) {
        loadContractForAccountOrOrg(contractId, accountId, callerOrgId);
        return em.createQuery(
                "SELECT t FROM PaymentTransaction t WHERE t.contractId = :contractId " +
                "ORDER BY t.createdAt DESC",
                PaymentTransaction.class)
            .setParameter("contractId", contractId)
            .getResultStream()
            .map(this::toPaymentAttemptResponse)
            .toList();
    }

    /**
     * Returns a single payment attempt for the contract.
     *
     * @param contractId the contract id
     * @param txId       the payment transaction id
     * @param accountId  the caller's JWT {@code sub} claim
     * @param callerOrgId the caller's organisation id; may be null when not available
     * @return the payment attempt response
     * @throws WebApplicationException 404 if the attempt does not belong to the contract
     */
    public PaymentAttemptResponse getPaymentAttempt(String contractId, String txId, String accountId, String callerOrgId) {
        loadContractForAccountOrOrg(contractId, accountId, callerOrgId);
        PaymentTransaction tx = em.find(PaymentTransaction.class, txId);
        if (tx == null || !tx.getContractId().equals(contractId)) {
            throw new WebApplicationException(
                Response.status(Response.Status.NOT_FOUND)
                    .entity("Payment attempt not found: " + txId)
                    .build());
        }
        return toPaymentAttemptResponse(tx);
    }

    // ==================== Org resolution ====================

    /**
     * Returns the seller {@code organisationId} for a contract without requiring a tenant session.
     * Used by the resource layer to set {@link dev.abstratium.core.service.CurrentOrgContext}
     * before entering a {@code @Transactional} boundary.
     */
    public String getOrgIdForContract(String contractId) {
        return em.createQuery(
                "SELECT c.organisationId FROM NonMultitenancyContract c WHERE c.id = :id",
                String.class)
            .setParameter("id", contractId)
            .getResultStream()
            .findFirst()
            .orElseThrow(() -> new WebApplicationException(
                Response.status(Response.Status.NOT_FOUND)
                    .entity("Contract not found: " + contractId)
                    .build()));
    }

    // ==================== private helpers ====================

    private String resolveCurrency(String sellerOrgId) {
        return em.createQuery(
                "SELECT c.currencyCode FROM NonMultitenancyConfig c WHERE c.organisationId = :orgId",
                String.class)
            .setParameter("orgId", sellerOrgId)
            .getResultStream()
            .findFirst()
            .orElse("EUR");
    }

    private NonMultitenancyProductDefinition findProductByPrefixedCode(String prefixedCode) {
        return em.createQuery(
                "SELECT p FROM NonMultitenancyProductDefinition p WHERE p.productCode = :code",
                NonMultitenancyProductDefinition.class)
            .setParameter("code", prefixedCode)
            .getResultStream()
            .findFirst()
            .orElseThrow(() -> unprocessable("Product not found: " + prefixedCode));
    }

    private void linkGeneralTerms(
            NonMultitenancyContract contract,
            NonMultitenancyProductDefinition pd,
            String sellerOrgId) {

        if (pd.getTermsAndConditionsCode() == null || pd.getTermsAndConditionsCode().isBlank()) {
            return;
        }

        LocalDate today = LocalDate.now();
        Optional<NonMultitenancyTermsAndConditions> tac = em.createQuery(
                "SELECT t FROM NonMultitenancyTermsAndConditions t " +
                "WHERE t.organisationId = :orgId AND t.code = :code " +
                "AND t.effectiveFrom <= :today " +
                "AND (t.effectiveUntil IS NULL OR t.effectiveUntil >= :today)",
                NonMultitenancyTermsAndConditions.class)
            .setParameter("orgId", sellerOrgId)
            .setParameter("code", pd.getTermsAndConditionsCode())
            .setParameter("today", today)
            .getResultStream()
            .findFirst();

        tac.ifPresent(t -> {
            NonMultitenancyContractTermsLink link = new NonMultitenancyContractTermsLink();
            link.setId(UUID.randomUUID().toString());
            link.setOrganisationId(sellerOrgId);
            link.setContract(contract);
            link.setTermsAndConditions(t);
            link.setTermsVersionAtSigning(t.getCurrentVersion());
            link.setScope(TermsScope.GENERAL);
            em.persist(link);
        });
    }

    private NonMultitenancyContract loadContractForAccount(String contractId, String accountId) {
        return loadContractForAccountOrOrg(contractId, accountId, null);
    }

    private NonMultitenancyContract loadContractForAccountOrOrg(
            String contractId,
            String accountId,
            String callerOrgId) {
        NonMultitenancyContract contract = em.find(NonMultitenancyContract.class, contractId);
        if (contract == null) {
            throw new WebApplicationException(
                Response.status(Response.Status.NOT_FOUND)
                    .entity("Contract not found: " + contractId)
                    .build());
        }

        // Seller view: allow access when the caller's organisation owns the contract.
        if (callerOrgId != null && !callerOrgId.isBlank()
                && contract.getOrganisationId().equals(callerOrgId)) {
            return contract;
        }

        // Customer view: allow access when the caller is linked to the contract.
        boolean linked = em.createQuery(
                "SELECT COUNT(r) FROM NonMultitenancyContractAccountRole r " +
                "WHERE r.contract.id = :cid AND r.accountId = :aid AND r.roleType = 'CUSTOMER'",
                Long.class)
            .setParameter("cid", contractId)
            .setParameter("aid", accountId)
            .getSingleResult() > 0;

        if (!linked) {
            throw new WebApplicationException(
                Response.status(Response.Status.FORBIDDEN)
                    .entity("Contract not accessible for this account")
                    .build());
        }
        return contract;
    }

    private void requireDraft(NonMultitenancyContract contract) {
        if (contract.getState() != ContractState.DRAFT) {
            throw unprocessable("Contract must be in DRAFT state but is: " + contract.getState());
        }
    }

    private CustomerContractSummary toSummary(NonMultitenancyContract c) {
        CustomerContractSummary s = new CustomerContractSummary();
        s.setId(c.getId());
        s.setContractReference(c.getContractReference());
        s.setSellerOrganisationId(c.getOrganisationId());
        s.setContractDate(c.getContractDate());
        s.setCurrency(c.getCurrency());
        s.setGrandTotal(c.getGrandTotal());
        s.setState(c.getState());
        s.setCreatedAt(c.getCreatedAt());
        s.setUpdatedAt(c.getUpdatedAt());
        return s;
    }

    private CustomerContractResponse toResponse(
            NonMultitenancyContract c,
            List<NonMultitenancyContractLineItem> lineItems) {

        CustomerContractResponse r = new CustomerContractResponse();
        r.setId(c.getId());
        r.setContractReference(c.getContractReference());
        r.setSellerOrganisationId(c.getOrganisationId());
        r.setContractDate(c.getContractDate());
        r.setCurrency(c.getCurrency());
        r.setGrandTotal(c.getGrandTotal());
        r.setState(c.getState());
        r.setPublicNotes(c.getPublicNotes());
        r.setCreatedAt(c.getCreatedAt());
        r.setUpdatedAt(c.getUpdatedAt());

        List<CustomerContractLineItemResponse> liResponses = new ArrayList<>();
        for (NonMultitenancyContractLineItem li : lineItems) {
            CustomerContractLineItemResponse liR = new CustomerContractLineItemResponse();
            liR.setId(li.getId());
            liR.setDisplayOrder(li.getDisplayOrder());
            liR.setLineTotal(li.getLineTotal());
            liR.setProductInstance(li.getProductInstance());
            liResponses.add(liR);
        }
        r.setLineItems(liResponses);
        return r;
    }

    private ContractStateChangeResponse toStateChangeResponse(NonMultitenancyProcessInstanceStep s) {
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

    private static WebApplicationException unprocessable(String message) {
        return new WebApplicationException(
            Response.status(422).entity(message).build());
    }
}

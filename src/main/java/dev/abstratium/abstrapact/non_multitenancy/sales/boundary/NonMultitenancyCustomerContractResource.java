package dev.abstratium.abstrapact.non_multitenancy.sales.boundary;

import dev.abstratium.abstrapact.Roles;
import dev.abstratium.core.service.CurrentOrgContext;
import dev.abstratium.core.service.OrgScopedCodec;
import dev.abstratium.core.util.Hashing;
import dev.abstratium.abstrapact.non_multitenancy.sales.boundary.dto.ContractStateChangeResponse;
import dev.abstratium.abstrapact.non_multitenancy.sales.boundary.dto.CreateCustomerContractRequest;
import dev.abstratium.abstrapact.non_multitenancy.sales.boundary.dto.CustomerContractResponse;
import dev.abstratium.abstrapact.non_multitenancy.sales.boundary.dto.CustomerContractSummary;
import dev.abstratium.abstrapact.non_multitenancy.sales.boundary.dto.CustomerLineItemRequest;
import dev.abstratium.abstrapact.non_multitenancy.sales.boundary.dto.PartInstanceRequest;
import dev.abstratium.abstrapact.non_multitenancy.sales.boundary.dto.PaymentAttemptResponse;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.entity.IdempotencyRecord;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.service.IdempotencyRaceException;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.service.IdempotencyService;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.service.PaymentService;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.service.ProcessedResponse;
import dev.abstratium.abstrapact.non_multitenancy.sales.service.NonMultitenancyCustomerContractService;
import dev.abstratium.abstrapact.non_multitenancy.sales.service.NonMultitenancyOrganisationResolutionService;
import dev.abstratium.abstrapact.non_multitenancy.sales.service.SalesProcessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;

import java.util.List;

/**
 * Cross-tenant REST resource for customer contracts.
 *
 * Resource methods are intentionally <strong>not</strong> {@code @Transactional}.
 * Each method resolves the seller {@code orgId} first, sets it into
 * {@link CurrentOrgContext}, and only then calls the {@code @Transactional} service.
 */
@Path("/api/public/sales/contracts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed(Roles.USER)
public class NonMultitenancyCustomerContractResource {

    @Inject
    SecurityIdentity identity;

    @Inject
    CurrentOrgContext currentOrgContext;

    @Inject
    NonMultitenancyOrganisationResolutionService orgResolutionService;

    @Inject
    NonMultitenancyCustomerContractService contractService;

    @Inject
    SalesProcessService salesProcessService;

    @Inject
    PaymentService paymentService;

    @Inject
    IdempotencyService idempotencyService;

    @Inject
    ObjectMapper objectMapper;

    @POST
    @Operation(summary = "Create a new contract draft from product codes")
    public Response create(
            CreateCustomerContractRequest request,
            @HeaderParam("Idempotency-Key") String idempotencyKey) {

        requireIdempotencyKey(idempotencyKey);
        if (request.getLineItems() == null || request.getLineItems().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("At least one line item is required").build();
        }
        if (request.getOrgId() == null || request.getOrgId().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("orgId is required").build();
        }

        prefixAllCodes(request);

        List<String> productCodes = request.getLineItems().stream()
            .map(CustomerLineItemRequest::getProductCode)
            .toList();

        String sellerOrgId = orgResolutionService.resolveSellerOrgId(productCodes);
        currentOrgContext.setOrgId(sellerOrgId);

        String accountId = accountId();

        // Fingerprint: account id + serialized request (the parameters that define
        // this operation). Serialized AFTER prefixAllCodes so identical logical
        // payloads always produce the same fingerprint.
        String fingerprint;
        try {
            fingerprint = Hashing.sha256(
                accountId + ":" + objectMapper.writeValueAsString(request));
        } catch (Exception e) {
            throw new WebApplicationException(
                Response.status(Response.Status.BAD_REQUEST)
                    .entity("Cannot fingerprint request")
                    .build());
        }

        IdempotencyRecord record;
        try {
            record = idempotencyService.execute(
                idempotencyKey,
                "contract_create",
                accountId,
                fingerprint,
                () -> {
                    try {
                        CustomerContractResponse response =
                            contractService.createContract(request, sellerOrgId, accountId);
                        return new ProcessedResponse(
                            201, objectMapper.writeValueAsString(response));
                    } catch (WebApplicationException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to process create contract", e);
                    }
                });
        } catch (IdempotencyRaceException e) {
            record = idempotencyService.replay(idempotencyKey, "contract_create", fingerprint);
        } catch (Exception e) {
            // The same violation can also surface at commit time wrapped in a
            // RollbackException rather than as an IdempotencyRaceException.
            if (isIdempotencyScopeViolation(e)) {
                record = idempotencyService.replay(idempotencyKey, "contract_create", fingerprint);
            } else {
                throw rethrow(e);
            }
        }

        return Response.status(record.getStatusCode())
            .entity(record.getResponseBody())
            .build();
    }

    @GET
    @Operation(summary = "List contracts linked to the caller's account")
    public List<CustomerContractSummary> list(@QueryParam("orgId") String orgId) {
        return contractService.listContracts(accountId(), orgId);
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get a single contract by id")
    public CustomerContractResponse get(@PathParam("id") String id) {
        return contractService.getContract(id, accountId());
    }

    @GET
    @Path("/{id}/state-changes")
    @Operation(summary = "List state changes for a contract")
    public List<ContractStateChangeResponse> listStateChanges(@PathParam("id") String id) {
        String callerOrgId = currentOrgContext.getOrgId();
        String sellerOrgId = resolveOrgIdFromContract(id);
        currentOrgContext.setOrgId(sellerOrgId);
        return contractService.listStateChanges(id, accountId(), callerOrgId);
    }

    @GET
    @Path("/{id}/payment-attempts")
    @Operation(summary = "List payment attempts for a contract")
    public List<PaymentAttemptResponse> listPaymentAttempts(@PathParam("id") String id) {
        String callerOrgId = currentOrgContext.getOrgId();
        String sellerOrgId = resolveOrgIdFromContract(id);
        currentOrgContext.setOrgId(sellerOrgId);
        return contractService.listPaymentAttempts(id, accountId(), callerOrgId);
    }

    @GET
    @Path("/{id}/payment-attempts/{txId}")
    @Operation(summary = "Get a single payment attempt for a contract")
    public PaymentAttemptResponse getPaymentAttempt(
            @PathParam("id") String id,
            @PathParam("txId") String txId) {
        String callerOrgId = currentOrgContext.getOrgId();
        String sellerOrgId = resolveOrgIdFromContract(id);
        currentOrgContext.setOrgId(sellerOrgId);
        return contractService.getPaymentAttempt(id, txId, accountId(), callerOrgId);
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Update a draft contract")
    public CustomerContractResponse update(
            @PathParam("id") String id,
            CreateCustomerContractRequest request) {

        if (request.getOrgId() == null || request.getOrgId().isBlank()) {
            throw new WebApplicationException(
                Response.status(Response.Status.BAD_REQUEST).entity("orgId is required").build());
        }

        prefixAllCodes(request);

        List<String> productCodes = request.getLineItems().stream()
            .map(CustomerLineItemRequest::getProductCode)
            .toList();

        String sellerOrgId = orgResolutionService.resolveSellerOrgId(productCodes);
        currentOrgContext.setOrgId(sellerOrgId);

        return contractService.updateContract(id, request, sellerOrgId, accountId());
    }

    @DELETE
    @Path("/{id}/line-items/{lineItemId}")
    @Operation(summary = "Remove a line item from a draft contract")
    public CustomerContractResponse deleteLineItem(
            @PathParam("id") String id,
            @PathParam("lineItemId") String lineItemId) {

        String sellerOrgId = resolveOrgIdFromContract(id);
        currentOrgContext.setOrgId(sellerOrgId);

        return contractService.deleteLineItem(id, lineItemId, sellerOrgId, accountId());
    }

    @POST
    @Path("/{id}/offer")
    @Operation(summary = "Move the contract from DRAFT to OFFERED")
    public Response offer(
            @PathParam("id") String id,
            @HeaderParam("Idempotency-Key") String idempotencyKey) {

        requireIdempotencyKey(idempotencyKey);

        String sellerOrgId = resolveOrgIdFromContract(id);
        currentOrgContext.setOrgId(sellerOrgId);
        String accountId = accountId();

        // Fingerprint: contract id + account id (the parameters that define this operation)
        String fingerprint = Hashing.sha256(id + ":" + accountId);

        IdempotencyRecord record;
        try {
            record = idempotencyService.execute(
                idempotencyKey,
                "contract_offer",
                id,
                fingerprint,
                () -> {
                    salesProcessService.offerContract(id, accountId);
                    return new ProcessedResponse(200, null);
                });
        } catch (IdempotencyRaceException e) {
            record = idempotencyService.replay(idempotencyKey, "contract_offer", fingerprint);
        } catch (Exception e) {
            if (isIdempotencyScopeViolation(e)) {
                record = idempotencyService.replay(idempotencyKey, "contract_offer", fingerprint);
            } else {
                throw rethrow(e);
            }
        }

        return Response.status(record.getStatusCode())
            .entity(record.getResponseBody())
            .build();
    }

    @POST
    @Path("/{id}/accept")
    @Operation(summary = "Move the contract from OFFERED to ACCEPTED, then trigger payment handling")
    public Response accept(
            @PathParam("id") String id,
            @HeaderParam("Idempotency-Key") String idempotencyKey) {

        requireIdempotencyKey(idempotencyKey);

        String sellerOrgId = resolveOrgIdFromContract(id);
        currentOrgContext.setOrgId(sellerOrgId);
        String accountId = accountId();

        // Fingerprint: contract id + account id (the parameters that define this operation)
        String fingerprint = Hashing.sha256(id + ":" + accountId);

        IdempotencyRecord record;
        try {
            record = idempotencyService.execute(
                idempotencyKey,
                "contract_accept",
                id,
                fingerprint,
                () -> {
                try {
                    String checkoutUrl = salesProcessService.acceptContract(id, accountId);
                    CustomerContractResponse response = contractService.getContract(id, accountId);
                    response.setCheckoutUrl(checkoutUrl);
                    String json = objectMapper.writeValueAsString(response);
                    return new ProcessedResponse(200, json);
                } catch (WebApplicationException e) {
                    // Re-throw WebApplicationException (e.g. 422 for invalid contract state)
                    // so that the IdempotencyService can cache the 4xx response.
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException("Failed to process accept contract", e);
                }
            });
        } catch (IdempotencyRaceException e) {
            // A concurrent request with the same key won the race and committed.
            // This transaction is dead; replay the winner's cached response in a
            // fresh transaction.
            record = idempotencyService.replay(idempotencyKey, "contract_accept", fingerprint);
        } catch (Exception e) {
            // A concurrent request with a DIFFERENT key raced past the OFFERED
            // check and lost on UQ_payment_transaction_pending_contract. Replay
            // the winner's checkout URL from the committed PENDING transaction.
            if (isPaymentContractViolation(e)) {
                return respondWithWinnerCheckoutUrl(id, accountId);
            }
            // The idempotency-key race can also surface at commit time wrapped in
            // a RollbackException rather than as an IdempotencyRaceException.
            if (isIdempotencyScopeViolation(e)) {
                record = idempotencyService.replay(idempotencyKey, "contract_accept", fingerprint);
            } else {
                throw rethrow(e);
            }
        }

        return Response.status(record.getStatusCode())
            .entity(record.getResponseBody())
            .build();
    }

    @POST
    @Path("/{id}/retry-payment")
    @Operation(summary = "Create a new payment session for an AWAITING_PAYMENT contract "
        + "whose previous attempt expired or failed")
    public Response retryPayment(
            @PathParam("id") String id,
            @HeaderParam("Idempotency-Key") String idempotencyKey) {

        requireIdempotencyKey(idempotencyKey);

        String sellerOrgId = resolveOrgIdFromContract(id);
        currentOrgContext.setOrgId(sellerOrgId);
        String accountId = accountId();

        // Fingerprint: contract id + account id (the parameters that define this operation)
        String fingerprint = Hashing.sha256(id + ":" + accountId);

        IdempotencyRecord record;
        try {
            record = idempotencyService.execute(
                idempotencyKey,
                "contract_retry_payment",
                id,
                fingerprint,
                () -> {
                try {
                    String checkoutUrl = paymentService.retryPayment(id, accountId);
                    CustomerContractResponse response = contractService.getContract(id, accountId);
                    response.setCheckoutUrl(checkoutUrl);
                    String json = objectMapper.writeValueAsString(response);
                    return new ProcessedResponse(200, json);
                } catch (WebApplicationException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException("Failed to process retry payment", e);
                }
            });
        } catch (IdempotencyRaceException e) {
            record = idempotencyService.replay(idempotencyKey, "contract_retry_payment", fingerprint);
        } catch (Exception e) {
            if (isPaymentContractViolation(e)) {
                return respondWithWinnerCheckoutUrl(id, accountId);
            }
            if (isIdempotencyScopeViolation(e)) {
                record = idempotencyService.replay(idempotencyKey, "contract_retry_payment", fingerprint);
            } else {
                throw rethrow(e);
            }
        }

        return Response.status(record.getStatusCode())
            .entity(record.getResponseBody())
            .build();
    }

    // ==================== private helpers ====================

    private String accountId() {
        return identity.getPrincipal().getName();
    }

    /**
     * Enforces that an {@code Idempotency-Key} header is present and fits the
     * {@code idempotency_key VARCHAR(255)} column — longer keys would otherwise
     * surface as a database error instead of a client error.
     */
    private void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new WebApplicationException(
                Response.status(Response.Status.BAD_REQUEST)
                    .entity("Missing Idempotency-Key header")
                    .build());
        }
        if (idempotencyKey.length() > 255) {
            throw new WebApplicationException(
                Response.status(Response.Status.BAD_REQUEST)
                    .entity("Idempotency-Key header exceeds 255 characters")
                    .build());
        }
    }

    /**
     * Recovery path after a {@code UQ_payment_transaction_pending_contract}
     * violation: a concurrent request with a different idempotency key already
     * committed the {@code PENDING} transaction. Poll briefly for its checkout
     * URL (the winner stores the PSP session asynchronously) and return it as a
     * normal {@code 200} contract response so the losing client pays once.
     */
    private Response respondWithWinnerCheckoutUrl(String contractId, String accountId) {
        String checkoutUrl = paymentService.findPendingCheckoutUrlForContract(contractId);
        if (checkoutUrl == null) {
            // The winner's PSP call did not complete in time — a retryable error.
            throw new WebApplicationException(
                Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity("Payment session is being initialised concurrently. "
                        + "Retry with the same Idempotency-Key.")
                    .build());
        }
        try {
            CustomerContractResponse response = contractService.getContract(contractId, accountId);
            response.setCheckoutUrl(checkoutUrl);
            return Response.ok(objectMapper.writeValueAsString(response)).build();
        } catch (Exception ex) {
            throw new RuntimeException("Failed to build payment response", ex);
        }
    }

    /**
     * Detects a {@code UQ_idempotency_key_scope} violation anywhere in the cause
     * chain — covering both the flush-time {@code ConstraintViolationException}
     * and commit-time {@code RollbackException}/{@code PersistenceException}
     * wrappers.
     */
    private boolean isIdempotencyScopeViolation(Throwable e) {
        while (e != null) {
            if (e instanceof org.hibernate.exception.ConstraintViolationException cve) {
                String name = cve.getConstraintName();
                if (name != null && name.toUpperCase().contains("UQ_IDEMPOTENCY_KEY_SCOPE")) {
                    return true;
                }
            }
            String msg = e.getMessage();
            if (msg != null && msg.toUpperCase().contains("UQ_IDEMPOTENCY_KEY_SCOPE")) {
                return true;
            }
            e = e.getCause();
        }
        return false;
    }

    private boolean isPaymentContractViolation(Throwable e) {
        while (e != null) {
            if (e instanceof org.hibernate.exception.ConstraintViolationException cve) {
                String name = cve.getConstraintName();
                // H2 may append an index suffix or use a generated constraint name,
                // so also fall through to the message check when the name is absent
                // or does not match.
                if (name != null
                        && name.toUpperCase().contains("UQ_PAYMENT_TRANSACTION_PENDING_CONTRACT")) {
                    return true;
                }
            }
            String msg = e.getMessage();
            if (msg != null && msg.toUpperCase().contains("UQ_PAYMENT_TRANSACTION_PENDING_CONTRACT")) {
                return true;
            }
            e = e.getCause();
        }
        return false;
    }

    private static RuntimeException rethrow(Exception e) {
        if (e instanceof RuntimeException re) {
            return re;
        }
        return new RuntimeException(e);
    }

    /**
     * Reads the {@code organisationId} directly from the non-tenant contract row,
     * without opening a tenant-scoped Hibernate session.
     */
    private String resolveOrgIdFromContract(String contractId) {
        return contractService.getOrgIdForContract(contractId);
    }

    /**
     * Prefixes all raw product codes and part codes in the request with the top-level orgId.
     * Callers send raw short codes; this method converts them to {@code {orgId}::{rawCode}}
     * in-place before validation and persistence.
     */
    private void prefixAllCodes(CreateCustomerContractRequest request) {
        String orgId = request.getOrgId();
        if (request.getLineItems() == null) return;
        for (CustomerLineItemRequest li : request.getLineItems()) {
            li.setProductCode(OrgScopedCodec.encode(orgId, li.getProductCode(), "Product"));
            prefixPartInstanceCodes(orgId, li.getPartInstances());
        }
    }

    private void prefixPartInstanceCodes(String orgId, List<PartInstanceRequest> partInstances) {
        if (partInstances == null) return;
        for (PartInstanceRequest pi : partInstances) {
            pi.setPartCode(OrgScopedCodec.encode(orgId, pi.getPartCode(), "Part"));
            prefixPartInstanceCodes(orgId, pi.getChildPartInstances());
        }
    }
}

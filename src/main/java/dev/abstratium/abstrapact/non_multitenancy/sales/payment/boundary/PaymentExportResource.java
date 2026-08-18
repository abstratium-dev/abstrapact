package dev.abstratium.abstrapact.non_multitenancy.sales.payment.boundary;

import dev.abstratium.abstrapact.Roles;
import dev.abstratium.abstrapact.non_multitenancy.sales.entity.NonMultitenancyContract;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.boundary.dto.PaymentExportRow;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.entity.PaymentTransaction;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.service.PaymentService;
import dev.abstratium.abstrapact.non_multitenancy.sales.payment.service.PaymentTransactionService;
import dev.abstratium.core.service.CurrentOrgContext;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.Operation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV export endpoint for successful payment transactions.
 *
 * <p>Returns a CSV file with one row per successful payment transaction in the date range,
 * in a format compatible with the abstraccount accounting software for manual import.
 *
 * <p>Requires the {@code abstratium-abstrapact_user} role and is scoped to the caller's
 * organisation (resolved from the JWT via {@link CurrentOrgContext}).
 */
@Path("/public/payment/export")
@RolesAllowed(Roles.USER)
public class PaymentExportResource {

    @Inject
    PaymentTransactionService transactionService;

    @Inject
    PaymentService paymentService;

    @Inject
    CurrentOrgContext currentOrgContext;

    @GET
    @Operation(summary = "Export successful payments as CSV for abstraccount import")
    public Response export(
            @QueryParam("from") String from,
            @QueryParam("to") String to) {

        if (from == null || from.isBlank() || to == null || to.isBlank()) {
            throw new BadRequestException("'from' and 'to' query parameters (YYYY-MM-DD) are required");
        }

        LocalDate fromDate = LocalDate.parse(from);
        LocalDate toDate = LocalDate.parse(to);

        // Resolve the org id from the request context (set by OrgIdResolutionFilter from the
        // JWT orgId claim). This must never fall back to a default — an export without a
        // resolved org is a security issue.
        String orgId = currentOrgContext.getOrgId();
        if (orgId == null || orgId.isBlank()) {
            throw new WebApplicationException(
                Response.status(Response.Status.BAD_REQUEST)
                    .entity("Organization id could not be resolved from the request context")
                    .build());
        }
        List<PaymentTransaction> txs = transactionService.findSucceededInRange(orgId, fromDate, toDate);

        List<PaymentExportRow> rows = new ArrayList<>();
        for (PaymentTransaction tx : txs) {
            PaymentExportRow row = new PaymentExportRow();
            row.setDate(tx.getUpdatedAt().toLocalDate());
            row.setPartner(""); // abstraccount assigns the partner
            row.setDescription(resolveContractReference(tx.getContractId()));
            row.setGrossAmount(formatAmount(tx.getGrossAmount()));
            row.setFeeAmount(formatAmount(tx.getFeeAmount() != null ? tx.getFeeAmount() : BigDecimal.ZERO));
            row.setStripeTxn(tx.getPspTransactionRef());
            row.setContractId(tx.getContractId());
            rows.add(row);
        }

        String csv = toCsv(rows);
        String filename = "payments-" + from + "-to-" + to + ".csv";

        return Response.ok(csv)
            .header("Content-Type", "text/csv")
            .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
            .build();
    }

    private String resolveContractReference(String contractId) {
        return paymentService.findContractById(contractId)
            .map(NonMultitenancyContract::getContractReference)
            .orElse("Contract " + contractId);
    }

    private static BigDecimal formatAmount(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private static String toCsv(List<PaymentExportRow> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("date,partner,description,gross_amount,fee_amount,stripe_txn,contract_id\n");
        DateTimeFormatter dateFmt = DateTimeFormatter.ISO_LOCAL_DATE;
        for (PaymentExportRow row : rows) {
            sb.append(row.getDate().format(dateFmt)).append(',');
            sb.append(csvEscape(row.getPartner())).append(',');
            sb.append(csvEscape(row.getDescription())).append(',');
            sb.append(row.getGrossAmount()).append(',');
            sb.append(row.getFeeAmount()).append(',');
            sb.append(csvEscape(row.getStripeTxn())).append(',');
            sb.append(csvEscape(row.getContractId())).append('\n');
        }
        return sb.toString();
    }

    private static String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}

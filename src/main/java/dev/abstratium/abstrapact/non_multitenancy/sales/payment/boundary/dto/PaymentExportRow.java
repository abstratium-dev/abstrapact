package dev.abstratium.abstrapact.non_multitenancy.sales.payment.boundary.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row in the CSV payment export ({@code GET /api/public/payment/export}).
 *
 * <p>Column order matches the abstraccount batch import format:
 * {@code date,partner,description,gross_amount,fee_amount,stripe_txn,contract_id}.
 */
public class PaymentExportRow {

    private LocalDate date;
    private String partner; // always empty — abstraccount assigns the partner
    private String description;
    private BigDecimal grossAmount;
    private BigDecimal feeAmount;
    private String stripeTxn;
    private String contractId;

    public PaymentExportRow() {
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getPartner() {
        return partner;
    }

    public void setPartner(String partner) {
        this.partner = partner;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getGrossAmount() {
        return grossAmount;
    }

    public void setGrossAmount(BigDecimal grossAmount) {
        this.grossAmount = grossAmount;
    }

    public BigDecimal getFeeAmount() {
        return feeAmount;
    }

    public void setFeeAmount(BigDecimal feeAmount) {
        this.feeAmount = feeAmount;
    }

    public String getStripeTxn() {
        return stripeTxn;
    }

    public void setStripeTxn(String stripeTxn) {
        this.stripeTxn = stripeTxn;
    }

    public String getContractId() {
        return contractId;
    }

    public void setContractId(String contractId) {
        this.contractId = contractId;
    }
}

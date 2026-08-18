package dev.abstratium.abstrapact.non_multitenancy.sales.payment.boundary.dto;

/**
 * Response from creating a payment through the active {@code PSPInterface}.
 */
public class CreatePaymentResponse {

    /** URL the customer is redirected to (Stripe's hosted checkout page). */
    private String checkoutUrl;

    /** PSP session/checkout id (e.g. Stripe's {@code cs_...}). */
    private String pspSessionId;

    public CreatePaymentResponse() {
    }

    public CreatePaymentResponse(String checkoutUrl, String pspSessionId) {
        this.checkoutUrl = checkoutUrl;
        this.pspSessionId = pspSessionId;
    }

    public String getCheckoutUrl() {
        return checkoutUrl;
    }

    public void setCheckoutUrl(String checkoutUrl) {
        this.checkoutUrl = checkoutUrl;
    }

    public String getPspSessionId() {
        return pspSessionId;
    }

    public void setPspSessionId(String pspSessionId) {
        this.pspSessionId = pspSessionId;
    }
}

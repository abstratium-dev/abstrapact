package dev.abstratium.core.filter;

import dev.abstratium.abstrapact.non_multitenancy.sales.payment.service.UnsupportedPaymentModelException;
import io.quarkiverse.resteasy.problem.HttpProblem;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Maps {@link UnsupportedPaymentModelException} to RFC 7807 Problem Details with HTTP 422
 * Unprocessable Entity.
 *
 * <p>This makes the limitation explicit at the boundary: a {@code POSTPAID} contract
 * cannot be paid through the prepaid payment flow until periodic invoicing is implemented.
 */
@Provider
public class UnsupportedPaymentModelExceptionMapper
        implements ExceptionMapper<UnsupportedPaymentModelException> {

    @Override
    public Response toResponse(UnsupportedPaymentModelException exception) {
        HttpProblem problem = HttpProblem.builder()
            .withStatus(422)
            .withTitle("Unsupported Payment Model")
            .withDetail(exception.getMessage())
            .build();
        return problem.toResponse();
    }
}

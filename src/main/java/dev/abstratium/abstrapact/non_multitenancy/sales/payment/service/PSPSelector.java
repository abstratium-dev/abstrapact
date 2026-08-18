package dev.abstratium.abstrapact.non_multitenancy.sales.payment.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Iterator;

/**
 * Selects the active {@link PSPInterface} implementation based on the
 * {@code abstrapact.payment.psp} configuration property.
 *
 * <p>The selected implementation's {@link PSPInterface#getPspIdentifier()} must equal
 * the configured value (e.g. {@code "stripe"}). If no match is found, a
 * {@link IllegalStateException} is thrown at first use.
 */
@ApplicationScoped
public class PSPSelector {

    @Inject
    Instance<PSPInterface> psps;

    @ConfigProperty(name = "abstrapact.payment.psp")
    String activePspIdentifier;

    /**
     * @return the active {@link PSPInterface} implementation.
     * @throws IllegalStateException if no implementation matches the configured identifier.
     */
    public PSPInterface getActive() {
        Iterator<PSPInterface> it = psps.iterator();
        while (it.hasNext()) {
            PSPInterface psp = it.next();
            if (activePspIdentifier.equals(psp.getPspIdentifier())) {
                return psp;
            }
        }
        throw new IllegalStateException(
            "No PSPInterface implementation found for abstrapact.payment.psp=" + activePspIdentifier);
    }
}

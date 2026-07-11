package dev.abstratium.non_multitenancy.contract.service;

import dev.abstratium.product.entity.ProductDefinition;
import dev.abstratium.core.service.OrgScopedCodec;
import dev.abstratium.product.service.ProductDefinitionService;
import dev.abstratium.test.TestDataCleaner;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class NonMultitenancyOrganisationResolutionServiceTest {

    @Inject
    NonMultitenancyOrganisationResolutionService service;

    @Inject
    ProductDefinitionService productDefinitionService;

    @Inject
    TestDataCleaner cleaner;

    @ConfigProperty(name = "default.org.uuid")
    String defaultOrgId;

    private String pc(String raw) {
        return OrgScopedCodec.encode(defaultOrgId, raw, "Product");
    }

    private String otherOrgId;

    @BeforeEach
    @TestSecurity(user = "testuser", roles = {"abstratium-abstrapact_user"})
    void setUp() {
        otherOrgId = UUID.randomUUID().toString().replace("-", "a").substring(0, 8)
            + "-0000-0000-0000-000000000099";

        ProductDefinition allowed = new ProductDefinition();
        allowed.setId(UUID.randomUUID().toString());
        allowed.setProductCode("ORG-RES-ALLOWED-001");
        allowed.setDescription("Cross-tenant allowed product");
        allowed.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        allowed.setProductValidFrom(LocalDate.now());
        allowed.setCrossTenantApiAllowed(true);
        productDefinitionService.createProductDefinition(allowed);

        ProductDefinition allowed2 = new ProductDefinition();
        allowed2.setId(UUID.randomUUID().toString());
        allowed2.setProductCode("ORG-RES-ALLOWED-002");
        allowed2.setDescription("Another cross-tenant allowed product");
        allowed2.setBillingModel(ProductDefinition.BillingModel.SUBSCRIPTION);
        allowed2.setProductValidFrom(LocalDate.now());
        allowed2.setCrossTenantApiAllowed(true);
        productDefinitionService.createProductDefinition(allowed2);

        ProductDefinition disallowed = new ProductDefinition();
        disallowed.setId(UUID.randomUUID().toString());
        disallowed.setProductCode("ORG-RES-DISALLOWED-001");
        disallowed.setDescription("Cross-tenant disallowed product");
        disallowed.setBillingModel(ProductDefinition.BillingModel.FIXED_PRICE);
        disallowed.setProductValidFrom(LocalDate.now());
        disallowed.setCrossTenantApiAllowed(false);
        productDefinitionService.createProductDefinition(disallowed);
    }

    @AfterEach
    void tearDown() throws Exception {
        cleaner.deleteAll();
    }

    @Test
    void resolvesSingleAllowedProduct() {
        String orgId = service.resolveSellerOrgId(List.of(pc("ORG-RES-ALLOWED-001")));
        assertEquals(defaultOrgId, orgId);
    }

    @Test
    void resolvesMultipleAllowedProductsFromSameOrg() {
        String orgId = service.resolveSellerOrgId(
            List.of(pc("ORG-RES-ALLOWED-001"), pc("ORG-RES-ALLOWED-002")));
        assertEquals(defaultOrgId, orgId);
    }

    @Test
    void rejects422WhenProductNotFound() {
        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> service.resolveSellerOrgId(List.of(pc("DOES-NOT-EXIST"))));
        assertEquals(422, ex.getResponse().getStatus());
    }

    @Test
    void rejects422WhenProductNotCrossTenantAllowed() {
        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> service.resolveSellerOrgId(List.of(pc("ORG-RES-DISALLOWED-001"))));
        assertEquals(422, ex.getResponse().getStatus());
    }

    @Test
    void rejects422WhenProductsFromDifferentOrgs() {
        String otherPrefixed = otherOrgId + "::" + "ORG-RES-ALLOWED-001";
        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> service.resolveSellerOrgId(
                List.of(pc("ORG-RES-ALLOWED-001"), otherPrefixed)));
        assertEquals(422, ex.getResponse().getStatus());
    }

    @Test
    void rejects422WhenEmptyList() {
        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> service.resolveSellerOrgId(List.of()));
        assertEquals(422, ex.getResponse().getStatus());
    }

    @Test
    void rejects422WhenNullList() {
        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> service.resolveSellerOrgId(null));
        assertEquals(422, ex.getResponse().getStatus());
    }

    @Test
    void rejects422WhenCodeHasNoSeparator() {
        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> service.resolveSellerOrgId(List.of("PLAIN-CODE-WITHOUT-PREFIX")));
        assertEquals(422, ex.getResponse().getStatus());
    }
}

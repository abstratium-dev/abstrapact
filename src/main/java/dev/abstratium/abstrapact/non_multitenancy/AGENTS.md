The entity classes and REST endpoints in this package are ONLY TO BE USED when specifically instructed, because they DO NOT conform to the design principles in the document docs/MULTITENANCY_DESIGN.md.

## Entity Classes (entity/)

These entity classes do not use the hibernate `@TenantId` annotation.

They can be used in certain circumstances when cross-tenant access is required. The user must provide explicit requirements in order to build such classes.
Entity classes in this package should mirror other tenant-bound entity classes where `non_multitenancy` is not in the package name. Check this whenever creating or modifying such entities.

## REST Endpoints (boundary/)

The `boundary` sub-package contains REST endpoints that perform cross-tenant (cross-organisation) data access. These endpoints are intentionally isolated here to make "dangerous" endpoints that bypass tenant isolation easily identifiable during security audits.

**Location principle**: Any REST endpoint that uses non-multitenancy entities to bypass the Hibernate discriminator should be placed in this package.

## Approved Exceptions (Usage Outside This Package)

**This table is to be kept up to date!**

The following classes outside this package are permitted to reference `non_multitenancy` types for specific, justified reasons:

| Class | References | Justification |
|-------|-----------|---------------|
| `core.filter.UnsupportedPaymentModelExceptionMapper` | `non_multitenancy.sales.payment.service.UnsupportedPaymentModelException` | Maps the payment-specific exception to an HTTP 422 response. The exception is thrown by `SalesProcessService` (which is in the non-multitenancy package) and the mapper must be in the core filter package to be discovered by JAX-RS globally. |
| `contracts.boundary.ContractResource` | `non_multitenancy.sales.boundary.dto.ContractStateChangeResponse`, `non_multitenancy.sales.boundary.dto.PaymentAttemptResponse` | Reuses the read-only response DTOs for the seller/org-scoped contract state-change and payment-attempt endpoints. The DTOs contain no cross-tenant logic and are deliberately shared to avoid duplication between the customer-facing public endpoints and the org-scoped management endpoints. |
| `contracts.service.ContractService` | `non_multitenancy.sales.payment.entity.PaymentTransaction` | Queries payment attempts for contracts that are already verified to belong to the caller's organisation through the tenant-scoped `Contract` entity. `PaymentTransaction` is a shared, non-tenant entity. |

Any new usage outside this package must be added to the above table with a justification, and approved by the chief architect.

### Security Requirements for Boundary Endpoints

All boundary endpoints MUST:

1. **Require explicit `orgId` verification** - Never rely solely on JWT-resolved tenant context
2. **Document cross-tenant nature** - Include clear OpenAPI documentation noting the cross-tenant behavior

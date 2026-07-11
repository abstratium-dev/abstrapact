# Design of Cross-Tenant Customer Contracts

## Overview

B2C users authenticate through abstrauth and obtain a JWT. abstrauth automatically creates a personal organisation for every user, so the `orgId` claim in the JWT refers to the user's own organisation, **not** the seller's organisation. Product definitions, product instances, and contracts are tenant-scoped with Hibernate discriminator multi-tenancy (see [HIBERNATE_DISCRIMINATOR_MULTITENANCY.md](./HIBERNATE_DISCRIMINATOR_MULTITENANCY.md)), so a B2C user cannot use the standard tenant-scoped API to purchase products from a seller.

This document defines a separate, cross-tenant API that allows authenticated B2C customers to:

1. Create a contract draft from one or more seller product codes.
2. Configure the product instances while the contract is still in `DRAFT`.
3. Move the contract to `OFFERED` once they are happy with the draft and want to consider it.
4. Accept the offer, which transitions the contract to `ACCEPTED`.
5. Search all contracts that are linked to their account (`sub` claim).

The product instances and contracts are physically stored in the **seller's organisation** so that the seller can manage them through the standard tenant-scoped API. The customer accesses their contracts through the new cross-tenant API.

---

## Terminology

- **Seller organisation** -- the organisation that owns the `ProductDefinition` and the resulting `Contract`.
- **Customer account** -- the external user, identified by the JWT `sub` claim.
- **Prefixed product code** -- the product code the caller provides, including the seller organisation id and a double colon: `{orgId}::{productCode}`, e.g. `00000000-0000-0000-0000-000000000000::PROD-001`.
- **Raw product code** -- the human-readable code without the prefix, e.g. `PROD-001`.

---

## Package and Module Structure

All new code lives under the root package `dev.abstratium.abstrapact.non_multitenancy`.

```
dev.abstratium.abstrapact.non_multitenancy
├── contract
│   ├── boundary
│   │   └── NonMultitenancyCustomerContractResource.java
│   ├── boundary/dto
│   │   ├── NonMultitenancyCreateCustomerContractRequest.java
│   │   ├── NonMultitenancyCustomerContractSummary.java
│   │   ├── NonMultitenancyCustomerContractResponse.java
│   │   ├── NonMultitenancyCustomerLineItemRequest.java
│   │   ├── NonMultitenancyPartInstanceRequest.java
│   │   └── ... (other DTOs as needed)
│   ├── service
│   │   ├── NonMultitenancyCustomerContractService.java
│   │   └── NonMultitenancyOrganisationResolutionService.java
│   └── entity
│       └── NonMultitenancyContractAccountRole.java
├── product
│   ├── entity
│   │   ├── NonMultitenancyProductDefinition.java        (copy, no @TenantId, includes crossTenantApiAllowed)
│   │   ├── NonMultitenancyPartDefinition.java           (copy, no @TenantId, references choice group)
│   │   ├── NonMultitenancyPartDefinitionChoiceGroup.java (copy, no @TenantId)
│   │   ├── NonMultitenancyPartDefinitionAttribute.java    (copy, no @TenantId)
│   │   ├── NonMultitenancyProductInstance.java          (copy, no @TenantId)
│   │   ├── NonMultitenancyPartInstance.java             (copy, no @TenantId)
│   │   └── NonMultitenancyPartInstanceAttribute.java    (copy, no @TenantId)
│   └── service
│       └── NonMultitenancyCustomerProductInstanceService.java
├── conditions
│   └── entity
│       ├── NonMultitenancyContract.java                  (copy, no @TenantId)
│       ├── NonMultitenancyContractLineItem.java          (copy, no @TenantId)
│       ├── NonMultitenancyContractTermsLink.java         (copy, no @TenantId)
│       ├── NonMultitenancyTermsAndConditions.java       (copy, no @TenantId)
│       └── NonMultitenancySignatory.java                 (copy, no @TenantId)
├── process
│   └── entity
│       ├── NonMultitenancyProcessInstance.java           (copy, no @TenantId)
│       └── NonMultitenancyProcessInstanceStep.java       (copy, no @TenantId)
└── core
    └── entity
        └── NonMultitenancyConfig.java                    (copy, no @TenantId)
```

The duplicated entities are **copies of the existing tenant-scoped entities with the `@TenantId` annotation removed**. They map to the same tables and therefore allow cross-tenant reads and writes when the current tenant context is the customer's organisation.

---

## Data Model Changes

### New Table: `T_contract_account_role`

Links a customer account to a contract. The role type is extensible for future relationship types (e.g. sales agent).

```sql
CREATE TABLE T_contract_account_role (
    id VARCHAR(36) PRIMARY KEY,
    contract_id VARCHAR(36) NOT NULL,
    account_id VARCHAR(255) NOT NULL,
    role_type VARCHAR(30) NOT NULL,
    valid_from DATE,
    valid_until DATE,
    CONSTRAINT FK_contract_account_role_contract_id
        FOREIGN KEY (contract_id) REFERENCES T_contract(id),
    CONSTRAINT CHK_contract_account_role_type CHECK (role_type IN ('CUSTOMER'))
);

CREATE INDEX I_contract_account_role_contract ON T_contract_account_role(contract_id);
CREATE INDEX I_contract_account_role_account ON T_contract_account_role(account_id);
```

Add the corresponding Envers audit table in the same migration:

```sql
CREATE TABLE T_contract_account_role_AUD (
    id VARCHAR(36) NOT NULL,
    contract_id VARCHAR(36),
    account_id VARCHAR(255),
    role_type VARCHAR(30),
    valid_from DATE,
    valid_until DATE,
    REV BIGINT NOT NULL,
    REVTYPE TINYINT,
    PRIMARY KEY (id, REV),
    CONSTRAINT FK_contract_account_role_aud_rev FOREIGN KEY (REV) REFERENCES REVINFO(REV)
);

CREATE INDEX I_contract_account_role_aud_rev ON T_contract_account_role_AUD(REV);
CREATE INDEX I_contract_account_role_aud_id ON T_contract_account_role_AUD(id);
```

Name the migration file `V01.019__createContractAccountRoleTable.sql` (use the next available Flyway version in `src/main/resources/db/migration`).

### Entity

```java
@Entity
@Table(name = "T_contract_account_role")
@Audited
public class ContractAccountRole {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @Column(name = "account_id", length = 255, nullable = false)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_type", length = 30, nullable = false)
    private RoleType roleType;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    public enum RoleType {
        CUSTOMER
    }

    // getters / setters
}
```

### Product Definition: Cross-Tenant API Flag

Add a boolean column to `T_product_definition` that controls whether a product may be purchased through the cross-tenant API:

```sql
ALTER TABLE T_product_definition
ADD COLUMN cross_tenant_api_allowed BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE T_product_definition_AUD
ADD COLUMN cross_tenant_api_allowed BOOLEAN;
```

> Defaulting to `FALSE` ensures existing products are not exposed through the cross-tenant API until the seller explicitly enables them.

Both the tenant-scoped `ProductDefinition` and the non-tenant `NonMultitenancyProductDefinition` must include the same field:

```java
@Column(name = "cross_tenant_api_allowed", nullable = false)
private boolean crossTenantApiAllowed;
```

---

## Product Code Encoding

The existing `T_product_definition.product_code` column is unique. To allow the same raw product code in multiple organisations, the stored value is prefixed with the seller organisation ID: `{orgId}::{productCode}`.

The cross-tenant API expects the caller to supply the full prefixed code in the request body. The service must:

1. **Validate:** look up the exact prefixed code in the non-tenant `ProductDefinition` table to confirm the product exists and that its stored organisation matches the prefix.
2. **On read:** strip the prefix from the stored value before returning it in API responses.

Example:

```
Caller provides: 00000000-0000-0000-0000-000000000000::PROD-001
Stored code:     00000000-0000-0000-0000-000000000000::PROD-001
Returned code:   PROD-001
```

> **Important:** The database always stores prefixed product codes so that the same raw code can exist in different organisations. The standard tenant-scoped product definition API uses raw values in DTOs and prepends the current tenant's orgId before storing. The cross-tenant API receives prefixed values from the caller and must not leak the prefix in responses.

---

## Organisation Resolution

The seller organisation is not taken from the JWT. It is derived from the product codes supplied by the customer.

1. The customer sends one or more **prefixed product codes** in the request body (one per line item).
2. For each prefixed code, the service looks up the exact value in the non-tenant `ProductDefinition` table and confirms that the product exists and that its stored `organisation_id` matches the prefix.
3. The service validates that **all resolved product definitions belong to the same organisation** and that **each product definition is flagged as allowed for the cross-tenant API** (`crossTenantApiAllowed = true`). If either check fails, the request fails with `422 Unprocessable Entity`.
4. The common organisation ID becomes the seller organisation for the new contract, product instances, line items, process instances, and terms links.

The currency for the contract is read from the seller organisation's `Config` record.

---

## Tenant Context and Session Management

The standard tenant-scoped API relies on `OrgIdResolutionFilter` to extract the `orgId` from the authenticated JWT and store it in the request-scoped `CurrentOrgContext`. `JwtOrgResolver` then reads `CurrentOrgContext` whenever Hibernate needs a tenant discriminator to create or join a session.

For the cross-tenant API the JWT `orgId` is the customer's organisation, not the seller's organisation. The seller `orgId` must be determined from the request and placed into `CurrentOrgContext` **before Hibernate creates the tenant-scoped session**.

### Timing Constraint

Hibernate resolves the tenant id when a method annotated with `@Transactional` is entered, using the value currently held in `CurrentOrgContext`. Once a session has been created with a particular tenant id, changing `CurrentOrgContext` does not retroactively change that session's discriminator.

Therefore every write operation in the cross-tenant API must follow this pattern:

1. The JAX-RS resource method is **not** annotated with `@Transactional`.
2. The resource method resolves the seller `orgId` using a non-tenant read:
   - For `POST /api/public/contracts` and `PUT /api/public/contracts/{id}`: resolve the seller `orgId` from the prefixed product codes in the request body.
   - For `POST /api/public/contracts/{id}/offer`, `POST /api/public/contracts/{id}/accept`, and `DELETE /api/public/contracts/{id}/line-items/{lineItemId}`: load the existing `NonMultitenancyContract` by id and read its `organisationId`.
3. The resource method calls `CurrentOrgContext.setOrgId(sellerOrgId)`.
4. Only after the context has been updated does the resource method call the `@Transactional` service that creates or updates tenant-scoped entities.

### Read-Only Operations

The list and single-view endpoints (`GET /api/public/contracts` and `GET /api/public/contracts/{id}`) only touch non-tenant entities (`NonMultitenancyContract`, `NonMultitenancyContractAccountRole`, etc.). Because these entities have no `@TenantId`, the value in `CurrentOrgContext` does not affect the query result. The caller's `sub` claim and the optional `orgId` query parameter are used directly for scoping.

### Avoiding a Premature Session

No JPA session may be opened for the wrong tenant before `CurrentOrgContext` is updated. In particular:

- The resource method must not be `@Transactional`.
- Helper code used to resolve the seller `orgId` must not trigger creation of a tenant-scoped session. Prefer non-tenant entities, raw JDBC, or a dedicated non-tenant persistence context for the lookup.
- If a transactional read is unavoidable, ensure it is fully isolated and only touches non-tenant entities, and that the write service starts a fresh `@Transactional` boundary after the context has been set.

---

## API Endpoints

Base path: `/api/public/contracts`

All endpoints require the authenticated role `abstratium-abstrapact_user`. The customer account ID is read from the JWT `sub` claim; it is **not** supplied by the caller.

| Method | Path | Summary |
|--------|------|---------|
| `POST`   | `/api/public/contracts` | Create a new contract draft from product codes. |
| `GET`    | `/api/public/contracts?orgId={orgId}` | List contracts linked to the caller's account, returning an overview/summary. `orgId` is optional. |
| `GET`    | `/api/public/contracts/{id}` | View a single contract linked to the caller's account, returning full contract details. |
| `PUT`    | `/api/public/contracts/{id}` | Update a contract that is still in `DRAFT` (replace line items and metadata). |
| `DELETE` | `/api/public/contracts/{id}/line-items/{lineItemId}` | Remove a line item from a contract that is still in `DRAFT`. |
| `POST`   | `/api/public/contracts/{id}/offer` | Customer finalises the draft; the contract moves from `DRAFT` to `OFFERED` so the SME can make a formal offer. |
| `POST`   | `/api/public/contracts/{id}/accept` | Move the contract from `OFFERED` to `ACCEPTED`. |

Payment endpoints (`/purchase`, `/pay`) are out of scope and will be added later.

### DTOs

#### `CreateCustomerContractRequest`

```java
public class CreateCustomerContractRequest {
    private String contractReference;
    private String publicNotes;
    private List<CustomerLineItemRequest> lineItems;
    // getters / setters
}
```

#### `CustomerLineItemRequest`

```java
public class CustomerLineItemRequest {
    private String productCode;          // prefixed code including orgId, e.g. orgId::PROD-001
    private Integer displayOrder;
    private List<PartInstanceRequest> partInstances; // root-level part instances
    // getters / setters
}
```

#### `PartInstanceRequest`

Models one concrete part instance, including attribute values and selected child parts. The structure mirrors the part tree so that choices (e.g. processor, warranty) can be expressed.

```java
public class PartInstanceRequest {
    private String partDefinitionId;   // UUID of the PartDefinition
    private List<PartInstanceAttributeRequest> attributeValues;
    private List<PartInstanceRequest> childPartInstances;
    // getters / setters
}
```

`PartInstanceAttributeRequest` can be reused from the existing contracts DTO package.

#### `CustomerContractSummary`

A lightweight, read-only DTO returned by the list endpoint. It contains enough information for the customer to identify and browse their contracts without the full part tree:

```java
public class CustomerContractSummary {
    private String id;
    private String contractReference;
    private String sellerOrganisationId;
    private LocalDate contractDate;
    private String currency;
    private BigDecimal grandTotal;
    private ContractState state;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    // getters / setters
}
```

#### `CustomerContractResponse`

A read-only DTO returned by the single-view endpoint and by create/update responses. It contains the full contract, including line items and their product instance part trees:

```java
public class CustomerContractResponse {
    private String id;
    private String contractReference;
    private String sellerOrganisationId;
    private LocalDate contractDate;
    private String currency;
    private BigDecimal grandTotal;
    private ContractState state;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<CustomerContractLineItemResponse> lineItems;
    // getters / setters
}
```

`CustomerContractLineItemResponse` should expose the line item id, display order, line total, and the full `ProductInstance` / `PartInstance` tree for the line item.

---

## Creating a Contract Draft

### Behaviour

1. Authenticate the caller and extract the JWT `sub` claim as `accountId`.
2. Validate that `lineItems` is non-empty.
3. Resolve each prefixed `productCode` to a non-tenant `ProductDefinition`.
   - If any code cannot be resolved, return `422`.
   - If any resolved product is not flagged as allowed for the cross-tenant API (`crossTenantApiAllowed = false`), return `422`.
4. Validate that **all resolved product definitions belong to the same seller organisation**.
5. Determine the seller `orgId`.
6. For each line item:
   - Instantiate a `ProductInstance` linked to the product definition.
   - Build the `PartInstance` tree from the request, validating cardinality, required attributes, and allowed values against the `PartDefinition` tree.
   - Calculate the line total from the resolved part prices.
7. Create the `Contract`:
   - `state = DRAFT`
   - `currency` from seller `Config`
   - `paymentModel` derived from the product definitions (any `PREPAID` → `PREPAID`, all `POSTPAID` → `POSTPAID`)
   - `grandTotal` as the sum of line totals
8. Create `ContractLineItem` records.
9. Create a `ContractAccountRole` linking the contract to the caller's `accountId` with role `CUSTOMER`.
10. Start the sales process (see below) so that the contract is under process control from the beginning.
11. Return the created contract response.

### Validation Rules

- Product codes must exist and be prefixed with a valid seller organisation id.
- All product codes must resolve to the same organisation.
- Each resolved product definition must be flagged as allowed for the cross-tenant API (`crossTenantApiAllowed = true`).
- Each requested part tree must be a valid instantiation of its product definition:
  - Number of instances must respect `minCardinality` and `maxCardinality`.
  - For each choice group on a parent part, the number of selected child parts from that group must respect the group's `minChoices` and `maxChoices`.
  - Required attributes must be present and non-empty.
  - Attribute names must exist on the corresponding `PartDefinition`.
  - Selected children must be valid child parts according to the definition tree.

---

## Updating a Contract Draft

A contract can only be changed while it is in `DRAFT`. Once it moves to `OFFERED` or beyond, its configuration is frozen.

### `PUT /api/public/contracts/{id}`

Replaces the contract metadata and the full set of line items for a contract in `DRAFT`. The request body reuses `CreateCustomerContractRequest` (or a dedicated `UpdateCustomerContractRequest` with the same shape).

1. Authenticate the caller and extract the JWT `sub` claim as `accountId`.
2. Load the contract through the non-tenant entity manager.
3. Verify the contract is linked to the caller's account and is in `DRAFT`.
4. Validate the request using the same product-code, organisation, and product-configuration rules as for create.
5. Remove the existing line items and their associated product instances.
6. Recreate line items and product instances from the request.
7. Recalculate totals and update the contract.
8. Return the updated contract response.

### `DELETE /api/public/contracts/{id}/line-items/{lineItemId}`

Removes a single line item from a contract in `DRAFT`.

1. Authenticate the caller and extract the JWT `sub` claim as `accountId`.
2. Load the contract through the non-tenant entity manager.
3. Verify the contract is linked to the caller's account and is in `DRAFT`.
4. Remove the line item and its associated product instance.
5. Recalculate the contract total.
6. Return the updated contract response.

---

## State Transitions and the Sales Process

The cross-tenant API does **not** manipulate the contract state directly. It delegates every transition to an application-scoped bean in the `dev.abstratium.sales_process` package (see [DESIGN_OF_SALES_PROCESS.md](./DESIGN_OF_SALES_PROCESS.md)).

The expected bean contract is:

```java
@ApplicationScoped
public class SalesProcessBean {

    public ProcessInstance startSalesProcess(Contract contract, String actorAccountId);

    public void offerContract(String contractId, String actorAccountId);

    public void acceptContract(String contractId, String actorAccountId);

    // payment methods added later
}
```

Each method:

1. Loads the contract through the non-tenant entity manager.
2. Validates the requested transition (e.g. `DRAFT -> OFFERED` only when in `DRAFT`).
3. Updates the contract state.
4. Creates or updates a `ProcessInstance` linked to the contract.
5. Records a `ProcessInstanceStep` with `fromState`, `toState`, `actorUserId`, and `stepTimestamp`.

The endpoint only needs to:

- Call `salesProcessBean.offerContract(contractId, accountId)` for `POST /api/public/contracts/{id}/offer`.
- Call `salesProcessBean.acceptContract(contractId, accountId)` for `POST /api/public/contracts/{id}/accept`.

### DRAFT Semantics

A contract in `DRAFT` can still be changed. Product configuration may be updated, line items may be added or removed, and pricing may be recalculated. The customer has not yet committed to the configuration. While in this state, the draft contract effectively acts as the customer's shopping cart: it can accumulate products and be adjusted until the customer is ready to proceed.

### OFFERED Semantics

`OFFERED` means the draft has been finalised and its configuration is frozen. The SME makes a formal offer to the customer, who can then take time to compare or consider the offer before accepting. This is analogous to an insurance quote that is printed and sent to the customer for review. From this state the customer can accept it (moving to `ACCEPTED`) or the seller can recall it (moving back to `DRAFT`).

### ACCEPTED Semantics

`ACCEPTED` means the customer has accepted the terms and conditions and the offer. The terms and conditions are recorded at the moment of acceptance (see below). Further transitions (`AWAITING_APPROVAL`, `APPROVED`, `AWAITING_PAYMENT`, `RUNNING`) are handled by the sales process.

---

## Terms and Conditions

When a contract is created, the applicable terms and conditions are identified but not yet finalised:

1. **General terms:** read from each line item's `ProductDefinition.termsAndConditionsCode`. Look up the `TermsAndConditions` record in the seller organisation whose `effectiveFrom <= today <= effectiveUntil` (or `effectiveUntil` is null). Store a `ContractTermsLink` with `scope = GENERAL` and `termsVersionAtSigning` set to the matched version.
2. **Special terms:** for each selected `PartDefinition` that has a special terms reference, look up the effective version in the seller organisation and store a `ContractTermsLink` with `scope = SPECIAL_FOR_LINE_ITEM` linked to the line item.

The content is not copied into the contract; only the reference and the version at signing are stored, exactly as described in [DESIGN_OF_CONTRACTS.md](./DESIGN_OF_CONTRACTS.md).

When the customer accepts the offer, the system records that the customer accepted the attached terms and conditions. The acceptance itself is modelled as part of the `ACCEPTED` process step.

---

## Searching Customer Contracts

`GET /api/public/contracts?orgId={orgId}` returns an overview of all contracts linked to the caller's account (`sub` claim) with role `CUSTOMER`. Each result is a `CustomerContractSummary`.

- The query uses the non-tenant `Contract` and `ContractAccountRole` entities.
- If `orgId` is provided, results are further filtered to that seller organisation.
- If `orgId` is omitted, all contracts linked to the account across all seller organisations are returned.
- Results are ordered by `createdAt DESC`.
- The customer can then call `GET /api/public/contracts/{id}` to load the full details of any individual contract.

The caller can only view contracts linked to their own account. The endpoint must never return contracts belonging to other accounts.

---

## Security Considerations

- The endpoints require the role `abstratium-abstrapact_user`.
- The customer account ID is taken from the JWT `sub` claim and is treated as authoritative.
- All list/get operations are scoped to the caller's account via `T_contract_account_role`.
- The seller organisation is derived from product codes, not from the JWT, preventing a customer from writing data into another customer's organisation.
- The cross-tenant non-tenant entities must only be used by code in the `non_multitenancy` package. The standard tenant-scoped API continues to use the original `@TenantId` entities and is unaffected.

---

## Implementation Notes

### Non-Tenant Entity Copies

When duplicating entities, remove only the `@TenantId` annotation and the corresponding `organisationId` setter logic. Keep the same table mappings, column names, relationships, and Envers `@Audited` annotations so that the duplicated entities read and write the same physical rows.

### Product Code Prefix Handling

Introduce a helper class (e.g. `ProductCodeCodec`) in the `non_multitenancy.product` package:

```java
public final class ProductCodeCodec {
    public static String encode(String orgId, String rawProductCode);
    public static String decode(String storedValue); // returns raw product code
    public static String extractOrgId(String storedValue);
}
```

Use this codec consistently in the service layer. Never expose prefixed values in boundary DTOs.

### Process Instance Ownership

`ProcessInstance` and `ProcessInstanceStep` records are written in the seller organisation (their `organisation_id` column is set to the seller `orgId`), even though they are created via the cross-tenant API.

### Choice Group Implementation

**This task must be implemented before the cross-tenant API can validate product configurations.** The choice group model described in [DESIGN_OF_PRODUCTS.md](./DESIGN_OF_PRODUCTS.md) (`T_part_definition_choice_group`, `choice_group_id` on `T_part_definition`, and the corresponding non-tenant entity copy) must exist in the database and the domain model so that the cross-tenant service can enforce `minChoices`/`maxChoices` during product instantiation.

### Testing

- Unit and integration tests must cover organisation-resolution validation, product-code prefix handling, state transitions, account-scoped search, and choice group validation.
- Tests must verify that a customer cannot create a contract with product codes from two different organisations.
- Tests must verify that a customer cannot create a contract with a product whose `crossTenantApiAllowed` flag is false.
- Tests must verify that a customer cannot read another customer's contracts.
- Tests must verify that product configuration violates `minChoices`/`maxChoices` choice group constraints are rejected.

---

## Implementation Task List

### Prerequisites

- [x] **Choice group support** — implement `T_part_definition_choice_group` table, `choice_group_id` FK on `T_part_definition`, and corresponding entities/migrations before any cross-tenant work begins (see `Implementation Notes` above). Done in `V01.022`.

### Database Migrations

- [x] **V01.019** — add `cross_tenant_api_allowed BOOLEAN NOT NULL DEFAULT FALSE` column to `T_product_definition` and `T_product_definition_AUD`. Also widens `product_code` to `VARCHAR(100)`.
- [x] **V01.020** — create `T_contract_account_role` and `T_contract_account_role_AUD` tables with indexes and FK constraints.
- [x] **V01.021** — **Prefix existing product codes** — one-off data migration to prepend `{orgId}::` to all existing `T_product_definition.product_code` values.

### Domain Model

- [x] Add `crossTenantApiAllowed` field to the existing `ProductDefinition` entity.
- [x] Create `ContractAccountRole` entity (in `dev.abstratium.conditions.entity`) with `RoleType.CUSTOMER` enum.
- [x] Create all `NonMultitenancy*` entity copies (remove `@TenantId`; keep table mappings, column names, relationships, and `@Audited`):
  - [x] `product`: `ProductDefinition`, `PartDefinition`, `PartDefinitionChoiceGroup`, `PartAttributeDefinition`, `ProductInstance`, `PartInstance`, `PartInstanceAttribute`
  - [x] `conditions`: `Contract`, `ContractLineItem`, `ContractTermsLink`, `TermsAndConditions`, `Signatory`
  - [x] `process`: `ProcessInstance`, `ProcessInstanceStep`
  - [x] `core`: `Config`

### Utilities

- [x] Implement `ProductCodeCodec` (`encode`, `decode`, `extractOrgId`) in `non_multitenancy.product`.
- [x] Update the existing tenant-scoped product definition create/update logic to store prefixed product codes.

### Services

- [ ] `NonMultitenancyOrganisationResolutionService` — resolve and validate seller `orgId` from prefixed product codes.
- [ ] `NonMultitenancyCustomerProductInstanceService` — instantiate product and part trees, enforce cardinality and choice-group constraints, calculate line totals.
- [ ] `NonMultitenancyCustomerContractService` — orchestrate contract create/update, link `ContractAccountRole`, resolve terms links, delegate state transitions to `SalesProcessBean`.
- [ ] Update `SalesProcessBean` to add `startSalesProcess`, `offerContract`, and `acceptContract` methods operating via non-tenant entities.

### DTOs

- [ ] `NonMultitenancyCreateCustomerContractRequest` / `UpdateCustomerContractRequest`
- [ ] `NonMultitenancyCustomerLineItemRequest`
- [ ] `NonMultitenancyPartInstanceRequest` / `PartInstanceAttributeRequest`
- [ ] `NonMultitenancyCustomerContractSummary`
- [ ] `NonMultitenancyCustomerContractResponse` / `CustomerContractLineItemResponse`

### REST Resource

- [ ] Implement `NonMultitenancyCustomerContractResource` under `/api/public/contracts`:
  - [ ] `POST /` — create draft (resolve seller `orgId`, set `CurrentOrgContext`, call service)
  - [ ] `GET /` — list caller's contracts (non-tenant query, scoped by `sub`)
  - [ ] `GET /{id}` — get single contract (non-tenant query, verify ownership)
  - [ ] `PUT /{id}` — update draft
  - [ ] `DELETE /{id}/line-items/{lineItemId}` — remove line item
  - [ ] `POST /{id}/offer` — delegate to `salesProcessBean.offerContract`
  - [ ] `POST /{id}/accept` — delegate to `salesProcessBean.acceptContract`
- [ ] Ensure resource methods are **not** `@Transactional`; set `CurrentOrgContext` before calling any `@Transactional` service.

### Security

- [ ] Protect all endpoints with role `abstratium-abstrapact_user`.
- [ ] Scope all list/get queries to the caller's `sub` claim via `T_contract_account_role`.

### Tests

- [ ] Unit tests for `ProductCodeCodec` (encode, decode, extractOrgId, malformed input).
- [ ] Integration tests (`@QuarkusTest`) for:
  - [ ] Organisation resolution: mixed seller orgs → 422; `crossTenantApiAllowed = false` → 422.
  - [ ] Contract create: happy path; invalid part cardinality; violated choice-group constraints.
  - [ ] Contract update and line-item deletion (DRAFT only).
  - [ ] State transitions: DRAFT → OFFERED → ACCEPTED; invalid transitions rejected.
  - [ ] Account scoping: customer cannot read another customer's contracts.
  - [ ] `ProductCodeCodec` round-trip in service layer.


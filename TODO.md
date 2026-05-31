# abstrapact TODOs

## Before Each Release

- upgrade all and check security issues in github
- update docs to describe the changes
- audit that all JPQL and SQL is multi-tenant conform
  - prompt:
    - you are a software expert and known all about hibernate multitenancy using the discriminator approach. search for `em\.find\(` in any java file (excluding Test.java files) and replace them with dedicated `findById` methods as described in @entity-manager-usage-constraints.md . search for all native sql in `src/main/java` and tell the user that they exists and suggest updates so that JPA queries are used instead. search for all bulk UPDATE and DELETE operations and tell the user that they exist and suggest updates so that non-bulk operations are used instead.

## Today

- do not allow products or their parts or attributes to be modified or deleted if instances exist for the product definition.
- when creating an instance, it needs to validate the attributes that they exist, the type is corrert and their values match the rules. cardinality must be ok too. the given date must match the product validity dates.
  - the simulator should call a validate backend
    - the price calculation should happen on the backend, and the validate method should return it
  - when the backend stores an instance, it should validate using the same method
  - pricing and discounts are private and should not leak out if the orgId doesn't match - this is a requirement for future PUBLIC interfaces used for b2c and b2b sales

- add discounts to UI and simulator

- add General Terms and Conditions and map product to them

- add contract and states

- add sales process including steps to show the process instances to org users

## Tomorrow

- parts have prices, but if they have kids, then the price can be zero, if the price should come purely from the kids

## Later (not yet necessary for initial release)

- fancy pricing rules for cardinality so that factor isn't 100% for each part instance

- `e2e-tests/pages/TODO.page.ts` needs to be renamed to a feature-specific filename (e.g., `home.page.ts`) once e2e tests are implemented. The file content has been updated, but the name still contains "TODO".
- `scripts/initialisation/fix_other_stuff.py` still contains baseline cleanup logic. Determine whether it should be run to catch any remaining "abstracore" references, or removed now that setup is complete.

- [ ] delete the top of this file that talks about the git hook
- [ ] Update database migration files
- [ ] add a new oauth client to your oauth authorization server like abstrauth
- [ ] ensure all TODOs in the code have been fixed


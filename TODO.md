# abstrapact TODOs

## Before Each Release

- upgrade all and check security issues in github
- update docs to describe the changes
- audit that all JPQL and SQL is multi-tenant conform
  - prompt:
    - you are a software expert and known all about hibernate multitenancy using the discriminator approach. search for `em\.find\(` in any java file (excluding Test.java files) and replace them with dedicated `findById` methods as described in @entity-manager-usage-constraints.md . search for all native sql in `src/main/java` and tell the user that they exists and suggest updates so that JPA queries are used instead. search for all bulk UPDATE and DELETE operations and tell the user that they exist and suggest updates so that non-bulk operations are used instead.

## Today

- do not allow products or their parts or attributes to be modified or deleted if instances exist for the product definition.



## Tomorrow


## Later (not yet necessary for initial release)


- `e2e-tests/pages/TODO.page.ts` needs to be renamed to a feature-specific filename (e.g., `home.page.ts`) once e2e tests are implemented. The file content has been updated, but the name still contains "TODO".
- `scripts/initialisation/fix_other_stuff.py` still contains baseline cleanup logic. Determine whether it should be run to catch any remaining "abstracore" references, or removed now that setup is complete.

- [ ] delete the top of this file that talks about the git hook
- [ ] Update database migration files
- [ ] add a new oauth client to your oauth authorization server like abstrauth
- [ ] ensure all TODOs in the code have been fixed


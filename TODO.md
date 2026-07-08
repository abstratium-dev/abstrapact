# abstrapact TODOs

## Before Each Release

- upgrade all and check security issues in github
- update docs to describe the changes
- audit that all JPQL and SQL is multi-tenant conform
  - prompt:
    - you are a software expert and know all about hibernate multitenancy using the discriminator approach, as well as envers. see @entity-manager-usage-constraints.md and ensure that the information in that file is adhered to in this project. for example: search for all native sql in `src/main/java` and tell the user that they exists and suggest updates so that JPA queries are used instead. for example: search for all bulk UPDATE and DELETE operations and tell the user that they exist and suggest updates so that non-bulk operations are used instead. you are free to address other potential issues related to using envers and multi-tenancy.
- double check that the legal.component.html still conforms in terms of what the site does and which data is collected

## Today

- implement choice groups on `PartDefinition` so that mutually exclusive alternatives can be validated by the non-multitenant API. see `DESIGN_OF_PRODUCTS.md` for the model (`T_part_definition_choice_group`, `choice_group_id`, `minChoices`, `maxChoices`) and create the corresponding Flyway migration and JPA entities.

- delete CreateDraftContractRequest and co, as they aren't needed except for the NonMultitenant API. there are probably a few such DTOs, defo an endpoint and maybe a service class or two

- does the DB do cascade deletes? if so, do only JPA cascade removes and never with remove orphans

- orgId to be taken from productId which is a query parameter or header that overrides the orgId taken from elsewhere in @JwtOrgResolver. 
  - this should depend on the URL! only "public" urls should allow the product to determine the org so that when managing definitions or instances, the user cannot do cross org stuff.

- e2e test for product management.

- default: data is managed for the orgId from your cert
- special case: create an offer using the productId
- that returns an offerId which is used to manage the offer; the offer should be linked to the accountId and when managing that you either have be be a user in the owning orgId or you have to be the user with the accountId that the offer is linked to


- T&Cs should include policy on what we do with their data to be EU GDPR and Swiss DSG conform

- connect product to t&c - or how is that in the design docs?

- implement contract and the process to work thru the states.

- how to deal with overlaps in t&c? return a warning when loading t&c. logic during contract creation is to take the newest version with the given code.

- T @V01.005__createTermsAndConditionsTable.sql has a unique constraint on the code - the code is allowed to be duplicated, but the service needs to check when an update / create / delete is being done, that for a given code, there are no gaps between the conditions. take all the conditions with the same code (within the tenant organisation) and ensure that there are no gaps between the conditions. the "chain" can be open ended, left and right on the time axis. no dates means it is valid from the beginning of time until forever

- contract t&c must state that the contract only comes into effect once the status reaches x and it is no longer in effect once status y is reached.


- gutscheine! or a discount code? or unique discount codes as gutscheine?

- map product to GTC? or contract? whatever, do that.

- add contract and states

- add sales process including steps to show the process instances to org users


- do not allow products or their parts or attributes to be modified or deleted if instances exist for the product definition.

- creating an instance creates a contract in an early status

- add version field and optimistic locking to all entities and always take the version from the client. write a doc about this and add it to abstracore and implement it as standard in the database.md file.

- when creating a contract, it should take the version of the GTC that matches the date of sale - since they have dates.
- GTC may not overlap if the code is the same!
- if two versions exist, take the one that lasts longer or starts later or something

- in order to create an instance, read "build directives" which allow the caller to see what values need to be set via placeholders. alternatively the b2c can simply send a map with paths and values and that can be merged with the build directives to create the instance.

- when creating an instance, it needs to validate the attributes that they exist, the type is corrert and their values match the rules. cardinality must be ok too. the given date must match the product validity dates.
  - the simulator should call a validate backend
    - the price calculation should happen on the backend, and the validate method should return it
  - when the backend stores an instance, it should validate using the same method
  - pricing and discounts are private and should not leak out if the orgId doesn't match - this is a requirement for future PUBLIC interfaces used for b2c and b2b sales

- add discounts to UI and simulator

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

- allow other addresses than localhost to read management/metrics. need to also expose it in docker file?


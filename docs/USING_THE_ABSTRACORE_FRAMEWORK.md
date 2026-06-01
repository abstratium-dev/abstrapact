# Using the Abstracore Framework

## Useful files and when to use them

- `testing.md` describes how to run tests (Java as well as Angular)
- `ENVERS_AUDITING.md` describes what an LLM should watch out for when introducing Hibernate Envers to a downstream project.
- `TESTING_WITH_MULTITENANCY.md` tells an LLM what to do in order to make a service support multi-tenancy so that one instance can support users with their own organisations.
- `styles.md` tells an LLM what it needs to know about CSS style in angular.
- `CONTROLLER_AND_MODEL.md` describes the design patterns used in the angular code.
- `e2e-testing.md` describes how to run tests. Useful to combine this with the `playwright-cli` SKILLS.md file so that an LLM can "see" what is going on in the UI.

- TODO continue describing other files here

## Other tips and tricks

- add logging to the backend and frontend so that an LLM can "see" what is going on.
- building bottom up works very well. generate a design document, then when you are happy, generate flyway scripts, then entities, then service layer with tests, then rest resource with tests, then add the UI on the top.


## Example prompt for creating the database / flyway scripts & JPA entities

```
re-read @DESIGN.md , create the database tables for the entities described there,  in the @migration  folder. see @database.md and create audit tables too. then generate the jpa entities in the @entity  folder. use the @TenantId on the discriminator field organisation_id  on all the entities. Remember to create the necessary indexes and constraints. remember to consider multi-tenancy and stop and ask if it isn't clear which entities need to be partitioned by a discriminator field (see also @HIBERNATE_DISCRIMINATOR_MULTITENANCY.md). use one script per aggregate (ask if you need help). keep audit tables in separate scripts. once that is done, create a new markdown document in the docs folder called @DATABASE.md which shows class diagrams for the entities with cardinality and foreign keys clearly marked in the mermaid diagram, and contains a section with markdown table for each database table, detailing the attributes, their types and what they are for. each section should describe what the table is for. keep the markdown document succinct and free of duplication. 

create no tests for the moment.
```

## Example prompt for creating the service and boundary layers

```
create a new service class in the @service  package for managing all the entities that you created above and that are mentioned in @DATABASE.md. see @entity-manager-usage-constraints.md .

then create a quarkus test for that service.

then create a rest resource in the @boundary  package and a quarkus test to test the boundary works.

run the tests after reading @testing.md .
see TESTING_WITH_MULTITENANCY.md .

Remember to adhere to points made about multi-tenancy.

add quarkus tests on the rest resources which explicitly test cross-tenancy access and guarantee that users of two organisation cannot see each others data.
```

## Example prompt for creating a UI

```
The @product package and sub-packages contain entities, a service and an endpoint for managing products. see @DESIGN.md and @DATABASE.md .

now create a user interface in the @product-definitions  folder. angular components that you add should be encapsulated in their own sub-folders.

adhere to the conventions described in @CONTROLLER_AND_MODEL.md , @DARK_MODE_IMPLEMENTATION.md , @styles.md . add tests and run them after reading @testing.md .
```


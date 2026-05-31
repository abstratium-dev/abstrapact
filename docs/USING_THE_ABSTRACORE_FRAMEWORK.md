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

## Example prompt for creating a UI

    The @product package and sub-packages contain entities, a service and an endpoint for managing products. see @DESIGN_OF_PRODUCTS.md  and @DESIGN_OF_PRODUCT_DEFINITION_SOURCE.md . see @DATABASE.md .

    now create a user interface in the @product-definitions  folder. angular components that you add should be encapsulated in their own sub-folders.

    adhere to the conventions described in @CONTROLLER_AND_MODEL.md , @DARK_MODE_IMPLEMENTATION.md , @styles.md . add tests and run them after reading @testing.md .


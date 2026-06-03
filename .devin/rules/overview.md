---
trigger: always_on
---

The aim of the project is to implement a functional component which includes the backend and frontend.

The project uses the quarkus framework with Java 21. The extensions in use are:

- Hibernate ORM [quarkus-hibernate-orm] Define your persistent model with Hibernate ORM and Jakarta Persistence
- Flyway [quarkus-flyway] Handle your database schema migrations
- JDBC Driver - MySQL [quarkus-jdbc-mysql] Connect to the MySQL database via JDBC
- quinoa for integrating an angular UI into the server
- quarkus-rest-jackson and quarkus-rest for transporting json
- quarkus-oidc for authentication

It MUST be deployed as a native image, so it may only use java constructs that are capable of being built into a native image.

It is EXTREMELY IMPORTANT that this project be tested using unit and integration tests.
The aim is to check that coverage is at 80-90%, in order to find missing tests.
Do not write senseless tests just to increase the coverage.
Coverage can be measured using `mvn verify` and reading coverage results in xml files from the folder `target/jacoco-report`.

The project should be developed in small steps, and in cycles of modifying the code, executing tests, fixing tests or modifying them as required, then going through the cycle again.

Only tests annotated with `@QuarkusTest` are counted towards backend coverage.
These are the primary kind of test for this project.
You can however write plain unit tests, in order to test edge cases.

NEVER disable tests e.g. with the @org.junit.jupiter.api.Disabled annotation.
NEVER delete tests just because you cannot make them work.
Do ask for help if you are going in circles and not getting the tests to pass.

The documentation in markdown files with the extension `.md` need to be checked to ensure that they are up to date. You should use mermaid diagrams where that will help.

Only create markdown documents if they are explicitly requested. Don't create them to simply document what you have just done.
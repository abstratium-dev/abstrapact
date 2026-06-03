---
trigger: glob
description: when working with quarkus (the back end server) or anything to do with java, business logic in the server
globs: src/main/java/**/.*,src/test/java/**/.*
---

The backend is built using the boundary/controller/entity pattern.

- The Boundary: Rest Resources are in the src/main/java/dev/abstratium/.../boundary folder.

- Controllers:  aka "services", are where the main business logic is found. They are in the service package in classes with the postfix "Service". They are responsible for starting transactions.

- Entities: JPA entities are in the "entity" package.

Sub-packages within those three main packages are used to group things that are related functionally.  Other top level packages like "filter", "helper", "util", etc. are also fine for technical cross-cutting concerns.

IT IS REALLY IMPORTANT THAT THE REST RESOURCES DO NOT EXECUTE LOGIC WITHIN MULTIPLE TRANSACTIONS! Because if they did, it's possible that the data would become inconsistent if there was a failure in the second transaction. The solution is that the rest resources make a single call to a service class which starts the transaction and calls all the business logic and database interactions.

DO NOT create a "repository" package or class.

Tests are run using `mvn test` - goals are 80% statement coverage and 70% branch coverage.
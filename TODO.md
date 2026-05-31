# TODO

These TODOs are to be resolved by the developer, NOT THE LLM.

## Before Each Release

- upgrade all and check security issues in github
- update docs to describe the changes
- audit that all JPQL and SQL is multi-tenant conform
  - prompt:
    - you are a software expert and know all about hibernate multitenancy using the discriminator approach, as well as envers. see @entity-manager-usage-constraints.md and ensure that the information in that file is adhered to in this project. for example: search for all native sql in `src/main/java` and tell the user that they exists and suggest updates so that JPA queries are used instead. for example: search for all bulk UPDATE and DELETE operations and tell the user that they exist and suggest updates so that non-bulk operations are used instead. you are free to address other potential issues related to using envers and multi-tenancy.

## Today


## Tomorrow


## Later (not yet necessary for initial release)


# TODOs for Abstracore (to be deleted downstream)

- allow other addresses than localhost to read management/metrics. need to also expose it in docker file?

- add a link to the sbom in readme: e.g. https://github.com/abstratium-dev/abnemo/dependency-graph/sbom. although a copy needs adding to the release! what does the law say?

- add observability (logging, metrics, tracing)
  - see https://quarkus.io/quarkus-workshop-langchain4j/section-1/step-10/#tracing

    # quarkus.otel.exporter.otlp.traces.endpoint=http://localhost:4317
    quarkus.otel.exporter.otlp.traces.headers=authorization=Bearer my_secret 
    quarkus.log.console.format=%d{HH:mm:ss} %-5p traceId=%X{traceId}, parentId=%X{parentId}, spanId=%X{spanId}, sampled=%X{sampled} [%c{2.}] (%t) %s%e%n  
    # enable tracing db requests
    quarkus.datasource.jdbc.telemetry=true

- fix security testing
  - use # Disable OIDC tenant in test mode to allow @TestSecurity to work without 302 redirects
        %test.quarkus.oidc.tenant-enabled=false
    in application.properties and then add     @TestSecurity(user = "testUser", roles = {Roles.USER})
    to any tests that need security 

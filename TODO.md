# TODO

These TODOs are to be resolved by the developer, NOT THE LLM.

## Before Each Release

- upgrade all and check security issues in github
- update docs to describe the changes

## Today


## Tomorrow


## Later (not yet necessary for initial release)


# TODOs for Abstracore (to be deleted downstream)

- sign out timer from abstraccount

- add toggles client

- add "down for maintenance page"
  - actually, nginx needs to use that
  - "going down for maintenance at..." message of the day? it comes out of the toggle

- create build script for maven similar to the one for angular which output the test errors, so the LLM doesn't need to use maven and can optimise token usage. then update the skills and rules to use those scripts

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

# TODO

These TODOs are to be resolved by the developer, NOT THE LLM.

## Before Each Release

- upgrade all and check security issues in github
- update docs to describe the changes

## Today


## Tomorrow


## Later (not yet necessary for initial release)


# TODOs for Abstracore (to be deleted downstream)

- add a banner for non-prod envs with a custom string to warn users that they are not using prod
- add observability (logging, metrics, tracing)
- fix tracking of the url in the auth service, so that if the user clicks or enters a link, they are redirected, regardless of whether they are already signed in, or need to sign in
- allow other addresses than localhost to read management/metrics. need to also expose it in docker file?
- add a link to the sbom in readme: e.g. https://github.com/abstratium-dev/abnemo/dependency-graph/sbom
- observability
  - see https://quarkus.io/quarkus-workshop-langchain4j/section-1/step-10/#tracing

    # quarkus.otel.exporter.otlp.traces.endpoint=http://localhost:4317
    quarkus.otel.exporter.otlp.traces.headers=authorization=Bearer my_secret 
    quarkus.log.console.format=%d{HH:mm:ss} %-5p traceId=%X{traceId}, parentId=%X{parentId}, spanId=%X{spanId}, sampled=%X{sampled} [%c{2.}] (%t) %s%e%n  
    # enable tracing db requests
    quarkus.datasource.jdbc.telemetry=true

# Observability Setup with Grafana, Loki, and OpenTelemetry

This document describes the observability setup for the Abstracore application, including how to view logs, traces, and telemetry data locally using Grafana and Loki.

## Overview

The application uses **OpenTelemetry** for comprehensive observability:

- **Distributed Tracing**: Tracks requests across the application with spans
- **Logging**: Application logs sent to Grafana Loki via OTLP protocol
- **JDBC Telemetry**: SQL statements automatically captured in trace spans
- **Error Enrichment**: Spans automatically enriched with exception details

## Architecture

```
Application (Quarkus)
    ↓ OTLP (OpenTelemetry Protocol)
    ↓
OpenTelemetry Collector
    ↓
    ├─→ Grafana Tempo (Traces)
    ├─→ Grafana Loki (Logs)
    └─→ Grafana Mimir (Metrics)
         ↓
    Grafana (Visualization)
```

## Local Development Setup

For local development, you'll run Grafana, Loki, Tempo, and the OpenTelemetry Collector using Docker Compose.

See the separate document `grafana-docker-setup.md` for detailed Docker Compose configuration.

### Viewing Data in Grafana

#### Exploring Logs (Loki)

1. In Grafana, go to **Explore** (compass icon in left sidebar)
2. Select **Loki** as the data source
3. Use LogQL queries to filter logs:
   ```logql
   # All logs from abstrapact
   {service_name="abstrapact"}
   
   # Logs with specific trace ID
   {service_name="abstrapact"} | traceID="abc123..."
   
   # Error logs only
   {service_name="abstrapact"} | level="ERROR"
   
   # Logs containing specific text
   {service_name="abstrapact"} |= "database"
   ```

#### Viewing Traces (Tempo)

1. In Grafana, go to **Explore**
2. Select **Tempo** as the data source
3. Search for traces:
   - By Trace ID
   - By service name: `abstrapact`
   - By duration or time range

4. **Trace Details** show:
   - Complete request flow through the application
   - Individual spans for each operation
   - SQL queries executed (from JDBC telemetry)
   - Error information (exception type, message, stack trace)
   - Timing information for each span

#### Creating Dashboards

You can create custom dashboards in Grafana to visualize your logs and traces:

1. Go to **Dashboards** in Grafana
2. Click **New Dashboard**
3. Add panels to query Loki (logs) and Tempo (traces)
4. Save your dashboard for future use

### Correlation Between Logs and Traces

Logs are automatically enriched with trace context:
- Each log entry includes `traceId` and `spanId`
- Click on a trace ID in logs to jump to the corresponding trace
- Click on a span in a trace to see associated logs

## Production Setup with External Grafana/Loki

For production deployments, you'll need to run your own Grafana/Loki infrastructure.

### Docker Compose Setup for Testing

Create a `docker-compose.yml` file to run the full stack locally:

```yaml
version: '3.8'

services:
  # OpenTelemetry Collector - receives telemetry and forwards to backends
  otel-collector:
    image: otel/opentelemetry-collector-contrib:latest
    container_name: otel-collector
    command: ["--config=/etc/otel-collector-config.yaml"]
    volumes:
      - ./otel-collector-config.yaml:/etc/otel-collector-config.yaml
    ports:
      - "4317:4317"   # OTLP gRPC receiver
      - "4318:4318"   # OTLP HTTP receiver
      - "8888:8888"   # Prometheus metrics exposed by the collector
      - "8889:8889"   # Prometheus exporter metrics
    networks:
      - observability

  # Grafana Tempo - distributed tracing backend
  tempo:
    image: grafana/tempo:latest
    container_name: tempo
    command: ["-config.file=/etc/tempo.yaml"]
    volumes:
      - ./tempo-config.yaml:/etc/tempo.yaml
      - tempo-data:/tmp/tempo
    ports:
      - "3200:3200"   # Tempo HTTP
      - "4317"        # OTLP gRPC
    networks:
      - observability

  # Grafana Loki - log aggregation system
  loki:
    image: grafana/loki:latest
    container_name: loki
    command: ["-config.file=/etc/loki/local-config.yaml"]
    ports:
      - "3100:3100"   # Loki HTTP
    networks:
      - observability
    volumes:
      - loki-data:/loki

  # Grafana - visualization and dashboards
  grafana:
    image: grafana/grafana:latest
    container_name: grafana
    ports:
      - "3000:3000"
    environment:
      - GF_AUTH_ANONYMOUS_ENABLED=true
      - GF_AUTH_ANONYMOUS_ORG_ROLE=Admin
      - GF_AUTH_DISABLE_LOGIN_FORM=true
    volumes:
      - grafana-data:/var/lib/grafana
      - ./grafana-datasources.yaml:/etc/grafana/provisioning/datasources/datasources.yaml
    networks:
      - observability

networks:
  observability:
    driver: bridge

volumes:
  tempo-data:
  loki-data:
  grafana-data:
```

### OpenTelemetry Collector Configuration

Create `otel-collector-config.yaml`:

```yaml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317
      http:
        endpoint: 0.0.0.0:4318

processors:
  batch:
    timeout: 10s
    send_batch_size: 1024

exporters:
  # Export traces to Tempo
  otlp/tempo:
    endpoint: tempo:4317
    tls:
      insecure: true

  # Export logs to Loki
  loki:
    endpoint: http://loki:3100/loki/api/v1/push
    tls:
      insecure: true

  # Debug exporter for troubleshooting
  logging:
    loglevel: info

service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [batch]
      exporters: [otlp/tempo, logging]
    
    logs:
      receivers: [otlp]
      processors: [batch]
      exporters: [loki, logging]
```

### Tempo Configuration

Create `tempo-config.yaml`:

```yaml
server:
  http_listen_port: 3200

distributor:
  receivers:
    otlp:
      protocols:
        grpc:
          endpoint: 0.0.0.0:4317

storage:
  trace:
    backend: local
    local:
      path: /tmp/tempo/traces

query_frontend:
  search:
    enabled: true
```

### Grafana Data Sources Configuration

Create `grafana-datasources.yaml`:

```yaml
apiVersion: 1

datasources:
  - name: Tempo
    type: tempo
    access: proxy
    url: http://tempo:3200
    uid: tempo
    isDefault: false
    editable: true

  - name: Loki
    type: loki
    access: proxy
    url: http://loki:3100
    uid: loki
    isDefault: true
    editable: true
    jsonData:
      derivedFields:
        - datasourceUid: tempo
          matcherRegex: "traceID=(\\w+)"
          name: TraceID
          url: "$${__value.raw}"
```

### Starting the Stack

1. **Create the configuration files** above in a directory (e.g., `observability/`)

2. **Start the services**:
   ```bash
   cd observability/
   docker-compose up -d
   ```

3. **Configure the application** to use the external collector:
   ```bash
   export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317
   ./mvnw quarkus:dev
   ```

4. **Access Grafana** at http://localhost:3000

5. **Stop the services**:
   ```bash
   docker-compose down
   ```

## Application Configuration

The observability features are configured in `src/main/resources/application.properties`:

### Key Configuration Properties

```properties
# Application name (appears as service.name in telemetry)
quarkus.application.name=abstrapact

# Enable OpenTelemetry logging
quarkus.otel.logs.enabled=true

# Enable JDBC telemetry (SQL statements in spans)
quarkus.datasource.jdbc.telemetry=true

# Production OTLP endpoint (override with environment variable)
%prod.quarkus.otel.exporter.otlp.traces.endpoint=${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4317}
%prod.quarkus.otel.exporter.otlp.logs.endpoint=${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4317}

# Resource attributes (metadata for filtering)
quarkus.otel.resource.attributes=deployment.environment=${DEPLOYMENT_ENV:dev}
```

### Environment Variables for Production

Set these environment variables in production:

```bash
# OTLP collector endpoint
export OTEL_EXPORTER_OTLP_ENDPOINT=http://your-collector:4317

# Deployment environment tag
export DEPLOYMENT_ENV=production

# Optional: Authentication for Grafana Cloud
# export GRAFANA_CLOUD_TOKEN=your-token
```

## What Gets Captured

### 1. Distributed Traces

Every HTTP request creates a trace with spans for:
- HTTP request handling
- Database queries (with SQL statements)
- REST client calls
- Custom operations (if instrumented)

### 2. SQL Statements in Spans

With `quarkus.datasource.jdbc.telemetry=true`, each database query creates a span containing:
- SQL statement text
- Database connection details
- Query execution time
- Parameters (if enabled)
- Errors and exceptions

### 3. Error Information

When an exception occurs, the span is automatically enriched with:
- `exception.type`: The exception class name
- `exception.message`: The error message
- `exception.stacktrace`: Full stack trace
- Span status set to `ERROR`

### 4. Logs with Trace Context

All application logs include:
- `traceId`: Links log to its trace
- `spanId`: Links log to specific span
- `service.name`: Application identifier
- Standard log fields (level, message, timestamp, logger)

## Troubleshooting

### No Traces Appearing

1. **Verify OpenTelemetry is enabled**: Check logs for OpenTelemetry initialization
2. **Check OTLP endpoint**: Ensure the collector is reachable
3. **Verify sampling**: Default is to sample all traces in dev mode
4. **Check for errors**: Look for OTLP export errors in logs

### SQL Statements Not in Spans

1. **Verify JDBC telemetry is enabled**:
   ```properties
   quarkus.datasource.jdbc.telemetry=true
   ```

2. **Check datasource configuration**: Telemetry only works with JDBC datasources

### Logs Not Appearing in Loki

1. **Verify logging is enabled**:
   ```properties
   quarkus.otel.logs.enabled=true
   ```

2. **Check OTLP logs endpoint**: Ensure it points to the collector
3. **Verify log level**: Logs below configured level won't be exported

## Performance Considerations

### Development
- Minimal runtime overhead from OpenTelemetry instrumentation
- All traces are sampled (100%) by default in dev mode
- Docker Compose stack uses local resources

### Production
- Consider **sampling** to reduce volume:
  ```properties
  %prod.quarkus.otel.traces.sampler=traceidratio
  %prod.quarkus.otel.traces.sampler.arg=0.1  # Sample 10% of traces
  ```
- Use **batch processing** in the OTLP collector
- Monitor collector resource usage
- Consider **tail-based sampling** for more intelligent trace selection

## Security Considerations

### Authentication

For production Grafana Cloud or secured endpoints:

```properties
%prod.quarkus.otel.exporter.otlp.traces.headers=authorization=Bearer ${GRAFANA_CLOUD_TOKEN}
%prod.quarkus.otel.exporter.otlp.logs.headers=authorization=Bearer ${GRAFANA_CLOUD_TOKEN}
```

### Sensitive Data

- **SQL parameters**: Not captured by default (only SQL text)
- **HTTP headers**: Sensitive headers can be excluded via configuration
- **Custom spans**: Be careful not to log sensitive data in span attributes

## References

- [Quarkus OpenTelemetry Tracing Guide](https://quarkus.io/guides/opentelemetry-tracing)
- [Quarkus OpenTelemetry Logging Guide](https://quarkus.io/guides/opentelemetry-logging)
- [OpenTelemetry Documentation](https://opentelemetry.io/docs/)
- [Grafana Loki Documentation](https://grafana.com/docs/loki/latest/)
- [Grafana Tempo Documentation](https://grafana.com/docs/tempo/latest/)
- [OpenTelemetry Collector Documentation](https://opentelemetry.io/docs/collector/)

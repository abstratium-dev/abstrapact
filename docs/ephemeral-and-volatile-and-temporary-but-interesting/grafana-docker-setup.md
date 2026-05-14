# Grafana Observability Stack - Docker Compose Setup

This document provides a complete Docker Compose setup for running the Grafana observability stack locally, including Grafana, Loki (logs), Tempo (traces), and the OpenTelemetry Collector.

**All services use open-source, free Grafana components.**

## Overview

The stack consists of:

- **Grafana** - Visualization and dashboards
- **Grafana Loki** - Log aggregation system
- **Grafana Tempo** - Distributed tracing backend
- **OpenTelemetry Collector** - Receives telemetry from applications and forwards to backends

All services are configured with:
- Network: `abstratium`
- Container names prefixed with `abstratium-`
- Persistent volumes for data backup
- Environment variables for configuration

## Quick Start

1. **Create a directory** for the observability stack:
   ```bash
   mkdir -p ~/observability-stack
   cd ~/observability-stack
   ```

2. **Create the configuration files** (see sections below)

3. **Start the stack**:
   ```bash
   docker-compose up -d
   ```

4. **Access Grafana** at http://localhost:3000
   - Default credentials: `admin` / `admin` (change on first login)

5. **Configure your application** to send telemetry:
   ```bash
   export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317
   ```

6. **Stop the stack**:
   ```bash
   docker-compose down
   ```

7. **Backup data** (volumes are in `./data/`):
   ```bash
   tar -czf observability-backup-$(date +%Y%m%d).tar.gz data/
   ```

## Docker Compose Configuration

Create a file named `docker-compose.yml`:

```yaml
version: '3.8'

services:
  # OpenTelemetry Collector - receives telemetry and forwards to backends
  abstratium-otel-collector:
    image: otel/opentelemetry-collector-contrib:${OTEL_COLLECTOR_VERSION:-0.96.0}
    container_name: abstratium-otel-collector
    command: ["--config=/etc/otel-collector-config.yaml"]
    volumes:
      - ./otel-collector-config.yaml:/etc/otel-collector-config.yaml:ro
    ports:
      - "${OTEL_GRPC_PORT:-4317}:4317"   # OTLP gRPC receiver
      - "${OTEL_HTTP_PORT:-4318}:4318"   # OTLP HTTP receiver
      - "${OTEL_METRICS_PORT:-8888}:8888"   # Prometheus metrics exposed by the collector
    networks:
      - abstratium
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "wget", "--spider", "-q", "http://localhost:13133/"]
      interval: 30s
      timeout: 10s
      retries: 3

  # Grafana Tempo - distributed tracing backend
  abstratium-tempo:
    image: grafana/tempo:${TEMPO_VERSION:-latest}
    container_name: abstratium-tempo
    command: ["-config.file=/etc/tempo.yaml"]
    volumes:
      - ./tempo-config.yaml:/etc/tempo.yaml:ro
      - ./data/tempo:/var/tempo
    ports:
      - "${TEMPO_HTTP_PORT:-3200}:3200"   # Tempo HTTP API
    networks:
      - abstratium
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "wget", "--spider", "-q", "http://localhost:3200/ready"]
      interval: 30s
      timeout: 10s
      retries: 3

  # Grafana Loki - log aggregation system
  abstratium-loki:
    image: grafana/loki:${LOKI_VERSION:-latest}
    container_name: abstratium-loki
    command: ["-config.file=/etc/loki/config.yaml"]
    volumes:
      - ./loki-config.yaml:/etc/loki/config.yaml:ro
      - ./data/loki:/loki
    ports:
      - "${LOKI_HTTP_PORT:-3100}:3100"   # Loki HTTP API
    networks:
      - abstratium
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "wget", "--spider", "-q", "http://localhost:3100/ready"]
      interval: 30s
      timeout: 10s
      retries: 3

  # Grafana - visualization and dashboards
  abstratium-grafana:
    image: grafana/grafana-oss:${GRAFANA_VERSION:-latest}
    container_name: abstratium-grafana
    ports:
      - "${GRAFANA_HTTP_PORT:-3000}:3000"
    environment:
      # Security settings
      - GF_SECURITY_ADMIN_USER=${GRAFANA_ADMIN_USER:-admin}
      - GF_SECURITY_ADMIN_PASSWORD=${GRAFANA_ADMIN_PASSWORD:-admin}
      - GF_SECURITY_ALLOW_EMBEDDING=${GRAFANA_ALLOW_EMBEDDING:-false}
      
      # Server settings
      - GF_SERVER_ROOT_URL=${GRAFANA_ROOT_URL:-http://localhost:3000}
      - GF_SERVER_DOMAIN=${GRAFANA_DOMAIN:-localhost}
      
      # Analytics (disable for privacy)
      - GF_ANALYTICS_REPORTING_ENABLED=${GRAFANA_ANALYTICS_ENABLED:-false}
      - GF_ANALYTICS_CHECK_FOR_UPDATES=${GRAFANA_CHECK_UPDATES:-false}
      
      # Auth settings (optional - enable anonymous access for development)
      - GF_AUTH_ANONYMOUS_ENABLED=${GRAFANA_ANONYMOUS_ENABLED:-false}
      - GF_AUTH_ANONYMOUS_ORG_ROLE=${GRAFANA_ANONYMOUS_ROLE:-Viewer}
      
      # Paths
      - GF_PATHS_PROVISIONING=/etc/grafana/provisioning
      - GF_PATHS_DATA=/var/lib/grafana
    volumes:
      - ./data/grafana:/var/lib/grafana
      - ./grafana-datasources.yaml:/etc/grafana/provisioning/datasources/datasources.yaml:ro
      - ./grafana-dashboards.yaml:/etc/grafana/provisioning/dashboards/dashboards.yaml:ro
      - ./dashboards:/etc/grafana/dashboards:ro
    networks:
      - abstratium
    restart: unless-stopped
    depends_on:
      - abstratium-loki
      - abstratium-tempo
    healthcheck:
      test: ["CMD", "wget", "--spider", "-q", "http://localhost:3000/api/health"]
      interval: 30s
      timeout: 10s
      retries: 3

networks:
  abstratium:
    name: abstratium
    driver: bridge

# Note: Using bind mounts to ./data/ directory for easy backup
# All data is stored in ./data/{grafana,loki,tempo}
```

## OpenTelemetry Collector Configuration

Create a file named `otel-collector-config.yaml`:

```yaml
# OpenTelemetry Collector Configuration
# Receives telemetry from applications and forwards to Loki (logs) and Tempo (traces)
# Documentation: https://opentelemetry.io/docs/collector/configuration/

receivers:
  # OTLP receiver - accepts logs and traces in OpenTelemetry format
  # Documentation: https://github.com/open-telemetry/opentelemetry-collector/tree/main/receiver/otlpreceiver
  otlp:
    protocols:
      # gRPC endpoint (default port 4317)
      grpc:
        endpoint: 0.0.0.0:4317
      # HTTP endpoint (default port 4318)
      http:
        endpoint: 0.0.0.0:4318
        cors:
          allowed_origins:
            - "http://localhost:*"
            - "http://127.0.0.1:*"

processors:
  # Batch processor - batches telemetry data before sending to reduce network overhead
  # Documentation: https://github.com/open-telemetry/opentelemetry-collector/tree/main/processor/batchprocessor
  batch:
    timeout: 10s
    send_batch_size: 1024
    send_batch_max_size: 2048

  # Memory limiter - prevents OOM by dropping data when memory usage is high
  # Documentation: https://github.com/open-telemetry/opentelemetry-collector/tree/main/processor/memorylimiterprocessor
  memory_limiter:
    check_interval: 1s
    limit_mib: 512
    spike_limit_mib: 128

  # Resource processor - adds/modifies resource attributes
  # Useful for adding environment, cluster, or deployment information
  resource:
    attributes:
      - key: service.namespace
        value: abstratium
        action: upsert

exporters:
  # Export traces to Tempo via OTLP
  # Documentation: https://github.com/open-telemetry/opentelemetry-collector/tree/main/exporter/otlpexporter
  otlp/tempo:
    endpoint: abstratium-tempo:4317
    tls:
      insecure: true
    retry_on_failure:
      enabled: true
      initial_interval: 5s
      max_interval: 30s
      max_elapsed_time: 300s

  # Export logs to Loki
  # Documentation: https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/exporter/lokiexporter
  loki:
    endpoint: http://abstratium-loki:3100/loki/api/v1/push
    tls:
      insecure: true
    retry_on_failure:
      enabled: true
      initial_interval: 5s
      max_interval: 30s
      max_elapsed_time: 300s

  # Debug exporter - logs telemetry to collector's stdout (useful for troubleshooting)
  # Disable in production by removing from pipelines
  logging:
    loglevel: info
    sampling_initial: 5
    sampling_thereafter: 200

service:
  # Telemetry configuration for the collector itself
  telemetry:
    logs:
      level: info
    metrics:
      address: 0.0.0.0:8888

  # Processing pipelines
  pipelines:
    # Traces pipeline: OTLP receiver -> processors -> Tempo exporter
    traces:
      receivers: [otlp]
      processors: [memory_limiter, resource, batch]
      exporters: [otlp/tempo, logging]
    
    # Logs pipeline: OTLP receiver -> processors -> Loki exporter
    logs:
      receivers: [otlp]
      processors: [memory_limiter, resource, batch]
      exporters: [loki, logging]
```

## Grafana Tempo Configuration

Create a file named `tempo-config.yaml`:

```yaml
# Grafana Tempo Configuration
# Documentation: https://grafana.com/docs/tempo/latest/configuration/

server:
  http_listen_port: 3200
  log_level: info

# Distributor configuration - receives traces from collectors
distributor:
  receivers:
    otlp:
      protocols:
        grpc:
          endpoint: 0.0.0.0:4317
        http:
          endpoint: 0.0.0.0:4318

# Ingester configuration - writes traces to storage
ingester:
  max_block_duration: 5m

# Compactor configuration - compacts and cleans up old data
compactor:
  compaction:
    block_retention: 168h  # 7 days

# Storage configuration - local filesystem storage
storage:
  trace:
    backend: local
    local:
      path: /var/tempo/traces
    wal:
      path: /var/tempo/wal
    pool:
      max_workers: 100
      queue_depth: 10000

# Query frontend configuration - enables search and filtering
query_frontend:
  search:
    enabled: true
    default_result_limit: 20
    max_result_limit: 100
  trace_by_id:
    query_timeout: 10s

# Metrics generator (optional) - generates metrics from traces
# Uncomment to enable RED metrics (Rate, Errors, Duration)
# metrics_generator:
#   registry:
#     external_labels:
#       source: tempo
#   storage:
#     path: /var/tempo/generator/wal
#   traces_storage:
#     path: /var/tempo/generator/traces

# Overrides configuration - per-tenant limits
overrides:
  defaults:
    max_traces_per_user: 10000
    max_bytes_per_trace: 5000000  # 5MB
```

## Grafana Loki Configuration

Create a file named `loki-config.yaml`:

```yaml
# Grafana Loki Configuration
# Documentation: https://grafana.com/docs/loki/latest/configuration/

auth_enabled: false

server:
  http_listen_port: 3100
  grpc_listen_port: 9096
  log_level: info

# Common configuration shared across components
common:
  path_prefix: /loki
  storage:
    filesystem:
      chunks_directory: /loki/chunks
      rules_directory: /loki/rules
  replication_factor: 1
  ring:
    kvstore:
      store: inmemory

# Schema configuration - defines how logs are stored
schema_config:
  configs:
    - from: 2024-01-01
      store: tsdb
      object_store: filesystem
      schema: v13
      index:
        prefix: index_
        period: 24h

# Ingester configuration - writes logs to storage
ingester:
  chunk_idle_period: 5m
  chunk_retain_period: 30s
  max_chunk_age: 1h
  chunk_encoding: snappy
  wal:
    enabled: true
    dir: /loki/wal

# Limits configuration - prevents abuse and OOM
limits_config:
  retention_period: 168h  # 7 days
  enforce_metric_name: false
  reject_old_samples: true
  reject_old_samples_max_age: 168h
  ingestion_rate_mb: 10
  ingestion_burst_size_mb: 20
  per_stream_rate_limit: 5MB
  per_stream_rate_limit_burst: 15MB
  max_entries_limit_per_query: 5000
  max_cache_freshness_per_query: 10m

# Compactor configuration - compacts and cleans up old data
compactor:
  working_directory: /loki/compactor
  shared_store: filesystem
  compaction_interval: 10m
  retention_enabled: true
  retention_delete_delay: 2h
  retention_delete_worker_count: 150

# Query range configuration - optimizes query performance
query_range:
  align_queries_with_step: true
  max_retries: 5
  cache_results: true
  results_cache:
    cache:
      embedded_cache:
        enabled: true
        max_size_mb: 100

# Ruler configuration (optional) - enables alerting
# Uncomment to enable alerting rules
# ruler:
#   storage:
#     type: local
#     local:
#       directory: /loki/rules
#   rule_path: /loki/rules-temp
#   alertmanager_url: http://alertmanager:9093
#   ring:
#     kvstore:
#       store: inmemory
#   enable_api: true

# Table manager configuration - manages index tables
table_manager:
  retention_deletes_enabled: true
  retention_period: 168h  # 7 days
```

## Grafana Data Sources Configuration

Create a file named `grafana-datasources.yaml`:

```yaml
# Grafana Data Sources Provisioning
# Documentation: https://grafana.com/docs/grafana/latest/administration/provisioning/#data-sources

apiVersion: 1

datasources:
  # Tempo data source for distributed tracing
  - name: Tempo
    type: tempo
    access: proxy
    url: http://abstratium-tempo:3200
    uid: tempo
    isDefault: false
    editable: true
    jsonData:
      httpMethod: GET
      tracesToLogs:
        datasourceUid: loki
        tags: ['job', 'instance', 'pod', 'namespace']
        mappedTags: [{ key: 'service.name', value: 'service' }]
        mapTagNamesEnabled: true
        spanStartTimeShift: '-1h'
        spanEndTimeShift: '1h'
        filterByTraceID: true
        filterBySpanID: false
      serviceMap:
        datasourceUid: prometheus
      search:
        hide: false
      nodeGraph:
        enabled: true
      lokiSearch:
        datasourceUid: loki

  # Loki data source for log aggregation
  - name: Loki
    type: loki
    access: proxy
    url: http://abstratium-loki:3100
    uid: loki
    isDefault: true
    editable: true
    jsonData:
      maxLines: 1000
      derivedFields:
        # Link logs to traces via traceID
        - datasourceUid: tempo
          matcherRegex: "traceID=(\\w+)"
          name: TraceID
          url: "$${__value.raw}"
        # Alternative trace ID formats
        - datasourceUid: tempo
          matcherRegex: "trace_id=(\\w+)"
          name: trace_id
          url: "$${__value.raw}"
```

## Grafana Dashboards Provisioning

Create a file named `grafana-dashboards.yaml`:

```yaml
# Grafana Dashboards Provisioning
# Documentation: https://grafana.com/docs/grafana/latest/administration/provisioning/#dashboards

apiVersion: 1

providers:
  - name: 'Abstratium Dashboards'
    orgId: 1
    folder: 'Abstratium'
    type: file
    disableDeletion: false
    updateIntervalSeconds: 10
    allowUiUpdates: true
    options:
      path: /etc/grafana/dashboards
      foldersFromFilesStructure: true
```

## Environment Variables

Create a file named `.env` to customize the configuration:

```bash
# OpenTelemetry Collector
OTEL_COLLECTOR_VERSION=0.96.0
OTEL_GRPC_PORT=4317
OTEL_HTTP_PORT=4318
OTEL_METRICS_PORT=8888

# Grafana Tempo
TEMPO_VERSION=latest
TEMPO_HTTP_PORT=3200

# Grafana Loki
LOKI_VERSION=latest
LOKI_HTTP_PORT=3100

# Grafana
GRAFANA_VERSION=latest
GRAFANA_HTTP_PORT=3000
GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=admin
GRAFANA_ALLOW_EMBEDDING=false
GRAFANA_ROOT_URL=http://localhost:3000
GRAFANA_DOMAIN=localhost
GRAFANA_ANALYTICS_ENABLED=false
GRAFANA_CHECK_UPDATES=false

# Optional: Enable anonymous access for development
GRAFANA_ANONYMOUS_ENABLED=false
GRAFANA_ANONYMOUS_ROLE=Viewer
```

## Directory Structure

After creating all files, your directory should look like this:

```
observability-stack/
├── docker-compose.yml
├── .env
├── otel-collector-config.yaml
├── tempo-config.yaml
├── loki-config.yaml
├── grafana-datasources.yaml
├── grafana-dashboards.yaml
├── dashboards/                    # Optional: custom dashboard JSON files
│   └── (your-dashboard.json)
└── data/                          # Created by Docker, contains persistent data
    ├── grafana/                   # Grafana data (dashboards, users, etc.)
    ├── loki/                      # Loki data (logs, indexes)
    └── tempo/                     # Tempo data (traces)
```

## Usage

### Starting the Stack

```bash
# Start all services in background
docker-compose up -d

# View logs
docker-compose logs -f

# View logs for specific service
docker-compose logs -f abstratium-grafana
```

### Stopping the Stack

```bash
# Stop all services (keeps data)
docker-compose stop

# Stop and remove containers (keeps data in ./data/)
docker-compose down

# Stop, remove containers, and delete data (CAUTION!)
docker-compose down -v
rm -rf data/
```

### Accessing Services

- **Grafana UI**: http://localhost:3000
- **Loki API**: http://localhost:3100
  - Ready check: http://localhost:3100/ready
  - Metrics: http://localhost:3100/metrics
- **Tempo API**: http://localhost:3200
  - Ready check: http://localhost:3200/ready
  - Metrics: http://localhost:3200/metrics
- **OpenTelemetry Collector**: 
  - OTLP gRPC: localhost:4317
  - OTLP HTTP: localhost:4318
  - Metrics: http://localhost:8888/metrics

### Configuring Your Application

Set the OTLP endpoint environment variable:

```bash
# For gRPC (default)
export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317

# Or in application.properties
%dev.quarkus.otel.exporter.otlp.traces.endpoint=http://localhost:4317
%dev.quarkus.otel.exporter.otlp.logs.endpoint=http://localhost:4317
```

### Viewing Logs in Grafana

1. Open Grafana at http://localhost:3000
2. Go to **Explore** (compass icon)
3. Select **Loki** data source
4. Use LogQL queries:
   ```logql
   # All logs
   {service_name="abstrapact"}
   
   # Error logs only
   {service_name="abstrapact"} | level="ERROR"
   
   # Logs with specific trace ID
   {service_name="abstrapact"} | traceID="abc123..."
   ```

### Viewing Traces in Grafana

1. Go to **Explore**
2. Select **Tempo** data source
3. Search by:
   - Service name: `abstrapact`
   - Trace ID
   - Duration
   - Tags

### Creating Dashboards

1. Go to **Dashboards** → **New Dashboard**
2. Add panels with queries:
   - **Loki panel**: Query logs with LogQL
   - **Tempo panel**: Query traces
3. Save dashboard
4. Export as JSON and save to `dashboards/` directory for persistence

## Data Backup

All data is stored in `./data/` directory with bind mounts for easy backup:

```bash
# Backup all data
tar -czf observability-backup-$(date +%Y%m%d).tar.gz data/

# Backup specific service
tar -czf grafana-backup-$(date +%Y%m%d).tar.gz data/grafana/

# Restore from backup
tar -xzf observability-backup-20260122.tar.gz
```

## Monitoring the Stack

### Check Service Health

```bash
# Check all services
docker-compose ps

# Check specific service health
curl http://localhost:3100/ready  # Loki
curl http://localhost:3200/ready  # Tempo
curl http://localhost:3000/api/health  # Grafana
```

### View Service Logs

```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f abstratium-loki
docker-compose logs -f abstratium-tempo
docker-compose logs -f abstratium-otel-collector
```

### Resource Usage

```bash
# View resource usage
docker stats abstratium-grafana abstratium-loki abstratium-tempo abstratium-otel-collector
```

## Troubleshooting

### Services Not Starting

1. **Check Docker is running**:
   ```bash
   docker ps
   ```

2. **Check port conflicts**:
   ```bash
   # Check if ports are already in use
   netstat -tuln | grep -E '3000|3100|3200|4317|4318'
   ```

3. **View service logs**:
   ```bash
   docker-compose logs abstratium-loki
   ```

### No Logs Appearing in Loki

1. **Verify Loki is healthy**:
   ```bash
   curl http://localhost:3100/ready
   ```

2. **Check OpenTelemetry Collector logs**:
   ```bash
   docker-compose logs abstratium-otel-collector
   ```

3. **Verify application is sending logs**:
   - Check `quarkus.otel.logs.enabled=true`
   - Check OTLP endpoint is correct

### No Traces Appearing in Tempo

1. **Verify Tempo is healthy**:
   ```bash
   curl http://localhost:3200/ready
   ```

2. **Check OpenTelemetry Collector logs**:
   ```bash
   docker-compose logs abstratium-otel-collector
   ```

3. **Verify application is sending traces**:
   - Check OpenTelemetry is enabled
   - Check OTLP endpoint is correct

### Grafana Can't Connect to Data Sources

1. **Verify services are on same network**:
   ```bash
   docker network inspect abstratium
   ```

2. **Test connectivity from Grafana container**:
   ```bash
   docker exec abstratium-grafana wget -O- http://abstratium-loki:3100/ready
   docker exec abstratium-grafana wget -O- http://abstratium-tempo:3200/ready
   ```

### Disk Space Issues

1. **Check data directory size**:
   ```bash
   du -sh data/*
   ```

2. **Reduce retention period** in `loki-config.yaml` and `tempo-config.yaml`:
   ```yaml
   # Loki
   limits_config:
     retention_period: 72h  # Reduce from 168h (7 days) to 72h (3 days)
   
   # Tempo
   compactor:
     compaction:
       block_retention: 72h  # Reduce from 168h to 72h
   ```

3. **Restart services** to apply changes:
   ```bash
   docker-compose restart abstratium-loki abstratium-tempo
   ```

## Performance Tuning

### For Development (Low Resource Usage)

Reduce retention and limits in configuration files:

**loki-config.yaml**:
```yaml
limits_config:
  retention_period: 24h  # 1 day instead of 7
  ingestion_rate_mb: 5
  ingestion_burst_size_mb: 10
```

**tempo-config.yaml**:
```yaml
compactor:
  compaction:
    block_retention: 24h  # 1 day instead of 7
```

### For Production (High Throughput)

Increase limits and add more resources:

**otel-collector-config.yaml**:
```yaml
processors:
  batch:
    timeout: 5s
    send_batch_size: 2048
    send_batch_max_size: 4096
  memory_limiter:
    limit_mib: 2048
    spike_limit_mib: 512
```

Add resource limits to `docker-compose.yml`:
```yaml
services:
  abstratium-loki:
    deploy:
      resources:
        limits:
          cpus: '2'
          memory: 2G
        reservations:
          cpus: '1'
          memory: 1G
```

## Security Considerations

### Production Checklist

1. **Change default Grafana password**:
   - Set `GRAFANA_ADMIN_PASSWORD` in `.env`
   - Or change after first login

2. **Disable anonymous access**:
   ```bash
   GRAFANA_ANONYMOUS_ENABLED=false
   ```

3. **Enable HTTPS** (requires reverse proxy like Nginx or Traefik)

4. **Restrict network access**:
   - Don't expose ports publicly
   - Use firewall rules
   - Consider VPN for remote access

5. **Regular backups**:
   ```bash
   # Add to crontab
   0 2 * * * cd ~/observability-stack && tar -czf backup-$(date +\%Y\%m\%d).tar.gz data/
   ```

6. **Monitor disk usage**:
   - Set up alerts for low disk space
   - Adjust retention periods as needed

## Documentation References

### Official Documentation

- **Grafana**: https://grafana.com/docs/grafana/latest/
- **Grafana Loki**: https://grafana.com/docs/loki/latest/
- **Grafana Tempo**: https://grafana.com/docs/tempo/latest/
- **OpenTelemetry Collector**: https://opentelemetry.io/docs/collector/
- **Docker Compose**: https://docs.docker.com/compose/

### Specific Guides

- **Loki Docker Installation**: https://grafana.com/docs/loki/latest/setup/install/docker/
- **Tempo Docker Compose**: https://grafana.com/docs/tempo/latest/set-up-for-tracing/setup-tempo/deploy/locally/docker-compose/
- **OpenTelemetry with Loki**: https://grafana.com/docs/loki/latest/send-data/otel/
- **Grafana Provisioning**: https://grafana.com/docs/grafana/latest/administration/provisioning/

### Query Languages

- **LogQL (Loki)**: https://grafana.com/docs/loki/latest/query/
- **TraceQL (Tempo)**: https://grafana.com/docs/tempo/latest/traceql/

## Support

For issues with:
- **Grafana services**: https://community.grafana.com/
- **OpenTelemetry**: https://github.com/open-telemetry/opentelemetry-collector/issues
- **Docker Compose**: https://github.com/docker/compose/issues

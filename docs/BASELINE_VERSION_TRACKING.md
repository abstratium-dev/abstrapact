# Baseline Version Tracking

## Overview

The Abstracore baseline includes an automatic version tracking system that allows you to identify which version of the baseline is deployed in each application. This is critical for managing updates across multiple applications that share the same baseline.

## How It Works

### Build Timestamp

Every commit to the baseline automatically updates a build timestamp in `src/main/java/dev/abstratium/core/BuildInfo.java`. This timestamp is:

- **Format**: ISO-8601 with second granularity (e.g., `2026-01-11T11:52:10Z`)
- **Timezone**: Always UTC
- **Updated**: Automatically by the pre-commit hook on every commit
- **Exposed**: Via public API endpoints at runtime

### Pre-Commit Hook

The `.git/hooks/pre-commit` script automatically:

1. Generates the current UTC timestamp
2. Updates the `BUILD_TIMESTAMP` constant in `BuildInfo.java`
3. Stages the updated file for inclusion in the commit

This ensures every commit has a unique, trackable timestamp.

## Accessing the Baseline Version

### Public Config Endpoint

The baseline timestamp is exposed at the public config endpoint, which requires no authentication:

```bash
curl http://localhost:8088/public/config
```

Response:
```json
{
  "logLevel": "INFO",
  "baselineBuildTimestamp": "2026-01-11T11:52:10Z"
}
```

### Management Info Endpoint

The timestamp is also available through the Quarkus management interface:

```bash
curl http://localhost:8088/q/info
```

Response:
```json
{
  "config": {
    "buildVersion": "1.0.0-SNAPSHOT",
    "baselineBuildTimestamp": "2026-01-11T11:52:10Z"
  },
  "os": { ... },
  "java": { ... }
}
```

## Syncing the Baseline

When you sync the latest baseline changes into your project using:

```bash
./scripts/sync-base.sh
```

The script will:

1. Fetch the latest changes from the upstream Abstracore repository
2. Merge them into your project (with `--no-commit` for review)
3. When you commit the merge, the pre-commit hook updates the timestamp

This means your application will automatically track to the latest baseline version after the merge is committed.

## Use Cases

### Identifying Outdated Applications

Query the `/public/config` endpoint on all your deployed applications to see which ones are running older baseline versions:

```bash
# Check production app
curl https://app1.example.com/public/config | jq .baselineBuildTimestamp

# Check staging app
curl https://app2.example.com/public/config | jq .baselineBuildTimestamp
```

Compare the timestamps to identify which applications need baseline updates.

### Monitoring Dashboard

Integrate the baseline timestamp into your monitoring dashboard to track baseline versions across your application fleet.

### Incident Response

When a baseline bug is discovered, quickly identify which applications are affected by checking their baseline timestamps against the commit history.

## Testing

The baseline version tracking is covered by automated tests:

```bash
mvn test -Dtest=ConfigResourceTest#testConfigEndpointExposesBaselineBuildTimestamp
```

This test verifies:
- The timestamp is exposed in the config endpoint
- The timestamp follows ISO-8601 format
- The timestamp is not null

## Implementation Details

### Files Involved

- **`src/main/java/dev/abstratium/core/BuildInfo.java`**: Contains the `BUILD_TIMESTAMP` constant
- **`src/main/java/dev/abstratium/core/boundary/publik/ConfigResource.java`**: Exposes timestamp via `/public/config`
- **`src/main/java/dev/abstratium/core/boundary/ConfigInfoContributor.java`**: Exposes timestamp via `/q/info`
- **`.git/hooks/pre-commit`**: Updates the timestamp on every commit

### Native Image Compatibility

The `BuildInfo` class is a simple Java class with a public static final String constant. This is fully compatible with GraalVM native image compilation, as the constant is inlined at compile time.

## Troubleshooting

### Hook Not Running

If the timestamp isn't being updated on commits:

1. Check the hook is executable: `ls -la .git/hooks/pre-commit`
2. Make it executable: `chmod +x .git/hooks/pre-commit`
3. Verify it runs: `.git/hooks/pre-commit`

### Timestamp Not Exposed

If the timestamp isn't appearing in the API response:

1. Verify the `BuildInfo` class exists and compiles
2. Check the test: `mvn test -Dtest=ConfigResourceTest`
3. Restart the application to pick up changes

### Merge Conflicts

When syncing the baseline, if there are conflicts in `BuildInfo.java`:

1. Accept the upstream version (it will have the latest timestamp)
2. Complete the merge
3. The pre-commit hook will update it to the current time when you commit

# User Manual

## Using abstrapact

abstrapact is an application for contract management, sales processes, and products. It provides a web-based UI built with Angular, backed by a Quarkus REST API, and uses OIDC authentication via the Abstrauth authorization server.


### Overview
#### Key Features
#### Core concepts
#### Typical workflow

---

## Installation

It is intended that this component be run using docker.
It supports MySql and will soon also support postgresql and MS SQL Server.

You need to add a database/schema and a user to the database manually.

### Prerequisites

Before installation, ensure you have:

- **Docker** installed and running
- **MySQL 8.0+** database server
- **Network connectivity** between Docker container and MySQL
- **OpenSSL** for generating JWT keys
- **GitHub account** (if pulling from GitHub Container Registry)
- **nginx** or similar for reverse proxying and terminating TLS

### Create the Database, User and Grant Permissions

#### MySQL

This component requires a MySQL database. Create a database and user with the following steps:

1. **Connect to MySQL** as root or admin user:

(change `<password>` to your password)

```bash
docker run -it --rm --network abstratium mysql mysql -h abstratium-mysql --port 3306 -u root -p<password>

DROP USER IF EXISTS 'abstrapact'@'%';

CREATE USER 'abstrapact'@'%' IDENTIFIED BY '<password>';

DROP DATABASE IF EXISTS abstrapact;

CREATE DATABASE abstrapact CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON abstrapact.* TO abstrapact@'%'; -- on own database

FLUSH PRIVILEGES;

EXIT;
```

This project will automatically create all necessary tables and any initial data when it first connects to the database.

New versions will update the database as needed.

### Generate Environment Variables

1. **Generate CSRF Token Signature Key** (64+ characters recommended):
   ```bash
   openssl rand -base64 64 | tr -d '\n'
   ```
   Use this output for `CSRF_TOKEN_SIGNATURE_KEY`.

2. **Generate Cookie Encryption Secret** (32+ characters recommended):
   ```bash
   openssl rand -base64 32
   ```
   Use this output for `COOKIE_ENCRYPTION_SECRET`.

3. **Get the OAuth Client Secret** from the Abstrauth application configuration for `abstratium-abstrapact`.

### Pull and Run the Docker Container

1. **Pull the latest image** from GitHub Container Registry:
   ```bash
   docker pull ghcr.io/abstratium-dev/abstrapact:latest
   ```

2. **Run the container**:

_Replace all placeholder values with the values generated above.

   ```bash
   docker run -d \
     --name abstrapact \
     --network your-network \
     -p 127.0.0.1:41080:8088 \
     -p 127.0.0.1:9010:9010 \
     -e QUARKUS_DATASOURCE_JDBC_URL="jdbc:mysql://your-mysql-host:3306/abstrapact" \
     -e QUARKUS_DATASOURCE_USERNAME="abstrapact" \
     -e QUARKUS_DATASOURCE_PASSWORD="<your-database-password>" \
     -e COOKIE_ENCRYPTION_SECRET="<your-cookie-encryption-secret>" \
     -e CSRF_TOKEN_SIGNATURE_KEY="<your-csrf-signature-key>" \
     -e ABSTRATIUM_CLIENT_SECRET="<your-oauth-client-secret>" \
     -e DEFAULT_ORG_UUID="TODO_YOUR_GENERATED_DEFAULT_ORG_UUID" \
     ghcr.io/abstratium-dev/abstrapact:latest
   ```

   **Required Environment Variables:**
   - `QUARKUS_DATASOURCE_JDBC_URL`: Database connection URL (format: `jdbc:mysql://<host>:<port>/<database>`)
   - `QUARKUS_DATASOURCE_USERNAME`: Database username
   - `QUARKUS_DATASOURCE_PASSWORD`: Database password (use strong, unique password)
   - `COOKIE_ENCRYPTION_SECRET`: Cookie encryption secret (min 32 chars, generate with `openssl rand -base64 32`)
   - `CSRF_TOKEN_SIGNATURE_KEY`: CSRF token signature key (min 32 chars, generate with `openssl rand -base64 64 | tr -d '\n'`)
   - `ABSTRATIUM_TOGGLES_API_URL`: URL of the Abstoggle public API (e.g., `https://toggles.abstratium.dev`, required in production only)
   - `ABSTRATIUM_TOGGLES_CONTEXT`: Context for the Abstoggle public API (e.g., `abstratium-public-...`)
   - `STAGE`: Deployment stage identifier exposed to the frontend (e.g., "dev", "test", "prod", defaults to "dev")
   - `DEFAULT_ORG_UUID`: UUID for the default organisation that existing data is migrated into (generate with `uuidgen`)

   **Optional Environment Variables:**
   - `ABSTRA_WARNING_MESSAGE`: Warning banner message displayed at the top of the UI (e.g., "You are in the TEST environment!"). Set to "-" or omit to hide the banner.
   - `ABSTRA_WARNING_BG_COLOR`: Warning banner background colour (CSS colour value, e.g., `#ff4444` for red). Defaults to `#fff3cd` (amber yellow). Useful for differentiating environments at a glance.
   - `ABSTRA_BRAND_LOGO_URL`: URL of the logo image shown in the header. Defaults to `https://abstratium.dev/abstratium-logo-small.png`.
   - `ABSTRA_BRAND_LOGO_ALT`: Alt text for the header logo image. Defaults to `Abstratium Logo`.
   - `ABSTRA_BRAND_NAME`: Brand name text shown next to the logo in the header. Defaults to `ABSTRATIUM`.
   - `ABSTRA_CURRENCY_CODE`: ISO 4217 currency code used for price formatting in the UI. Defaults to `CHF`. Affects the product simulator, product structure view, and part form.
   - `ABSTRA_CURRENCY_LOCALE`: BCP 47 locale tag used for number formatting (e.g., `en-US`, `de-DE`, `fr-CH`). Defaults to `fr-CH`. Affects the product simulator and product structure view.
   - `ABSTRA_LEGAL_CONTENT_FILE`: **Required for non-abstratium deployments.** Absolute path inside the container to an HTML file containing your organisation's legal page content. When set, this file's contents are served to the frontend and displayed instead of the built-in abstratium legal text — with no misconfiguration warnings. If this variable is not set and the deployment is not on `abstratium.dev`, the legal page will display a prominent error warning to users, and the home page will display a disclaimer stating that abstratium is not responsible for this deployment. Example: `-e ABSTRA_LEGAL_CONTENT_FILE=/config/legal.html -v /host/legal.html:/config/legal.html`.


----

> **⚠ LEGAL NOTICE FOR OPERATORS AND DEPLOYERS**
>
> This software ships with a legal page (`src/main/webui/src/app/core/legal/legal.component.html`)
> that is **specific to abstratium informatique sàrl** and applies **only** to the official deployment
> at **abstratium.dev**.
>
> If you deploy this software on **any other domain**, the legal page will automatically display a
> prominent misconfiguration warning to users, and the abstratium legal text will be visually
> invalidated. However, **you are still legally required** to:
>
> 1. Replace the legal page with one that correctly names **your** organisation as data controller.
> 2. Ensure the page accurately reflects **your** data processing practices, applicable law, and contact details.
> 3. Comply with the GDPR, Swiss revDSG, and any other applicable data protection law in your jurisdiction.
>
> Failure to do so may expose **you** (the operator) to regulatory action. abstratium informatique sàrl
> accepts no liability whatsoever for deployments made by third parties.
>
> See the checklist below for full configuration steps.




3. **Verify the container is running**:
   ```bash
   docker ps
   docker logs abstrapact
   curl http://localhost:4108x/m/health
   curl http://localhost:4108x/m/info
   ```

4. **Access the application**:
   - Main application: http://localhost:4108x
   - Management interface: http://localhost:9010/m/info

## Monitoring and Health Checks

This project provides several endpoints for monitoring:

- **Health Check**: `http://localhost:9010/m/health`
  - Returns application health status
  - Includes database connectivity check

- **Info Endpoint**: `http://localhost:9010/m/info`
  - Returns build information, version, and configuration
  - Useful for verifying deployment

## Troubleshooting

### Container won't start

1. Check Docker logs: `docker logs abstrapact`
2. Verify environment variables are set correctly
3. Ensure database is accessible from container
4. Check network connectivity: `docker network inspect your-network`

### Database connection errors

1. Verify MySQL is running: `mysql -u abstrapact -p -h your-mysql-host`
2. Check firewall rules allow connection on port 3306
3. Verify database user has correct permissions
4. Check JDBC URL format is correct

### JWT token errors

1. Verify keys are correctly base64-encoded
2. Ensure public key matches private key
3. Check key length is at least 2048 bits
4. Verify no extra whitespace in environment variables

## Security Best Practices

1. **Never use default/test keys in production**
2. **Store secrets in secure secret management systems** (e.g., HashiCorp Vault, AWS Secrets Manager)
3. **Use strong, unique passwords** for database and admin accounts
4. **Enable HTTPS** in production (configure reverse proxy)
5. **Regularly update** the Docker image to get security patches
6. **Monitor logs** for suspicious activity
7. **Backup database regularly**
8. **Limit network access** to database and management interface
9. **Rotate JWT keys periodically** (requires user re-authentication)

### Additional Resources

- [RFC 7636 - PKCE](https://datatracker.ietf.org/doc/html/rfc7636)


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
     ghcr.io/abstratium-dev/abstrapact:latest
   ```

   **Required Environment Variables:**
   - `QUARKUS_DATASOURCE_JDBC_URL`: Database connection URL (format: `jdbc:mysql://<host>:<port>/<database>`)
   - `QUARKUS_DATASOURCE_USERNAME`: Database username
   - `QUARKUS_DATASOURCE_PASSWORD`: Database password (use strong, unique password)
   - `COOKIE_ENCRYPTION_SECRET`: Cookie encryption secret (min 32 chars, generate with `openssl rand -base64 32`)
   - `CSRF_TOKEN_SIGNATURE_KEY`: CSRF token signature key (min 32 chars, generate with `openssl rand -base64 64 | tr -d '\n'`)
   - `ABSTRA_WARNING_MESSAGE`: Warning banner message displayed at the top of the UI (e.g., "You are in the TEST environment!"). Set to "-" or leave empty to hide the banner.
   - `STAGE`: Deployment stage identifier exposed to the frontend (e.g., "dev", "test", "prod", defaults to "dev")
   
   **Optional Environment Variables:**
   - `ABSTRA_WARNING_MESSAGE`: Warning banner message displayed at the top of the UI (e.g., "You are in the TEST environment!"). Set to "-" or leave empty to hide the banner.


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


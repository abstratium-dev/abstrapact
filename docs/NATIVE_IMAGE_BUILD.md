# Building Native Image with Docker and Mandrel

This document describes how to build the component as a native executable using Docker with Mandrel, without requiring GraalVM or Mandrel to be installed on your local Ubuntu system.

Commit everything to git.

```bash
./build-docker-image.sh
```

This script will:
1. Build the native executable using Maven with container build
2. Extract the build version from `application.properties`
3. Build the Docker image with both version-specific and `latest` tags

Then run the container (make sure to source your env file first: `source /w/abstratium-TODO.env`) as shown below, in order to test it. For a production deployment, see [../USER_GUIDE.md](USER_GUIDE.md).

Note: The `latest` tag always refers to the most recently built and pushed image. You can also use a specific version tag (e.g., `ghcr.io/abstratium-dev/TODO:20251223212503`).

```bash
docker run -it --rm \
  -p 127.0.0.1:8088:8088 \
  -p 127.0.0.1:9010:9010 \
  --network abstratium \
  -e QUARKUS_DATASOURCE_JDBC_URL=jdbc:mysql://abstratium-mysql:3306/TODO \
  -e QUARKUS_DATASOURCE_USERNAME=TODO \
  -e QUARKUS_DATASOURCE_PASSWORD=secret \
  -e ABSTRATIUM_CLIENT_SECRET="${ABSTRATIUM_CLIENT_SECRET}" \
  -e CSRF_TOKEN_SIGNATURE_KEY="KU/PESqYGdsE0psW7aOaXF/tszvDKCecFo/1u3tSKoQmo4YZfEjZNvUppot1svY1Yj9oub4GSy/5mueqfRlKOw==" \
  -e COOKIE_ENCRYPTION_SECRET="dnde2xhez89RGV0nJHqSR8Khu3SFCE6fxqCgDzu9Hng=" \
  -e OAUTH_REDIRECT_URI="http://localhost:8088/oauth/callback" \
  -e QUARKUS_MANAGEMENT_HOST=0.0.0.0 \
  ghcr.io/abstratium-dev/TODO:latest
```

e2e tests will work against this running image. see dev readme for tips on how to run them manually.

Delete test accounts as follows (which cascade deletes other data like federated identities, roles, credentials, authorization codes, etc.):

```
delete from T_TODO;
```

### Deploy to GitHub Container Registry

After building, the upload is based on https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry

Create a personal access token with `read:packages`, `write:packages` and `delete:packages`. (Settings > Developer Settings > Personal access token > Tokens (classic) > Generate new token classic). Select 30 days.

Export it as follows:

```
export CR_PAT=your_token_here
```

(alternatively add it to `/w/abstratium-TODO.env`)

Run the script named `./push-docker-image.sh`, which also tags the source code and pushes it to GitHub.

You are now finished. Re-install in test and production environments.

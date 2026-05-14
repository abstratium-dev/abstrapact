# Development and Testing

## Update CLI

    jbang version --update
    jbang app install --fresh --force quarkus@quarkusio

## Server

First add env vars:

    source /w/abstratium-TODO.env

That file should contain:

    export ABSTRATIUM_CLIENT_ID="abstratium-TODO"
    export ABSTRATIUM_CLIENT_SECRET="... (taken from the abstrauth application)"
    export CSRF_TOKEN_SIGNATURE_KEY="... (generated with `openssl rand -base64 64 | tr -d '\n'`)"
    export COOKIE_ENCRYPTION_SECRET="... (generated with `openssl rand -base64 32`)"

The application uses Quarkus. Run it with either `./mvnw quarkus:dev` or `quarkus dev` if you have installed the Quarkus CLI.

### Code coverage

    ./mvnw clean verify

Open the jacoco report from `target/jacoco-report/index.html`.

## Trouble Shooting

### Error in Quinoa while running package manager

Use `ng serve` and accept the port it offers, in order to see the actual error messages that are occuring during the build, if you see the following error in Quarkus, and it shows a Quarkus page with the error message `Error restarting Quarkus` and `io.vertx.core.impl.NoStackTraceException`

### Browser says ERR_RESPONSE_HEADERS_MULTIPLE_LOCATION

Delete all the cookies and refresh the page.

## Database

The application uses a MySQL database. It expects a database to be running at `localhost:41040` with the user `root` and password `secret`.

Create the container, the database and user:

```bash
docker run -d \
    --restart unless-stopped \
    --name abstratium-mysql \
    --network abstratium \
    -e MYSQL_ROOT_PASSWORD=secret \
    -p 127.0.0.1:41040:3306 \
    -v /shared2/mysql-abstratium/:/var/lib/mysql:rw \
    mysql:9.3

# create the database and user
docker run -it --rm --network abstratium mysql mysql -h abstratium-mysql --port 3306 -u root -psecret

DROP USER IF EXISTS 'abstracore'@'%';

CREATE USER 'abstracore'@'%' IDENTIFIED BY 'secret';

DROP DATABASE IF EXISTS abstracore;

CREATE DATABASE abstracore CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON abstracore.* TO abstracore@'%'; -- on own database

FLUSH PRIVILEGES;
USE abstracore;
```

exit, then reconnect using the new user:

```bash
docker run -it --network abstratium --rm mysql mysql -h abstratium-mysql --port 3306 -u TODO -psecret TODO
```

# Authorization

See the [USER_GUIDE.md](../USER_GUIDE.md) file and its section called `Account and Role Management` for details on how to set up authorization.

# Testing

## Unit and Integration Tests

Run unit tests (including angular tests):

    mvn test

Run all tests (unit + integration):

    mvn verify

## E2E Testing with Playwright

The E2E tests are in `e2e-tests/` and use Playwright to test the full application stack.

See the [E2E Testing Documentation](../e2e-tests/README.md) for detailed instructions.

It might be easier to test these manually during testing.

Start the component:
```bash
source /w/abstratium-TODO.env
quarkus dev
```

And then the e2e tests:

```bash
cd e2e-tests
npx playwright test --ui
```

And then execute them manually by clicking the play button in the UI which opened.

# Upgrading

## Upgrading the Abstracore Baseline

To merge the latest version of the Abstracore baseline into your project, use the provided sync script:

```bash
./scripts/sync-base.sh
```

This script will:
1. Fetch the latest changes from the upstream Abstracore repository
2. Merge them into your project (with `--no-commit` for review)
3. Update the baseline build timestamp via the pre-commit hook when you commit

The baseline build timestamp is automatically exposed at `/public/config` under `baselineBuildTimestamp`, allowing you to track which version of the baseline is deployed in each application.

After running the script, review the changes and resolve any conflicts before committing.

## Upgrading Dependencies

**THIS SHOULD REALLY BE DONE IN `abstracore` AND NOT IN THE DOWNSTREAM PROJECTS. After that, the sync script will merge the changes into the downstream projects.**

1. Update Quarkus:
```bash
jbang version --update
quarkus update
```

2. Update the quarkus extensions too, if the above doesn't do it - this is done by using the internet to find the latest version of the extension and updating the version in `pom.xml`.

3. Update node/npm using nvm.
Do this if Angular needs a new version.
Search for `nvm` in all the docs in this project and update which version is used, e.g. `v24.11.1`

4. Update Angular:
```bash
cd src/main/webui
nvm use v24.11.1 
ng update
# or 
ng update @angular/cli @angular/core
```

5. Check Github for security problems by signing in and viewing the problems here: https://github.com/abstratium-dev/TODO/security/dependabot and https://github.com/abstratium-dev/TODO/security/code-scanning

# Issues with Webkit

For some strange reason, cookies aren't properly transported when testing localhost with Webkit (e.g. e2e tests, but also manual browser tests). If you sign out and try and sign in again and it doesn't pass the cookies properly and you remain on the sign in page.

The application works fine in production, so just don't test with Webkit locally.

# Building and Releasing

Ensure everything is up to date (see upgrading above).

Ensure docs and especially USER_GUIDE.md is up to date.

Ensure that `mvn verify` is successful.

Start `quarkus dev`

(Alternatively, don't start quarkus or the client example, and set `BASE_URL=http://localhost:808x` in the command line after ALLOW_SIGNUP)

Run `npx playwright test --ui` in the `e2e-tests` directory.

Manually run all the tests - this tests the e2e tests.

(Don't run `mvn verify -Pe2e` since it's a little flakey.)

If that all works, see [NATIVE_IMAGE_BUILD.md](NATIVE_IMAGE_BUILD.md) for instructions on building a native image and releasing it.

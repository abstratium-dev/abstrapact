# End-to-End Tests

This directory contains Playwright-based end-to-end tests.

**IMPORTANT**: E2E tests require the application to be built with H2 database support. 
Since `quarkus.datasource.db-kind` is a build-time property, you must use the `e2e` profile:

    source /w/abstratium-abstrapact.env
    mvn verify -Pe2e

This will:
1. Build the application with H2 database configured
2. Run unit tests, including angular tests
3. Package the JAR with H2 database drivers too
4. Start Quarkus with H2 via `start-e2e-server.sh`
5. Run Playwright tests
6. Stop the server

**Note**: Running `mvn verify` without `-Pe2e` will skip the Playwright tests.


## Directory Structure

### `/pages`
Contains Page Object Model (POM) files that encapsulate page interactions and element locators. These provide reusable functions for common operations.

### `/tests`
The actual test scripts.

⚠️ **IMPORTANT**: If tests in this folder **must run in order** then name them with `1-`, `2-`, etc. prefixes.
The numeric prefixes ensure Playwright runs them alphabetically in the correct order.

### `/start-e2e-server.sh`
Shell script that starts the Quarkus application for e2e testing. It:
- Runs the built JAR file with the `e2e` profile (uses H2 in-memory database)
- Is automatically invoked by Playwright when `BASE_URL` is set

## Environment Variables

### `BASE_URL`
Controls whether Playwright starts the server automatically:
- **Not set**: Assumes server is already running (manual testing mode)
- **Set to `http://localhost:8088`**: Playwright will execute `start-e2e-server.sh` to start the server

## Running Tests

### Manual Testing (Development)
When developing tests or debugging, you can run the server manually and then run tests:

```bash
# Start Quarkus in dev mode
source /w/abstratium-abstrapact.env
mvn quarkus:dev

# In another terminal, run tests
cd e2e-tests
npx playwright test
```

### Maven Integration (CI/CD)

#### Run all e2e tests:
```bash
mvn verify -Pe2e
```

This will:
1. Build the application with H2 database support
2. Package the JAR
3. Start the server via `start-e2e-server.sh`
4. Run tests
6. Stop the server

The `e2e` profile runs both test suites sequentially to ensure complete coverage of all scenarios.

## Configuration

### `playwright.config.ts`
Main Playwright configuration file. Key settings:
- `baseURL`: Defaults to `http://localhost:8088`
- `webServer`: Conditionally starts server when `BASE_URL` is set
- `workers: 1`: Tests run sequentially (not in parallel) to avoid database conflicts
- `forbidOnly`: Prevents `test.only` from being committed to CI

### `pom.xml` Profiles
- **`e2e`**: Runs e2e tests in sequence:

The profile activates the `e2e` Quarkus profile which configures H2 database at build time.

## Writing Tests

### Use Page Objects
Always use functions from the `/pages` directory instead of directly interacting with elements:

```typescript
// ❌ Bad - direct element interaction
await page.locator("#username").fill("admin@abstratium.dev");
await page.locator("#password").fill("password");
await page.locator("#signin-button").click();

// ✅ Good - use page object functions
import { signInAsAdmin } from '../pages/signin.page';
await signInAsAdmin(page);
```

### Wait for Elements
Always wait for elements to be visible before interacting:

```typescript
await page.locator("#username").waitFor({ state: 'visible', timeout: 10000 });
await page.locator("#username").fill("test@example.com");
```

### Test Isolation
Each test should be independent and not rely on state from other tests. The `happy2.spec.ts` test includes database cleanup to ensure a clean state.

## Playwright Commands

    cd e2e-tests

    npx playwright test --ui
     # Starts the interactive UI mode.

    npx playwright test --project=chromium
     # Runs the tests only on Desktop Chrome.

    npx playwright test example
     # Runs the tests in a specific file.

    npx playwright test --debug
     # Runs the tests in debug mode.

    npx playwright codegen
     # Auto generate tests with Codegen.


## Debugging Tests

### Run in headed mode:
```bash
npx playwright test --headed
```

### Run specific test file:
```bash
npx playwright test tests/happy.spec.ts
```

### Run with debug mode:
```bash
npx playwright test --debug
```

### View test report:
```bash
npx playwright show-report
```

## Common Issues

### `ERR_CONNECTION_REFUSED`
The server is not running. Either:
- Start Quarkus manually with `mvn quarkus:dev`, or
- Set `BASE_URL=http://localhost:8088` to let Playwright start the server

### Test timeout waiting for elements
The Angular application may not have loaded yet. Ensure you're waiting for elements with appropriate timeouts:
```typescript
await page.locator("#username").waitFor({ state: 'visible', timeout: 10000 });
```

### Port 8088 already in use
Another instance of Quarkus is running. Stop it before running e2e tests via Maven.

## test.only

Playwright's `test.only` allows running a single test during development:

```typescript
test.only('this test will run', async ({ page }) => {
  // only this test runs
});
```

The `forbidOnly: !!process.env.CI` configuration prevents `test.only` from being accidentally committed, as it would cause other tests to be skipped in CI.

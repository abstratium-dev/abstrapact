#!/bin/bash
# Start Quarkus with e2e profile for e2e tests.
#
# PREREQUISITES:
#   1. Build the JAR:  ./mvnw package -DskipTests
#   2. Install the Stripe CLI:  https://docs.stripe.com/cli
#   3. Set the Stripe test API key env var:
#        export STRIPE_API_KEY=sk_test_...   (from Stripe Dashboard → Developers → API Keys)
#
# The STRIPE_API_KEY env var authenticates the Stripe CLI — no `stripe login` needed.
# Update it when the key expires (every few months).
#
# This script automatically starts the Stripe CLI helper (start-stripe-cli.js) in the
# background if STRIPE_API_KEY is set and the helper is not already running. The helper
# is killed when this script exits.
#
set -x  # Enable debug output
echo "Starting Quarkus for e2e tests..."
echo "Working directory: $(pwd)"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}/.."

echo "Checking if jar exists: target/quarkus-app/quarkus-run.jar"
ls -lh target/quarkus-app/quarkus-run.jar || echo "JAR NOT FOUND!"

# ── Stripe CLI helper ─────────────────────────────────────────────────────────
# Start the helper in the background if STRIPE_API_KEY is set and the helper's
# HTTP server is not already responding on port 19997.
HELPER_PID=""
if [ -n "$STRIPE_API_KEY" ]; then
    if curl -s http://localhost:19997/webhook-secret >/dev/null 2>&1; then
        echo "Stripe CLI helper already running on port 19997."
    else
        echo "Starting Stripe CLI helper (start-stripe-cli.js)..."
        node e2e-tests/start-stripe-cli.js &
        HELPER_PID=$!

        # Wait up to 10 seconds for the helper to capture the secret
        for i in {1..20}; do
            if curl -s http://localhost:19997/webhook-secret >/dev/null 2>&1; then
                echo "Stripe CLI helper ready (secret captured)."
                break
            fi
            sleep 0.5
        done

        if ! curl -s http://localhost:19997/webhook-secret >/dev/null 2>&1; then
            echo "WARNING: Stripe CLI helper did not become ready within 10s."
            echo "         Payment E2E tests may fail."
        fi
    fi
else
    echo "WARNING: STRIPE_API_KEY is not set. Payment E2E tests will fail."
    echo "         Get it from Stripe Dashboard → Developers → API Keys (test mode)."
    echo "         Set it with: export STRIPE_API_KEY=sk_test_..."
fi

# Clean up the helper on exit
cleanup() {
    if [ -n "$HELPER_PID" ] && kill -0 "$HELPER_PID" 2>/dev/null; then
        echo "Stopping Stripe CLI helper (pid $HELPER_PID)..."
        kill "$HELPER_PID" 2>/dev/null || true
    fi
}
trap cleanup EXIT

# Start Quarkus (this will run in foreground).
# The %e2e profile in application.properties points the Stripe API base at the
# real Stripe API (https://api.stripe.com). Webhooks are forwarded by the Stripe CLI
# started above.
exec java -Dquarkus.profile=e2e -jar target/quarkus-app/quarkus-run.jar 2>&1

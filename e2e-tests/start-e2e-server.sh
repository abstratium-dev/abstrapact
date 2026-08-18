#!/bin/bash
# Start Quarkus with e2e profile (H2 database) for e2e tests
# Also starts the example OAuth client on port 3333 and a mock Stripe API on port 19998
set -x  # Enable debug output
echo "Starting Quarkus for e2e tests..."
echo "Working directory: $(pwd)"

# Start the example OAuth client in the background
echo "Starting example OAuth client on port 3333..."
cd ../client-example

if [ ! -d "node_modules" ]; then
  echo "Installing client-example dependencies..."
  npm install
fi
PORT=3333 node server.js > ../e2e-tests/client-example.log 2>&1 > /tmp/client-example.log &
CLIENT_PID=$!
echo "Example client started with PID: $CLIENT_PID"
cd ..

# Start the mock Stripe API server in the background
echo "Starting mock Stripe API on port 19998..."
node e2e-tests/mock-stripe-api.js 19998 > e2e-tests/mock-stripe.log 2>&1 &
STRIPE_PID=$!
echo "Mock Stripe API started with PID: $STRIPE_PID"

echo "Checking if jar exists: target/quarkus-app/quarkus-run.jar"
ls -lh target/quarkus-app/quarkus-run.jar || echo "JAR NOT FOUND!"

# Function to cleanup on exit
cleanup() {
  echo "Cleaning up..."
  if [ ! -z "$CLIENT_PID" ]; then
    echo "Stopping example client (PID: $CLIENT_PID)..."
    kill $CLIENT_PID 2>/dev/null || true
  fi
  if [ ! -z "$STRIPE_PID" ]; then
    echo "Stopping mock Stripe API (PID: $STRIPE_PID)..."
    kill $STRIPE_PID 2>/dev/null || true
  fi
}

# Register cleanup function
trap cleanup EXIT INT TERM

# Start Quarkus (this will run in foreground)
# The mock Stripe API is on port 19998; the Quarkus server reads the API base from
# the e2e profile config in application.properties.
exec java -Dquarkus.profile=e2e -jar target/quarkus-app/quarkus-run.jar 2>&1

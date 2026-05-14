#!/bin/bash

# Configuration
UPSTREAM_URL="git@github.com:abstratium-dev/abstracore.git"
MAIN_BRANCH="main"

# Check if upstream remote exists, if not, add it
if ! git remote | grep -q "upstream"; then
    echo "Adding upstream remote for Abstracore..."
    git remote add upstream "$UPSTREAM_URL"
fi

echo "Fetching updates from Abstracore..."
git fetch upstream

echo "Attempting to merge baseline changes..."
# We use --no-commit to allow the dev to review changes before finalizing
if git merge upstream/"$MAIN_BRANCH" --no-commit --no-ff; then
    echo "✅ Baseline merged successfully. Review changes and commit."
else
    echo "⚠️  Conflicts detected. Please resolve conflicts manually."
    exit 1
fi


#!/usr/bin/env bash
# Run this from the notification-service folder after creating a new repo on GitHub.
# Usage: ./push-to-github.sh YOUR_GITHUB_USERNAME
# Example: ./push-to-github.sh johndoe

set -e
GITHUB_USER="${1:?Usage: ./push-to-github.sh YOUR_GITHUB_USERNAME}"
REPO_NAME="notification-service"

git init
git add .
git commit -m "Initial commit: plan + Spring Boot skeleton"
git remote add origin "https://github.com/${GITHUB_USER}/${REPO_NAME}.git"
git branch -M main
git push -u origin main

echo "Done! Repo: https://github.com/${GITHUB_USER}/${REPO_NAME}"

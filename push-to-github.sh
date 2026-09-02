#!/usr/bin/env bash
#
# One-shot helper: creates the GitHub repository and pushes this project to it.
#
# Requirements:
#   - git            (https://git-scm.com)
#   - GitHub CLI     (https://cli.github.com)  -> run `gh auth login` once
#
# Usage:
#   ./push-to-github.sh                        # repo name defaults to the folder name
#   ./push-to-github.sh my-custom-repo-name
#   VISIBILITY=private ./push-to-github.sh     # default is public
#
set -euo pipefail

REPO_NAME="${1:-TV-Sideload-Shortcut-Maker}"
VISIBILITY="${VISIBILITY:-public}"
DESCRIPTION="Create beautiful Leanback banners and home-screen shortcuts for sideloaded Android TV apps"

cd "$(dirname "$0")"

# --- sanity checks ----------------------------------------------------------
command -v git >/dev/null || { echo "git is not installed"; exit 1; }
command -v gh  >/dev/null || {
  echo "GitHub CLI (gh) is not installed."
  echo "Install it from https://cli.github.com, then run: gh auth login"
  exit 1
}
gh auth status >/dev/null 2>&1 || { echo "Not logged in. Run: gh auth login"; exit 1; }

# --- local repository -------------------------------------------------------
if [ ! -d .git ]; then
  git init -b main
fi

git add .
# `|| true` keeps the script idempotent when there is nothing new to commit.
git commit -m "Initial commit: TV Sideload Shortcut Maker" || true

# --- remote repository ------------------------------------------------------
if git remote get-url origin >/dev/null 2>&1; then
  echo "Remote 'origin' already configured: $(git remote get-url origin)"
  git push -u origin main
else
  gh repo create "$REPO_NAME" \
    --"$VISIBILITY" \
    --source=. \
    --description "$DESCRIPTION" \
    --push
fi

echo
echo "Done. GitHub Actions is now building the APK."
echo "Watch it here:  gh run watch"
echo "Download it:    gh run download --name tv-shortcut-maker-debug"

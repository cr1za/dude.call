#!/data/data/com.termux/files/usr/bin/bash
# Publish the dude.call Android source ZIP to cr1za/dude.call from Termux.
# Usage: bash ~/storage/downloads/dude-call-push-termux.sh [path/to/source.zip]
set -euo pipefail

REPOSITORY='cr1za/dude.call'
REMOTE="https://github.com/${REPOSITORY}.git"
ZIP_PATH="${1:-$HOME/storage/downloads/dude-call-android-source.zip}"

fail() {
    printf '\nERROR: %s\n' "$*" >&2
    exit 1
}

[[ -f "$ZIP_PATH" ]] || fail "Cannot find: $ZIP_PATH. Download the source ZIP to Downloads or provide its path."

if ! command -v pkg >/dev/null 2>&1; then
    fail 'This script must run in Termux, not a regular Linux shell.'
fi

printf '\n== dude.call: GitHub upload from Termux ==\n'
printf 'Repository: %s\nSource ZIP: %s\n\n' "$REPOSITORY" "$ZIP_PATH"

printf 'Installing Git, GitHub CLI, and unzip (if needed)...\n'
pkg install -y git gh unzip

unzip -tqq "$ZIP_PATH" || fail 'ZIP integrity check failed.'
for required in \
    'settings.gradle' \
    'app/build.gradle' \
    'app/src/main/AndroidManifest.xml' \
    '.github/workflows/build-apk.yml'; do
    unzip -Z -1 "$ZIP_PATH" | grep -Fxq "$required" \
        || fail "ZIP is not the expected dude.call project (missing $required)."
done

if ! gh auth status --hostname github.com >/dev/null 2>&1; then
    printf '\nGitHub login required. Complete the browser login for cr1za.\n'
    gh auth login --hostname github.com --git-protocol https --web
fi

gh auth setup-git
LOGIN="$(gh api user --jq .login)"
[[ "${LOGIN,,}" == 'cr1za' ]] || fail "Signed in as $LOGIN, expected cr1za. Run: gh auth switch"

gh repo view "$REPOSITORY" --json name >/dev/null \
    || fail "Cannot access $REPOSITORY using your GitHub login."

WORKDIR="$(mktemp -d "$HOME/dude-call-push.XXXXXXXX")"
trap 'rm -rf "$WORKDIR"' EXIT
mkdir -p "$WORKDIR/source"
unzip -q "$ZIP_PATH" -d "$WORKDIR/source"

printf '\nCloning %s ...\n' "$REPOSITORY"
git clone "$REMOTE" "$WORKDIR/repo"
# This repository is initially empty. This also handles a preexisting main branch.
git -C "$WORKDIR/repo" checkout -B main

# Copy into the repository while preserving unrelated existing files.
cp -R "$WORKDIR/source/." "$WORKDIR/repo/"

git -C "$WORKDIR/repo" add -A
if git -C "$WORKDIR/repo" diff --cached --quiet; then
    printf '\nNothing new to upload; the GitHub project is already up to date.\n'
    exit 0
fi

USER_ID="$(gh api user --jq .id)"
git -C "$WORKDIR/repo" config user.name "$LOGIN"
git -C "$WORKDIR/repo" config user.email "${USER_ID}+${LOGIN}@users.noreply.github.com"

git -C "$WORKDIR/repo" status --short
git -C "$WORKDIR/repo" commit -m 'Add dude.call Android app and APK build workflow'
printf '\nPushing to GitHub...\n'
git -C "$WORKDIR/repo" push -u origin main

printf '\nSUCCESS: Uploaded to https://github.com/%s\n' "$REPOSITORY"
printf 'Build APK: https://github.com/%s/actions\n' "$REPOSITORY"
printf 'The first push should trigger the Android APK workflow if Actions is enabled.\n'

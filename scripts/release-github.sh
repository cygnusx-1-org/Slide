#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
CHANGELOG_OVERRIDE_FILENAME="${REPO_ROOT}/changelog_override.txt"

RELEASE_NOTES="${1:-}"
if [ -z "${RELEASE_NOTES}" ] && [ -f "${CHANGELOG_OVERRIDE_FILENAME}" ]; then
  RELEASE_NOTES="$(cat "${CHANGELOG_OVERRIDE_FILENAME}")"
fi

if [ -z "${RELEASE_NOTES}" ]; then
  echo "Error: release notes are empty and ${CHANGELOG_OVERRIDE_FILENAME} was not found or is empty." >&2
  exit 1
fi

cd "${REPO_ROOT}" || exit 1

# Check return code, and exit with the return code if it is not zero.
# Call with "${?}" on the line immediately after the command being checked.
check_rc() {
  RC="${1}"

  if [ "${RC}" -ne 0 ]; then
    exit "${RC}"
  fi
}

./gradlew assembleWithGPlayReleaseRemote
check_rc "${?}"

# Mirakle does not reliably forward arbitrary environment variables to the remote Gradle
# invocation. Project properties are part of the Gradle command line and survive that handoff.
./gradlew githubRelease "-PreleaseNotes=${RELEASE_NOTES}"
check_rc "${?}"

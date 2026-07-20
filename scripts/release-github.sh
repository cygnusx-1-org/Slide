#!/bin/bash

RELEASE_NOTES="${1}"
export RELEASE_NOTES

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

./gradlew githubRelease
check_rc "${?}"

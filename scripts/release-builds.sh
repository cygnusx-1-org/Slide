#!/bin/bash

# changelog files
CHANGELOG_FILENAME='CHANGELOG.md'
CHANGELOG_OVERRIDE_FILENAME="changelog_override.txt"

# build.gradle
BUILD_GRADLE_FILENAME="app/build.gradle"

# changelog
DATE="$(date +%Y-%-m-%-d)"

COMMIT_MESSAGE_PREFIX='    '
COMMIT_MESSAGE_SPECIAL_PREFIX='\* '

if [ ! -f ${CHANGELOG_OVERRIDE_FILENAME} ]; then
  # Grab commit messages since that tag, matching specific format
  RELEVANT_COMMIT_MESSAGES=`git log $(git describe --tags --abbrev=0)..HEAD | grep "^${COMMIT_MESSAGE_PREFIX}${COMMIT_MESSAGE_SPECIAL_PREFIX}" | sed "s/^${COMMIT_MESSAGE_PREFIX}//g"`
  if [ -z "${RELEVANT_COMMIT_MESSAGES}" ]; then
    echo "Warning: No relevant commit messages found after latest tag"
  fi
else
  RELEVANT_COMMIT_MESSAGES=`cat ${CHANGELOG_OVERRIDE_FILENAME}`
fi

# Building release notes for the Google Play Store and truncating them to avoid going over the max length
PLAYSTORE_RELEASE_NOTES=`echo "${RELEVANT_COMMIT_MESSAGES}" | sed "s/^${COMMIT_MESSAGE_SPECIAL_PREFIX}//g" | head -5`

SEPARATOR='---'

# Section 1: everything before the gradle build — version bump, CHANGELOG, commit, tag.
1() {
  # Block the release first thing if changelog_override.txt was never updated for
  # this release, i.e. it still matches the body of the most recent CHANGELOG.md
  # entry (the previous release).
  if [ -f "${CHANGELOG_OVERRIDE_FILENAME}" ]; then
    PREVIOUS_CHANGELOG_BODY=`awk '
      /^---$/ { seen_sep = 1; next }
      seen_sep && !started && /^[0-9]+(\.[0-9]+)+ \/ / { started = 1; next }
      started && !body && /^=+$/ { body = 1; next }
      body && /^[0-9]+(\.[0-9]+)+ \/ / { exit }
      body { print }
    ' "${CHANGELOG_FILENAME}"`

    if [ "`cat ${CHANGELOG_OVERRIDE_FILENAME}`" = "${PREVIOUS_CHANGELOG_BODY}" ]; then
      echo "Error: ${CHANGELOG_OVERRIDE_FILENAME} still matches the previous release in ${CHANGELOG_FILENAME}." >&2
      echo "Update ${CHANGELOG_OVERRIDE_FILENAME} with this release's changes before releasing." >&2
      exit 1
    fi
  fi

  # versions
  VERSIONCODE=`grep '^        versionCode' app/build.gradle | awk '{ print $2 }'`
  let NEXT_VERSIONCODE=VERSIONCODE+1

  ## Takes versionCode, like "728". Adds dots between the characters, like "7.2.8".
  NEXT_VERSION=`echo ${NEXT_VERSIONCODE} | sed 's/./&./g;s/\.$//'`

  # Update build.gradle
  sed -i "s/ versionCode ${VERSIONCODE}/ versionCode ${NEXT_VERSIONCODE}/g" "${BUILD_GRADLE_FILENAME}"

  VERISON_LINE="${VERSION} / ${DATE}"
  VERSION_LENGTH=${#VERISON_LINE}
  VERSION_SEPARATOR=$(printf '=%.0s' $(seq 1 ${VERSION_LENGTH}))

  CHANGELOG_ENTRY="
${NEXT_VERSION} / ${DATE}
${VERSION_SEPARATOR}
${RELEVANT_COMMIT_MESSAGES}"

  # Update CHANGELOG automatically
  awk -v sep="${SEPARATOR}" -v new_entry="${CHANGELOG_ENTRY}" '{
    print $0
    if ($0 == sep) {
      print new_entry
    }
  }' "${CHANGELOG_FILENAME}" > "${CHANGELOG_FILENAME}.tmp" && mv "${CHANGELOG_FILENAME}.tmp" "${CHANGELOG_FILENAME}"

  # commit
  git add "${BUILD_GRADLE_FILENAME}" "${CHANGELOG_FILENAME}"

  COMMIT_MESSAGE="Updated versionCode in ${BUILD_GRADLE_FILENAME}

Updated ${CHANGELOG_FILENAME}"

  # Make a new commit for a new release
  git commit -m "${COMMIT_MESSAGE}"

  # Creating new tag for the new release
  git tag -a "${NEXT_VERSION}" -m "Version ${NEXT_VERSION}"

  # Push tags to the git repository
  git push --tags
}

# Section 2: the gradle bundle build.
2() {
  # Creating bundle/.aab in app/build/outputs/bundle/withGPlayRelease
  #./gradlew bundleWithGPlayRelease

  #RC="${?}"

  # Check return code, and exit with the return code if it is not zero.
  #if [ "${RC}" -ne 0 ]; then
  #  exit "${RC}"
  #fi
  :
}

# Section 3: everything after the gradle build.
3() {
  # Creating .apk in app/build/outputs/apk/withGPlay, and uploading it to git repository in GitHub as a new release.
  scripts/release-github.sh "${RELEVANT_COMMIT_MESSAGES}"

  git push
}

# Dispatch: run all sections in order by default, or an individual section
# when its name is passed as the argument.
case "${1}" in
  1) 1 ;;
  2) 2 ;;
  3) 3 ;;
  ""|all)
    1
    2
    3
    ;;
  *)
    echo "Usage: ${0} [1|2|3|all]"
    exit 1
    ;;
esac

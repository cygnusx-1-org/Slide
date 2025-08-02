#!/bin/bash

# versions
VERSIONCODE=`grep '^        versionCode' app/build.gradle | awk '{ print $2 }'`
let NEXT_VERSIONCODE=VERSIONCODE+1

## Takes versionCode, like "728". Adds dots between the characters, like "7.2.8".
NEXT_VERSION=`echo ${NEXT_VERSIONCODE} | sed 's/./&./g;s/\.$//'`

# build.gradle
BUILD_GRADLE_FILENAME="app/build.gradle"

# Update build.gradle
sed -i "s/ versionCode ${VERSIONCODE}/ versionCode ${NEXT_VERSIONCODE}/g" "${BUILD_GRADLE_FILENAME}"

# changelog
CHANGELOG_FILENAME='CHANGELOG.md'
DATE="$(date +%Y-%-m-%-d)"

COMMIT_MESSAGE_PREFIX='    '
COMMIT_MESSAGE_SPECIAL_PREFIX='\* '

CHANGELOG_OVERRIDE_FILENAME="changelog_override.txt"
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

# Creating bundle/.aab in app/build/outputs/bundle/withGPlayRelease
./gradlew bundleWithGPlayRelease

RC="${?}"

# Check return code, and exit with the return code if it is not zero.
if [ "${RC}" -ne 0 ]; then
  exit "${RC}"
fi

# Creating .apk in app/build/outputs/apk/withGPlay, and uploading it to git repository in GitHub as a new release.
scripts/release-github.sh "${RELEVANT_COMMIT_MESSAGES}"

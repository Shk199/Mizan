#!/bin/bash
set -e

echo "Building signed Release App Bundle (.aab) and APK (.apk)..."
STORE_PASSWORD="${STORE_PASSWORD:-android}" KEY_PASSWORD="${KEY_PASSWORD:-android}" gradle :app:bundleRelease :app:assembleRelease

echo "Done! Signed release outputs and signing key copy are ready in Release/:"
echo " - Release/app-release.aab (Signed Android App Bundle for Google Play)"
echo " - Release/app-release.apk (Signed APK for direct installation)"
echo " - Release/my-upload-key.jks (Backup copy of the upload signing key)"
echo " - Release/versions/ (Archived versioned builds for every release update)"

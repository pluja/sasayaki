#!/bin/sh
# sign-release.sh signs an unsigned release APK with the release key and the rotation
# proof in signing/lineage, which lets installs signed with the old debug key update.
#
# Usage: scripts/sign-release.sh <unsigned.apk> <signed.apk>
# Needs SASAYAKI_KEYSTORE_PASSWORD; SASAYAKI_KEYSTORE defaults to signing/release.jks.
set -eu

in=$1
out=$2
keystore=${SASAYAKI_KEYSTORE:-signing/release.jks}
: "${SASAYAKI_KEYSTORE_PASSWORD:?SASAYAKI_KEYSTORE_PASSWORD must hold the release keystore password}"
[ -f "$keystore" ] || { echo "Release keystore not found at $keystore (see README, Release signing)" >&2; exit 1; }
[ -f signing/lineage ] || { echo "signing/lineage is missing (see README, Release signing)" >&2; exit 1; }

apksigner=$(ls -d "$ANDROID_HOME"/build-tools/*/apksigner | sort -V | tail -n 1)

# Only v3 carries the lineage. v1 and v2 would have to be signed by the oldest key in it,
# the public debug key, and minSdk 28 means every device reads v3 anyway.
# Preserving the alignment keeps the signed APK byte-identical to the unsigned one outside
# the signature, which F-Droid's reproducible build check (apksigcopier) relies on.
"$apksigner" sign \
    --ks "$keystore" \
    --ks-key-alias sasayaki \
    --ks-pass env:SASAYAKI_KEYSTORE_PASSWORD \
    --key-pass env:SASAYAKI_KEYSTORE_PASSWORD \
    --lineage signing/lineage \
    --rotation-min-sdk-version 28 \
    --v1-signing-enabled false \
    --v2-signing-enabled false \
    --v3-signing-enabled true \
    --v4-signing-enabled false \
    --alignment-preserved \
    --out "$out" \
    "$in"

"$apksigner" verify --verbose --print-certs "$out"

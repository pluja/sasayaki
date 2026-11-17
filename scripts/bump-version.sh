#!/bin/sh
# bump-version.sh sets the release version in app/build.gradle.kts to today's date, or to
# the next same-day patch when today already has a release, and creates the F-Droid
# changelog file for it. Pass a version (20261117 or 20261117.2) to choose it yourself.
#
# Usage: scripts/bump-version.sh [<date>[.<patch>]]
set -eu

gradle_file=app/build.gradle.kts
changelogs=fastlane/metadata/android/en-US/changelogs

current_code=$(sed -n 's/^ *versionCode = \([0-9]*\)$/\1/p' "$gradle_file")
[ -n "$current_code" ] || { echo "No versionCode literal found in $gradle_file" >&2; exit 1; }

if [ $# -gt 0 ]; then
    version=${1#v}
else
    today=$(date +%Y%m%d)
    if [ "${current_code%??}" = "$today" ]; then
        patch=$(( ${current_code#"$today"} + 1 ))
        [ "$patch" -lt 2 ] && patch=2
        version="$today.$patch"
    else
        version=$today
    fi
fi

base=${version%%.*}
patch=0
case $version in *.*) patch=${version#*.} ;; esac

case $base in
    [0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]) ;;
    *) echo "Version must start with a date like 20261117, got $version" >&2; exit 1 ;;
esac
case $patch in
    '' | *[!0-9]*) echo "Patch must be a number, got $patch" >&2; exit 1 ;;
esac
[ "$patch" -le 99 ] || { echo "Patch must be at most 99, got $patch" >&2; exit 1; }

code=$(printf '%s%02d' "$base" "$patch")
[ "$code" -gt "$current_code" ] || { echo "New versionCode $code is not above the current $current_code" >&2; exit 1; }

sed -i \
    -e "s/^\( *versionCode = \)[0-9]*$/\1$code/" \
    -e "s/^\( *versionName = \)\"[^\"]*\"$/\1\"v$version\"/" \
    "$gradle_file"

mkdir -p "$changelogs"
changelog="$changelogs/$code.txt"
[ -f "$changelog" ] || : > "$changelog"

echo "Version v$version (versionCode $code)."
echo "Write the release notes in $changelog (500 characters at most), then commit and tag v$version."

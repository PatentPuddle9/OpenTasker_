#!/usr/bin/env bash
set -euo pipefail

BUILD_SHA="${1:?Usage: generate-release-description.sh BUILD_SHA VERSION OUTPUT_FILE [FALLBACK_BASE_SHA]}"
VERSION="${2:?Missing app version}"
OUTPUT_FILE="${3:?Missing output file}"
FALLBACK_BASE_SHA="${4:-}"
CURRENT_TAG="v$VERSION"

git cat-file -e "$BUILD_SHA^{commit}" 2>/dev/null || {
  echo "Build commit $BUILD_SHA is not available in this checkout." >&2
  exit 1
}

# Prefer the most recent earlier semantic-version release tag that is an ancestor
# of the APK's source commit. This makes the notes describe the actual release
# delta, not unrelated commits added after the APK was built.
git fetch --force --tags origin

mapfile -t ancestor_tags < <(
  git tag --merged "$BUILD_SHA" --list 'v[0-9]*' |
    grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' |
    sort -V
)

previous_tag=""
if (("${#ancestor_tags[@]}" > 0)); then
  mapfile -t ordered_tags < <(
    printf '%s\n' "${ancestor_tags[@]}" "$CURRENT_TAG" | sort -Vu
  )
  for candidate in "${ordered_tags[@]}"; do
    [[ "$candidate" == "$CURRENT_TAG" ]] && break
    previous_tag="$candidate"
  done
fi

BASE_REF=""
BASE_LABEL=""
if [[ -n "$previous_tag" ]]; then
  BASE_REF="$previous_tag"
  BASE_LABEL="since $previous_tag"
elif [[ -n "$FALLBACK_BASE_SHA" ]] &&
     git cat-file -e "$FALLBACK_BASE_SHA^{commit}" 2>/dev/null &&
     [[ "$FALLBACK_BASE_SHA" != "$BUILD_SHA" ]] &&
     git merge-base --is-ancestor "$FALLBACK_BASE_SHA" "$BUILD_SHA"; then
  BASE_REF="$FALLBACK_BASE_SHA"
  BASE_LABEL="since the previous successful build"
else
  parent="$(git rev-list --parents -n 1 "$BUILD_SHA" | cut -d' ' -f2)"
  if [[ -n "$parent" ]]; then
    BASE_REF="$parent"
    BASE_LABEL="in the build commit"
  else
    BASE_LABEL="in the initial repository commit"
  fi
fi

if [[ -n "$BASE_REF" ]]; then
  mapfile -t subjects < <(git log --no-merges --format='%s' "$BASE_REF..$BUILD_SHA")
  mapfile -t changed_files < <(git diff --name-only "$BASE_REF" "$BUILD_SHA" | sort -u)
else
  mapfile -t subjects < <(git log --no-merges --format='%s' -n 30 "$BUILD_SHA")
  mapfile -t changed_files < <(git diff-tree --root --no-commit-id --name-only -r "$BUILD_SHA" | sort -u)
fi

# Keep release notes readable even after a long development interval.
subjects=("${subjects[@]:0:30}")
changed_files=("${changed_files[@]:0:60}")

features=()
fixes=()
improvements=()
maintenance=()
other=()

for subject in "${subjects[@]}"; do
  lower="${subject,,}"
  case "$lower" in
    feat:*|feature:*|add:*|implement:*|support:*|add\ *|implement\ *|support\ *)
      features+=("$subject")
      ;;
    fix:*|bug:*|repair:*|prevent:*|fix\ *|repair\ *|prevent\ *)
      fixes+=("$subject")
      ;;
    improve:*|improvement:*|refactor:*|change:*|update:*|upgrade:*|improve\ *|refactor\ *|update\ *|upgrade\ *)
      improvements+=("$subject")
      ;;
    test:*|tests:*|build:*|ci:*|docs:*|chore:*|test\ *|build\ *|ci\ *|docs\ *|chore\ *)
      maintenance+=("$subject")
      ;;
    *security*|*cve*|*vulnerability*)
      fixes+=("$subject")
      ;;
    *)
      other+=("$subject")
      ;;
  esac
done

areas=()
declare -A seen_areas=()
for path in "${changed_files[@]}"; do
  case "$path" in
    app/src/*|app/*.gradle.kts)
      area="Android app and interface"
      ;;
    core/engine/*|feature/automation/*)
      area="Automation engine and actions"
      ;;
    core/storage/*|core/model/*)
      area="Data, persistence, and models"
      ;;
    app/src/test/*|app/src/androidTest/*|core/*/src/test/*)
      area="Automated tests"
      ;;
    .github/*|tools/*|gradlew|gradle/*|*.gradle.kts|settings.gradle.kts)
      area="Build and repository tooling"
      ;;
    README.md|CHANGELOG.md|docs/*|fastlane/*)
      area="Documentation and store metadata"
      ;;
    *)
      area="Other repository files"
      ;;
  esac

  if [[ -z "${seen_areas[$area]+x}" ]]; then
    areas+=("$area")
    seen_areas["$area"]=1
  fi
done

mkdir -p "$(dirname "$OUTPUT_FILE")"
{
  printf '## What changed\n\n'
  printf 'Automatically detected changes %s for **OpenTasker v%s**.\n\n' "$BASE_LABEL" "$VERSION"

  print_group() {
    local heading="$1"
    shift
    local -a entries=("$@")
    (("${#entries[@]}" == 0)) && return 0
    printf '### %s\n\n' "$heading"
    for entry in "${entries[@]}"; do
      printf -- '- %s\n' "$entry"
    done
    printf '\n'
  }

  print_group "New features" "${features[@]}"
  print_group "Fixes and security" "${fixes[@]}"
  print_group "Improvements" "${improvements[@]}"
  print_group "Maintenance" "${maintenance[@]}"
  print_group "Other changes" "${other[@]}"

  if (("${#subjects[@]}" == 0)); then
    printf 'No new non-merge commit messages were found in the comparison range.\n\n'
  fi

  if (("${#areas[@]}" > 0)); then
    printf '### Areas updated\n\n'
    for area in "${areas[@]}"; do
      printf -- '- %s\n' "$area"
    done
    printf '\n'
  fi

  printf '_Generated from the Git commit history and changed-file list. The bullets above reflect commit subjects; areas are inferred from changed paths._\n'
} > "$OUTPUT_FILE"

echo "Generated release description: $OUTPUT_FILE"
echo "Comparison base: ${BASE_REF:-<root commit>}"
echo "Commits summarized: ${#subjects[@]}"
echo "Files detected: ${#changed_files[@]}"

#!/usr/bin/env bash
# Publish EXIT for every Minecraft version to mavenLocal.
#
#   scripts/publish-all.sh                 the current checkout + every local port/<mc> branch
#   scripts/publish-all.sh 1.20.6 1.16.5   only these ("main" = the current checkout)
#   scripts/publish-all.sh --missing       only versions not yet in mavenLocal
#
# Each port/<mc> branch is checked out once as a worktree under worktrees/<mc> (git-ignored) and reused afterwards;
# a branch already checked out elsewhere is published from that worktree.
set -u

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOGS="$ROOT/worktrees/logs"
MAVEN="${HOME}/.m2/repository/ml/mypals/exit"
mkdir -p "$LOGS"

missing_only=0
if [ "${1:-}" = --missing ]; then missing_only=1; shift; fi
if [ $# -gt 0 ]; then
    targets=("$@")
else
    targets=(main $(git -C "$ROOT" for-each-ref --format='%(refname:strip=3)' 'refs/heads/port/*' | sort -V -r))
fi

# Worktree that already has the branch checked out, if any
existing_worktree() {
    git -C "$ROOT" worktree list --porcelain | awk -v ref="refs/heads/$1" '
        /^worktree /{ dir = substr($0, 10) } $0 == "branch " ref { print dir; exit }'
}

declare -A result
for mc in "${targets[@]}"; do
    if [ "$mc" = main ]; then
        dir="$ROOT"
    else
        dir="$(existing_worktree "port/$mc")"
        if [ -z "$dir" ]; then
            dir="$ROOT/worktrees/$mc"
            if ! git -C "$ROOT" show-ref --verify --quiet "refs/heads/port/$mc"; then
                result[$mc]="no branch port/$mc"; continue
            fi
            git -C "$ROOT" worktree add "$dir" "port/$mc" >/dev/null || { result[$mc]="worktree failed"; continue; }
        fi
    fi

    version="$(sed -n 's/^version=//p' "$dir/gradle.properties" | tr -d '\r')"
    if [ $missing_only = 1 ] && [ -d "$MAVEN/emergency_exit-fabric/$version" ]; then
        result[$mc]="already in mavenLocal ($version)"; continue
    fi

    echo "==> Publishing $mc ($version) from $dir"
    if (cd "$dir" && ./gradlew publishToMavenLocal --console=plain) > "$LOGS/publish-$mc.log" 2>&1; then
        result[$mc]="OK ($version)"
        rm -f "$LOGS/publish-$mc.log"
    else
        result[$mc]="FAILED (see worktrees/logs/publish-$mc.log)"
    fi
done

echo
failed=0
for mc in "${targets[@]}"; do
    printf '%-10s %s\n' "$mc" "${result[$mc]}"
    case "${result[$mc]}" in OK*|already*) ;; *) failed=1 ;; esac
done
exit $failed

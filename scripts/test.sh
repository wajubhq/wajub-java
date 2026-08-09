#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if ! command -v javac >/dev/null 2>&1; then
  cat <<'EOF'
Wajub Java SDK: JDK required (javac not found).

Fedora / RHEL:
  sudo dnf install java-25-openjdk-devel
  # or: sudo dnf install java-17-openjdk-devel

Then set JAVA_HOME and re-run:
  export JAVA_HOME=/usr/lib/jvm/java-25-openjdk   # adjust if you installed 17
  export PATH="$JAVA_HOME/bin:$PATH"
  ./scripts/test.sh

Verify:
  javac -version
  java -version
EOF
  exit 1
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
  JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
  export JAVA_HOME
  export PATH="$JAVA_HOME/bin:$PATH"
fi

exec ./mvnw test "$@"

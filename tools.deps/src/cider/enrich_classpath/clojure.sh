#!/usr/bin/env bash
set -Eeuo pipefail
# sample usage: clojure.sh clojure -Asome-alias <<< "(System/getProperty \"java.class.path\")"

clojure="$1"
# remove it from "$@"/"$*":
shift

file="deps.edn"

if [ ! -e $file ]; then
  echo "$file not found."
  $clojure "$@"
elif [[ "$*" == *Spath* ]]; then
  echo "-Spath was passed; skipping enrich-classpath."
  $clojure "$@"
elif [[ "$*" == *Scp* ]]; then
  echo "-Scp was passed; skipping enrich-classpath."
  $clojure "$@"
else

  here="$PWD"

  # don't let local deps.edn files interfere:
  # XXX prefer a tmp dir. Make x-platform
  cd

  # enrich-classpath will emit a command starting by "clojure", or print a stacktrace:
  output=$(2>&1 "$clojure" -Sforce -Srepro -J-XX:-OmitStackTraceInFastThrow -J-Dclojure.main.report=stderr -Sdeps '{:deps {mx.cider/tools.deps.enrich-classpath {:mvn/version "1.13.0"}}}' -M -m cider.enrich-classpath.clojure "$clojure" "$here" "true" "$@")
  cmd=$(tail -n1 <(echo "$output"))

  cd "$here"

  if grep --silent "^$clojure" <<< "$cmd"; then
    $cmd
  else
    # Print errors:
    echo "$output"
    $clojure "$@"
  fi

fi

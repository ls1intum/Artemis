#!/bin/sh
# Reads changed file paths from stdin and outputs a comma-separated list of Playwright test globs
# relative to src/test/playwright.

set -eu

GLOBS=""

add_glob() {
  new="$1"

  if [ -z "$GLOBS" ]; then
    GLOBS="$new"
    return 0
  fi

  case ",${GLOBS}," in
    *",${new},"*) return 0 ;;
  esac

  GLOBS="${GLOBS},${new}"
}

add_all() {
  # If we already decided to run all, keep it as the only glob.
  if [ "$GLOBS" = "e2e/**/*.spec.ts" ]; then
    return 0
  fi
  GLOBS="e2e/**/*.spec.ts"
}

while IFS= read -r file; do
  [ -z "$file" ] && continue

  case "$file" in
    # Any changes to CI / docker / Playwright infrastructure => run all
    .github/*|.ci/*|docker/*|src/test/playwright/*|package.json|package-lock.json|angular.json|gradle*|build.gradle|settings.gradle)
      add_all
      ;;

    # Direct changes inside Playwright tests
    src/test/playwright/e2e/*.spec.ts)
      add_glob "${file#src/test/playwright/}"
      ;;
    src/test/playwright/e2e/*/*.spec.ts|src/test/playwright/e2e/*/*/*.spec.ts|src/test/playwright/e2e/*/*/*/*.spec.ts)
      suite_dir=$(printf "%s" "$file" | awk -F/ '{print $5}')
      if [ -n "$suite_dir" ]; then
        add_glob "e2e/${suite_dir}/**/*.spec.ts"
      else
        add_all
      fi
      ;;

    # Client feature folders
    src/main/webapp/app/exam/*|src/main/webapp/app/exam/*/*|src/main/webapp/app/exam/*/*/*|src/main/webapp/app/exam/*/*/*/*)
      add_glob "e2e/exam/**/*.spec.ts"
      ;;
    src/main/webapp/app/course/*|src/main/webapp/app/course/*/*|src/main/webapp/app/course/*/*/*|src/main/webapp/app/course/*/*/*/*)
      add_glob "e2e/course/**/*.spec.ts"
      ;;
    src/main/webapp/app/lecture/*|src/main/webapp/app/lecture/*/*|src/main/webapp/app/lecture/*/*/*|src/main/webapp/app/lecture/*/*/*/*)
      add_glob "e2e/lecture/**/*.spec.ts"
      ;;
    src/main/webapp/app/atlas/*|src/main/webapp/app/atlas/*/*|src/main/webapp/app/atlas/*/*/*|src/main/webapp/app/atlas/*/*/*/*)
      add_glob "e2e/atlas/**/*.spec.ts"
      ;;

    # Cross-cutting client folders (too broad to map safely)
    src/main/webapp/app/shared/*|src/main/webapp/app/shared/*/*|src/main/webapp/app/core/*|src/main/webapp/app/core/*/*|src/main/webapp/app/admin/*|src/main/webapp/app/admin/*/*|src/main/webapp/app/entities/*|src/main/webapp/app/entities/*/*)
      add_all
      ;;

    # Server feature folders (best-effort mapping)
    src/main/java/*/artemis/exam/*|src/main/java/*/artemis/exam/*/*|src/main/java/*/artemis/exam/*/*/*)
      add_glob "e2e/exam/**/*.spec.ts"
      ;;
    src/main/java/*/artemis/course/*|src/main/java/*/artemis/course/*/*|src/main/java/*/artemis/course/*/*/*)
      add_glob "e2e/course/**/*.spec.ts"
      ;;
    src/main/java/*/artemis/lecture/*|src/main/java/*/artemis/lecture/*/*|src/main/java/*/artemis/lecture/*/*/*)
      add_glob "e2e/lecture/**/*.spec.ts"
      ;;

    # Otherwise: leave unmapped (no-op)
    *)
      :
      ;;
  esac

done

printf "%s" "$GLOBS"

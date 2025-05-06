#!/usr/bin/env bash
set -euo pipefail

# Where to write the report
OUTPUT_FILE="openapi_status.log"
LOG_DIR="openapi_logs"
# Where SpringDoc writes its spec
SPEC_FILE="openapi/openapi.yaml"

# Fresh start
: > "$OUTPUT_FILE"
rm -rf "$LOG_DIR" && mkdir -p "$LOG_DIR"

# Strip ANSI escape codes
strip_ansi() {
  sed -r "s/\x1B\[[0-9;]*[mK]//g"
}

# 1) Discover all @RestController packages
mapfile -t packages < <(
  grep -R --include='*.java' -l '@RestController' src/main/java \
    | xargs -n1 sed -nE 's/^[[:space:]]*package[[:space:]]+([^;]+);/\1/p' \
    | sort -u
)

if [ ${#packages[@]} -eq 0 ]; then
  echo "No packages with @RestController found under src/main/java." >&2
  exit 1
fi

total=${#packages[@]}
echo "Found $total package(s) with @RestController:"
for pkg in "${packages[@]}"; do
  echo "  • $pkg"
done
echo

# 2) Initialize counters
idx=0
succ_docs=0
fail_docs=0
succ_gen=0
fail_gen=0

# 3) Process each package
for pkg in "${packages[@]}"; do
  idx=$((idx+1))
  echo "[${idx}/${total}] Processing package: $pkg"
  safe_pkg=${pkg//./_}

  # a) generateApiDocs
  log1="$LOG_DIR/${safe_pkg}_generateApiDocs.log"
  if ./gradlew --console=plain generateApiDocs -x webapp \
       -Dspringdoc.packages-to-scan="$pkg" \
       2>&1 | strip_ansi >"$log1"; then
    s1="SUCCESS"
    succ_docs=$((succ_docs+1))
  else
    s1="FAILURE"
    fail_docs=$((fail_docs+1))
  fi

  # b) openApiGenerate
  log2="$LOG_DIR/${safe_pkg}_openApiGenerate.log"
  if ./gradlew --console=plain openApiGenerate \
       2>&1 | strip_ansi >"$log2"; then
    s2="SUCCESS"
    succ_gen=$((succ_gen+1))
  else
    s2="FAILURE"
    fail_gen=$((fail_gen+1))
    # Save the generated spec for inspection
    if [ -f "$SPEC_FILE" ]; then
      cp "$SPEC_FILE" "$LOG_DIR/${safe_pkg}_openapi.yaml"
    fi
  fi

  # 4) Append summary line
  echo "$pkg: generateApiDocs=$s1, openApiGenerate=$s2" >> "$OUTPUT_FILE"

  # 5) Embed logs (and saved spec path) on failure
  if [ "$s1" = "FAILURE" ]; then
    {
      echo "---- Logs for generateApiDocs on $pkg ----"
      cat "$log1"
      echo
    } >> "$OUTPUT_FILE"
  fi
  if [ "$s2" = "FAILURE" ]; then
    {
      echo "---- Logs for openApiGenerate on $pkg ----"
      cat "$log2"
      echo
      echo "---- Saved OpenAPI spec for $pkg at $LOG_DIR/${safe_pkg}_openapi.yaml ----"
      echo
    } >> "$OUTPUT_FILE"
  fi

  # 6) Inline feedback
  echo "    → generateApiDocs: $s1, openApiGenerate: $s2"
  echo
done

# 7) Final summary
echo "All done."
echo "  Packages processed: $total"
echo "  generateApiDocs → succeeded: $succ_docs, failed: $fail_docs"
echo "  openApiGenerate → succeeded: $succ_gen, failed: $fail_gen"
echo
echo "Detailed status: $OUTPUT_FILE"
echo "Logs & specs:   $LOG_DIR/"

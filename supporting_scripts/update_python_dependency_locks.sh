#!/usr/bin/env bash
# Regenerate complete, cross-platform Python locks. Requires uv 0.12.23 or newer.
set -Eeuo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
command -v uv >/dev/null || { printf 'uv is required to generate Python dependency locks.\n' >&2; exit 1; }

for directory in \
    supporting_scripts/code-coverage/generate_code_cov_table \
    supporting_scripts/course-scripts/exam-automation \
    supporting_scripts/course-scripts/quick-course-setup \
    supporting_scripts/database_approval_check \
    supporting_scripts/hyperion/consistency-check-benchmark \
    supporting_scripts/lecture-transcription \
    .github/python-dependencies; do
    python_version=3.10
    if [[ "$directory" == .github/python-dependencies ]]; then
        python_version=3.14
    fi
    # Keep paths relative to the lock so Renovate can replay the generated command in that directory.
    (
        cd "$directory"
        uv pip compile requirements.in --universal --python-version "$python_version" \
            --generate-hashes --output-file requirements.txt "$@"
    )
done

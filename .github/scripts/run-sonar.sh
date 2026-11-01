#!/usr/bin/env bash
set -Eeuo pipefail
: "${SONAR_REVISION:?Missing analyzed source revision}"
: "${SONAR_BRANCH_NAME:?Missing analyzed branch}"
: "${SONAR_TOKEN:?Missing Sonar analysis token}"
tasks=(compileJava)
args=("-Dsonar.scm.revision=${SONAR_REVISION}" -Dsonar.qualitygate.wait=true -Dsonar.qualitygate.timeout=900)
if [[ "${SONAR_SELECTED_SERVER:-false}" == true ]]; then
    script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
    args+=(--init-script "${script_dir}/sonar-pr-coverage.gradle")
    tasks+=(sonarPrCoverage)
    args+=("-Dsonar.coverage.jacoco.xmlReportPaths=build/reports/jacoco/sonar-pr/jacocoTestReport.xml")
fi
lcov=()
for report in build/test-results/vitest/coverage/lcov.info \
    build/test-results/vitest/sonar-coverage/normal/lcov.info \
    build/test-results/vitest/sonar-coverage/monaco/lcov.info; do
    [[ ! -s "$report" ]] || lcov+=("$report")
done
if ((${#lcov[@]})); then
    args+=("-Dsonar.javascript.lcov.reportPaths=$(IFS=,; echo "${lcov[*]}")")
fi
if [[ "${SONAR_PULL_REQUEST:-0}" != 0 ]]; then
    : "${SONAR_BASE_BRANCH:?Missing pull request target}"
    args+=("-Dsonar.pullrequest.key=${SONAR_PULL_REQUEST}" "-Dsonar.pullrequest.branch=${SONAR_BRANCH_NAME}" "-Dsonar.pullrequest.base=${SONAR_BASE_BRANCH}")
else
    args+=("-Dsonar.branch.name=${SONAR_BRANCH_NAME}")
fi
./gradlew "${tasks[@]}" sonar -x webapp "${args[@]}"


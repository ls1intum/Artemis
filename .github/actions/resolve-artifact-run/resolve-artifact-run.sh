#!/usr/bin/env bash
# Resolve the CI run that actually carries a named, non-expired artifact for an exact commit.
# Deploy paths that DOWNLOAD an artifact (e.g. Artemis.war) can't use the registry as truth — the
# artifact lives on one run, and a commit can have several (synchronize, ready_for_review, a
# superseded re-run). `.workflow_runs[0]` fails when an artifact-less run sorts first even though a
# sibling has it (Helios#1196). Enumerate all runs for the SHA, newest first, return the first with
# a non-expired copy. Inputs are documented in action.yml.
set -Eeuo pipefail

: "${REPO:?REPO is required}"
: "${WORKFLOW:?WORKFLOW is required}"
: "${SHA:?SHA is required}"
: "${ARTIFACT_NAME:?ARTIFACT_NAME is required}"

# The runs API filters by full 40-char head_sha; an abbreviated value silently matches nothing.
if [[ ! "${SHA}" =~ ^[0-9a-f]{40}$ ]]; then
  echo "::error::Expected a full 40-char commit SHA, got: '${SHA}'." >&2
  exit 1
fi

# Build the runs query with `-f` params so gh URL-encodes the values (no query injection via an
# attacker-influenced branch name). `-X GET` is required: `-f` otherwise makes gh issue a POST.
runs_args=(-f "head_sha=${SHA}" -f "per_page=100")
[ -n "${BRANCH:-}" ] && runs_args+=(-f "branch=${BRANCH}")

run_id=""
while read -r id; do
  [ -n "${id}" ] || continue
  fresh=$(gh api -X GET "repos/${REPO}/actions/runs/${id}/artifacts" -f "name=${ARTIFACT_NAME}" \
    --jq '[.artifacts[] | select(.expired == false)] | length')
  if [ "${fresh:-0}" -gt 0 ]; then
    run_id="${id}"
    break
  fi
done < <(gh api -X GET "repos/${REPO}/actions/workflows/${WORKFLOW}/runs" "${runs_args[@]}" \
  --jq '.workflow_runs[].id')

if [ -z "${run_id}" ]; then
  echo "::error::No CI run for ${SHA} carries a non-expired '${ARTIFACT_NAME}' artifact." >&2
  echo "::error::The build for this commit did not publish it, or the artifact has expired." >&2
  exit 1
fi

echo "✓ Resolved run ${run_id} (has '${ARTIFACT_NAME}') for ${SHA}"
if [ -n "${GITHUB_OUTPUT:-}" ]; then
  echo "run_id=${run_id}" >> "${GITHUB_OUTPUT}"
fi

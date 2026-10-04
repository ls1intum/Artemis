#!/usr/bin/env bash
# Verify a deployable image exists for an exact commit: the immutable `sha-<commit>` tag is the
# source of truth (not a per-run `docker-tag` artifact), so a superseded/cancelled CI run for the
# same commit can't cause a false negative (Helios#1196). Inputs are documented in action.yml.
set -Eeuo pipefail

: "${IMAGE:?IMAGE is required}"
: "${SHA:?SHA is required}"

# A commit SHA is 40 lowercase hex; this both guards against tag injection and rejects abbreviated
# values (the tag is the full sha-<commit>, so a short SHA would look up a tag that was never pushed).
if [[ ! "${SHA}" =~ ^[0-9a-f]{40}$ ]]; then
  echo "::error::Expected a full 40-char commit SHA, got: '${SHA}'." >&2
  exit 1
fi

registry="${IMAGE%%/*}"
ref="${IMAGE}:sha-${SHA}"

if [ -n "${GH_TOKEN:-}" ]; then
  printf '%s' "${GH_TOKEN}" \
    | docker login "${registry}" -u "${REGISTRY_USER:-github-actions}" --password-stdin >/dev/null
fi

if docker manifest inspect "${ref}" >/dev/null 2>&1; then
  echo "✓ Deployable image present for ${SHA}: ${ref}"
  exit 0
fi

echo "::error::No deployable image ${ref}." >&2
echo "::error::The 'Build and Push Docker Image' job for commit ${SHA} has not published (yet)." >&2
echo "::error::Wait for that commit's CI image build to finish, then re-trigger the deployment." >&2
exit 1

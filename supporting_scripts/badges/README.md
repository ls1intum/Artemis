# Self-hosted README badges

Artemis generates its coverage, latest release and MIT license badges as standalone SVGs using
Node 24 built-ins. They use system monospace text with no downloaded fonts or hosted renderer.
The README loads `coverage.svg`, `latest-version.svg` and `license.svg` directly from the orphan
`badges` branch via `raw.githubusercontent.com`.

## Render locally

In a checkout of the `badges` branch that already contains the authoritative `coverage.json`:

```bash
node supporting_scripts/badges/render-badges.mjs coverage /path/to/badges-checkout
GITHUB_REPOSITORY=ls1intum/Artemis GITHUB_TOKEN="$(gh auth token)" \
  node supporting_scripts/badges/render-badges.mjs metadata /path/to/badges-checkout
```

The `coverage` mode uses the existing JSON's validated percentage and color. The `metadata` mode
queries all GitHub release pages, selects the highest stable numeric Artemis release (two or three
version components, optional `v` prefix), renders the static MIT badge, and renders coverage when
its JSON exists. Drafts, prereleases, development suffixes and scoped package releases are ignored.
Inputs are validated before files are written. An unavailable or invalid source leaves the prior
SVGs untouched and returns a nonzero exit code for the workflow to report as a warning.

## Publish and bootstrap

`ci.yml` computes `coverage.json` from a green develop run and renders its SVG, including when
the percentage is unchanged. `publish-badges.yml` handles the release and license badges on release
events, daily and by manual dispatch; it also renders the existing coverage JSON so migration does
not require another test run. Dispatch **Publish README Badges** after deploying the workflow to
create all three SVGs. If the `badges` branch is missing, first follow the
[coverage seed instructions](../code-coverage/coverage-badge/README.md#re-seeding-the-badges-branch).

Both workflows call the shared publisher in an isolated `badges` checkout:

```bash
node supporting_scripts/badges/publish-badges.mjs /path/to/badges-checkout coverage.json coverage.svg
```

It commits only changed named files, never force-pushes, and retries up to three pushes, fetching
and rebasing over concurrent updates. Conflicts and network failures leave the remote files intact.
The workflows log a warning and remain advisory. No external badge service or extra credentials
are required: only the built-in Actions token with `contents: write` in publishing jobs.

## Tests

```bash
node --test supporting_scripts/badges/badges.test.mjs
```

Tests cover release filtering and pagination, SVG escaping, bad source retention, and real local
Git repositories with overlapping writers and conflicts. The workflow lint job runs these without
installing npm dependencies.

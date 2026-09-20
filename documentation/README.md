# Website

This website is built using [Docusaurus](https://docusaurus.io/), a modern static website generator.

## Installation

```bash
corepack enable          # one-time: activate the pnpm version pinned in package.json
pnpm install
```

## Local Development

```bash
pnpm start
```

This command starts a local development server and opens up a browser window. Most changes are reflected live without having to restart the server.

## Build

```bash
pnpm run build
```

This command generates the Docusaurus site in the `build` directory.

To test the build locally, you can run:

```bash
pnpm run serve
```

To build and serve the complete deployed site, including the TUM UI Storybook at
`/developer/tum-ui/`, run from the repository root:

```bash
pnpm install --frozen-lockfile
pnpm --dir documentation install --frozen-lockfile
pnpm run docs:build
pnpm run docs:serve
```

`pnpm run docs:test` builds the same combined artifact and checks the developer-guide navigation into
Storybook and back.

## Deployment

We are using GitHub pages to host the website.

The develop-branch deployment is defined in [ci.yml](../.github/workflows/ci.yml).
[deploy-documentation.yml](../.github/workflows/deploy-documentation.yml) provides the manual
redeploy path; both use [ci-docs.yml](../.github/workflows/ci-docs.yml) to build the same artifact.

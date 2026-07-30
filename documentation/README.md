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

This command generates the Docusaurus site in the `build` directory. The deployment workflow also
builds the TUM UI Storybook from `packages/tum-ui` and adds it at `/tum-ui/` before publishing the
site.

To test the build locally, you can run:

```bash
pnpm run serve
```

## Deployment

We are using GitHub pages to host the website.

The develop-branch deployment is defined in [ci.yml](../.github/workflows/ci.yml).
[deploy-documentation.yml](../.github/workflows/deploy-documentation.yml) provides the manual
redeploy path; both use [ci-docs.yml](../.github/workflows/ci-docs.yml) to build the same artifact.

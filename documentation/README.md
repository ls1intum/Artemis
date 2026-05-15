# Website

This website is built using [Docusaurus](https://docusaurus.io/), a modern static website generator.

## Installation

```bash
bun install
```

Bun must be installed first — see [bun.sh/install](https://bun.sh/install) or run `brew install oven-sh/bun/bun` on macOS.

## Local Development

```bash
bun start
```

This command starts a local development server and opens up a browser window. Most changes are reflected live without having to restart the server.

## Build

```bash
bun run build
```

This command generates static content into the `build` directory and can be served using any static contents hosting service.

To test the build locally, you can run:
```bash
bun run serve
```

## Deployment

We are using GitHub pages to host the website.

See [deploy-documentation.yml](../.github/workflows/deploy-documentation.yml) for the deployment configuration.

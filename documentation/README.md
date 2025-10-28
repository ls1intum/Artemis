# Website

This website is built using [Docusaurus](https://docusaurus.io/), a modern static website generator.

## Installation

```bash
npm install
```

## Local Development

```bash
npm start
```

This command starts a local development server and opens up a browser window. Most changes are reflected live without having to restart the server.

## Build

```bash
npm run build
```

This command generates static content into the `build` directory and can be served using any static contents hosting service.

To test the build locally, you can run:
```bash
npm run serve
```

## Deployment

We are using GitHub pages to host the website.

See [deploy-documentation.yml](../.github/workflows/deploy-documentation.yml) for the deployment configuration.

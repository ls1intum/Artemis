/**
 * Shadow the repo-root `.postcssrc.json` (which loads `@tailwindcss/postcss` for the Angular webapp build).
 *
 * PostCSS auto-discovers config by walking up from the processed file; without this, the Docusaurus build's
 * postcss-loader walks up to the repo root, finds that config, and tries to load `@tailwindcss/postcss` — which the
 * docs CI does not install (it installs only `documentation/package.json`), so the build fails with
 * "Cannot find module '@tailwindcss/postcss'". This empty config is found first and stops the walk. Docusaurus adds
 * its own autoprefixer via webpack, so no plugins are needed here and the docs CSS is unchanged.
 */
module.exports = { plugins: {} };

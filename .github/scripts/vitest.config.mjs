/**
 * Vitest config for the CI helper scripts in this directory (.github/scripts/*.spec.mjs).
 *
 * These are plain-Node CommonJS scripts run by actions/github-script, not client code — the repo's
 * top-level vitest.config.ts globs only `src/main/webapp/app` under the Angular Vite/jsdom environment,
 * which does not apply here. Mirrors rules/vitest.config.mjs. Run with:
 *
 *   pnpm exec vitest run --config .github/scripts/vitest.config.mjs
 */
import { defineConfig } from 'vitest/config';

export default defineConfig({
    test: {
        include: ['.github/scripts/**/*.spec.mjs'],
        environment: 'node',
    },
});

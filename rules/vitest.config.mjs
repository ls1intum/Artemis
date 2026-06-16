/**
 * Vitest config for the custom ESLint rule unit tests in this directory.
 *
 * The repo's top-level vitest.config.ts globs only `src/main/webapp/app/**​/*.spec.ts` (the Angular
 * client) and uses the Angular Vite plugin / jsdom — neither of which applies to these plain-Node
 * `.spec.mjs` rule tests. This standalone config keeps them isolated and runnable on their own:
 *
 *   pnpm exec vitest run --config rules/vitest.config.mjs
 */
import { defineConfig } from 'vitest/config';

export default defineConfig({
    test: {
        include: ['rules/**/*.spec.mjs'],
        environment: 'node',
    },
});

/**
 * Vitest config for the plain-Node unit tests of the custom ESLint rules in this directory and of
 * the build helpers in `supporting_scripts/`.
 *
 * The repo's top-level vitest.config.ts globs only the Angular client's `.spec.ts` files under
 * `src/main/webapp/app` and uses the Angular Vite plugin / jsdom - neither of which applies to these plain-Node
 * `.spec.mjs` tests. This standalone config keeps them isolated and runnable on their own:
 *
 *   pnpm exec vitest run --config rules/vitest.config.mjs
 */
import { defineConfig } from 'vitest/config';

export default defineConfig({
    test: {
        include: ['rules/**/*.spec.mjs', 'supporting_scripts/**/*.spec.mjs'],
        environment: 'node',
    },
});

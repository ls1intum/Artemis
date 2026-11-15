import { defineConfig } from 'vitest/config';

// E2E harness contracts run in Node without the Angular application's setup or browser services.
export default defineConfig({
    test: {
        include: ['supporting_scripts/ci/E2E-tests/*.spec.mjs'],
        environment: 'node',
    },
});
